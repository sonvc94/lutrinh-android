package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** Camera focus rules (docs/05-map.md "Camera rules"). */
class CameraMathTest {
    @Test fun flyDurationGrowsWithDistanceAndZoomAndIsClamped() {
        assertEquals(CameraMath.MIN_ANIMATION_MS, CameraMath.flyDurationMs(0.0, 10.0, 10.0))
        assertEquals(820, CameraMath.flyDurationMs(100.0, 9.0, 11.0))
        assertEquals(CameraMath.MAX_ANIMATION_MS, CameraMath.flyDurationMs(1500.0, 5.0, 11.0))
    }

    @Test fun focusZoomZoomsInToTownScaleButNeverOut() {
        assertEquals(MapConfig.STEP_FOCUS_ZOOM, CameraMath.focusZoom(7.0), 0.0)
        assertEquals(12.5, CameraMath.focusZoom(12.5), 0.0)
        assertEquals(MapConfig.FIT_MAX_ZOOM, CameraMath.focusZoom(17.0), 0.0)
    }
}
