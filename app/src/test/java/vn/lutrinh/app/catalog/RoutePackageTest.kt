package vn.lutrinh.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.domain.catalog.PackageRules
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RouteImport
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.routing.LegRouteStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** R40: the spec sample reads as expected.json says and maps to a new trip (dates from the start date, D-5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoutePackageTest {
    private val expected = Spec.json(Spec.SAMPLE_EXPECTED)
    private val bytes = Spec.bytes(Spec.SAMPLE)
    private val pkg = PackageJson.parse(bytes.toString(Charsets.UTF_8))!!

    @Test fun sampleReadsAsExpectedJsonSays() {
        assertEquals(expected.getString("sha256"), PackageJson.sha256(bytes))
        assertEquals(expected.getInt("bytes"), bytes.size)
        assertEquals(expected.getInt("stops"), pkg.stops.size)
        assertEquals(expected.getInt("legs"), pkg.legs.size)
        assertEquals(expected.getInt("photos"), pkg.photos)
        assertEquals(expected.getInt("videos"), pkg.videos)
        assertEquals(expected.getLong("sizeMedia"), pkg.sizeMedia)
        assertEquals(expected.getInt("sizeFiles"), pkg.sizeFiles)
        assertEquals(emptyList<String>(), PackageRules.check(pkg))
        assertEquals(emptyList<String>(), PackageRules.appLimits(pkg))
        assertEquals(14, pkg.files().size)
    }

    @Test fun planMakesANewTripFromThePickedStartDate() {
        var n = 0
        val start = LocalDate.of(2026, 12, 5)
        val plan = RouteImport.plan(pkg, RouteCode.of("HG4N7KQE")!!, "abc", start, 1_000L, { "id${n++}" })
        assertEquals(start, plan.trip.startDate)
        assertEquals(start.plusDays(3), plan.trip.endDate)
        assertEquals(pkg.route.title, plan.trip.title)
        assertEquals(9, plan.steps.size)
        assertEquals(plan.steps.map { it.id }.toSet().size, 9)
        assertEquals(14, plan.steps.sumOf { it.photos.size })
        assertEquals(2, plan.steps.flatMap { it.photos }.count { it.mediaType == MediaType.VIDEO })
        assertEquals(14, plan.files.size)
        // Every day of a stop is start + (day - 1); times come from the package.
        pkg.stops.zip(plan.steps).forEach { (s, step) ->
            assertEquals(start.plusDays((s.day - 1).toLong()), step.arrivedAt.toLocalDate())
            s.time?.let { assertEquals(it, step.arrivedAt.toLocalTime().toString()) }
            assertEquals(TransportMode.fromCode(s.arrival), step.arrivalTransport)
        }
        assertEquals(LocalDateTime.of(start, plan.steps[0].arrivedAt.toLocalTime()), plan.steps[0].arrivedAt)
        // Legs: server geometry between the trip's own stops.
        assertEquals(8, plan.legRoutes.size)
        plan.legRoutes.forEach { l ->
            assertEquals(LegRouteStatus.OK, l.status)
            assertTrue(l.path.size >= 2)
            val at = plan.steps.indexOfFirst { it.id == l.toStepId }
            assertEquals(plan.steps[at - 1].location, l.from)
        }
        assertEquals("HG4N7KQE", plan.link.code.value)
        assertEquals(pkg.route.version, plan.link.version)
        assertTrue(plan.trip.coverPhotoId != null)
    }

    @Test fun nullTimesGetEightOClockPlusThePosition() {
        val doc = Spec.json(Spec.SAMPLE)
        val stops = doc.getJSONArray("stops")
        for (i in 0 until stops.length()) stops.getJSONObject(i).put("time", org.json.JSONObject.NULL)
        val p = PackageJson.parse(doc.toString())!!
        val plan = RouteImport.plan(p, RouteCode.of("HG4N7KQE")!!, "abc", LocalDate.of(2026, 12, 5), 0L, { java.util.UUID.randomUUID().toString() })
        assertEquals("08:00", plan.steps[0].arrivedAt.toLocalTime().toString())
        assertEquals("08:03", plan.steps[3].arrivedAt.toLocalTime().toString())
    }

    @Test fun notJsonOrAnotherFormatIsNotRead() {
        assertNull(PackageJson.parse("not json"))
        assertTrue(PackageJson.read("{\"format\":\"gpx\"}") is PackageRead.Invalid)
    }
}
