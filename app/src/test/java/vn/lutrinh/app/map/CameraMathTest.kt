package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Geo
import kotlin.math.abs
import kotlin.math.pow

/** Camera focus rules (docs/05-map.md "Camera rules", R37). */
class CameraMathTest {
    @Test fun flyDurationGrowsWithDistanceAndZoomAndIsClamped() {
        assertEquals(400, CameraMath.MIN_ANIMATION_MS)
        assertEquals(1800, CameraMath.MAX_ANIMATION_MS)
        assertEquals(CameraMath.MIN_ANIMATION_MS, CameraMath.flyDurationMs(0.0, 10.0, 10.0))
        assertEquals(820, CameraMath.flyDurationMs(100.0, 9.0, 11.0))
        assertEquals(CameraMath.MAX_ANIMATION_MS, CameraMath.flyDurationMs(1500.0, 5.0, 11.0))
    }

    @Test fun focusZoomZoomsInToTownScaleButNeverOut() {
        assertEquals(MapConfig.STEP_FOCUS_ZOOM, CameraMath.focusZoom(7.0), 0.0)
        assertEquals(12.5, CameraMath.focusZoom(12.5), 0.0)
        assertEquals(MapConfig.FIT_MAX_ZOOM, CameraMath.focusZoom(17.0), 0.0)
    }

    // A 1080 × 2200 px map at density 2.75; 48 dp edges, carousel 200 dp at the bottom.
    private val w = 1080
    private val h = 2200
    private val d = 2.75f
    private val pad = MapInsets(132, 132, 132, 132 + 550)
    private val dongVan = GeoPoint(23.28, 105.36)
    private val haGiang = GeoPoint(22.82, 104.98)

    @Test fun stepPlan_centresOnTheStepWithThePaddingAndFitsTheLegAroundIt() {
        val from = MapCamera(haGiang, 9.0)
        val plan = CameraMath.stepPlan(dongVan, listOf(haGiang, dongVan), from, w, h, d, pad, reducedMotion = false)
        assertEquals(dongVan, plan.center)
        assertEquals(pad, plan.padding)
        // The far end of the leg sits just inside the free area on its binding axis.
        val scale = MapFit.TILE * 2.0.pow(plan.zoom) * d
        val offX = abs(MapFit.mercX(haGiang.longitude) - MapFit.mercX(dongVan.longitude)) * scale
        val offY = abs(MapFit.mercY(haGiang.latitude) - MapFit.mercY(dongVan.latitude)) * scale
        val halfW = (w - pad.left - pad.right) / 2.0
        val halfH = (h - pad.top - pad.bottom) / 2.0
        assertTrue("$offX $offY", offX <= halfW + 0.5 && offY <= halfH + 0.5)
        assertTrue("$offX $offY", abs(offX - halfW) < 0.5 || abs(offY - halfH) < 0.5)
        assertTrue(plan.zoom < MapConfig.STEP_MAX_ZOOM)
        // Length from the distance and zoom change, inside the clamp.
        val expected = CameraMath.flyDurationMs(Geo.distanceKm(haGiang, dongVan), 9.0, plan.zoom)
        assertEquals(expected, plan.durationMs)
        assertTrue(plan.durationMs in CameraMath.MIN_ANIMATION_MS..CameraMath.MAX_ANIMATION_MS)
    }

    @Test fun stepPlan_zoomIsCappedForShortLegs_andFlooredForHugeOnes() {
        val near = GeoPoint(23.281, 105.361)
        assertEquals(MapConfig.STEP_MAX_ZOOM, CameraMath.stepPlan(dongVan, listOf(near), null, w, h, d, pad, false).zoom, 0.0)
        val farAway = GeoPoint(-40.0, -100.0)
        assertEquals(MapConfig.OVERVIEW_ZOOM, CameraMath.stepPlan(dongVan, listOf(farAway), null, w, h, d, pad, false).zoom, 0.0)
        // No region (lone step, draft, flights on both sides) or no size yet: village zoom.
        assertEquals(MapConfig.SINGLE_POINT_ZOOM, CameraMath.stepPlan(dongVan, emptyList(), null, w, h, d, pad, false).zoom, 0.0)
        assertEquals(MapConfig.SINGLE_POINT_ZOOM, CameraMath.stepPlan(dongVan, listOf(haGiang), null, 0, 0, d, pad, false).zoom, 0.0)
    }

    @Test fun stepPlan_durationIsClampedAndJumpsWithReducedMotion() {
        val far = MapCamera(GeoPoint(10.77, 106.70), 5.0)
        assertEquals(CameraMath.MAX_ANIMATION_MS, CameraMath.stepPlan(dongVan, listOf(haGiang), far, w, h, d, pad, false).durationMs)
        val same = MapCamera(dongVan, MapConfig.SINGLE_POINT_ZOOM)
        assertEquals(CameraMath.MIN_ANIMATION_MS, CameraMath.stepPlan(dongVan, emptyList(), same, w, h, d, pad, false).durationMs)
        // System animations off, or no camera yet: jump.
        assertEquals(0, CameraMath.stepPlan(dongVan, listOf(haGiang), far, w, h, d, pad, reducedMotion = true).durationMs)
        assertEquals(0, CameraMath.stepPlan(dongVan, listOf(haGiang), null, w, h, d, pad, false).durationMs)
    }
}
