package vn.lutrinh.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.lutrinh.app.domain.usecase.GalleryPhoto
import vn.lutrinh.app.domain.usecase.GallerySelection
import vn.lutrinh.app.domain.usecase.PhotoWindow
import vn.lutrinh.app.ui.steps.StepForm
import vn.lutrinh.app.ui.steps.StopPhotosState
import java.time.LocalDateTime

/** F3: the step form counts picked files and ticked gallery photos in one "Ảnh (N)". */
class StepFormTest {
    private val t = LocalDateTime.of(2025, 10, 20, 10, 0)
    private fun g(id: String) = GalleryPhoto(id, "content://x/$id", t, 1000)
    private val window = PhotoWindow(t.minusMinutes(10), t.plusMinutes(10))

    private fun form(picked: Int, offered: Int, unticked: Set<String> = emptySet()) = StepForm(
        photos = List(picked) { "f$it.jpg" },
        stopPhotos = StopPhotosState(window, selection = GallerySelection(List(offered) { g("g$it") }, unticked)),
    )

    @Test fun `ticked gallery photos count with picked ones`() {
        assertEquals(3, form(picked = 0, offered = 3).photoCount)
        assertEquals(5, form(picked = 2, offered = 3).photoCount)
    }

    @Test fun `unticking removes a gallery photo from the grid and the count`() {
        val f = form(picked = 1, offered = 3, unticked = setOf("g1"))
        assertEquals(listOf("g0", "g2"), f.galleryPhotos.map { it.id })
        assertEquals(3, f.photoCount)
    }

    @Test fun `a form not opened from a suggestion counts only picked photos`() {
        assertEquals(2, StepForm(photos = listOf("a.jpg", "b.jpg")).photoCount)
    }
}
