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
 */
@Database(
    entities = [TripEntity::class, StepEntity::class, PhotoEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stepDao(): StepDao

    companion object {
        const val NAME = "lutrinh.db"
        const val VERSION = 2

        /** Adds the nullable leg transport column; existing rows read as "not set". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steps ADD COLUMN transport_mode TEXT")
            }
        }

        /** Every migration, oldest first. Never use destructive fallback: user data is local only. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
