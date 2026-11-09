package vn.lutrinh.app.map

import kotlin.math.abs

/** Pure camera helpers (docs/05-map.md "Camera rules"); no Android types, unit-tested. */
object CameraMath {
    const val MIN_ANIMATION_MS = 450
    const val MAX_ANIMATION_MS = 1600

    /**
     * Length of the fly-to animation when a step or leg is selected: a base, plus time for the
     * distance travelled and for each zoom level changed, clamped so a far jump never drags.
     */
    fun flyDurationMs(distanceKm: Double, zoomFrom: Double, zoomTo: Double): Int =
        (350 + distanceKm * 1.5 + abs(zoomTo - zoomFrom) * 160).toInt()
            .coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS)

    /** Zoom used when focusing a step: zoom in to at least [minZoom], never zoom the user out. */
    fun focusZoom(current: Double, minZoom: Double = MapConfig.STEP_FOCUS_ZOOM): Double =
        maxOf(current, minZoom).coerceAtMost(MapConfig.FIT_MAX_ZOOM)
}
