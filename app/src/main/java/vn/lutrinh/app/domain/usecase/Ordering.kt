package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** R1: trips newest first by start date, ties by creation time (newest first). */
val TripOrder: Comparator<Trip> =
    compareByDescending<Trip> { it.startDate }.thenByDescending { it.createdAt }.thenBy { it.id }

/** R2: steps in time order by arrival, ties by creation time, then id (stable). */
val StepOrder: Comparator<Step> =
    compareBy<Step> { it.arrivedAt }.thenBy { it.createdAt }.thenBy { it.id }

fun List<Step>.inTimelineOrder(): List<Step> = sortedWith(StepOrder)

/** R3: the route line joins the locations of published steps in timeline order (R20: drafts are not on it). */
fun routeOf(steps: List<Step>): List<GeoPoint> = steps.published().inTimelineOrder().map { it.location }

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Straight-line length of the route (no road snapping). */
    fun routeLengthKm(points: List<GeoPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceKm(a, b) }
}

/** Where [TripStats.distanceKm] comes from. */
enum class DistanceSource {
    /** Every leg is a straight line ("đường chim bay"): no stored road route (R10). */
    STRAIGHT_LINE,

    /** Every leg is measured along the road (R36, "theo đường đi"). */
    ROAD,

    /** Some legs along the road, the others straight (R36). */
    MIXED,

    /** The ended GPS tracks (R10, "đã ghi GPS"). */
    GPS,
}

data class TripStats(
    val stepCount: Int,
    val photoCount: Int,
    val videoCount: Int = 0,
    val distanceKm: Double,
    val days: Long?,
    val distanceSource: DistanceSource = DistanceSource.STRAIGHT_LINE,
    /** R36: sum of the road routes' travel-time estimates, in seconds; null when no leg has a road route. */
    val durationS: Double? = null,
    /** R36: legs whose time is not in [durationS] (no road route): the total time is partial when > 0. */
    val legsWithoutTime: Int = 0,
)

/** Planned km of the legs (R10/R36): road km where a usable route is stored, straight elsewhere. */
fun legKmOf(steps: List<Step>, routes: Map<String, LegRoute>): Double =
    legsOf(steps).sumOf { LegRoutes.measure(it, LegRoutes.usable(it, routes)).km }

/**
 * Trip numbers, of published steps only (R20: drafts are left out). The distance is the sum of the
 * legs (R10): along the road for a leg with a usable stored route (R36, [routes] by arrival step
 * id; pass none when routing is off), the straight line otherwise. When the ended GPS tracks
 * together are at least as long, they cover the trip and their length (the real road) is shown
 * instead (docs/03 "Trip detail"). Open tracks are not counted.
 */
fun statsOf(trip: Trip, all: List<Step>, tracks: List<Track> = emptyList(), routes: Map<String, LegRoute> = emptyMap()): TripStats {
    val steps = all.published()
    val measures = legsOf(steps).map { LegRoutes.measure(it, LegRoutes.usable(it, routes)) }
    val legKm = measures.sumOf { it.km }
    val road = measures.count { it.byRoad }
    val gpsKm = tracks.filter { !it.isOpen }.sumOf { it.distanceM } / 1000
    val useGps = gpsKm > 0 && gpsKm >= legKm
    val source = when {
        useGps -> DistanceSource.GPS
        road == 0 -> DistanceSource.STRAIGHT_LINE
        road == measures.size -> DistanceSource.ROAD
        else -> DistanceSource.MIXED
    }
    return TripStats(
        stepCount = steps.size,
        photoCount = steps.sumOf { it.photos.size },
        videoCount = steps.sumOf { s -> s.photos.count { it.isVideo } },
        distanceKm = if (useGps) gpsKm else legKm,
        days = trip.endDate?.let { ChronoUnit.DAYS.between(trip.startDate, it) + 1 },
        distanceSource = source,
        durationS = if (road == 0) null else measures.sumOf { it.durationS ?: 0.0 },
        legsWithoutTime = if (road == 0) 0 else measures.size - road,
    )
}

/** R6: defaults for a new step. */
object NewStepDefaults {
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /** Vietnam overview used when nothing better is known (approx. centre of the country). */
    val FALLBACK_CENTER = GeoPoint(16.0, 106.0)

    fun arrival(trip: Trip, steps: List<Step>, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        if (isWithin(today, trip)) return now.truncatedTo(ChronoUnit.MINUTES)
        val last = steps.maxWithOrNull(StepOrder)
        val candidate = last?.arrivedAt?.toLocalDate()?.plusDays(1) ?: trip.startDate
        return LocalDateTime.of(clamp(candidate, trip), DEFAULT_TIME)
    }

    /** Where the location picker opens for a new step: last step of the trip, else null. */
    fun location(steps: List<Step>): GeoPoint? = steps.maxWithOrNull(StepOrder)?.location

    private fun isWithin(day: LocalDate, trip: Trip): Boolean =
        !day.isBefore(trip.startDate) && (trip.endDate == null || !day.isAfter(trip.endDate))

    private fun clamp(day: LocalDate, trip: Trip): LocalDate = when {
        day.isBefore(trip.startDate) -> trip.startDate
        trip.endDate != null && day.isAfter(trip.endDate) -> trip.endDate
        else -> day
    }
}
