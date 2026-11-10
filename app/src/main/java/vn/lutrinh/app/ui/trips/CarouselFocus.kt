package vn.lutrinh.app.ui.trips

import androidx.compose.runtime.saveable.Saver

/**
 * R37: the trip map's card carousel and camera. [selected] = the card and pin drawn as selected
 * (changes at once); [camera] = the step the map moves to and whose arriving leg is emphasised,
 * set only once the carousel has stopped, so the camera never moves while cards slide.
 * Pure rules (only its [Saver] touches Compose); TripMapScreen holds one in a saved state.
 */
data class CarouselFocus(val selected: String? = null, val camera: String? = null) {
    /**
     * A pin tap, a card tap or "Xem toàn bộ hành trình" (null): selects at once; the carousel
     * then scrolls to the card by itself and [scrolledTo] moves the camera. Null clears both.
     */
    fun pick(id: String?): CarouselFocus = if (id == null) CarouselFocus() else copy(selected = id)

    /** Our own scroll to [selected] has finished: the camera follows now. */
    fun scrolledTo(): CarouselFocus = copy(camera = selected)

    /**
     * The user's swipe has settled (the fling snapped) with [centred] in the middle: that step is
     * selected and the camera follows. Our own scrolls never come here; when one ends on the
     * selected card anyway (frame order), nothing changes.
     */
    fun settled(centred: String?): CarouselFocus = if (centred == null) this else CarouselFocus(centred, centred)

    companion object {
        /** Keeps the focus across the step detail (back stack) and configuration changes. */
        val Saver: Saver<CarouselFocus, List<String?>> = Saver(
            save = { listOf(it.selected, it.camera) },
            restore = { CarouselFocus(it[0], it[1]) },
        )
    }
}
