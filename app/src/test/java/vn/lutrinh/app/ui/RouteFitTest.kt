package vn.lutrinh.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.ui.common.RouteFit

/** The trip-card route cover (docs/08 "Imagery"): projection fits the box, graticule stays sparse. */
class RouteFitTest {
    private val w = 1000f
    private val h = 500f
    private val pad = 40f

    @Test
    fun routeFitsInsideThePaddedBoxAndKeepsOrder() {
        val route = listOf(GeoPoint(22.82, 104.98), GeoPoint(23.36, 105.32), GeoPoint(23.16, 105.41))
        val fit = RouteFit.of(route, w, h, pad)
        assertEquals(3, fit.points.size)
        fit.points.forEach { (x, y) ->
            assertTrue("x=$x", x in pad - 0.5f..w - pad + 0.5f)
            assertTrue("y=$y", y in pad - 0.5f..h - pad + 0.5f)
        }
        // North is up: Lũng Cú (23.36 N) is above TP Hà Giang (22.82 N).
        assertTrue(fit.points[1].second < fit.points[0].second)
        // East is right.
        assertTrue(fit.points[2].first > fit.points[0].first)
    }

    @Test
    fun graticuleHasFewLinesInsideTheBox() {
        val fit = RouteFit.of(listOf(GeoPoint(20.29, 105.91), GeoPoint(20.22, 105.94)), w, h, pad)
        assertTrue(fit.gridX.size in 1..7 && fit.gridY.size in 1..7)
        assertTrue(fit.gridX.all { it in 0f..w } && fit.gridY.all { it in 0f..h })
    }

    @Test
    fun singlePointIsCentredAndNoStepsGivesPaperOnly() {
        val one = RouteFit.of(listOf(GeoPoint(21.0, 105.8)), w, h, pad)
        assertEquals(w / 2, one.points.single().first, 0.5f)
        assertEquals(h / 2, one.points.single().second, 0.5f)
        val none = RouteFit.of(emptyList(), w, h, pad)
        assertTrue(none.points.isEmpty())
    }
}
