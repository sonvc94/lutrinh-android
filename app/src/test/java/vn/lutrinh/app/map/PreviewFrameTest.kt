package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint

/** The trip-detail preview shows every pin clear of the credit (bottom) and the expand button (top right). */
class PreviewFrameTest {
    private val ninhBinh = listOf(
        GeoPoint(20.2862, 105.9063), GeoPoint(20.2560, 105.9150), GeoPoint(20.2299, 105.9327), GeoPoint(20.2163, 105.9370),
    )
    private val haGiang = listOf(
        GeoPoint(22.8233, 104.9836), GeoPoint(23.0688, 104.9930), GeoPoint(23.1203, 105.1406), GeoPoint(23.3638, 105.3164),
        GeoPoint(23.3557, 105.3266), GeoPoint(23.2786, 105.3621), GeoPoint(23.2397, 105.4096), GeoPoint(23.2128, 105.4366),
        GeoPoint(23.1597, 105.4086), GeoPoint(22.9333, 105.1667),
    )

    /** Phone-sized frame (dp = px here: the maths is scale-free). */
    private fun fit(points: List<GeoPoint>, widthDp: Int) = PreviewFrame.insetsDp.let {
        MapFit.fit(points, widthDp, PreviewFrame.HEIGHT_DP, it)
    }

    private fun assertClear(points: List<GeoPoint>, widthDp: Int) {
        val f = fit(points, widthDp)
        val r = PreviewFrame.PIN_RADIUS_DP
        points.forEach { p ->
            val (x, y) = f.project(p)
            assertTrue("pin left/top edge in frame $p", x - r >= 0 && y - r >= 0)
            assertTrue("pin right edge in frame $p", x + r <= widthDp)
            assertTrue("pin bottom clear of the credit $p: ${y + r}", y + r <= PreviewFrame.HEIGHT_DP - PreviewFrame.ATTRIBUTION_DP)
            // The button column: no pin disc may reach it, whatever its height.
            assertTrue("pin clear of the expand button column $p: ${x + r}", x + r <= widthDp - PreviewFrame.BUTTON_LEFT_FROM_RIGHT_DP)
        }
    }

    @Test fun ninhBinhPinsAreInsideTheFrame() { assertClear(ninhBinh, 360); assertClear(ninhBinh, 411) }

    @Test fun haGiangPinsAreInsideTheFrame() { assertClear(haGiang, 360); assertClear(haGiang, 411) }

    @Test fun narrowPhoneStillKeepsPinsClear() { assertClear(ninhBinh, 320); assertClear(haGiang, 320) }

    @Test fun symmetricFitIsUnchanged() {
        val a = MapFit.fit(haGiang, 384, 344, 28)
        val b = MapFit.fit(haGiang, 384, 344, MapInsets(28, 28, 28, 28))
        assertEquals(a.zoom, b.zoom, 0.0)
        assertEquals(a.center.latitude, b.center.latitude, 1e-9)
        assertEquals(a.center.longitude, b.center.longitude, 1e-9)
    }

    @Test fun legacyEvenPaddingWouldHidePinFour() {
        // The old preview (40 dp all round): pin 4 of Ninh Bình reaches under the 30 dp credit.
        val old = MapFit.fit(ninhBinh, 360, PreviewFrame.HEIGHT_DP, PreviewFrame.PADDING_DP)
        val (_, y) = old.project(ninhBinh.last())
        assertTrue("old frame puts the last pin's disc under the credit: ${y + PreviewFrame.PIN_RADIUS_DP}",
            y + PreviewFrame.PIN_RADIUS_DP > PreviewFrame.HEIGHT_DP - PreviewFrame.ATTRIBUTION_DP)
    }
}
