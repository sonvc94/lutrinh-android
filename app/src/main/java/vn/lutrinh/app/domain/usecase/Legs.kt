package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import kotlin.math.cos

/**
 * A leg is the move between two consecutive steps in timeline order (R12). Its transport is
 * stored on the step where the leg ends ([to]); the first step of a trip has no incoming leg.
 */
data class Leg(val from: Step, val to: Step) {
    val mode: TransportMode? get() = to.arrivalTransport
    val distanceKm: Double get() = Geo.distanceKm(from.location, to.location)
}

/** R12: legs of a trip, one per pair of consecutive steps in timeline order (R2). */
fun legsOf(steps: List<Step>): List<Leg> = steps.inTimelineOrder().zipWithNext { a, b -> Leg(a, b) }

/**
 * Line geometry drawn for a leg (docs/05-map.md "Legs"). No routing: road/rail/water modes are
 * a straight segment; a flight is a symmetric arc so it reads as "by air" at a glance.
 */
object LegGeometry {
    /** Arc height as a fraction of the leg length. */
    const val ARC_BULGE = 0.2

    /** Points per arc (segments + 1). */
    const val ARC_POINTS = 25

    fun path(from: GeoPoint, to: GeoPoint, mode: TransportMode?): List<GeoPoint> =
        if (mode == TransportMode.PLANE) arc(from, to) else listOf(from, to)

    /**
     * Quadratic Bezier from [a] to [b] whose control point sits [ARC_BULGE] x length to the LEFT of
     * the travel direction, computed in a locally-scaled plane (x = lng * cos(lat)) so the arc
     * looks round on a Web Mercator map. Not for legs crossing the antimeridian.
     */
    fun arc(a: GeoPoint, b: GeoPoint, points: Int = ARC_POINTS): List<GeoPoint> {
        if (a == b) return listOf(a, b)
        val k = cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val ax = a.longitude * k
        val bx = b.longitude * k
        val dx = bx - ax
        val dy = b.latitude - a.latitude
        // Left normal of (dx, dy) is (-dy, dx); its length equals the leg length.
        val cx = (ax + bx) / 2 - dy * ARC_BULGE
        val cy = (a.latitude + b.latitude) / 2 + dx * ARC_BULGE
        return (0 until points).map { i ->
            val t = i.toDouble() / (points - 1)
            val u = 1 - t
            val x = u * u * ax + 2 * u * t * cx + t * t * bx
            val y = u * u * a.latitude + 2 * u * t * cy + t * t * b.latitude
            GeoPoint(y, x / k)
        }
    }
}

/** Totals for "Bản đồ của tôi" (R14). Distances are straight lines per trip, never between trips. */
data class OverallStats(val tripCount: Int, val stepCount: Int, val distanceKm: Double)

fun overallStatsOf(trips: List<Trip>, steps: List<Step>): OverallStats {
    val ids = trips.map { it.id }.toSet()
    val mine = steps.filter { it.tripId in ids }
    val km = mine.groupBy { it.tripId }.values.sumOf { Geo.routeLengthKm(routeOf(it)) }
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
