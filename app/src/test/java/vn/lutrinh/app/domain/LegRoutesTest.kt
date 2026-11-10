package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRouteStatus
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.DistanceSource
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.overallStatsOf
import vn.lutrinh.app.domain.usecase.statsOf
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos

/** R36: which legs are routed, the stored-route rules, the off-road guard, km/time shown. */
class LegRoutesTest {
    // The sample stops (data/SampleData.kt).
    private val haGiang = GeoPoint(22.8233, 104.9836)
    private val quanBa = GeoPoint(23.0688, 104.9930)
    private val trangAn = GeoPoint(20.2546, 105.8954)
    private val tamCoc = GeoPoint(20.2163, 105.9370)

    private fun step(id: String, at: String, p: GeoPoint, mode: TransportMode? = null) =
        Step(id, "t", id, "", p, LocalDateTime.parse(at), 0, 0, emptyList(), mode)

    private val trip = Trip("t", "Hà Giang", "", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), 0, 0)

    /** A point [m] metres north of [p]. */
    private fun north(p: GeoPoint, m: Double) = GeoPoint(p.latitude + m / 111_195.0, p.longitude)

    /** A point [m] metres east of [p]. */
    private fun east(p: GeoPoint, m: Double) = GeoPoint(p.latitude, p.longitude + m / (111_195.0 * cos(Math.toRadians(p.latitude))))

    private fun found(km: Double, min: Double, vararg path: GeoPoint) = RouteResult.Found(km * 1000, min * 60, path.toList())

    private fun ok(toStepId: String, from: GeoPoint, to: GeoPoint, profile: RouteProfile, km: Double, min: Double, path: List<GeoPoint>) =
        LegRoute(toStepId, from, to, profile, "valhalla", LegRouteStatus.OK, km * 1000, min * 60, path, 1L)

    @Test fun notChosenIsRoutedExactlyLikeMotorbike() {
        assertEquals(RouteProfile.MOTORBIKE, RouteProfile.of(null))
        assertEquals(RouteProfile.of(TransportMode.MOTORBIKE), RouteProfile.of(null))
        val none = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa, null))).single()
        val bike = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa, TransportMode.MOTORBIKE))).single()
        assertEquals(LegRoutes.requestOf(bike), LegRoutes.requestOf(none))
    }

    @Test fun groundModesAreRoutedTrainPlaneBoatStayStraight() {
        assertEquals(RouteProfile.CAR, RouteProfile.of(TransportMode.CAR))
        assertEquals(RouteProfile.BUS, RouteProfile.of(TransportMode.BUS))
        assertEquals(RouteProfile.WALK, RouteProfile.of(TransportMode.WALK))
        listOf(TransportMode.TRAIN, TransportMode.PLANE, TransportMode.BOAT).forEach { assertNull(RouteProfile.of(it)) }
        val boat = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa, TransportMode.BOAT))).single()
        assertNull(LegRoutes.requestOf(boat))
        assertEquals(emptyList<Any>(), LegRoutes.pending(listOf(boat), emptyMap()))
    }

    @Test fun haGiangToQuanBaKeepsTheRoadRouteAndShowsRoadKmAndTime() {
        // Re-run 2026-11-10 (motor_scooter): 48.5 km, 92 min, stops 3 m / 20 m from the road.
        val leg = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa, TransportMode.MOTORBIKE))).single()
        val request = LegRoutes.requestOf(leg)!!
        val row = LegRoutes.judge(request, found(48.5, 92.0, east(haGiang, 3.0), GeoPoint(22.95, 104.97), north(quanBa, 20.0)), "valhalla", 5L)
        assertEquals(LegRouteStatus.OK, row.status)
        val m = LegRoutes.measure(leg, LegRoutes.usable(leg, mapOf("b" to row)))
        assertTrue(m.byRoad)
        assertEquals(48.5, m.km, 1e-9)
        assertEquals("~1 giờ 32 phút", DisplayFormat.approxDuration(m.durationS!!))
        // Straight would have been 27 km.
        assertEquals(27.3, leg.distanceKm, 0.1)
    }

    @Test fun trangAnOffRoadSampleStaysStraightInsteadOf29Km() {
        // Tam Cốc -> Tràng An (straight 6.1 km). Valhalla `auto` re-run 2026-11-10: 29.4 km, 48 min,
        // Tam Cốc 51 m from the road, Tràng An 713 m (inside the karst park): road on the far side.
        val leg = legsOf(listOf(step("a", "2026-10-20T08:00", tamCoc), step("b", "2026-10-20T11:00", trangAn, TransportMode.CAR))).single()
        val request = LegRoutes.requestOf(leg)!!
        val result = found(29.4, 48.0, east(tamCoc, 51.0), GeoPoint(20.30, 105.95), north(trangAn, 713.0))
        assertEquals(713.0, LegRoutes.snapM(request, result.path), 2.0)
        // The scout's 1 km / 5x guard would have let it through (713 m < 1 km, 29.4 / 6.1 = 4.8x).
        assertTrue(result.distanceM < leg.distanceKm * 1000 * 5)
        val row = LegRoutes.judge(request, result, "valhalla", 5L)
        assertEquals(LegRouteStatus.TOO_FAR, row.status)
        assertTrue(row.path.isEmpty())
        // Stored (no re-fetch) but drawn and counted straight: ~6 km, labelled "chim bay".
        assertTrue(LegRoutes.pending(listOf(leg), mapOf("b" to row)).isEmpty())
        val m = LegRoutes.measure(leg, LegRoutes.usable(leg, mapOf("b" to row)))
        assertFalse(m.byRoad)
        assertEquals(6.1, m.km, 0.1)
        assertEquals(listOf(tamCoc, trangAn), LegRoutes.path(leg, LegRoutes.usable(leg, mapOf("b" to row))))
        assertEquals(6.1, statsOf(trip, leg.let { listOf(it.from, it.to) }, routes = mapOf("b" to row)).distanceKm, 0.1)
    }

    @Test fun aDetourOfMoreThanFiveTimesStaysStraight() {
        val leg = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa))).single()
        val row = LegRoutes.judge(LegRoutes.requestOf(leg)!!, found(140.0, 300.0, haGiang, quanBa), "valhalla", 5L)
        assertEquals(LegRouteStatus.TOO_FAR, row.status)
    }

    @Test fun storedRouteAppliesOnlyWhileStopsAndProfileMatch() {
        val a = step("a", "2026-10-20T08:00", haGiang)
        val b = step("b", "2026-10-20T11:00", quanBa, TransportMode.MOTORBIKE)
        val route = ok("b", haGiang, quanBa, RouteProfile.MOTORBIKE, 48.5, 92.0, listOf(haGiang, quanBa))
        val leg = legsOf(listOf(a, b)).single()
        assertEquals(route, LegRoutes.usable(leg, mapOf("b" to route)))
        assertTrue(LegRoutes.pending(listOf(leg), mapOf("b" to route)).isEmpty())
        // "Chưa chọn" uses the same stored route as Xe máy.
        val none = legsOf(listOf(a, b.copy(arrivalTransport = null))).single()
        assertEquals(route, LegRoutes.usable(none, mapOf("b" to route)))
        // Another mode, or a moved stop: the stored row no longer applies and the leg is fetched again.
        val car = legsOf(listOf(a, b.copy(arrivalTransport = TransportMode.CAR))).single()
        assertNull(LegRoutes.usable(car, mapOf("b" to route)))
        assertEquals(RouteProfile.CAR, LegRoutes.pending(listOf(car), mapOf("b" to route)).single().profile)
        val moved = legsOf(listOf(a.copy(location = north(haGiang, 200.0)), b)).single()
        assertNull(LegRoutes.usable(moved, mapOf("b" to route)))
        assertEquals(1, LegRoutes.pending(listOf(moved), mapOf("b" to route)).size)
        // A train leg ignores a stored road route.
        val train = legsOf(listOf(a, b.copy(arrivalTransport = TransportMode.TRAIN))).single()
        assertNull(LegRoutes.usable(train, mapOf("b" to route)))
    }

    @Test fun roadLineIsJoinedToTheStopsByShortConnectors() {
        val leg = legsOf(listOf(step("a", "2026-10-20T08:00", haGiang), step("b", "2026-10-20T11:00", quanBa))).single()
        val road = listOf(east(haGiang, 30.0), GeoPoint(22.95, 104.97), north(quanBa, 20.0))
        val route = ok("b", haGiang, quanBa, RouteProfile.MOTORBIKE, 48.5, 92.0, road)
        assertEquals(listOf(haGiang) + road + quanBa, LegRoutes.path(leg, route))
        // Without a route: the straight segment (LegGeometry).
        assertEquals(listOf(haGiang, quanBa), LegRoutes.path(leg, null))
    }

    @Test fun storedLineIsSimplified() {
        val dense = (0..200).map { i -> GeoPoint(22.8233 + i * 0.0012, 104.9836 + (if (i % 2 == 0) 0.0 else 0.00001)) }
        val leg = legsOf(listOf(step("a", "2026-10-20T08:00", dense.first()), step("b", "2026-10-20T11:00", dense.last()))).single()
        val row = LegRoutes.judge(LegRoutes.requestOf(leg)!!, RouteResult.Found(27_000.0, 3600.0, dense), "valhalla", 1L)
        assertEquals(LegRouteStatus.OK, row.status)
        assertTrue("${row.path.size}", row.path.size < 10)
    }

    @Test fun tripTotalsUseRoadKmAndTimeAndMarkPartialTotals() {
        val a = step("a", "2026-10-20T08:00", haGiang)
        val b = step("b", "2026-10-20T11:00", quanBa, TransportMode.MOTORBIKE)
        val c = step("c", "2026-10-20T15:00", haGiang, TransportMode.CAR)
        val ab = ok("b", haGiang, quanBa, RouteProfile.MOTORBIKE, 48.5, 92.0, listOf(haGiang, quanBa))
        val bc = ok("c", quanBa, haGiang, RouteProfile.CAR, 48.4, 67.0, listOf(quanBa, haGiang))

        val none = statsOf(trip, listOf(a, b, c))
        assertEquals(DistanceSource.STRAIGHT_LINE, none.distanceSource)
        assertEquals(Geo.distanceKm(haGiang, quanBa) * 2, none.distanceKm, 1e-9)
        assertNull(none.durationS)

        val all = statsOf(trip, listOf(a, b, c), routes = mapOf("b" to ab, "c" to bc))
        assertEquals(DistanceSource.ROAD, all.distanceSource)
        assertEquals(96.9, all.distanceKm, 1e-9)
        assertEquals((92.0 + 67.0) * 60, all.durationS!!, 1e-9)
        assertEquals(0, all.legsWithoutTime)

        // One leg still straight: km mixes road + straight, the time is partial (1 leg missing).
        val part = statsOf(trip, listOf(a, b, c), routes = mapOf("b" to ab))
        assertEquals(DistanceSource.MIXED, part.distanceSource)
        assertEquals(48.5 + Geo.distanceKm(quanBa, haGiang), part.distanceKm, 1e-9)
        assertEquals(92.0 * 60, part.durationS!!, 1e-9)
        assertEquals(1, part.legsWithoutTime)

        // "Bản đồ của tôi" totals use the same leg km.
        assertEquals(96.9, overallStatsOf(listOf(trip), listOf(a, b, c), mapOf("b" to ab, "c" to bc)).distanceKm, 1e-9)
    }

    @Test fun approximateDurationsAlwaysCarryATilde() {
        assertEquals("~35 phút", DisplayFormat.approxDuration(35 * 60.0 + 10))
        assertEquals("~2 giờ 35 phút", DisplayFormat.approxDuration(2 * 3600.0 + 35 * 60))
        assertEquals("~3 giờ", DisplayFormat.approxDuration(3 * 3600.0 + 20))
        assertEquals("~1 phút", DisplayFormat.approxDuration(5.0))
    }

    @Test fun polyline6RoundTrips() {
        val pts = listOf(GeoPoint(22.823312, 104.983645), GeoPoint(23.068801, 104.993012), GeoPoint(-33.5, -70.25))
        val back = Polyline.decode6(Polyline.encode6(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-6)
            assertEquals(a.longitude, b.longitude, 1e-6)
        }
        assertEquals(emptyList<GeoPoint>(), Polyline.decode6(""))
    }
}
