package vn.lutrinh.app.map

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Geo
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min

/**
 * A planned camera move (R37): [center] sits in the middle of the map minus [padding] (px) at
 * [zoom]; the move is eased in and out over [durationMs] (0 = jump, no animation).
 */
data class CameraPlan(val center: GeoPoint, val zoom: Double, val padding: MapInsets, val durationMs: Int)

/** Pure camera helpers (docs/05-map.md "Camera rules"); no Android types, unit-tested. */
object CameraMath {
    const val MIN_ANIMATION_MS = 400
    const val MAX_ANIMATION_MS = 1800

    /**
     * Length of a camera fly (step, leg, whole route, my location): a base, plus time for the
     * distance travelled and for each zoom level changed, clamped so a short hop is still seen
     * and a far jump never drags (R37).
     */
    fun flyDurationMs(distanceKm: Double, zoomFrom: Double, zoomTo: Double): Int =
        (350 + distanceKm * 1.5 + abs(zoomTo - zoomFrom) * 160).toInt()
            .coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS)

    /** Zoom used when focusing a step: zoom in to at least [minZoom], never zoom the user out. */
    fun focusZoom(current: Double, minZoom: Double = MapConfig.STEP_FOCUS_ZOOM): Double =
        maxOf(current, minZoom).coerceAtMost(MapConfig.FIT_MAX_ZOOM)

    /**
     * R37: the camera move to a selected step of the trip map. Centred on [step] in the map of
     * [widthPx] × [heightPx] minus [padding] (the header edge and the measured card carousel);
     * zoomed so every point of [region] (the leg just travelled, docs/05) shows around the step,
     * at most [MapConfig.STEP_MAX_ZOOM] (terrain stays readable), at least
     * [MapConfig.OVERVIEW_ZOOM]; no region = [MapConfig.SINGLE_POINT_ZOOM]. The length follows
     * [flyDurationMs] from [from]; [reducedMotion] (system animations off) or no camera yet = jump.
     */
    fun stepPlan(
        step: GeoPoint,
        region: List<GeoPoint>,
        from: MapCamera?,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        padding: MapInsets,
        reducedMotion: Boolean,
    ): CameraPlan {
        val zoom = regionZoom(step, region, widthPx, heightPx, density, padding)
        val duration = if (reducedMotion || from == null) 0
        else flyDurationMs(Geo.distanceKm(from.center, step), from.zoom, zoom)
        return CameraPlan(step, zoom, padding, duration)
    }

    /** Largest zoom at which every [region] point lies inside the free area centred on [step], clamped (see [stepPlan]). */
    fun regionZoom(step: GeoPoint, region: List<GeoPoint>, widthPx: Int, heightPx: Int, density: Float, padding: MapInsets): Double {
        val sx = MapFit.mercX(step.longitude)
        val sy = MapFit.mercY(step.latitude)
        val dx = region.maxOfOrNull { abs(MapFit.mercX(it.longitude) - sx) } ?: 0.0
        val dy = region.maxOfOrNull { abs(MapFit.mercY(it.latitude) - sy) } ?: 0.0
        // Half the free area in logical px (MapLibre's world is 512 dp × 2^zoom).
        val halfW = (widthPx - padding.left - padding.right) / 2.0 / density
        val halfH = (heightPx - padding.top - padding.bottom) / 2.0 / density
        if ((dx <= 0.0 && dy <= 0.0) || halfW <= 0 || halfH <= 0) return MapConfig.SINGLE_POINT_ZOOM
        val zx = if (dx > 0) log2(halfW / (dx * MapFit.TILE)) else Double.MAX_VALUE
        val zy = if (dy > 0) log2(halfH / (dy * MapFit.TILE)) else Double.MAX_VALUE
        return min(min(zx, zy), MapConfig.STEP_MAX_ZOOM).coerceAtLeast(MapConfig.OVERVIEW_ZOOM)
    }
}
