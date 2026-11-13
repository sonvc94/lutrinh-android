package vn.lutrinh.app.routing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.routing.osrm.OsrmRouter
import vn.lutrinh.app.routing.valhalla.ValhallaRouter

/** R36 adapters: the app's request in each router's words and its answer back in the app's types. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RouterAdaptersTest {
    private val haGiang = GeoPoint(22.8233, 104.9836)
    private val quanBa = GeoPoint(23.0688, 104.9930)
    private val line = listOf(haGiang, GeoPoint(22.95, 104.97), quanBa)

    @Test fun valhallaCostingPerProfileNotChosenIsMotorScooter() {
        assertEquals("motor_scooter", ValhallaRouter.costing(RouteProfile.MOTORBIKE))
        assertEquals("motor_scooter", ValhallaRouter.costing(RouteProfile.of(null)!!))
        assertEquals("auto", ValhallaRouter.costing(RouteProfile.CAR))
        assertEquals("bus", ValhallaRouter.costing(RouteProfile.BUS))
        assertEquals("pedestrian", ValhallaRouter.costing(RouteProfile.WALK))
    }

    @Test fun valhallaRequestCarriesOnlyTheTwoStopsAndTheCosting() {
        val body = JSONObject(ValhallaRouter.request(haGiang, quanBa, RouteProfile.MOTORBIKE))
        assertEquals(setOf("locations", "costing", "directions_type", "units"), body.keys().asSequence().toSet())
        assertEquals("motor_scooter", body.getString("costing"))
        val locs = body.getJSONArray("locations")
        assertEquals(2, locs.length())
        assertEquals(22.8233, locs.getJSONObject(0).getDouble("lat"), 1e-9)
        assertEquals(104.9930, locs.getJSONObject(1).getDouble("lon"), 1e-9)
    }

    @Test fun valhallaMatrixAsksOneSourceManyTargetsAndReadsDistancesInMetres() {
        val body = JSONObject(ValhallaRouter.matrixRequest(haGiang, listOf(quanBa, haGiang), RouteProfile.MOTORBIKE))
        assertEquals("motor_scooter", body.getString("costing"))
        assertEquals(1, body.getJSONArray("sources").length())
        assertEquals(2, body.getJSONArray("targets").length())
        val answer = """{"sources_to_targets":[[{"from_index":0,"to_index":0,"time":421,"distance":2.159},{"from_index":0,"to_index":1,"time":null,"distance":null}]]}"""
        val r = ValhallaRouter.parseMatrix(HttpAnswer(200, answer), 2) as vn.lutrinh.app.domain.routing.MatrixResult.Rows
        assertEquals(2_159.0, r.distances[0]!!.distanceM, 1e-6)
        assertEquals(421.0, r.distances[0]!!.durationS, 1e-6)
        assertEquals(null, r.distances[1])
        // A far target spoils the whole call (error 154): ask each stop alone. 429 = rate limited.
        assertEquals(vn.lutrinh.app.domain.routing.MatrixResult.NotSupported, ValhallaRouter.parseMatrix(HttpAnswer(400, """{"error_code":154}"""), 2))
        assertTrue((ValhallaRouter.parseMatrix(HttpAnswer(429, ""), 2) as vn.lutrinh.app.domain.routing.MatrixResult.Unavailable).rateLimited)
    }

    @Test fun valhallaAnswerIsConverted() {
        val body = """{"trip":{"summary":{"length":48.512,"time":5530.2},"legs":[{"shape":"${Polyline.encode6(line)}"}]}}"""
        val r = ValhallaRouter.parse(HttpAnswer(200, body)) as RouteResult.Found
        assertEquals(48_512.0, r.distanceM, 1e-6)
        assertEquals(5530.2, r.durationS, 1e-6)
        assertEquals(3, r.path.size)
        assertEquals(quanBa.latitude, r.path.last().latitude, 1e-6)
        assertEquals(RouteResult.NoRoute, ValhallaRouter.parse(HttpAnswer(400, """{"error_code":442,"error":"No path could be found for input"}""")))
        assertTrue((ValhallaRouter.parse(HttpAnswer(429, "")) as RouteResult.Unavailable).rateLimited)
        assertTrue(ValhallaRouter.parse(HttpAnswer(503, "")) is RouteResult.Unavailable)
    }

    @Test fun osrmIsCarOnlyAndConverted() {
        val osrm = OsrmRouter("https://example.org/routed-car", RouterHttp("test"))
        assertTrue(osrm.supports(RouteProfile.CAR))
        assertTrue(listOf(RouteProfile.MOTORBIKE, RouteProfile.BUS, RouteProfile.WALK).none { osrm.supports(it) })
        assertEquals(
            "https://example.org/routed-car/route/v1/driving/104.983600,22.823300;104.993000,23.068800?overview=full&geometries=polyline6",
            OsrmRouter.url("https://example.org/routed-car", haGiang, quanBa),
        )
        val body = """{"code":"Ok","routes":[{"distance":48364.9,"duration":3900.5,"geometry":"${Polyline.encode6(line)}"}]}"""
        val r = OsrmRouter.parse(HttpAnswer(200, body)) as RouteResult.Found
        assertEquals(48_364.9, r.distanceM, 1e-6)
        assertEquals(3, r.path.size)
        assertEquals(RouteResult.NoRoute, OsrmRouter.parse(HttpAnswer(400, """{"code":"NoSegment"}""")))
        assertTrue(OsrmRouter.parse(HttpAnswer(500, "oops")) is RouteResult.Unavailable)
    }

    @Test fun registryHasValhallaFirstAndOsrmAsFallbackWithAUserAgent() {
        assertEquals("valhalla", Routers.registry.primary.id)
        assertEquals(listOf("osrm"), Routers.registry.fallbacks.map { it.id })
        assertTrue(Routers.USER_AGENT, Routers.USER_AGENT.startsWith("LuTrinh/") && "vn.lutrinh.app" in Routers.USER_AGENT)
        assertTrue(Routers.registry.all.all { "OpenStreetMap" in it.credit.text && it.credit.fixUrl == "https://www.openstreetmap.org/fixthemap" })
    }

    @Test fun shareImageCreditWrapsAtSeparatorsInsteadOfBeingCut() {
        val line = vn.lutrinh.app.map.StaticTripMap.routingLine(ValhallaRouter.CREDIT)
        val lines = vn.lutrinh.app.map.StaticTripMap.wrapCredits(listOf(line), 44f) { it.length.toFloat() }
        assertTrue(lines.toString(), lines.size > 1 && lines.all { it.length <= 44 })
        assertEquals(line, lines.joinToString(" · "))
        assertTrue("www.openstreetmap.org/fixthemap" in line)
    }
}
