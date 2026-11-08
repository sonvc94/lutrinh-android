package vn.lutrinh.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.data.photo.Orientation
import vn.lutrinh.app.data.photo.PhotoImportPlan
import vn.lutrinh.app.data.photo.PixelSize

class PhotoImportPlanTest {
    @Test
    fun `large landscape is downscaled to 2560 on the long edge keeping aspect`() {
        assertEquals(PixelSize(2560, 1707), PhotoImportPlan.scaledSize(PixelSize(6000, 4000)))
        assertEquals(PixelSize(1707, 2560), PhotoImportPlan.scaledSize(PixelSize(4000, 6000)))
    }

    @Test
    fun `small and boundary images keep their size`() {
        assertEquals(PixelSize(2000, 1000), PhotoImportPlan.scaledSize(PixelSize(2000, 1000)))
        assertEquals(PixelSize(2560, 2560), PhotoImportPlan.scaledSize(PixelSize(2560, 2560)))
    }

    @Test
    fun `rotated 90 or 270 swaps width and height of the stored copy`() {
        val src = PixelSize(4032, 3024) // as a phone stores a portrait shot with EXIF 6
        assertEquals(PixelSize(1920, 2560), PhotoImportPlan.storedSize(src, Orientation.fromExif(6)))
        assertEquals(PixelSize(1920, 2560), PhotoImportPlan.storedSize(src, Orientation.fromExif(8)))
        assertEquals(PixelSize(2560, 1920), PhotoImportPlan.storedSize(src, Orientation.fromExif(3)))
        assertEquals(PixelSize(2560, 1920), PhotoImportPlan.storedSize(src, Orientation.fromExif(1)))
    }

    @Test
    fun `sample size is the largest power of two that still covers the target`() {
        assertEquals(1, PhotoImportPlan.sampleSize(PixelSize(2000, 1500)))
        assertEquals(1, PhotoImportPlan.sampleSize(PixelSize(4032, 3024)))
        assertEquals(2, PhotoImportPlan.sampleSize(PixelSize(6000, 4000)))
        assertEquals(4, PhotoImportPlan.sampleSize(PixelSize(12000, 9000)))
    }

    @Test
    fun `reencode only when too large, not upright or not strippable in place`() {
        val small = PixelSize(1600, 1200)
        assertFalse(PhotoImportPlan.needsReencode(small, Orientation.NORMAL, canStripInPlace = true))
        assertTrue(PhotoImportPlan.needsReencode(PixelSize(4000, 3000), Orientation.NORMAL, canStripInPlace = true))
        assertTrue(PhotoImportPlan.needsReencode(small, Orientation.fromExif(6), canStripInPlace = true))
        assertTrue(PhotoImportPlan.needsReencode(small, Orientation.NORMAL, canStripInPlace = false))
    }

    @Test
    fun `unknown exif values are treated as upright`() {
        assertEquals(Orientation.NORMAL, Orientation.fromExif(0))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(9))
    }

    /** EXIF spec: where the stored top-left pixel must appear in the upright image. */
    @Test
    fun `each exif orientation puts the stored top-left corner where the spec says`() {
        val size = PixelSize(40, 30)
        val tl = 0.0 to 0.0
        val expected = mapOf(
            1 to tl, 2 to (40.0 to 0.0), 3 to (40.0 to 30.0), 4 to (0.0 to 30.0),
            // axes swapped: upright image is 30 x 40
            5 to tl, 6 to (30.0 to 0.0), 7 to (30.0 to 40.0), 8 to (0.0 to 40.0),
        )
        expected.forEach { (exif, corner) ->
            assertEquals("EXIF $exif", corner, PhotoImportPlan.mapPoint(0.0, 0.0, size, Orientation.fromExif(exif)))
        }
    }

    @Test
    fun `transpose maps x to y`() {
        assertEquals(0.0 to 40.0, PhotoImportPlan.mapPoint(40.0, 0.0, PixelSize(40, 30), Orientation.fromExif(5)))
    }
}
