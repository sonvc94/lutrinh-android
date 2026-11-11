package vn.lutrinh.app.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
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
import vn.lutrinh.app.catalog.server.LuTrinhServerCatalog
import vn.lutrinh.app.data.RoomLegRouteRepository
import vn.lutrinh.app.data.RoomRouteRepository
import vn.lutrinh.app.data.RoomStepRepository
import vn.lutrinh.app.data.RoomTripRepository
import vn.lutrinh.app.data.RouteStagingDir
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.catalog.CatalogError
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.DownloadedRoute
import vn.lutrinh.app.domain.catalog.LinkRef
import vn.lutrinh.app.domain.catalog.RemoteStatus
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RouteDownload
import vn.lutrinh.app.domain.catalog.RouteImport
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.routing.LegRouteStatus
import vn.lutrinh.app.domain.usecase.Polyline
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * R40 end to end on the JVM: the spec sample served by a fake public API v1 → RouteDownload
 * (route.json checked, 14 files with sha256, resume, token renewal) → ONE transaction into Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RouteDownloadImportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val served = ServedSample()
    private val server = MockWebServer().apply { dispatcher = served; start() }
    private lateinit var db: AppDatabase
    private lateinit var photoDir: File
    private lateinit var staging: RouteStagingDir
    private lateinit var routes: RoomRouteRepository
    private val catalog = LuTrinhServerCatalog(server.url("/").toString(), "LuTrinh/test (Android)")
    private val code = RouteCode.of(served.code)!!

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = Files.createTempDirectory("route-dl").toFile()
        photoDir = File(root, "photos")
        staging = RouteStagingDir(File(root, "staging"))
        routes = RoomRouteRepository(db, photoDir) { names -> names.forEach { File(photoDir, it).delete() } }
    }

    @After fun tearDown() { db.close(); server.shutdown() }

    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun download(): CatalogResult<DownloadedRoute> = runBlocking { RouteDownload(catalog, staging).run(code) }

    private fun import(d: DownloadedRoute, start: LocalDate = LocalDate.of(2026, 12, 5), replacing: String? = null): String = runBlocking {
        val plan = RouteImport.plan(d.pkg, code, d.sha256, start, 1_000L, { UUID.randomUUID().toString() }, tripId = replacing ?: UUID.randomUUID().toString())
        routes.import(plan, staging::fileOf, replacing)
    }

    @Test fun downloadingTheSpecSampleMakesANewTripWith9Stops8Legs14MediaAndALink() = runBlocking {
        // An existing trip of the user stays as it is.
        RoomTripRepository(db).upsertTrip(Trip("mine", "Đà Lạt", "", LocalDate.parse("2025-05-01"), null, 1, 1))
        RoomStepRepository(db).saveStep(Step("m1", "mine", "Hồ Xuân Hương", "", GeoPoint(11.94, 108.44), LocalDateTime.parse("2025-05-01T09:00"), 1, 1))

        val result = download()
        assertTrue("$result", result is CatalogResult.Ok)
        val d = (result as CatalogResult.Ok).value
        val tripId = import(d)

        assertEquals(2, count("trips"))
        val trip = RoomTripRepository(db).getTrip(tripId)!!
        assertEquals("Vòng cung Hà Giang 4 ngày", trip.title)
        assertEquals(LocalDate.of(2026, 12, 5) to LocalDate.of(2026, 12, 8), trip.startDate to trip.endDate)
        val steps = RoomStepRepository(db).getSteps(tripId)
        assertEquals(9, steps.size)
        val media = steps.flatMap { it.photos }
        assertEquals(14, media.size)
        assertEquals(2, media.count { it.mediaType == MediaType.VIDEO })
        assertEquals(14, count("photos"))
        // Every file is in the photo folder under its new name, byte for byte the served file.
        val bySha = d.pkg.media.associate { it.id to it.sha256 }
        media.forEach { p ->
            val f = File(photoDir, p.fileName)
            assertTrue(p.fileName, f.isFile)
            assertTrue(p.fileName, PackageJson.sha256(f.readBytes()) in served.files.keys)
        }
        assertEquals(14, bySha.size)
        assertEquals(LocalDateTime.of(2026, 12, 5, 7, 30), steps.first().arrivedAt)

        // 8 legs with the server's geometry between the trip's own stops (D-13).
        val legs = RoomLegRouteRepository(db).observeForTrip(tripId).first()
        assertEquals(8, legs.size)
        val served0 = Polyline.decode6(served.doc.getJSONArray("legs").getJSONObject(0).getString("path"))
        legs.forEach { assertEquals(LegRouteStatus.OK, it.status); assertTrue(it.path.size >= 2) }
        val sources = (0 until 8).map { served.doc.getJSONArray("legs").getJSONObject(it).getString("source") }
        assertEquals(sources, steps.drop(1).map { s -> legs.single { it.toStepId == s.id }.provider })
        val first = legs.single { it.toStepId == steps[1].id }
        assertEquals(served0, first.path)
        assertEquals(steps[0].location to steps[1].location, first.from to first.to)

        // The link row.
        val link = routes.link(tripId)!!
        assertEquals(Triple("HG4N7KQE", "rt_5f3c9a1e", 3), Triple(link.code.value, link.routeId, link.version))
        assertEquals(PackageJson.sha256(served.routeBytes), link.packageSha256)
        assertEquals(RemoteStatus.ACTIVE to false, link.remoteStatus to link.edited)
        assertEquals(1, count("route_links"))

        // The user's trip is untouched.
        assertEquals(listOf("m1"), RoomStepRepository(db).getSteps("mine").map { it.id })

        // Only the code went out: lookup, route, 14 files, with our User-Agent + Accept, no cookie.
        val paths = served.requests.map { it.path!! }
        assertEquals(listOf("/api/v1/meta", "/api/v1/codes/HG4N7KQE"), paths.take(2))
        assertEquals(1 + 1 + 1 + 14, paths.size)
        served.requests.forEach { r ->
            assertEquals("LuTrinh/test (Android)", r.getHeader("User-Agent"))
            assertNull(r.getHeader("Cookie"))
        }
        assertEquals(LuTrinhServerCatalog.ACCEPT, served.requests[1].getHeader("Accept"))
    }

    /** R41: the served sample's leg note (stop s5) is stored on its step, in the same transaction. */
    @Test fun legNotesOfAServedPackageAreStoredOnTheirSteps() = runBlocking {
        val tripId = import((download() as CatalogResult.Ok).value)
        val steps = RoomStepRepository(db).getSteps(tripId)
        assertEquals(
            listOf(null, null, null, null, "Đường dốc khó đi. Gửi xe phía đối diện, 30.000 ₫.", null, null, null, null),
            steps.map { it.arrivalNote },
        )
    }

    @Test fun aStoppedDownloadResumesWithRangeFromTheStagedBytes() {
        val (sha, body) = served.files.entries.first()
        runBlocking { staging.write(sha, append = false) { it.write(body, body.size / 2) } }
        val result = download()
        assertTrue("$result", result is CatalogResult.Ok)
        val r = served.requests.single { it.path!!.endsWith(sha) }
        assertEquals("bytes=${body.size / 2}-", r.getHeader("Range"))
        assertEquals(sha, staging.hashOf(sha))
    }

    @Test fun anExpiredTokenIsRenewedOnceByLookingTheCodeUpAgain() {
        served.beforeMedia = { i -> if (i == 3) served.validTokens.clear() }
        val result = download()
        assertTrue("$result", result is CatalogResult.Ok)
        assertEquals(2, served.requests.count { it.path!!.startsWith("/api/v1/codes/") })
    }

    @Test fun aTokenThatExpiresTwiceStopsTheDownload() {
        served.beforeMedia = { i -> if (i == 2 || i == 5) served.validTokens.clear() }
        assertEquals(CatalogResult.Refused(CatalogError.TOKEN_EXPIRED), download())
    }

    @Test fun aCorruptedFileIsFetchedAgainOnce() {
        val sha = served.files.keys.elementAt(4)
        served.corrupt = setOf(sha)
        served.corruptTimes = 1
        assertTrue(download() is CatalogResult.Ok)
        assertEquals(2, served.requests.count { it.path!!.endsWith(sha) })
    }

    @Test fun aFileCorruptedTwiceRefusesThePackageAndImportsNothing() {
        served.corrupt = setOf(served.files.keys.elementAt(4))
        assertEquals(CatalogResult.Refused(CatalogError.BAD_PACKAGE), download())
        assertEquals(0, count("trips"))
    }

    @Test fun aPackageThatFailsTheSchemaIsRefusedBeforeAnyFile() {
        served.overrides["/api/v1/dl/"] = ServedSample.json(served.doc.put("route", served.doc.getJSONObject("route").put("days", 31)))
        assertEquals(CatalogResult.Refused(CatalogError.BAD_PACKAGE), download())
    }

    @Test fun theApiErrorTableMapsToCatalogErrors() = runBlocking {
        val cases = listOf(
            ServedSample.error(404, "code_not_found") to CatalogResult.Refused(CatalogError.CODE_NOT_FOUND),
            ServedSample.error(410, "code_revoked") to CatalogResult.Refused(CatalogError.CODE_REVOKED),
            ServedSample.error(410, "code_expired") to CatalogResult.Refused(CatalogError.CODE_EXPIRED),
            ServedSample.error(410, "route_unpublished") to CatalogResult.Refused(CatalogError.ROUTE_UNPUBLISHED),
            ServedSample.error(400, "bad_check") to CatalogResult.Refused(CatalogError.BAD_CHECK),
            ServedSample.error(406, "format_unsupported") to CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED),
            ServedSample.error(429, "rate_limited", 1800) to CatalogResult.Refused(CatalogError.RATE_LIMITED, 1800),
            ServedSample.error(503, "maintenance", 600) to CatalogResult.Unavailable(600),
            ServedSample.error(503, "quota") to CatalogResult.Unavailable(),
        )
        cases.forEach { (answer, expected) ->
            served.overrides["/api/v1/codes/"] = answer
            assertEquals(expected, catalog.resolve(code))
        }
        server.shutdown()
        assertEquals(CatalogResult.Unavailable(), catalog.resolve(code))
    }

    @Test fun aNewerFormatIsRefusedAsFormatUnsupported() {
        served.overrides["/api/v1/dl/"] = ServedSample.json(served.doc.put("formatVersion", 2))
        assertEquals(CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED), download())
    }

    @Test fun theUpdateCheckSendsOnlyCodeRouteAndVersionAndReadsTheResults() = runBlocking {
        val answer = """{"results":[
            {"code":"HG4N7KQE","routeId":"rt_5f3c9a1e","status":"newer","version":4,"publishedAt":"2026-12-01T03:00:00Z","note":"Thêm Du Già",
             "changes":{"stopsAdded":["Du Già"],"stopsRemoved":[],"stopsEdited":2,"mediaAdded":3,"bytes":5400000}},
            {"code":"NB2XW9DS","routeId":"rt_aaaaaaaa","status":"current","version":1},
            {"code":"M3T8RCAJ","routeId":"rt_bbbbbbbb","status":"revoked"}]}"""
        served.overrides["/api/v1/updates"] = ServedSample.json(answer)
        val links = listOf(
            LinkRef(code, "rt_5f3c9a1e", 3),
            LinkRef(RouteCode.of("NB2XW9DS")!!, "rt_aaaaaaaa", 1),
            LinkRef(RouteCode.of("M3T8RCAJ")!!, "rt_bbbbbbbb", 2),
        )
        val r = catalog.checkUpdates(links) as CatalogResult.Ok
        assertEquals(listOf(RemoteStatus.ACTIVE, RemoteStatus.ACTIVE, RemoteStatus.REVOKED), r.value.map { it.status })
        val newer = r.value[0].newer!!
        assertEquals(4 to listOf("Du Già"), newer.version to newer.stopsAdded)
        assertEquals(5_400_000L, newer.bytes)
        assertNull(r.value[1].newer)
        val sent = served.requests.single { it.path == "/api/v1/updates" }
        assertEquals("POST", sent.method)
        val body = JSONArray(sent.body.readUtf8())
        assertEquals(3, body.length())
        assertEquals(setOf("code", "routeId", "version"), body.getJSONObject(0).keys().asSequence().toSet())
    }

    /** D-4: any change to the downloaded trip marks the link edited, so "Thay bản trong máy" is no longer offered. */
    @Test fun editingTheDownloadedTripMarksTheLinkEdited() = runBlocking {
        val tripId = import((download() as CatalogResult.Ok).value)
        assertFalse(routes.link(tripId)!!.edited)
        val steps = RoomStepRepository(db)
        val s = steps.getSteps(tripId)[2]
        steps.saveStep(s.copy(note = "Đã đi qua"))
        assertTrue(routes.link(tripId)!!.edited)
        assertFalse(vn.lutrinh.app.domain.catalog.RouteUpdates.canReplace(routes.link(tripId)!!))
    }

    @Test fun replacingAnUneditedCopyKeepsTheTripIdAndSwapsTheFiles() {
        val d = (download() as CatalogResult.Ok).value
        val tripId = import(d)
        val oldFiles = runBlocking { RoomStepRepository(db).getSteps(tripId) }.flatMap { it.photos }.map { it.fileName }
        // A second download of the same code (the newer version in real life) replaces it.
        val again = (download() as CatalogResult.Ok).value
        assertEquals(tripId, import(again, LocalDate.of(2027, 1, 2), replacing = tripId))
        assertEquals(1, count("trips"))
        assertEquals(9, count("steps"))
        assertEquals(14, count("photos"))
        assertEquals(1, count("route_links"))
        oldFiles.forEach { assertFalse(it, File(photoDir, it).exists()) }
        assertEquals(LocalDate.of(2027, 1, 2), runBlocking { RoomTripRepository(db).getTrip(tripId)!!.startDate })
    }
}
