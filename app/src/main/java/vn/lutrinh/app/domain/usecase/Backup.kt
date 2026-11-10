package vn.lutrinh.app.domain.usecase

import java.time.LocalDate

/*
 * R26: local backup and restore of all user data (docs/04-business-rules.md R26, file layout in
 * docs/02-data-model.md "Backup file"). Pure Kotlin, mirrored 1:1 in Swift (docs/07-ios-port.md).
 */

/** Rows per table (a backup's content, or the data on the device now). */
data class BackupCounts(
    val trips: Int = 0,
    val steps: Int = 0,
    val photos: Int = 0,
    val tracks: Int = 0,
    val trackPoints: Int = 0,
    val stopDecisions: Int = 0,
    /** How many of the [photos] rows are videos (R29); [photos] counts both, as the file layout always did. */
    val videos: Int = 0,
) {
    /** Photos without the videos: what the screens call "ảnh" (R26). */
    val stills: Int get() = photos - videos

    /** The table row counts only: a manifest written before [videos] existed still matches its database. */
    fun rows(): BackupCounts = copy(videos = 0)
}

/** `manifest.json` of a backup file. */
data class BackupManifest(
    val format: String = BackupRules.FORMAT,
    val formatVersion: Int = BackupRules.FORMAT_VERSION,
    val appId: String,
    val appVersion: String,
    /** Database schema version (Room `user_version`) of `lutrinh.db` in the file. */
    val schemaVersion: Int,
    /** UTC ISO-8601 instant the backup was made. */
    val createdAt: String,
    val counts: BackupCounts,
    /** Photo files stored under `photos/` in the file. */
    val photoFiles: Int,
    /** Photo rows whose file was already missing on the device (not in the file). */
    val missingPhotoFiles: Int = 0,
)

/** Why a picked file is not restored (R26). Current data is never touched for any of these. */
sealed interface BackupProblem {
    /** No readable `manifest.json` with our format id: not a Lữ Trình backup (or not a zip). */
    data object NotABackup : BackupProblem

    /** Made by a newer app with a newer file layout. */
    data class NewerFormat(val backup: Int, val app: Int) : BackupProblem

    /** Made by a newer app with a newer database schema: update the app first. */
    data class NewerSchema(val backup: Int, val app: Int) : BackupProblem

    /** The file is incomplete or inconsistent with its manifest. */
    data class Damaged(val detail: String) : BackupProblem

    /** A track is being recorded: stop it before restoring. */
    data object Recording : BackupProblem
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception(problem.toString(), cause)

object BackupRules {
    const val FORMAT = "lutrinh-backup"
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val DB_ENTRY = "lutrinh.db"
    const val PHOTO_PREFIX = "photos/"
    const val MIME = "application/zip"

    /** Photo file names are app-generated (`<uuid>.<ext>`); anything else in `photos/` is rejected (no path tricks). */
    private val PHOTO_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun isPhotoName(name: String): Boolean = PHOTO_NAME.matches(name) && !name.contains("..")

    /**
     * The manifest check before anything is extracted: our format, a file layout and a schema
     * this app can read. An older schema is accepted (migrated like an app update); a newer one
     * is refused.
     */
    fun check(m: BackupManifest, appSchema: Int, appFormat: Int = FORMAT_VERSION): BackupProblem? = when {
        m.format != FORMAT -> BackupProblem.NotABackup
        m.formatVersion > appFormat -> BackupProblem.NewerFormat(m.formatVersion, appFormat)
        m.schemaVersion > appSchema -> BackupProblem.NewerSchema(m.schemaVersion, appSchema)
        m.schemaVersion < 1 || m.formatVersion < 1 -> BackupProblem.Damaged("manifest")
        m.photoFiles < 0 || m.counts.trips < 0 -> BackupProblem.Damaged("manifest")
        else -> null
    }

    /** Suggested file name: `LuTrinh-saoluu-2026-11-09.zip`. */
    fun fileName(day: LocalDate): String = "LuTrinh-saoluu-$day.zip"
}
