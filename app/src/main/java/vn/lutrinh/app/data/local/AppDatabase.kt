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
 *  5 - photos.source_id (nullable TEXT) + photos.source_size (nullable INTEGER): gallery photos of a stop (R19)
 *  6 - steps.is_draft (INTEGER NOT NULL DEFAULT 0): draft steps (R20) + trips.cover_photo_id (nullable TEXT): chosen cover (R21)
 *  7 - photos.position renumbered 0..n-1 per step in display order (R27 drag order) +
 *      photos.media_type (TEXT NOT NULL DEFAULT 'photo') + photos.duration_ms (nullable INTEGER): short videos (R29)
 *  8 - leg_routes: stored road route per leg, keyed by the arrival step (R36)
 *  9 - leg_routes.used_profile (nullable TEXT): the profile that answered; `car` on a Xe máy leg = car-route fallback (R36)
 */
@Database(
    entities = [
        TripEntity::class, StepEntity::class, PhotoEntity::class, TrackEntity::class, TrackPointEntity::class,
        StopDecisionEntity::class, LegRouteEntity::class,
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
    abstract fun legRouteDao(): LegRouteDao

    companion object {
        const val NAME = "lutrinh.db"
        const val VERSION = 9

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

        /**
         * Adds where a photo was copied from (R19: a gallery photo is not offered twice); existing
         * photos read as "unknown" and nothing existing is rewritten.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN source_id TEXT")
                db.execSQL("ALTER TABLE photos ADD COLUMN source_size INTEGER")
            }
        }

        /**
         * Draft steps (R20: every existing step reads as published, 0) and the chosen trip cover
         * (R21: existing trips read as automatic, null); nothing existing is rewritten.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steps ADD COLUMN is_draft INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN cover_photo_id TEXT")
            }
        }

        /**
         * R27: the order the user arranges is `photos.position`; existing rows are renumbered
         * 0..n-1 per step in the order the app showed them (position, then created time, then id),
         * so ties of older data become a fixed order. R29: every existing row is a photo, no length.
         * The correlated subquery (no window functions) runs on the SQLite of API 26.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // R27: number every step's photos 0..n-1 in the order v6 showed them (position,
                // created_at, id). Ranks are computed into a temp table first: an UPDATE whose
                // subquery reads the same table sees rows it has already renumbered.
                db.execSQL(
                    "CREATE TEMP TABLE photo_rank AS SELECT p.id AS id, (SELECT COUNT(*) FROM photos q WHERE q.step_id = p.step_id AND (" +
                        "q.position < p.position OR (q.position = p.position AND (" +
                        "q.created_at < p.created_at OR (q.created_at = p.created_at AND q.id < p.id))))) AS rank FROM photos p",
                )
                db.execSQL("UPDATE photos SET position = (SELECT rank FROM photo_rank WHERE photo_rank.id = photos.id)")
                db.execSQL("DROP TABLE photo_rank")
                db.execSQL("ALTER TABLE photos ADD COLUMN media_type TEXT NOT NULL DEFAULT 'photo'")
                db.execSQL("ALTER TABLE photos ADD COLUMN duration_ms INTEGER")
            }
        }

        /** Adds the table of stored leg routes (R36), empty; nothing existing is touched. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL copied from app/schemas/.../8.json (what Room creates on a fresh install).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `leg_routes` (`to_step_id` TEXT NOT NULL, `from_lat` REAL NOT NULL, " +
                        "`from_lng` REAL NOT NULL, `to_lat` REAL NOT NULL, `to_lng` REAL NOT NULL, `profile` TEXT NOT NULL, " +
                        "`provider` TEXT NOT NULL, `status` TEXT NOT NULL, `distance_m` REAL NOT NULL, `duration_s` REAL NOT NULL, " +
                        "`geometry` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, PRIMARY KEY(`to_step_id`), " +
                        "FOREIGN KEY(`to_step_id`) REFERENCES `steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        /**
         * Adds `leg_routes.used_profile` (R36 car fallback). Existing rows keep NULL = answered with
         * their own profile; a stored motorbike "no route" is then asked once with the car profile.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE leg_routes ADD COLUMN used_profile TEXT")
            }
        }

        /** Every migration, oldest first. Never use destructive fallback: user data is local only. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
