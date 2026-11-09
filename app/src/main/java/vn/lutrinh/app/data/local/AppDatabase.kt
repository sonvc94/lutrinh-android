package vn.lutrinh.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history (JSON per version in app/schemas/, docs/02-data-model.md "Migrations"):
 *  1 - trips, steps, photos
 *  2 - steps.transport_mode (nullable TEXT): transport of the leg ending at the step (R12)
 *  3 - tracks + track_points: recorded GPS tracks (R16)
 *  4 - photos.taken_at (nullable TEXT) + stop_decisions: stop suggestions from tracks (R17)
 */
@Database(
    entities = [
        TripEntity::class, StepEntity::class, PhotoEntity::class, TrackEntity::class, TrackPointEntity::class,
        StopDecisionEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stepDao(): StepDao
    abstract fun trackDao(): TrackDao
    abstract fun stopDecisionDao(): StopDecisionDao

    companion object {
        const val NAME = "lutrinh.db"
        const val VERSION = 4

        /** Adds the nullable leg transport column; existing rows read as "not set". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steps ADD COLUMN transport_mode TEXT")
            }
        }

        /** Adds the two track tables (R16); existing trips, steps and photos are not touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL copied from app/schemas/.../3.json (what Room creates on a fresh install).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `trip_id` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, `ended_at` INTEGER, `active_ms` INTEGER NOT NULL, `resumed_at` INTEGER, " +
                        "`segment` INTEGER NOT NULL, `distance_m` REAL NOT NULL, `point_count` INTEGER NOT NULL, " +
                        "`interrupted` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_trip_id` ON `tracks` (`trip_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`track_id` TEXT NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, " +
                        "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` REAL, `altitude_m` REAL, `speed_mps` REAL, " +
                        "FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_track_points_track_id_segment_recorded_at` " +
                        "ON `track_points` (`track_id`, `segment`, `recorded_at`)",
                )
            }
        }

        /**
         * Adds the photo capture time (existing photos read as "unknown") and the table of answers
         * to stop suggestions (R17); nothing existing is rewritten.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN taken_at TEXT")
                // SQL copied from app/schemas/.../4.json (what Room creates on a fresh install).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stop_decisions` (`track_id` TEXT NOT NULL, `start_at` INTEGER NOT NULL, " +
                        "`end_at` INTEGER NOT NULL, `decision` TEXT NOT NULL, `step_id` TEXT, `decided_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`track_id`, `start_at`), " +
                        "FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stop_decisions_track_id` ON `stop_decisions` (`track_id`)")
            }
        }

        /** Every migration, oldest first. Never use destructive fallback: user data is local only. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
