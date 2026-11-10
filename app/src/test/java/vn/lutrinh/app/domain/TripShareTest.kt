package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.ShareStats
import vn.lutrinh.app.domain.usecase.TripPage
import vn.lutrinh.app.domain.usecase.TripShare
import vn.lutrinh.app.map.MapConfig
import vn.lutrinh.app.map.basemap.OpenFreeMap
import vn.lutrinh.app.map.MapFit
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/** R18 — share a trip: stats, photo choice, file names, the offline page and the static map fit. */
class TripShareTest {
    private val start = LocalDate.of(2025, 11, 3)
    private fun trip(end: LocalDate? = LocalDate.of(2025, 11, 6)) = Trip("t", "Hà Giang", "Vòng cung", start, end, 0, 0)

    private fun step(n: Int, day: Int = 0, photos: Int = 0, note: String = "", title: String = "Điểm $n", p: GeoPoint = GeoPoint(22.8 + n * 0.1, 105.0)) = Step(
        id = "s$n", tripId = "t", title = title, note = note, location = p,
        arrivedAt = start.plusDays(day.toLong()).atTime(8 + n % 10, 0), createdAt = n.toLong(), updatedAt = 0,
        photos = (0 until photos).map { Photo("s$n-p$it", "s$n", "s$n-p$it.jpg", it, 0) },
    )

    // ---- stats ----

    @Test fun statsCountStepsPhotosDaysAndRoundKm() {
        val steps = listOf(step(1, photos = 2), step(2, day = 1, photos = 1), step(3, day = 3))
        val s = TripShare.stats(trip(), steps)
        assertEquals(3, s.stepCount)
        assertEquals(3, s.photoCount)
        assertEquals(4, s.days) // 3–6 Nov inclusive
        assertEquals(22, s.distanceKm) // two legs of 0.1° latitude ≈ 11.1 km each
    }

    @Test fun openEndedTripCountsDaysUpToTheLastStep() {
        assertEquals(6, TripShare.stats(trip(end = null), listOf(step(1), step(2, day = 5))).days)
        assertEquals(1, TripShare.stats(trip(end = null), emptyList()).days)
    }

    // ---- photos on the image ----

    @Test fun imagePhotosSpreadCoversOverTheTrip() {
        val steps = (1..7).map { step(it, day = it % 3, photos = 2) }
        val picked = TripShare.imagePhotos(steps)
        assertEquals(4, picked.size)
        assertTrue(picked.all { it.position == 0 })
        val timeline = steps.sortedWith(compareBy({ it.arrivedAt }, { it.createdAt })).map { it.id }
        assertEquals(timeline.first(), picked.first().stepId)
        assertEquals(timeline.last(), picked.last().stepId)
        assertEquals(4, picked.map { it.stepId }.distinct().size)
    }

    @Test fun fewStepsWithPhotosFillWithTheirNextPhotos() {
        val picked = TripShare.imagePhotos(listOf(step(1, photos = 3), step(2), step(3, day = 1, photos = 1)))
        assertEquals(listOf("s1-p0", "s3-p0", "s1-p1", "s1-p2"), picked.map { it.id })
        assertTrue(TripShare.imagePhotos(listOf(step(1), step(2))).isEmpty())
    }

    // ---- file names ----

    @Test fun fileNameIsAsciiSlugWithStartDate() {
        assertEquals("LuTrinh-vong-cung-ha-giang-20251103.png", TripShare.fileName("Vòng cung Hà Giang", start, "png"))
        assertEquals("LuTrinh-duong-di-dong-van-20251103.html", TripShare.fileName("Đường đi Đồng Văn!", start, "html"))
        assertEquals("LuTrinh-chuyen-di-20251103.png", TripShare.fileName("🙂 ///", start, "png"))
    }

    // ---- the page ----

    private fun content(steps: List<Step>, hideNotes: Boolean = false, title: String = "Hà Giang") = TripPage.Content(
        title = title, dates = "3 – 6 thg 11, 2025", description = "", tripStart = start,
        stats = ShareStats(22, 4, steps.size, steps.sumOf { it.photos.size }), steps = steps, hideNotes = hideNotes,
        mapJpeg = byteArrayOf(1, 2, 3), attribution = OpenFreeMap.ATTRIBUTION,
    )

    @Test fun pageEscapesUserTextIncludingScript() {
        val evil = "<script>alert('x')</script> & <img src=\"https://evil.example/a.png\">"
        val html = TripPage.build(content(listOf(step(1, note = evil, title = "<b>Đồng Văn</b>")), title = "A \"quoted\" <trip>")) { byteArrayOf(9) }.html
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("<b>Đồng Văn"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &lt;img src=&quot;https://evil.example/a.png&quot;&gt;"))
        assertTrue(html.contains("<title>A &quot;quoted&quot; &lt;trip&gt;</title>"))
        assertTrue(html.contains("&lt;b&gt;Đồng Văn&lt;/b&gt;"))
    }

    @Test fun pageLoadsNothingFromTheNetwork() {
        val html = TripPage.build(content(listOf(step(1, photos = 2, note = "xem https://example.com"), step(2, day = 1)))) { byteArrayOf(1, 2) }.html
        assertEquals(0, Regex("(src|href)=\"https?://").findAll(html).count())
        // the only url( is the embedded Barlow digits subset: a data: font, never a request
        assertEquals(0, Regex("url\\((?!data:font/woff2;base64,)").findAll(html).count())
        assertEquals(1, Regex("url\\(data:font/woff2;base64,").findAll(html).count())
        assertFalse(html.contains("<link"))
        assertFalse(html.contains("<script"))
        assertTrue(html.contains("Content-Security-Policy\" content=\"default-src 'none'; img-src data:;"))
        // Images are data URIs only; the attribution is plain text.
        assertEquals(3, Regex("src=\"data:image/jpeg;base64,").findAll(html).count())
        assertTrue(html.contains("Bản đồ: OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"))
    }

    @Test fun pageHasTimelineByDayAndHidesNotesOnRequest() {
        val steps = listOf(step(1, note = "Ghi chú một\ndòng hai"), step(2, note = "Ghi chú hai"), step(3, day = 2))
        val html = TripPage.build(content(steps)) { null }.html
        assertTrue(html.contains("<h2>Ngày 1 · "))
        assertTrue(html.contains("<h2>Ngày 3 · "))
        assertFalse(html.contains("<h2>Ngày 2 · "))
        assertTrue(html.contains("Ghi chú một<br>dòng hai"))
        val hidden = TripPage.build(content(steps, hideNotes = true)) { null }.html
        assertFalse(hidden.contains("Ghi chú"))
        assertTrue(hidden.contains("Điểm 2"))
    }

    @Test fun capLowersQualityThenDropsPhotos() {
        val steps = listOf(step(1, photos = 5), step(2, day = 1, photos = 3))
        // Fake encoder: bytes depend on size and quality only.
        val encode = { _: Photo, l: TripPage.Level -> ByteArray(l.maxPx * l.quality / 10) }
        val normal = TripPage.buildWithinCap(content(steps), cap = Int.MAX_VALUE, encode = encode)
        assertEquals(TripPage.LEVELS.first(), normal.level)
        assertEquals(8, normal.photosIncluded)

        // Fits once photos are 800 px / q50 (4000 B each, ~5.3 kB base64): 8 photos ≈ 43 kB (+ 3 kB for the embedded digits).
        val smaller = TripPage.buildWithinCap(content(steps), cap = 54_000, encode = encode)
        assertEquals(800, smaller.level.maxPx)
        assertEquals(Int.MAX_VALUE, smaller.level.maxPhotosPerStep)
        assertTrue(smaller.bytes <= 54_000)

        // Tighter: at most 2 photos per step.
        val fewer = TripPage.buildWithinCap(content(steps), cap = 34_000, encode = encode)
        assertEquals(2, fewer.level.maxPhotosPerStep)
        assertEquals(4, fewer.photosIncluded)
        assertEquals(4, fewer.photosOmitted)
        assertTrue(fewer.html.contains("Đã lược bớt 4 ảnh"))

        // Impossible: the last level (no photos) is returned anyway.
        val none = TripPage.buildWithinCap(content(steps), cap = 10, encode = encode)
        assertEquals(TripPage.LEVELS.last(), none.level)
        assertEquals(0, none.photosIncluded)
    }

    @Test fun pageSizeIsCountedInUtf8Bytes() {
        val r = TripPage.build(content(listOf(step(1, title = "Đồng Văn ữ 🙂")))) { null }
        assertEquals(r.html.toByteArray(Charsets.UTF_8).size, r.bytes)
    }

    // ---- static map fit (docs/05 "Static map for sharing") ----

    @Test fun mapFitKeepsEveryPointInsideThePadding() {
        val pts = listOf(GeoPoint(22.82, 104.98), GeoPoint(23.28, 105.36), GeoPoint(23.06, 105.0))
        val fit = MapFit.fit(pts, 384, 344, 28)
        pts.forEach { p ->
            val (x, y) = fit.project(p)
            assertTrue("x=$x", x >= 28 - 1e-6 && x <= 384 - 28 + 1e-6)
            assertTrue("y=$y", y >= 28 - 1e-6 && y <= 344 - 28 + 1e-6)
        }
        // The tighter axis touches the padding.
        val ys = pts.map { fit.project(it).second }
        val xs = pts.map { fit.project(it).first }
        assertTrue(abs(ys.max() - ys.min() - (344 - 56)) < 1e-6 || abs(xs.max() - xs.min() - (384 - 56)) < 1e-6)
    }

    @Test fun mapFitFollowsTheCameraRules() {
        assertEquals(MapConfig.OVERVIEW_ZOOM, MapFit.fit(emptyList(), 300, 300, 20).zoom, 0.0)
        val one = MapFit.fit(listOf(GeoPoint(21.0, 105.8)), 300, 300, 20)
        assertEquals(MapConfig.SINGLE_POINT_ZOOM, one.zoom, 0.0)
        assertEquals(150.0, one.project(GeoPoint(21.0, 105.8)).first, 1e-6)
        val close = MapFit.fit(listOf(GeoPoint(21.0, 105.8), GeoPoint(21.0001, 105.8001)), 300, 300, 20)
        assertEquals(MapConfig.FIT_MAX_ZOOM, close.zoom, 0.0)
    }
}
