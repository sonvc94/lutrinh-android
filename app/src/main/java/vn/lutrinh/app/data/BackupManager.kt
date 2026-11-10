package vn.lutrinh.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.usecase.BackupCounts
import vn.lutrinh.app.domain.usecase.BackupException
import vn.lutrinh.app.domain.usecase.BackupManifest
import vn.lutrinh.app.domain.usecase.BackupProblem
import vn.lutrinh.app.domain.usecase.BackupRules
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * R26: one zip file with `manifest.json`, `lutrinh.db` (a consistent copy of every table, made
 * inside one transaction into a fresh database with the app's schema) and `photos/<file>` for
 * every stored photo (covers are photos too, R21). Restore checks the file, migrates an older
 * schema with the app's own migrations on a staging copy, then replaces every table in ONE
 * transaction: on any failure the current data stays as it was. Offline map packs, the
 * gazetteer and on-device flags are not user data and are not in the file.
 */
class BackupManager(
    context: Context,
    private val db: AppDatabase,
    private val photoDir: File,
    private val appVersion: String,
    private val workDir: File = File(context.cacheDir, "backup"),
) {
    private val context = context.applicationContext

    /** Test hook: called after each table is copied during a restore (throw to simulate a failure). */
    internal var afterRestoreTable: ((String) -> Unit)? = null

    /** A checked backup, staged in the cache and ready to [restore]. */
    /** [contents] = what the file holds, counted in its own database (a manifest of schema ≤ 7 has no video count). */
    data class Prepared(val manifest: BackupManifest, val contents: BackupCounts, val current: BackupCounts, internal val dir: File)

    /** Rows on the device now. */
    suspend fun currentCounts(): BackupCounts = withContext(Dispatchers.IO) { counts(db.openHelper.writableDatabase) }

    /** Writes a backup of all user data to [out]. */
    suspend fun write(out: OutputStream, now: Instant = Instant.now()): BackupManifest = withContext(Dispatchers.IO) {
        val dir = fresh("out")
        try {
            val snap = File(dir, BackupRules.DB_ENTRY)
            val copy = open(snap)
            val counts: BackupCounts
            val files: List<String>
            try {
                val dst = copy.openHelper.writableDatabase
                // One transaction on the app database: every table from the same moment.
                db.runInTransaction {
                    val src = db.openHelper.writableDatabase
                    dst.beginTransaction()
                    try {
                        for (t in TABLES) copyTable(src, dst, t)
                        dst.setTransactionSuccessful()
                    } finally {
                        dst.endTransaction()
                    }
                }
                counts = counts(dst)
                files = dst.query("SELECT DISTINCT file_name FROM photos ORDER BY file_name").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            } finally {
                copy.close()
            }
            val present = files.filter { BackupRules.isPhotoName(it) && File(photoDir, it).isFile }
            val manifest = BackupManifest(
                appId = context.packageName, appVersion = appVersion, schemaVersion = AppDatabase.VERSION,
                createdAt = now.truncatedTo(ChronoUnit.SECONDS).toString(), counts = counts,
                photoFiles = present.size, missingPhotoFiles = files.size - present.size,
            )
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BackupRules.MANIFEST_ENTRY))
                zip.write(toJson(manifest).toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupRules.DB_ENTRY))
                snap.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                for (name in present) {
                    zip.putNextEntry(ZipEntry(BackupRules.PHOTO_PREFIX + name))
                    File(photoDir, name).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            Log.i(TAG, "backup: $manifest")
            manifest
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Copies [input] to the cache and checks it (manifest, schema, database, photo files). Nothing
     * on the device changes. Throws [BackupException].
     */
    suspend fun prepare(input: InputStream): Prepared = withContext(Dispatchers.IO) {
        val dir = fresh("in")
        try {
            val zipFile = File(dir, "backup.zip")
            zipFile.outputStream().use { input.copyTo(it) }
            val manifest: BackupManifest
            val contents: BackupCounts
            val photosDir = File(dir, "photos").apply { mkdirs() }
            val staged = File(dir, BackupRules.DB_ENTRY)
            val zip = try { ZipFile(zipFile) } catch (e: Exception) { throw BackupException(BackupProblem.NotABackup, e) }
            zip.use { z ->
                val m = z.getEntry(BackupRules.MANIFEST_ENTRY) ?: throw BackupException(BackupProblem.NotABackup)
                manifest = try {
                    fromJson(JSONObject(z.getInputStream(m).use { it.readBytes().toString(Charsets.UTF_8) }))
                } catch (e: Exception) {
                    throw BackupException(BackupProblem.NotABackup, e)
                }
                BackupRules.check(manifest, AppDatabase.VERSION)?.let { throw BackupException(it) }
                val dbEntry = z.getEntry(BackupRules.DB_ENTRY) ?: throw BackupException(BackupProblem.Damaged("no database"))
                z.getInputStream(dbEntry).use { i -> staged.outputStream().use { i.copyTo(it) } }
                var photos = 0
                for (e in z.entries()) {
                    if (e.isDirectory || !e.name.startsWith(BackupRules.PHOTO_PREFIX)) continue
                    val name = e.name.removePrefix(BackupRules.PHOTO_PREFIX)
                    if (!BackupRules.isPhotoName(name)) throw BackupException(BackupProblem.Damaged("photo name"))
                    z.getInputStream(e).use { i -> File(photosDir, name).outputStream().use { i.copyTo(it) } }
                    photos++
                }
                if (photos != manifest.photoFiles) throw BackupException(BackupProblem.Damaged("photos $photos/${manifest.photoFiles}"))
            }
            zipFile.delete()
            val version = userVersion(staged)
            if (version != manifest.schemaVersion) throw BackupException(BackupProblem.Damaged("schema $version"))
            // Opening with Room migrates an older copy (the app's own migrations) and validates the schema.
            val copy = open(staged)
            try {
                val counts = try {
                    counts(copy.openHelper.writableDatabase)
                } catch (e: Exception) {
                    throw BackupException(BackupProblem.Damaged("database"), e)
                }
                if (counts.rows() != manifest.counts.rows()) throw BackupException(BackupProblem.Damaged("counts $counts"))
                contents = counts
            } finally {
                copy.close()
            }
            Prepared(manifest, contents, currentCounts(), dir)
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /**
     * Replaces ALL user data with [prepared] (R26): new photo files are added first, then every
     * table is emptied and refilled in one transaction; only after it commits are the photo files
     * no row refers to any more deleted. A failure rolls everything back and removes the added
     * files. Returns the counts now on the device.
     */
    suspend fun restore(prepared: Prepared): BackupCounts = withContext(Dispatchers.IO) {
        val added = ArrayList<File>()
        try {
            photoDir.mkdirs()
            File(prepared.dir, "photos").listFiles()?.forEach { f ->
                val target = File(photoDir, f.name)
                if (!target.exists()) {
                    val tmp = File(photoDir, ".restore-" + f.name)
                    f.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(target)) { tmp.delete(); error("rename ${f.name}") }
                    added += target
                }
            }
            val copy = open(File(prepared.dir, BackupRules.DB_ENTRY))
            try {
                val src = copy.openHelper.writableDatabase
                db.runInTransaction {
                    val dst = db.openHelper.writableDatabase
                    for (t in TABLES.reversed()) dst.execSQL("DELETE FROM `$t`")
                    for (t in TABLES) {
                        copyTable(src, dst, t)
                        afterRestoreTable?.invoke(t)
                    }
                    val now = counts(dst)
                    check(now.rows() == prepared.manifest.counts.rows()) { "restored $now" }
                }
            } finally {
                copy.close()
            }
        } catch (e: Throwable) {
            added.forEach { it.delete() }
            Log.w(TAG, "restore failed, data unchanged", e)
            throw e
        } finally {
            prepared.dir.deleteRecursively()
        }
        // Committed: drop files no photo row refers to any more (the replaced data's photos).
        val keep = db.openHelper.writableDatabase.query("SELECT file_name FROM photos").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        photoDir.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
        currentCounts().also { Log.i(TAG, "restored: $it") }
    }

    /** Removes a [Prepared] that will not be restored. */
    fun discard(prepared: Prepared) { prepared.dir.deleteRecursively() }

    private fun fresh(name: String): File = File(workDir, name).apply { deleteRecursively(); mkdirs() }

    /** A database file with the app's schema (created, or migrated + validated by Room). */
    private fun open(file: File): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()

    private fun userVersion(file: File): Int = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        }
    } catch (e: Exception) {
        throw BackupException(BackupProblem.Damaged("database"), e)
    }

    companion object {
        private const val TAG = "Backup"

        /**
         * User tables, parents first (insert order; deletes go in reverse). `leg_routes` (R36) is
         * copied too, but not counted in the manifest (routes can always be fetched again).
         */
        val TABLES = listOf("trips", "steps", "leg_routes", "photos", "tracks", "track_points", "stop_decisions")

        fun counts(d: SupportSQLiteDatabase): BackupCounts {
            fun n(t: String) = d.query("SELECT COUNT(*) FROM `$t`").use { c -> c.moveToFirst(); c.getInt(0) }
            fun videos() = d.query("SELECT COUNT(*) FROM `photos` WHERE media_type = 'video'").use { c -> c.moveToFirst(); c.getInt(0) }
            return BackupCounts(n("trips"), n("steps"), n("photos"), n("tracks"), n("track_points"), n("stop_decisions"), videos())
        }

        /** Copies every row of [table] by column name (column order may differ after migrations). */
        fun copyTable(src: SupportSQLiteDatabase, dst: SupportSQLiteDatabase, table: String) {
            src.query("SELECT * FROM `$table`").use { c ->
                val cv = ContentValues()
                while (c.moveToNext()) {
                    cv.clear()
                    for (i in 0 until c.columnCount) {
                        val n = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> cv.putNull(n)
                            Cursor.FIELD_TYPE_INTEGER -> cv.put(n, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> cv.put(n, c.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> cv.put(n, c.getBlob(i))
                            else -> cv.put(n, c.getString(i))
                        }
                    }
                    dst.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv)
                }
            }
        }

        fun toJson(m: BackupManifest): JSONObject = JSONObject()
            .put("format", m.format)
            .put("formatVersion", m.formatVersion)
            .put("appId", m.appId)
            .put("appVersion", m.appVersion)
            .put("schemaVersion", m.schemaVersion)
            .put("createdAt", m.createdAt)
            .put(
                "counts",
                JSONObject()
                    .put("trips", m.counts.trips).put("steps", m.counts.steps).put("photos", m.counts.photos)
                    .put("tracks", m.counts.tracks).put("trackPoints", m.counts.trackPoints)
                    .put("stopDecisions", m.counts.stopDecisions).put("videos", m.counts.videos),
            )
            .put("photoFiles", m.photoFiles)
            .put("missingPhotoFiles", m.missingPhotoFiles)

        fun fromJson(o: JSONObject): BackupManifest {
            val c = o.getJSONObject("counts")
            return BackupManifest(
                format = o.getString("format"),
                formatVersion = o.getInt("formatVersion"),
                appId = o.optString("appId"),
                appVersion = o.optString("appVersion"),
                schemaVersion = o.getInt("schemaVersion"),
                createdAt = o.optString("createdAt"),
                counts = BackupCounts(
                    c.getInt("trips"), c.getInt("steps"), c.getInt("photos"), c.getInt("tracks"),
                    c.getInt("trackPoints"), c.getInt("stopDecisions"), c.optInt("videos"),
                ),
                photoFiles = o.getInt("photoFiles"),
                missingPhotoFiles = o.optInt("missingPhotoFiles"),
            )
        }
    }
}
