package vn.lutrinh.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.BackupCounts
import vn.lutrinh.app.domain.usecase.BackupException
import vn.lutrinh.app.domain.usecase.BackupManifest
import vn.lutrinh.app.domain.usecase.BackupProblem
import vn.lutrinh.app.domain.usecase.BackupRules
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** R26: backup file content, manifest/schema checks and an all-or-nothing restore on the real SQLite schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupManagerTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "backup-test.db"
    private lateinit var db: AppDatabase
    private lateinit var photoDir: File
    private lateinit var backup: BackupManager

    @Before fun setUp() {
        ctx.deleteDatabase(name)
        db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        photoDir = File(ctx.filesDir, "photos-test").apply { deleteRecursively(); mkdirs() }
        backup = BackupManager(ctx, db, photoDir, "test")
    }

    @After fun tearDown() {
        db.close()
        ctx.deleteDatabase(name)
        photoDir.deleteRecursively()
    }

    /** Trip A: 2 steps (one draft), 3 photos (one a chosen cover), 1 track with 3 points, 1 stop decision. */
    private suspend fun seedA() {
        val trips = RoomTripRepository(db)
        val steps = RoomStepRepository(db)
        val tracks = RoomTrackRepository(db)
        trips.upsertTrip(Trip("tA", "Vòng cung Hà Giang", "", LocalDate.parse("2025-10-20"), null, 1, 1, coverPhotoId = "p3"))
        listOf("a.jpg", "b.jpg", "c.jpg").forEach { File(photoDir, it).writeText("photo $it") }
        steps.saveStep(Step("s1", "tA", "TP Hà Giang", "", GeoPoint(22.8, 105.0), LocalDateTime.parse("2025-10-20T08:00"), 1, 1,
            listOf(Photo("p1", "s1", "a.jpg", 0, 1), Photo("p2", "s1", "b.jpg", 1, 1))))
        steps.saveStep(Step("s2", "tA", "Nháp", "", GeoPoint(23.0, 105.2), LocalDateTime.parse("2025-10-20T12:00"), 1, 1,
            listOf(Photo("p3", "s2", "c.jpg", 0, 1)), isDraft = true))
        val t = Track("k1", "tA", TrackStatus.ENDED, 1000, 3000, 2000, null, 0, 20.0, 3, false, 1, 1)
        tracks.insertTrack(t)
        listOf(1000L, 2000L, 3000L).forEachIndexed { i, at -> tracks.addPoint(t, TrackPoint("k1", 0, at, GeoPoint(22.8 + i * 0.001, 105.0))) }
        RoomStopDecisionRepository(db).saveDecision(StopDecision("k1", 1000, 3000, StopDecisionKind.DISMISSED, null, 5))
    }

    private val countsA = BackupCounts(trips = 1, steps = 2, photos = 3, tracks = 1, trackPoints = 3, stopDecisions = 1)

    private fun files() = photoDir.listFiles()!!.map { it.name }.sorted()

    private suspend fun backupBytes(): Pair<BackupManifest, ByteArray> {
        val out = ByteArrayOutputStream()
        val m = backup.write(out, Instant.parse("2026-11-09T03:00:00Z"))
        return m to out.toByteArray()
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes()) }
        }
    }

    /** Replaces data A by data B: a different trip with its own photo. */
    private suspend fun replaceWithB() {
        val files = RoomTripRepository(db).deleteTrip("tA")
        files.forEach { File(photoDir, it).delete() }
        RoomTripRepository(db).upsertTrip(Trip("tB", "Ninh Bình", "", LocalDate.parse("2025-11-01"), null, 2, 2))
        File(photoDir, "z.jpg").writeText("photo z")
        RoomStepRepository(db).saveStep(Step("sB", "tB", "Tràng An", "", GeoPoint(20.25, 105.9), LocalDateTime.parse("2025-11-01T09:00"), 2, 2,
            listOf(Photo("pz", "sB", "z.jpg", 0, 2))))
    }

    private val countsB = BackupCounts(trips = 1, steps = 1, photos = 1)

    @Test fun backupHoldsManifestDatabaseAndEveryPhoto() = runTest {
        seedA()
        File(photoDir, "orphan.jpg").writeText("not referenced")
        val (m, zip) = backupBytes()
        assertEquals(countsA, m.counts)
        assertEquals(AppDatabase.VERSION, m.schemaVersion)
        assertEquals(3, m.photoFiles)
        assertEquals("2026-11-09T03:00:00Z", m.createdAt)
        val e = entries(zip)
        assertEquals(setOf("manifest.json", "lutrinh.db", "photos/a.jpg", "photos/b.jpg", "photos/c.jpg"), e.keys)
        assertEquals(m, BackupManager.fromJson(JSONObject(e.getValue("manifest.json").toString(Charsets.UTF_8))))
        assertEquals("photo c.jpg", e.getValue("photos/c.jpg").toString(Charsets.UTF_8)) // the chosen cover's file
    }

    @Test fun restoreReplacesAllDataWithTheBackup() = runTest {
        seedA()
        val (_, zip) = backupBytes()
        replaceWithB()
        val prepared = backup.prepare(ByteArrayInputStream(zip))
        assertEquals(countsA, prepared.manifest.counts)
        assertEquals(countsB, prepared.current)
        assertEquals(countsB, backup.currentCounts()) // preparing changes nothing

        assertEquals(countsA, backup.restore(prepared))
        assertNull(RoomTripRepository(db).getTrip("tB"))
        assertEquals("p3", RoomTripRepository(db).getTrip("tA")!!.coverPhotoId)
        val s2 = RoomStepRepository(db).getStep("s2")!!
        assertTrue(s2.isDraft)
        assertEquals(listOf("c.jpg"), s2.photos.map { it.fileName })
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), files()) // B's photo is gone with B
        assertEquals("photo a.jpg", File(photoDir, "a.jpg").readText())
        assertFalse(prepared.dir.exists())
    }

    @Test fun restoreFailureLeavesCurrentDataIntact() = runTest {
        seedA()
        val (_, zip) = backupBytes()
        replaceWithB()
        val prepared = backup.prepare(ByteArrayInputStream(zip))
        backup.afterRestoreTable = { if (it == "tracks") error("disk full") }
        try {
            backup.restore(prepared)
            fail("restored")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertEquals(countsB, backup.currentCounts())
        assertEquals("Tràng An", RoomStepRepository(db).getStep("sB")!!.title)
        assertNull(RoomTripRepository(db).getTrip("tA"))
        assertEquals(listOf("z.jpg"), files()) // the backup's files added for the restore are removed again
    }

    private fun zipOf(manifest: BackupManifest?, db: File? = null, photos: Map<String, String> = emptyMap(), rawManifest: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            (rawManifest ?: manifest?.let { BackupManager.toJson(it).toString() })?.let {
                z.putNextEntry(ZipEntry(BackupRules.MANIFEST_ENTRY)); z.write(it.toByteArray()); z.closeEntry()
            }
            db?.let { z.putNextEntry(ZipEntry(BackupRules.DB_ENTRY)); z.write(it.readBytes()); z.closeEntry() }
            photos.forEach { (n, s) -> z.putNextEntry(ZipEntry(BackupRules.PHOTO_PREFIX + n)); z.write(s.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }

    private suspend fun refused(zip: ByteArray): BackupProblem = try {
        backup.prepare(ByteArrayInputStream(zip))
        fail("accepted"); error("unreachable")
    } catch (e: BackupException) {
        e.problem
    }

    @Test fun newerSchemaIsRefused() = runTest {
        seedA()
        val (m, zip) = backupBytes()
        val newer = m.copy(schemaVersion = AppDatabase.VERSION + 1)
        val db = File(ctx.cacheDir, "copy.db").apply { writeBytes(entries(zip).getValue("lutrinh.db")) }
        val photos = mapOf("a.jpg" to "a", "b.jpg" to "b", "c.jpg" to "c")
        assertEquals(BackupProblem.NewerSchema(AppDatabase.VERSION + 1, AppDatabase.VERSION), refused(zipOf(newer, db, photos)))
        assertEquals(BackupProblem.NewerSchema(7, 6), BackupRules.check(m.copy(schemaVersion = 7), 6))
        assertEquals(BackupProblem.NewerFormat(2, 1), BackupRules.check(m.copy(formatVersion = 2), 6))
        assertNull(BackupRules.check(m.copy(schemaVersion = 3), 6)) // older: migrated
        assertEquals(countsA, backup.currentCounts())
    }

    @Test fun otherFilesAreRefusedAndChangeNothing() = runTest {
        seedA()
        val (m, zip) = backupBytes()
        val db = File(ctx.cacheDir, "copy.db").apply { writeBytes(entries(zip).getValue("lutrinh.db")) }
        val photos = mapOf("a.jpg" to "a", "b.jpg" to "b", "c.jpg" to "c")
        assertEquals(BackupProblem.NotABackup, refused("not a zip".toByteArray()))
        assertEquals(BackupProblem.NotABackup, refused(zipOf(null, db, photos)))
        assertEquals(BackupProblem.NotABackup, refused(zipOf(null, db, photos, rawManifest = "{broken")))
        assertEquals(BackupProblem.NotABackup, refused(zipOf(m.copy(format = "other"), db, photos)))
        assertTrue(refused(zipOf(m, null, photos)) is BackupProblem.Damaged) // no database
        assertTrue(refused(zipOf(m, db, photos - "c.jpg")) is BackupProblem.Damaged) // a photo missing
        assertTrue(refused(zipOf(m, db, photos + ("../evil" to "x"))) is BackupProblem.Damaged)
        assertTrue(refused(zipOf(m.copy(counts = m.counts.copy(steps = 9)), db, photos)) is BackupProblem.Damaged)
        assertTrue(refused(zip.copyOf(zip.size / 2)) is BackupProblem.NotABackup) // truncated download
        assertEquals(countsA, backup.currentCounts())
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), files())
    }

    @Test fun olderSchemaBackupIsMigrated() = runTest {
        // A version-5 database (before drafts and covers), as the v5 app wrote it.
        val schema = JSONObject(File(SCHEMAS, "5.json").readText()).getJSONObject("database")
        val old = File(ctx.cacheDir, "v5.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(old, null).use { d ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                d.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (k in 0 until indices.length()) d.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) d.execSQL(setup.getString(i))
            d.version = 5
            d.insert("trips", null, ContentValues().apply {
                put("id", "t5"); put("title", "Cũ"); put("description", ""); put("start_date", "2025-01-01"); put("created_at", 1L); put("updated_at", 1L)
            })
            d.insert("steps", null, ContentValues().apply {
                put("id", "s5"); put("trip_id", "t5"); put("title", "Bước"); put("note", ""); put("latitude", 21.0); put("longitude", 105.8)
                put("arrived_at", "2025-01-01T10:00"); put("created_at", 1L); put("updated_at", 1L)
            })
            d.insert("photos", null, ContentValues().apply {
                put("id", "p5"); put("step_id", "s5"); put("file_name", "old.jpg"); put("position", 0); put("created_at", 1L)
            })
        }
        val m = BackupManifest(appId = "vn.lutrinh.app", appVersion = "0.0.5", schemaVersion = 5, createdAt = "2025-01-02T00:00:00Z",
            counts = BackupCounts(trips = 1, steps = 1, photos = 1), photoFiles = 1)
        seedA()
        val prepared = backup.prepare(ByteArrayInputStream(zipOf(m, old, mapOf("old.jpg" to "old"))))
        assertEquals(BackupCounts(trips = 1, steps = 1, photos = 1), backup.restore(prepared))
        val step = RoomStepRepository(db).getStep("s5")!!
        assertFalse(step.isDraft) // migrated default (R20)
        assertNull(RoomTripRepository(db).getTrip("t5")!!.coverPhotoId)
        assertEquals(listOf("old.jpg"), files())
    }

    companion object {
        val SCHEMAS = File("schemas/vn.lutrinh.app.data.local.AppDatabase")
    }
}
