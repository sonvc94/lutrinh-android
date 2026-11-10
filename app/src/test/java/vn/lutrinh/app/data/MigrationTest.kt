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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRouteStatus
import vn.lutrinh.app.domain.routing.RouteProfile
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test fun version3UpgradesWithPhotoTimesAndStopDecisionsAndKeepsTracks() = runTest {
        createVersion(3)
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insert("tracks", null, ContentValues().apply {
                put("id", "k1"); put("trip_id", "t1"); put("status", "ended"); put("started_at", 10L); put("ended_at", 20L)
                put("active_ms", 10L); put("segment", 0); put("distance_m", 120.0); put("point_count", 2); put("interrupted", 0)
                put("created_at", 10L); put("updated_at", 20L)
            })
            listOf(11L to 22.82, 19L to 22.821).forEach { (t, lat) ->
                db.insert("track_points", null, ContentValues().apply {
                    put("track_id", "k1"); put("segment", 0); put("recorded_at", t); put("latitude", lat); put("longitude", 104.98)
                })
            }
        }
        val db = open()
        val trips = RoomTripRepository(db)
        val steps = RoomStepRepository(db)
        val tracks = RoomTrackRepository(db)
        val decisions = RoomStopDecisionRepository(db)

        // Everything survives; existing photos have no capture time.
        assertEquals(listOf("s1", "s2"), steps.getSteps("t1").map { it.id })
        val photo = steps.getSteps("t1")[1].photos.single()
        assertEquals("a.jpg", photo.fileName)
        assertNull(photo.takenAt)
        assertEquals(2, tracks.getTrack("k1")!!.pointCount)
        assertEquals(2, tracks.observePoints("t1").first().size)
        assertTrue(decisions.observeDecisions("t1").first().isEmpty())

        // photos.taken_at is writable and read back (minute precision).
        val s2 = steps.getStep("s2")!!
        val taken = LocalDateTime.of(2025, 10, 20, 12, 7)
        steps.saveStep(s2.copy(photos = listOf(photo.copy(takenAt = taken))))
        assertEquals(taken, steps.getStep("s2")!!.photos.single().takenAt)

        // stop_decisions: upsert by (track, start); they go with their track.
        decisions.saveDecision(StopDecision("k1", 11, 19, StopDecisionKind.DISMISSED, null, 30))
        decisions.saveDecision(StopDecision("k1", 11, 19, StopDecisionKind.ADDED, "s2", 31))
        assertEquals(listOf(StopDecision("k1", 11, 19, StopDecisionKind.ADDED, "s2", 31)), decisions.observeDecisions("t1").first())
        assertTrue(tracks.deleteTrack("k1"))
        assertTrue(decisions.observeDecisions("t1").first().isEmpty())
        assertEquals(1, trips.observeTripSummaries().first().single().photoCount)
    }

    @Test fun version4UpgradesWithPhotoSourcesAndKeepsEverything() = runTest {
        createVersion(4)
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE photos SET taken_at = '2025-10-20T12:07' WHERE id = 'p1'")
            db.insert("tracks", null, ContentValues().apply {
                put("id", "k1"); put("trip_id", "t1"); put("status", "ended"); put("started_at", 10L); put("ended_at", 20L)
                put("active_ms", 10L); put("segment", 0); put("distance_m", 120.0); put("point_count", 1); put("interrupted", 0)
                put("created_at", 10L); put("updated_at", 20L)
            })
            db.insert("track_points", null, ContentValues().apply {
                put("track_id", "k1"); put("segment", 0); put("recorded_at", 11L); put("latitude", 22.82); put("longitude", 104.98)
            })
            db.insert("stop_decisions", null, ContentValues().apply {
                put("track_id", "k1"); put("start_at", 11L); put("end_at", 19L); put("decision", "dismissed"); put("decided_at", 30L)
            })
        }
        val db = open()
        val steps = RoomStepRepository(db)

        // Everything survives; the existing photo keeps its capture time and has no source.
        assertEquals(listOf("s1", "s2"), steps.getSteps("t1").map { it.id })
        val photo = steps.getStep("s2")!!.photos.single()
        assertEquals(LocalDateTime.of(2025, 10, 20, 12, 7), photo.takenAt)
        assertNull(photo.sourceId)
        assertNull(photo.sourceSize)
        assertEquals(1, RoomTrackRepository(db).observePoints("t1").first().size)
        assertEquals(1, RoomStopDecisionRepository(db).observeDecisions("t1").first().size)

        // The new columns are writable and read back.
        steps.saveStep(steps.getStep("s2")!!.copy(photos = listOf(photo.copy(sourceId = "1234", sourceSize = 2_048_000))))
        val stored = steps.getStep("s2")!!.photos.single()
        assertEquals("1234" to 2_048_000L, stored.sourceId to stored.sourceSize)
    }

    @Test fun version5UpgradesWithDraftsAndCoverAndKeepsEverything() = runTest {
        createVersion(5)
        val db = open()
        val trips = RoomTripRepository(db)
        val steps = RoomStepRepository(db)

        // Every existing step is published (R20) and the trip cover is automatic (R21).
        assertEquals(listOf("s1", "s2"), steps.getSteps("t1").map { it.id })
        assertTrue(steps.getSteps("t1").none { it.isDraft })
        assertNull(trips.getTrip("t1")!!.coverPhotoId)
        val summary = trips.observeTripSummaries().first().single()
        assertEquals(2 to 1, summary.stepCount to summary.photoCount)
        assertEquals(0 to 0, summary.draftCount to summary.draftPhotoCount)
        assertEquals("a.jpg", summary.coverPhotoFile)

        // The new columns are writable and read back.
        assertTrue(steps.setDraft("s2", true, 50L))
        assertTrue(steps.getStep("s2")!!.isDraft)
        assertTrue(trips.setCover("t1", "p1", 60L))
        assertEquals("p1", trips.getTrip("t1")!!.coverPhotoId)
    }

    @Test fun version6UpgradesKeepingPhotoOrderAndAddsMediaType() = runTest {
        createVersion(6)
        // The order as v6 showed it: position, then created_at, then id. Gaps and ties (old
        // imports) are renumbered 0..n-1 in that same order (R27).
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE photos SET position = 5, created_at = 9 WHERE id = 'p1'")
            listOf(
                listOf("p2", "b.jpg", 2, 3L), listOf("p3", "c.jpg", 5, 4L), listOf("p0", "d.jpg", 5, 4L),
                listOf("p4", "e.jpg", 0, 7L),
            ).forEach { (id, file, pos, at) ->
                db.insert("photos", null, ContentValues().apply {
                    put("id", id as String); put("step_id", "s2"); put("file_name", file as String)
                    put("position", pos as Int); put("created_at", at as Long)
                })
            }
            db.insert("photos", null, ContentValues().apply {
                put("id", "q1"); put("step_id", "s1"); put("file_name", "f.jpg"); put("position", 3); put("created_at", 1L)
            })
            db.execSQL("UPDATE trips SET cover_photo_id = 'p3' WHERE id = 't1'")
        }
        val db = open()
        val steps = RoomStepRepository(db)

        val s2 = steps.getStep("s2")!!.photos
        assertEquals(listOf("e.jpg", "b.jpg", "d.jpg", "c.jpg", "a.jpg"), s2.map { it.fileName })
        assertEquals(listOf(0, 1, 2, 3, 4), s2.map { it.position })
        assertEquals(listOf(0), steps.getStep("s1")!!.photos.map { it.position })
        // Every row stays, all of them photos; the chosen cover (R21) is untouched.
        assertEquals(6, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM photos").use { it.moveToFirst(); it.getInt(0) })
        assertTrue(s2.all { !it.isVideo && it.durationMs == null })
        assertEquals("p3", RoomTripRepository(db).getTrip("t1")!!.coverPhotoId)

        // The new columns are writable and read back.
        val video = s2.first().copy(mediaType = MediaType.VIDEO, durationMs = 12_345)
        steps.saveStep(steps.getStep("s2")!!.copy(photos = listOf(video) + s2.drop(1)))
        val stored = steps.getStep("s2")!!.photos.first()
        assertEquals(MediaType.VIDEO to 12_345L, stored.mediaType to stored.durationMs)
    }

    @Test fun version7UpgradesWithLegRoutesAndKeepsEverything() = runTest {
        createVersion(7)
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE steps SET transport_mode = 'motorbike', latitude = 23.0688, longitude = 104.993 WHERE id = 's2'")
        }
        val db = open()
        val trip = RoomTripRepository(db).getTrip("t1")!!
        assertEquals("Vòng cung Hà Giang", trip.title)
        val steps = RoomStepRepository(db)
        assertEquals(listOf("s1", "s2"), steps.getSteps("t1").map { it.id })
        assertEquals(TransportMode.MOTORBIKE, steps.getStep("s2")!!.arrivalTransport)
        assertEquals(listOf("a.jpg"), steps.getStep("s2")!!.photos.map { it.fileName })

        // The new table starts empty, stores a route and goes with its step.
        val routes = RoomLegRouteRepository(db)
        assertEquals(emptyList<LegRoute>(), routes.observeAll().first())
        val path = listOf(GeoPoint(22.82, 104.98), GeoPoint(22.95, 104.97), GeoPoint(23.0688, 104.993))
        val route = LegRoute("s2", GeoPoint(22.82, 104.98), GeoPoint(23.0688, 104.993), RouteProfile.MOTORBIKE, "valhalla", LegRouteStatus.OK, 48_500.0, 5520.0, path, 9L)
        routes.save(route)
        val back = routes.observeForTrip("t1").first().single()
        assertEquals(route.copy(path = emptyList()), back.copy(path = emptyList()))
        assertEquals(3, back.path.size)
        steps.deleteStep("s2")
        assertEquals(emptyList<LegRoute>(), routes.observeAll().first())
    }

    @Test fun version8UpgradesWithUsedProfileAndKeepsStoredRoutes() = runTest {
        createVersion(8)
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE steps SET transport_mode = 'motorbike', latitude = 23.0688, longitude = 104.993 WHERE id = 's2'")
            db.execSQL(
                "INSERT INTO leg_routes (to_step_id, from_lat, from_lng, to_lat, to_lng, profile, provider, status, distance_m, duration_s, geometry, fetched_at) " +
                    "VALUES ('s2', 22.82, 104.98, 23.0688, 104.993, 'motorbike', 'valhalla', 'no_route', 0, 0, '', 5)",
            )
        }
        val db = open()
        assertEquals(listOf("s1", "s2"), RoomStepRepository(db).getSteps("t1").map { it.id })
        assertEquals("Vòng cung Hà Giang", RoomTripRepository(db).getTrip("t1")!!.title)

        // The stored v8 row survives; its answer counts as given by its own profile (NULL used_profile).
        val routes = RoomLegRouteRepository(db)
        val old = routes.observeAll().first().single()
        assertEquals(LegRouteStatus.NO_ROUTE to RouteProfile.MOTORBIKE, old.status to old.profile)
        assertEquals(RouteProfile.MOTORBIKE, old.usedProfile)
        assertTrue(old.needsCarRetry)

        // The car answer replaces it and reads back with the profile that was used.
        val path = listOf(GeoPoint(22.82, 104.98), GeoPoint(22.95, 104.97), GeoPoint(23.0688, 104.993))
        val car = old.copy(status = LegRouteStatus.OK, distanceM = 61_000.0, durationS = 5400.0, path = path, usedProfile = RouteProfile.CAR)
        routes.save(car)
        val back = routes.observeAll().first().single()
        assertEquals(RouteProfile.MOTORBIKE to RouteProfile.CAR, back.profile to back.usedProfile)
        assertTrue(back.carFallback)
        assertFalse(back.needsCarRetry)
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
