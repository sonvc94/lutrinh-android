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

    /**
     * Encoded polyline with 6 decimals ("polyline6", the format Valhalla and OSRM answer in and
     * `leg_routes.geometry` stores): per point the lat/lng deltas × 10^6, zig-zag, 5-bit chunks + 63.
     */
    fun encode6(points: List<GeoPoint>): String {
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLng = 0L
        for (p in points) {
            val lat = Math.round(p.latitude * 1e6)
            val lng = Math.round(p.longitude * 1e6)
            encodeValue(lat - lastLat, sb)
            encodeValue(lng - lastLng, sb)
            lastLat = lat
            lastLng = lng
        }
        return sb.toString()
    }

    /** Inverse of [encode6]; a damaged string yields the points read so far. */
    fun decode6(encoded: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var i = 0
        var lat = 0L
        var lng = 0L
        while (i < encoded.length) {
            val dLat = decodeValue(encoded, i) ?: break
            i = dLat.second
            val dLng = decodeValue(encoded, i) ?: break
            i = dLng.second
            lat += dLat.first
            lng += dLng.first
            out += GeoPoint(lat / 1e6, lng / 1e6)
        }
        return out
    }

    private fun encodeValue(v: Long, sb: StringBuilder) {
        var n = if (v < 0) (v shl 1).inv() else v shl 1
        while (n >= 0x20) {
            sb.append(((0x20 or (n and 0x1f).toInt()) + 63).toChar())
            n = n shr 5
        }
        sb.append((n + 63).toInt().toChar())
    }

    /** One zig-zag value from [start]: (value, index after it), or null when the string ends inside it. */
    private fun decodeValue(s: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            if (i >= s.length) return null
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val v = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return v to i
    }

    private fun planarLength(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians((a.latitude + b.latitude) / 2))
        return hypot((b.longitude - a.longitude) * k, b.latitude - a.latitude)
    }
}
