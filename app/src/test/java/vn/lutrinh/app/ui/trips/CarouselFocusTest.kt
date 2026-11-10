package vn.lutrinh.app.ui.trips

import org.junit.Assert.assertEquals
import org.junit.Test

/** R37: the camera waits for the carousel to settle; our own scrolls never re-select. */
class CarouselFocusTest {
    @Test fun pinTap_selectsAtOnce_cameraFollowsAfterOurScroll() {
        val tapped = CarouselFocus().pick("b")
        assertEquals(CarouselFocus(selected = "b", camera = null), tapped)
        assertEquals(CarouselFocus("b", "b"), tapped.scrolledTo())
    }

    @Test fun swipeSettle_selectsTheCentredCard_andMovesTheCamera() {
        val start = CarouselFocus("a", "a")
        assertEquals(CarouselFocus("c", "c"), start.settled("c"))
        // Nothing centred (empty list): unchanged.
        assertEquals(start, start.settled(null))
    }

    @Test fun ourScrollEndingOnTheSelectedCard_changesNothing() {
        val afterTap = CarouselFocus("a", "a").pick("b").scrolledTo()
        // The settle of that same scroll may still arrive (frame order): same state, no new camera move.
        assertEquals(afterTap, afterTap.settled("b"))
    }

    @Test fun cameraStaysWhileANewSelectionIsStillScrolling() {
        val moving = CarouselFocus("a", "a").pick("d")
        assertEquals("d", moving.selected)
        assertEquals("a", moving.camera)
    }

    @Test fun fitAll_clearsBoth() {
        assertEquals(CarouselFocus(), CarouselFocus("a", "a").pick(null))
    }
}
