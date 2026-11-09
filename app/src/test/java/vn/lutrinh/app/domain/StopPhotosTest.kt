package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.usecase.GalleryPhoto
import vn.lutrinh.app.domain.usecase.GallerySelection
import vn.lutrinh.app.domain.usecase.PhotoWindow
import vn.lutrinh.app.domain.usecase.StopPhotos
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** R19 "Ảnh chụp trong lúc dừng": window, de-duplication and ticks (docs/04-business-rules.md). */
class StopPhotosTest {
    private val zone: ZoneId = ZoneOffset.ofHours(7)
    private fun ms(h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2025, 10, 20, h, m, s).atZone(zone).toInstant().toEpochMilli()
    private fun at(h: Int, m: Int) = LocalDateTime.of(2025, 10, 20, h, m)
    private fun g(id: String, takenAt: LocalDateTime?, size: Long? = 1000L + id.hashCode() % 100) =
        GalleryPhoto(id, "content://media/external/images/media/$id", takenAt, size)
    private fun photo(sourceId: String? = null, takenAt: LocalDateTime? = null, size: Long? = null) =
        Photo("p", "s", "f.jpg", 0, 0, takenAt, sourceId, size)

    /** Stay 10:05:40 – 10:40:10 local. */
    private val w = StopPhotos.window(ms(10, 5, 40), ms(10, 40, 10), zone)

    @Test fun `window is ten minutes before the start to ten minutes after the end, to the minute`() {
        assertEquals(PhotoWindow(at(9, 55), at(10, 50)), w)
    }

    @Test fun `window bounds are inclusive and seconds do not matter`() {
        assertTrue(at(9, 55) in w)
        assertTrue(at(10, 50).plusSeconds(59) in w)
        assertFalse(at(9, 54).plusSeconds(59) in w)
        assertFalse(at(10, 51) in w)
    }

    @Test fun `window uses the given zone for track instants`() {
        val utc = StopPhotos.window(ms(10, 5), ms(10, 40), ZoneOffset.UTC)
        assertEquals(PhotoWindow(at(2, 55), at(3, 50)), utc)
    }

    @Test fun `query range covers the window with a day of slack each side`() {
        val r = StopPhotos.queryRange(w, zone)
        assertEquals(ms(9, 55) - StopPhotos.QUERY_SLACK_MS, r.first)
        assertEquals(ms(10, 51) + StopPhotos.QUERY_SLACK_MS, r.last)
    }

    @Test fun `only photos with a capture time inside the window are offered, oldest first`() {
        val offered = StopPhotos.offer(
            listOf(g("3", at(10, 30)), g("1", at(9, 50)), g("2", at(9, 56)), g("4", null), g("5", at(10, 51)), g("6", at(10, 50))),
            w, emptyList(),
        )
        assertEquals(listOf("2", "3", "6"), offered.map { it.id })
    }

    @Test fun `a gallery photo already in the trip by id is not offered again`() {
        val tripPhotos = listOf(photo(sourceId = "2"))
        assertTrue(StopPhotos.isAlreadyImported(g("2", at(10, 0)), tripPhotos))
        assertEquals(listOf("3"), StopPhotos.offer(listOf(g("2", at(10, 0)), g("3", at(10, 1))), w, tripPhotos).map { it.id })
    }

    @Test fun `same capture minute and same original size counts as already imported`() {
        val tripPhotos = listOf(photo(takenAt = at(10, 0), size = 2048))
        assertTrue(StopPhotos.isAlreadyImported(g("9", LocalDateTime.of(2025, 10, 20, 10, 0, 42), 2048), tripPhotos))
        assertFalse(StopPhotos.isAlreadyImported(g("9", at(10, 0), 2049), tripPhotos))
        assertFalse(StopPhotos.isAlreadyImported(g("9", at(10, 1), 2048), tripPhotos))
    }

    @Test fun `unknown size or time never matches by time and size`() {
        assertFalse(StopPhotos.isAlreadyImported(g("9", at(10, 0), null), listOf(photo(takenAt = at(10, 0), size = null))))
        assertFalse(StopPhotos.isAlreadyImported(g("9", at(10, 0), 5), listOf(photo(takenAt = null, size = 5))))
        assertFalse(StopPhotos.isAlreadyImported(g("9", at(10, 0), 5), listOf(photo())))
    }

    @Test fun `the same gallery id is offered once`() {
        assertEquals(1, StopPhotos.offer(listOf(g("1", at(10, 0)), g("1", at(10, 0))), w, emptyList()).size)
    }

    @Test fun `every offered photo starts ticked and a tap unticks and reticks it`() {
        val sel = GallerySelection().withPhotos(listOf(g("1", at(10, 0)), g("2", at(10, 1))))
        assertEquals(listOf("1", "2"), sel.selected.map { it.id })
        val unticked = sel.toggle("1")
        assertFalse(unticked.isSelected("1"))
        assertEquals(listOf("2"), unticked.selected.map { it.id })
        assertTrue(unticked.toggle("1").isSelected("1"))
    }

    @Test fun `an untick survives a refresh, new photos arrive ticked, gone ones are forgotten`() {
        val sel = GallerySelection().withPhotos(listOf(g("1", at(10, 0)), g("2", at(10, 1)))).toggle("1").toggle("2")
        val refreshed = sel.withPhotos(listOf(g("1", at(10, 0)), g("3", at(10, 2))))
        assertEquals(listOf("3"), refreshed.selected.map { it.id })
        assertEquals(setOf("1"), refreshed.unticked)
    }

    @Test fun `toggling a photo that is not offered changes nothing`() {
        val sel = GallerySelection().withPhotos(listOf(g("1", at(10, 0))))
        assertEquals(sel, sel.toggle("x"))
        assertFalse(sel.isSelected("x"))
    }
}
