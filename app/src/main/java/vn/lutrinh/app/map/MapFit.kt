package vn.lutrinh.app.map

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Pure Web Mercator camera + projection for the static trip map of a share export (R18,
 * docs/05-map.md "Static map for sharing"). Same zoom scale as MapLibre (the world is
 * [TILE] × 2^zoom logical pixels), so the snapshot's basemap and the route we draw over it line
 * up. No Android types; unit-tested.
 */
data class MapFit(val center: GeoPoint, val zoom: Double, val width: Int, val height: Int) {
    private val scale = TILE * 2.0.pow(zoom)
    private val cx = mercX(center.longitude)
    private val cy = mercY(center.latitude)

    /** Logical pixel position of [p] in a [width] × [height] view (origin top left). */
    fun project(p: GeoPoint): Pair<Double, Double> =
        (mercX(p.longitude) - cx) * scale + width / 2.0 to (mercY(p.latitude) - cy) * scale + height / 2.0

    companion object {
        const val TILE = 512.0
        private const val MAX_LAT = 85.05112878

        /** x in 0..1 from the antimeridian eastwards. */
        fun mercX(lon: Double) = (lon + 180.0) / 360.0

        /** y in 0..1 from the north edge southwards. */
        fun mercY(lat: Double): Double {
            val phi = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
            return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2
        }

        fun unMercY(y: Double): Double = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y))) - PI / 2)

        /**
         * Camera rules C1–C3 (docs/05-map.md) for a static map: no point = Vietnam overview, one
         * point = [MapConfig.SINGLE_POINT_ZOOM], else every point inside the view minus [padding]
         * logical px on each side, zoomed in at most [MapConfig.FIT_MAX_ZOOM].
         */
        fun fit(points: List<GeoPoint>, width: Int, height: Int, padding: Int): MapFit {
            if (points.isEmpty()) return MapFit(NewStepDefaults.FALLBACK_CENTER, MapConfig.OVERVIEW_ZOOM, width, height)
            if (points.size == 1) return MapFit(points[0], MapConfig.SINGLE_POINT_ZOOM, width, height)
            val xs = points.map { mercX(it.longitude) }
            val ys = points.map { mercY(it.latitude) }
            val dx = xs.max() - xs.min()
            val dy = ys.max() - ys.min()
            val availW = (width - 2 * padding).coerceAtLeast(1)
            val availH = (height - 2 * padding).coerceAtLeast(1)
            val zx = if (dx > 0) log2(availW / (dx * TILE)) else Double.MAX_VALUE
            val zy = if (dy > 0) log2(availH / (dy * TILE)) else Double.MAX_VALUE
            val zoom = min(min(zx, zy), MapConfig.FIT_MAX_ZOOM).coerceAtLeast(0.0)
            val center = GeoPoint(unMercY((ys.max() + ys.min()) / 2), (xs.max() + xs.min()) / 2 * 360.0 - 180.0)
            return MapFit(center, zoom, width, height)
        }
    }
}
