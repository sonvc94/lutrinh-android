package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.LocationFix
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.usecase.FixVerdict
import vn.lutrinh.app.domain.usecase.TrackFilter
import vn.lutrinh.app.domain.usecase.TrackFormat
import vn.lutrinh.app.domain.usecase.TrackMath

/** R16: filter, distance and drawing of recorded tracks (docs/04-business-rules.md). */
class TrackLogicTest {
    private val base = GeoPoint(22.8233, 104.9836)

    /** [dLat] degrees north of [base] (0.001° ≈ 111 m). */
    private fun at(dLat: Double, dLng: Double = 0.0) = GeoPoint(base.latitude + dLat, base.longitude + dLng)
    private fun point(dLat: Double, t: Long, segment: Int = 0, track: String = "k") = TrackPoint(track, segment, t, at(dLat))
    private fun fix(dLat: Double, t: Long, acc: Float? = 5f) = LocationFix(at(dLat), t, acc)

    @Test fun `first fix of a segment is accepted`() {
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.0, 10_000), null, 0, 10_000))
    }

    @Test fun `fix with accuracy worse than 50 m is dropped, missing accuracy is kept`() {
        assertEquals(FixVerdict.INACCURATE, TrackFilter.check(fix(0.0, 10_000, acc = 51f), null, 0, 10_000))
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.0, 10_000, acc = 50f), null, 0, 10_000))
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.0, 10_000, acc = null), null, 0, 10_000))
    }

    @Test fun `invalid coordinates are dropped`() {
        val bad = LocationFix(GeoPoint(95.0, 105.0), 10_000, 5f)
        assertEquals(FixVerdict.INVALID, TrackFilter.check(bad, null, 0, 10_000))
    }

    @Test fun `fix closer than 10 m to the last point is dropped`() {
        val last = point(0.0, 10_000)
        assertEquals(FixVerdict.TOO_CLOSE, TrackFilter.check(fix(0.00005, 15_000), last, 0, 0)) // ≈ 5.6 m
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.0002, 15_000), last, 0, 0)) // ≈ 22 m
    }

    @Test fun `impossible jump faster than 85 m per s is dropped`() {
        val last = point(0.0, 10_000)
        // 1.11 km in 5 s = 222 m/s.
        assertEquals(FixVerdict.JUMP, TrackFilter.check(fix(0.01, 15_000), last, 0, 0))
        // The same 1.11 km in 20 s = 55 m/s (fast car) is fine.
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.01, 30_000), last, 0, 0))
    }

    @Test fun `fix older than the last point or the segment start is stale`() {
        val last = point(0.0, 10_000)
        assertEquals(FixVerdict.STALE, TrackFilter.check(fix(0.001, 10_000), last, 0, 0))
        assertEquals(FixVerdict.STALE, TrackFilter.check(fix(0.001, 5_000), null, 0, 10_000))
    }

    @Test fun `after a pause the first fix is not compared with the point before the pause`() {
        val beforePause = point(0.0, 10_000, segment = 0)
        // 11 km away 5 s later would be a jump inside one segment, but it is a new segment.
        assertEquals(FixVerdict.ACCEPT, TrackFilter.check(fix(0.1, 15_000), beforePause, 1, 14_000))
    }

    @Test fun `length sums the segments and never the gap of a pause`() {
        val pts = listOf(
            point(0.0, 0), point(0.001, 5_000), point(0.002, 10_000), // 2 × ≈111 m
            point(0.05, 60_000, segment = 1), point(0.051, 65_000, segment = 1), // gap ≈ 5.3 km not counted
        )
        val oneStep = TrackMath.distanceM(at(0.0), at(0.001))
        assertEquals(111.2, oneStep, 0.2)
        assertEquals(3 * oneStep, TrackMath.lengthM(pts), 0.5)
    }

    @Test fun `display lines are one per segment and per track, single points draw nothing`() {
        val pts = listOf(
            point(0.0, 0), point(0.001, 5_000),
            point(0.01, 60_000, segment = 1), // a lone point after a resume
            point(0.02, 90_000, segment = 2), point(0.021, 95_000, segment = 2),
            point(0.0, 0, track = "other"), point(0.001, 5_000, track = "other"),
        )
        val lines = TrackMath.displayLines(pts)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.size == 2 })
    }

    @Test fun `display lines are simplified but keep both ends`() {
        // 50 points on a straight line north + 0.00001° of noise: drawn as one segment.
        val pts = (0 until 50).map { i -> TrackPoint("k", 0, i * 5_000L, at(i * 0.0003, if (i % 2 == 0) 0.00001 else 0.0)) }
        val line = TrackMath.displayLines(pts).single()
        assertEquals(2, line.size)
        assertEquals(pts.first().location, line.first())
        assertEquals(pts.last().location, line.last())
        // A real bend (≈ 110 m sideways) survives.
        val bent = pts.mapIndexed { i, p -> if (i == 25) p.copy(location = at(25 * 0.0003, 0.001)) else p }
        assertTrue(TrackMath.displayLines(bent).single().contains(at(25 * 0.0003, 0.001)))
    }

    @Test fun `distance and duration formats`() {
        assertEquals("0 m", TrackFormat.distance(0.0))
        assertEquals("850 m", TrackFormat.distance(849.6))
        assertEquals("2,4 km", TrackFormat.distance(2_449.0))
        assertEquals("125 km", TrackFormat.distance(124_600.0))
        assertEquals("0:00", TrackFormat.duration(0))
        assertEquals("12:34", TrackFormat.duration((12 * 60 + 34) * 1000L + 999))
        assertEquals("2:05:09", TrackFormat.duration((2 * 3600 + 5 * 60 + 9) * 1000L))
    }
}
