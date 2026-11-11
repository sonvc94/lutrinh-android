package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.routing.LegRoute
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A leg is the move between two consecutive steps in timeline order (R12). Its transport is
 * stored on the step where the leg ends ([to]); the first step of a trip has no incoming leg.
 */
data class Leg(val from: Step, val to: Step) {
    val mode: TransportMode? get() = to.arrivalTransport
    /** R41: the traveller's note about this leg (stored on [to]), or null. */
    val note: String? get() = to.arrivalNote
    val distanceKm: Double get() = Geo.distanceKm(from.location, to.location)
}

/**
 * R12: legs of a trip, one per pair of consecutive steps in timeline order (R2). R20: drafts are
 * skipped, so a leg joins the published steps on either side of a draft.
 */
fun legsOf(steps: List<Step>): List<Leg> = steps.published().inTimelineOrder().zipWithNext { a, b -> Leg(a, b) }

/**
 * Line geometry drawn for a leg without a stored road route (docs/05-map.md "Legs"; routed legs:
 * LegRoutes.path, R36): road/rail/water modes are a straight segment; a flight is a great-circle path lifted sideways so it reads as "by air" at
 * any length (a pure great circle looks straight at Vietnam scale).
 */
object LegGeometry {
    /** Sideways lift of a flight at mid-leg, as a fraction of the leg's on-map (Mercator) length. */
    const val ARC_LIFT = 0.2

    /** Fewest / most points per flight path; in between one point per [ARC_KM_PER_POINT] km. */
    const val ARC_MIN_POINTS = 25
    const val ARC_MAX_POINTS = 129
    const val ARC_KM_PER_POINT = 20.0

    /** Kept for callers that only need the minimum. */
    const val ARC_POINTS = ARC_MIN_POINTS

    private const val MAX_LAT = 85.0

    fun path(from: GeoPoint, to: GeoPoint, mode: TransportMode?): List<GeoPoint> =
        if (mode == TransportMode.PLANE) arc(from, to) else listOf(from, to)

    /** Points of the flight path from [a] to [b]: depends on the great-circle length only. */
    fun arcPointCount(a: GeoPoint, b: GeoPoint): Int =
        (Geo.distanceKm(a, b) / ARC_KM_PER_POINT).roundToInt().coerceIn(ARC_MIN_POINTS, ARC_MAX_POINTS)

    /**
     * Great circle from [a] to [b] (spherical interpolation, the shortest way, also across the
     * antimeridian: longitudes are unwrapped so the line never jumps by 360°), then each point is
     * moved to the LEFT of the travel direction by `lift × 4t(1 − t) × chord`, measured in Web
     * Mercator so the bow looks round at any latitude (t = 0..1 along the path, so the lift is
     * 0 at both ends and [lift] × chord at mid-leg). The first and last points equal [a] and [b]
     * (the last one's longitude may be shifted by ±360° when the leg crosses the antimeridian).
     */
    fun arc(a: GeoPoint, b: GeoPoint, points: Int = arcPointCount(a, b), lift: Double = ARC_LIFT): List<GeoPoint> {
        if (a == b) return listOf(a, b)
        val gc = greatCircle(a, b, points)
        // Chord and its left normal in Mercator degrees (x = lon, y = Mercator y in degrees).
        val ax = gc.first().longitude
        val ay = mercY(a.latitude)
        val bx = gc.last().longitude
        val by = mercY(b.latitude)
        val dx = bx - ax
        val dy = by - ay
        // Left normal of (dx, dy) is (-dy, dx); its length equals the chord length.
        return gc.mapIndexed { i, p ->
            if (i == 0 || i == gc.lastIndex) return@mapIndexed p
            val t = i.toDouble() / gc.lastIndex
            val k = lift * 4 * t * (1 - t)
            val x = p.longitude - dy * k
            val y = mercY(p.latitude) + dx * k
            GeoPoint(unMercY(y).coerceIn(-MAX_LAT, MAX_LAT), x)
        }
    }

    /** [points] points on the great circle from [a] to [b], ends exact, longitudes unwrapped. */
    fun greatCircle(a: GeoPoint, b: GeoPoint, points: Int): List<GeoPoint> {
        require(points >= 2)
        val va = unit(a)
        val vb = unit(b)
        val dot = (va[0] * vb[0] + va[1] * vb[1] + va[2] * vb[2]).coerceIn(-1.0, 1.0)
        val omega = acos(dot)
        val out = ArrayList<GeoPoint>(points)
        var prevLon = a.longitude
        for (i in 0 until points) {
            val p = when {
                i == 0 -> a
                i == points - 1 -> b
                omega < 1e-12 -> a
                else -> {
                    val t = i.toDouble() / (points - 1)
                    val s = sin(omega)
                    val f1 = sin((1 - t) * omega) / s
                    val f2 = sin(t * omega) / s
                    val x = f1 * va[0] + f2 * vb[0]
                    val y = f1 * va[1] + f2 * vb[1]
                    val z = f1 * va[2] + f2 * vb[2]
                    GeoPoint(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
                }
            }
            var lon = p.longitude
            while (lon - prevLon > 180) lon -= 360
            while (prevLon - lon > 180) lon += 360
            prevLon = lon
            out += GeoPoint(p.latitude, lon)
        }
        return out
    }

    private fun unit(p: GeoPoint): DoubleArray {
        val lat = Math.toRadians(p.latitude)
        val lon = Math.toRadians(p.longitude)
        return doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }

    /** Web Mercator y, in degrees so it is comparable with longitude. */
    internal fun mercY(lat: Double): Double {
        val phi = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return Math.toDegrees(ln(tan(PI / 4 + phi / 2)))
    }

    internal fun unMercY(y: Double): Double = Math.toDegrees(2 * atan(exp(Math.toRadians(y))) - PI / 2)
}

/**
 * Totals for "Bản đồ của tôi" (R14). Distances are the legs of each trip (road km where a usable
 * route is stored, R36; straight otherwise), never between trips. Drafts are left out (R20).
 */
data class OverallStats(val tripCount: Int, val stepCount: Int, val distanceKm: Double)

fun overallStatsOf(trips: List<Trip>, steps: List<Step>, routes: Map<String, LegRoute> = emptyMap()): OverallStats {
    val ids = trips.map { it.id }.toSet()
    val mine = steps.published().filter { it.tripId in ids }
    val km = mine.groupBy { it.tripId }.values.sumOf { legKmOf(it, routes) }
    return OverallStats(trips.size, mine.size, km)
}

/**
 * R14: each trip keeps the same colour as trips are added: colours are handed out in creation
 * order (oldest first) and repeat after [paletteSize].
 */
fun tripColorSlots(trips: List<Trip>, paletteSize: Int): Map<String, Int> =
    trips.sortedWith(compareBy<Trip> { it.createdAt }.thenBy { it.id })
        .mapIndexed { i, t -> t.id to i % paletteSize }
        .toMap()
