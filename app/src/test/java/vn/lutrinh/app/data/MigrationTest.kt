package vn.lutrinh.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.TransportMode
import java.io.File
import java.time.LocalDate

/**
 * Builds a database exactly as version 1 created it (SQL from app/schemas/.../1.json, the file
 * Room exported for the released schema), fills it, then opens it with the current Room
 * database. Room checks the migrated schema against the current entities on open, so a missing
 * or wrong migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    private var room: AppDatabase? = null

    @Before fun clean() { ctx.deleteDatabase(name) }

    @After fun close() { room?.close(); ctx.deleteDatabase(name) }

    /** Creates the database exactly as [version] created it and fills it with one trip. */
    private fun createVersion(version: Int) {
        val schema = JSONObject(File(SCHEMAS, "$version.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (k in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version

            db.insert("trips", null, ContentValues().apply {
                put("id", "t1"); put("title", "Vòng cung Hà Giang"); put("description", "xe máy")
                put("start_date", "2025-10-20"); put("end_date", "2025-10-23"); put("created_at", 1L); put("updated_at", 1L)
            })
            listOf("s1" to "2025-10-20T08:00", "s2" to "2025-10-20T11:30").forEach { (id, at) ->
                db.insert("steps", null, ContentValues().apply {
                    put("id", id); put("trip_id", "t1"); put("title", "Step $id"); put("note", "")
                    put("latitude", 22.82); put("longitude", 104.98); put("arrived_at", at); put("created_at", 1L); put("updated_at", 1L)
                })
            }
            db.insert("photos", null, ContentValues().apply {
                put("id", "p1"); put("step_id", "s2"); put("file_name", "a.jpg"); put("position", 0); put("created_at", 1L)
            })
        }
    }

    @Test fun version1UpgradesToCurrentWithoutDataLoss() = runTest {
        createVersion(1)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { room = it }
        val trips = RoomTripRepository(db)
        val steps = RoomStepRepository(db)

        val trip = trips.getTrip("t1")!!
        assertEquals("Vòng cung Hà Giang", trip.title)
        assertEquals(LocalDate.parse("2025-10-23"), trip.endDate)
        val list = steps.getSteps("t1")
        assertEquals(listOf("s1", "s2"), list.map { it.id })
        assertEquals(listOf("a.jpg"), list[1].photos.map { it.fileName })
        // Existing legs read as "not set".
        assertTrue(list.all { it.arrivalTransport == null })
        assertEquals(1, trips.observeTripSummaries().first().single().photoCount)

        // The new column is writable after the upgrade.
        assertTrue(steps.setArrivalTransport("s2", TransportMode.MOTORBIKE, 5L))
        assertEquals(TransportMode.MOTORBIKE, steps.getStep("s2")!!.arrivalTransport)
        assertEquals(5L, steps.getStep("s2")!!.updatedAt)
        assertNull(steps.getStep("s1")!!.arrivalTransport)
    }

    @Test fun version2UpgradesWithTrackTablesAndKeepsTripsAndSteps() = runTest {
        createVersion(2)
        val db = open()
        val trips = RoomTripRepository(db)
        val steps = RoomStepRepository(db)
        val tracks = RoomTrackRepository(db)

        assertEquals(listOf("t1"), trips.observeTripSummaries().first().map { it.trip.id })
        assertEquals(listOf("s1", "s2"), steps.getSteps("t1").map { it.id })
        assertEquals(listOf("a.jpg"), steps.getSteps("t1")[1].photos.map { it.fileName })
        assertTrue(tracks.observeTracks("t1").first().isEmpty())

        // The new tables work: a track with points, deleted with its points.
        val track = Track("k1", "t1", TrackStatus.RECORDING, 10, null, 0, 10, 0, 0.0, 0, false, 10, 10)
        tracks.insertTrack(track)
        tracks.addPoint(track.copy(pointCount = 1), TrackPoint("k1", 0, 11, GeoPoint(22.82, 104.98), 5f, 300.0, 1f))
        tracks.addPoint(track.copy(pointCount = 2), TrackPoint("k1", 0, 16, GeoPoint(22.83, 104.98)))
        assertEquals(2, tracks.observePoints("t1").first().size)
        assertEquals(2, tracks.getTrack("k1")!!.pointCount)
        assertTrue(tracks.deleteTrack("k1"))
        assertTrue(tracks.observeAllPoints().first().isEmpty())

        // A trip's tracks and points go with the trip (foreign keys cascade).
        tracks.insertTrack(track)
        tracks.addPoint(track.copy(pointCount = 1), TrackPoint("k1", 0, 11, GeoPoint(22.82, 104.98)))
        trips.deleteTrip("t1")
        assertNull(tracks.getTrack("k1"))
        assertTrue(tracks.observeAllPoints().first().isEmpty())
    }

    private fun open(): AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .allowMainThreadQueries()
        .build()
        .also { room = it }

    @Test fun everySchemaVersionHasAnExportedFileAndAMigration() {
        val current = AppDatabase.VERSION
        (1..current).forEach { v -> assertTrue("schema $v exported", File(SCHEMAS, "$v.json").exists()) }
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until current).map { it to it + 1 }, covered)
    }

    private companion object {
        /** Gradle runs unit tests with the module directory as working directory. */
        val SCHEMAS = File("schemas/vn.lutrinh.app.data.local.AppDatabase")
    }
}
