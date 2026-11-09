package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import kotlin.math.cos
import kotlin.math.hypot

/** Pure polyline helpers used before drawing a route (docs/05-map.md "Data prep per leg"). */
object Polyline {
    /** Douglas–Peucker tolerance in degrees (≈ 22 m north–south): finer detail is invisible. */
    const val SIMPLIFY_EPSILON_DEG = 2e-4

    /**
     * Douglas–Peucker: keeps both end points and, recursively, the point farthest from the chord
     * while that distance is above [epsilonDeg] (perpendicular distance to the segment, measured
     * in the plain longitude/latitude plane). Lines of 0–2 points are returned unchanged.
     */
    fun simplify(points: List<GeoPoint>, epsilonDeg: Double = SIMPLIFY_EPSILON_DEG): List<GeoPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            var maxDist = -1.0
            var index = -1
            for (i in first + 1 until last) {
                val d = segmentDistance(points[i], points[first], points[last])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index >= 0 && maxDist > epsilonDeg) {
                keep[index] = true
                stack.addLast(first to index)
                stack.addLast(index to last)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Distance from [p] to the segment [a]–[b] in the lon/lat plane, in degrees. */
    internal fun segmentDistance(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else
            (((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.longitude - (a.longitude + t * dx), p.latitude - (a.latitude + t * dy))
    }

    /**
     * The point at [fraction] (0..1) of the line's length, interpolated on the segment it falls
     * in. Length is measured in a locally scaled plane (x = lon × cos(lat)), which is what the eye
     * sees on the map. Used to put a transport badge in the middle of a leg.
     */
    fun pointAt(points: List<GeoPoint>, fraction: Double): GeoPoint {
        require(points.isNotEmpty())
        if (points.size == 1) return points[0]
        val lengths = points.zipWithNext { a, b -> planarLength(a, b) }
        val total = lengths.sum()
        if (total == 0.0) return points[0]
        var target = total * fraction.coerceIn(0.0, 1.0)
        lengths.forEachIndexed { i, len ->
            if (target <= len || i == lengths.lastIndex) {
                val t = if (len == 0.0) 0.0 else (target / len).coerceIn(0.0, 1.0)
                val a = points[i]
                val b = points[i + 1]
                return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
            target -= len
        }
        return points.last()
    }

    private fun planarLength(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians((a.latitude + b.latitude) / 2))
        return hypot((b.longitude - a.longitude) * k, b.latitude - a.latitude)
    }
}
