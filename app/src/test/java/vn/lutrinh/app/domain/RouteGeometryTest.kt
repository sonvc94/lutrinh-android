package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.Polyline
import kotlin.math.abs
import kotlin.math.hypot

/** Route drawing maths (docs/05-map.md "Data prep per leg"): flight arc and simplification. */
class RouteGeometryTest {
    private val hanoi = GeoPoint(21.0285, 105.8542)
    private val saigon = GeoPoint(10.8231, 106.6297)
    private val haGiang = GeoPoint(22.8233, 104.9836)
    private val dongVan = GeoPoint(23.2786, 105.3621)
    private val tokyo = GeoPoint(35.6762, 139.6503)
    private val losAngeles = GeoPoint(34.0522, -118.2437)

    private fun mercator(p: GeoPoint) = p.longitude to LegGeometry.mercY(p.latitude)

    @Test fun greatCircleKeepsEndsAndSpacesPointsEvenly() {
        val gc = LegGeometry.greatCircle(hanoi, saigon, 11)
        assertEquals(11, gc.size)
        assertEquals(hanoi, gc.first())
        assertEquals(saigon, gc.last())
        val steps = gc.zipWithNext { a, b -> Geo.distanceKm(a, b) }
        val expected = Geo.distanceKm(hanoi, saigon) / 10
        steps.forEach { assertEquals(expected, it, expected * 1e-3) }
    }

    @Test fun greatCircleAcrossTheAntimeridianNeverJumps() {
        val gc = LegGeometry.greatCircle(tokyo, losAngeles, 60)
        gc.zipWithNext { a, b -> assertTrue("jump ${a.longitude} -> ${b.longitude}", abs(b.longitude - a.longitude) < 10) }
        assertEquals(losAngeles.longitude + 360, gc.last().longitude, 1e-9)
        // The shortest way bulges towards the pole.
        assertTrue(gc.maxOf { it.latitude } > 45)
    }

    @Test fun arcLiftIsZeroAtTheEndsAndTwoTenthsOfTheChordAtMidLeg() {
        val gc = LegGeometry.greatCircle(hanoi, saigon, 25)
        val arc = LegGeometry.arc(hanoi, saigon, points = 25)
        assertEquals(hanoi, arc.first())
        assertEquals(saigon, arc.last())
        val (ax, ay) = mercator(hanoi)
        val (bx, by) = mercator(saigon)
        val chord = hypot(bx - ax, by - ay)
        val (mx, my) = mercator(arc[12])
        val (gx, gy) = mercator(gc[12])
        assertEquals(LegGeometry.ARC_LIFT * chord, hypot(mx - gx, my - gy), 1e-6)
        // A quarter of the way the lift is 4·¼·¾ = ¾ of the mid-leg lift.
        val (qx, qy) = mercator(arc[6])
        val (hx, hy) = mercator(gc[6])
        assertEquals(0.75 * LegGeometry.ARC_LIFT * chord, hypot(qx - hx, qy - hy), 1e-6)
    }

    @Test fun arcBendsToTheLeftOfTheTravelDirection() {
        // Southbound: left is east.
        val south = LegGeometry.arc(hanoi, saigon, points = 25)
        assertTrue(south[12].longitude > LegGeometry.greatCircle(hanoi, saigon, 25)[12].longitude + 1)
        // Eastbound: left is north; westbound: left is south.
        val a = GeoPoint(21.0, 100.0)
        val b = GeoPoint(21.0, 110.0)
        val gcMid = LegGeometry.greatCircle(a, b, 25)[12].latitude
        assertTrue(LegGeometry.arc(a, b, points = 25)[12].latitude > gcMid + 1)
        assertTrue(LegGeometry.arc(b, a, points = 25)[12].latitude < gcMid - 1)
    }

    @Test fun arcPointCountGrowsWithDistanceWithinBounds() {
        assertEquals(LegGeometry.ARC_MIN_POINTS, LegGeometry.arcPointCount(haGiang, dongVan))
        val vn = LegGeometry.arcPointCount(hanoi, saigon)
        assertTrue("$vn", vn in 50..65)
        assertEquals(LegGeometry.ARC_MAX_POINTS, LegGeometry.arcPointCount(tokyo, losAngeles))
        assertEquals(vn, LegGeometry.arc(hanoi, saigon).size)
    }

    @Test fun simplifyDropsPointsWithinToleranceAndKeepsEnds() {
        val wobbly = (0..100).map { i ->
            GeoPoint(21.0 + (if (i % 2 == 0) 5e-5 else -5e-5), 105.0 + i * 0.001)
        }
        val simple = Polyline.simplify(wobbly)
        assertEquals(listOf(wobbly.first(), wobbly.last()), simple)
    }

    @Test fun simplifyKeepsCornersAndShortLines() {
        val l = listOf(GeoPoint(21.0, 105.0), GeoPoint(21.0, 105.5), GeoPoint(21.0, 106.0), GeoPoint(21.5, 106.0), GeoPoint(22.0, 106.0))
        assertEquals(listOf(l[0], l[2], l[4]), Polyline.simplify(l))
        val two = listOf(haGiang, dongVan)
        assertSame(two, Polyline.simplify(two))
        assertEquals(emptyList<GeoPoint>(), Polyline.simplify(emptyList()))
    }

    @Test fun simplifiedFlightArcKeepsItsShape() {
        val arc = LegGeometry.arc(hanoi, saigon)
        val simple = Polyline.simplify(arc)
        assertEquals(arc.first(), simple.first())
        assertEquals(arc.last(), simple.last())
        assertTrue(simple.size in 10..arc.size)
        // Every dropped point stays within the tolerance of the simplified line.
        arc.forEach { p ->
            val d = simple.zipWithNext { a, b -> Polyline.segmentDistance(p, a, b) }.min()
            assertTrue("$d", d <= Polyline.SIMPLIFY_EPSILON_DEG + 1e-12)
        }
    }

    @Test fun pointAtFindsTheMiddleByLength() {
        val straight = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 2.0))
        assertEquals(1.0, Polyline.pointAt(straight, 0.5).longitude, 1e-9)
        // L shape: first segment 3 long, second 1 long → middle (length 2) on the first segment.
        val l = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 3.0), GeoPoint(1.0, 3.0))
        val mid = Polyline.pointAt(l, 0.5)
        assertEquals(0.0, mid.latitude, 1e-6)
        assertEquals(2.0, mid.longitude, 1e-3)
        assertEquals(l.first(), Polyline.pointAt(l, 0.0))
        assertEquals(1.0, Polyline.pointAt(l, 1.0).latitude, 1e-9)
    }
}
