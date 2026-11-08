package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.domain.usecase.PhotoGrouping
import vn.lutrinh.app.domain.usecase.PhotoMeta
import vn.lutrinh.app.domain.usecase.suggestStepsFrom
import java.time.LocalDate
import java.time.LocalDateTime

/** R13: photos -> suggested steps. */
class PhotoGroupingTest {
    private val hoiAn = GeoPoint(15.8801, 108.3380)
    private val hoiAnNear = GeoPoint(15.8770, 108.3260) // ~1.3 km away
    private val myson = GeoPoint(15.7640, 108.1240) // ~25 km away
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2025, 3, day, h, m, 17)
    private var n = 0
    private fun photo(t: LocalDateTime?, p: GeoPoint?) = PhotoMeta("p${n}", n++, t, p)

    @Test fun samePlaceSameDayIsOneGroupAndDayOrPlaceChangeSplits() {
        val a = photo(at(1, 9), hoiAn)
        val b = photo(at(1, 10), hoiAnNear)
        val c = photo(at(1, 14), myson) // same day, far
        val d = photo(at(2, 8), hoiAn) // next day, same place
        assertTrue(Geo.distanceKm(hoiAn, hoiAnNear) < PhotoGrouping.SAME_PLACE_KM)
        val groups = PhotoGrouping.group(listOf(d, c, b, a)) // pick order differs from time order
        assertEquals(listOf(listOf(a, b), listOf(c), listOf(d)), groups.map { it.photos })
        assertEquals(LocalDateTime.of(2025, 3, 1, 9, 0), groups[0].arrivedAt)
        assertEquals(LocalDate.of(2025, 3, 2), groups[2].day)
        assertEquals((hoiAn.latitude + hoiAnNear.latitude) / 2, groups[0].location!!.latitude, 1e-9)
    }

    @Test fun photosWithoutLocationFormOneLastGroup() {
        val a = photo(at(1, 9), hoiAn)
        val noGps1 = photo(at(1, 11), null)
        val noGps2 = photo(null, GeoPoint(0.0, 0.0)) // redacted/broken EXIF counts as missing
        val groups = PhotoGrouping.group(listOf(noGps2, a, noGps1))
        assertEquals(2, groups.size)
        assertEquals(listOf(a), groups[0].photos)
        assertNull(groups[1].location)
        assertEquals(listOf(noGps1, noGps2), groups[1].photos)
        assertEquals(LocalDateTime.of(2025, 3, 1, 11, 0), groups[1].arrivedAt)
    }

    @Test fun undatedLocatedPhotosGroupByPlaceAfterDatedOnes() {
        val dated = photo(at(1, 9), hoiAn)
        val u1 = photo(null, hoiAn)
        val u2 = photo(null, hoiAnNear)
        val groups = PhotoGrouping.group(listOf(u1, dated, u2))
        assertEquals(listOf(listOf(dated), listOf(u1, u2)), groups.map { it.photos })
        assertNull(groups[1].arrivedAt)
    }

    @Test fun aGroupNeverExceedsThePhotoLimit() {
        val many = (0 until Limits.PHOTOS_PER_STEP_MAX + 5).map { photo(at(1, 9, it % 60), hoiAn) }
        val groups = PhotoGrouping.group(many)
        assertEquals(listOf(Limits.PHOTOS_PER_STEP_MAX, 5), groups.map { it.photos.size })
    }

    @Test fun exifDatesParseAndRejectGarbage() {
        assertEquals(LocalDateTime.of(2025, 3, 1, 9, 5, 7), PhotoGrouping.parseExifDateTime("2025:03:01 09:05:07"))
        assertEquals(LocalDateTime.of(2025, 3, 1, 9, 5, 7), PhotoGrouping.parseExifDateTime("2025:03:01 09:05:07\u0000"))
        assertNull(PhotoGrouping.parseExifDateTime("0000:00:00 00:00:00"))
        assertNull(PhotoGrouping.parseExifDateTime("    :  :     :  :  "))
        assertNull(PhotoGrouping.parseExifDateTime(null))
        assertFalse(PhotoGrouping.isUsable(GeoPoint(91.0, 10.0)))
    }

    @Test fun suggestionsNumberAfterExistingStepsAndFlagDatesOutsideTheTrip() {
        val trip = Trip("t", "Hội An", "", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1), 0, 0)
        val groups = PhotoGrouping.group(
            listOf(photo(at(1, 9), hoiAn), photo(at(2, 9), myson), photo(null, null)),
        )
        val s = suggestStepsFrom(groups, trip, emptyList(), LocalDateTime.of(2026, 1, 1, 12, 0)) { "Điểm dừng $it" }
        assertEquals(listOf("Điểm dừng 1", "Điểm dừng 2", "Điểm dừng 3"), s.map { it.title })
        assertEquals(listOf(true, false, false), s.map { it.include })
        assertEquals(listOf(false, true, false), s.map { it.outsideTrip })
        // undated, unlocated: R6 default arrival (trip start, 09:00)
        assertEquals(LocalDateTime.of(2025, 3, 1, 9, 0), s[2].arrivedAt)
        assertFalse(s[2].datedFromPhotos)
    }
}
