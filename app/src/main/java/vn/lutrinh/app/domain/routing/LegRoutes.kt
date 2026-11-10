package vn.lutrinh.app.domain.routing

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.Polyline
import kotlin.math.abs

/** What is stored for a leg (R36): a usable route, or why the leg stays straight. */
enum class LegRouteStatus(val id: String) {
    /** A road route that passed the guard: drawn and measured along the road. */
    OK("ok"),

    /** The router has no route: the leg stays straight ("đường chim bay"). */
    NO_ROUTE("no_route"),

    /** The route failed the off-road guard ([LegRoutes.MAX_SNAP_M], [LegRoutes.MAX_DETOUR]): straight. */
    TOO_FAR("too_far"),
    ;

    companion object {
        fun fromId(id: String): LegRouteStatus = entries.firstOrNull { it.id == id } ?: NO_ROUTE
    }
}

/**
 * The stored route of one leg (table `leg_routes`, keyed by the step where the leg ends, like the
 * leg's mode, R12). Valid only while its inputs ([from], [to], [profile]) equal the leg's current
 * ones ([LegRoutes.matches]); a stale row is simply ignored and replaced by the next fetch.
 */
data class LegRoute(
    val toStepId: String,
    val from: GeoPoint,
    val to: GeoPoint,
    val profile: RouteProfile,
    val provider: String,
    val status: LegRouteStatus,
    val distanceM: Double,
    val durationS: Double,
    /** The road line (simplified to about 10 m), without the connectors to the stops. */
    val path: List<GeoPoint>,
    val fetchedAt: Long,
    /**
     * The profile that produced this answer: [profile] itself, or [RouteProfile.CAR] when a Xe máy
     * leg had no motorbike route and the car route was used instead ("tuyến ô tô", [carFallback]).
     */
    val usedProfile: RouteProfile = profile,
) {
    /** A Xe máy / "Chưa chọn" leg answered with the car profile (the screens say "tuyến ô tô"). */
    val carFallback: Boolean get() = usedProfile != profile

    /**
     * A motorbike "no route" that the car profile has not been asked about yet (an answer stored
     * before the car fallback existed): the leg is pending once more, see [LegRoutes.pending].
     */
    val needsCarRetry: Boolean
        get() = profile == RouteProfile.MOTORBIKE && status == LegRouteStatus.NO_ROUTE && usedProfile == RouteProfile.MOTORBIKE
}

/** A leg that needs a route: what is sent to the router (only the two stop positions and the profile). */
data class LegRouteRequest(val toStepId: String, val from: GeoPoint, val to: GeoPoint, val profile: RouteProfile)

/** Distance (and, for a road route, travel time) shown for one leg. */
data class LegMeasure(val km: Double, val byRoad: Boolean, val durationS: Double?, val carRoute: Boolean = false)

/**
 * R36 rules for road-following legs. Pure Kotlin, mirrored 1:1 in Swift (docs/07): which legs need
 * a route, when a stored route still applies, the off-road guard, the line drawn and the km shown.
 */
object LegRoutes {
    /**
     * Off-road guard: a stop farther than this from the road the router used is not on the road
     * network (e.g. "Tràng An" inside the karst park, 713 m): the router then picks a road on the
     * far side and a 6 km leg becomes 25-29 km. Such a leg stays straight.
     */
    const val MAX_SNAP_M = 500.0

    /** A route longer than this many times the straight line is treated as wrong and the leg stays straight. */
    const val MAX_DETOUR = 5.0

    /** Stored lines are simplified to this tolerance (Douglas–Peucker, ≈ 11 m). */
    const val STORE_EPSILON_DEG = 1e-4

    /** Two positions are the same input when they differ by less than this (≈ 0.1 m). */
    private const val SAME_DEG = 1e-6

    /** The request for [leg], or null when its mode is not routed (train, plane, boat). */
    fun requestOf(leg: Leg): LegRouteRequest? =
        RouteProfile.of(leg.mode)?.let { LegRouteRequest(leg.to.id, leg.from.location, leg.to.location, it) }

    /** Does the stored [route] still answer [request]? (same stops, same profile) */
    fun matches(route: LegRoute, request: LegRouteRequest): Boolean =
        route.toStepId == request.toStepId && route.profile == request.profile &&
            same(route.from, request.from) && same(route.to, request.to)

    /** The stored road route [leg] is drawn and measured with, or null (straight / arc). */
    fun usable(leg: Leg, routes: Map<String, LegRoute>): LegRoute? {
        val request = requestOf(leg) ?: return null
        val route = routes[leg.to.id] ?: return null
        return route.takeIf { it.status == LegRouteStatus.OK && matches(it, request) }
    }

    /** Routed legs without a stored answer for their current inputs, in the given order. */
    fun pending(legs: List<Leg>, routes: Map<String, LegRoute>): List<LegRouteRequest> =
        legs.mapNotNull { leg ->
            requestOf(leg)?.takeIf { r -> routes[leg.to.id]?.let { matches(it, r) && !it.needsCarRetry } != true }
        }

    /**
     * The row to store for a router's [found] route: [LegRouteStatus.OK] with the simplified line,
     * or [LegRouteStatus.TOO_FAR] when a stop is more than [MAX_SNAP_M] from the route's ends or the
     * route is more than [MAX_DETOUR] times the straight line.
     */
    fun judge(
        request: LegRouteRequest,
        found: RouteResult.Found,
        provider: String,
        now: Long,
        usedProfile: RouteProfile = request.profile,
    ): LegRoute {
        val straightM = Geo.distanceKm(request.from, request.to) * 1000
        val snapM = snapM(request, found.path)
        val tooFar = found.path.isEmpty() || snapM > MAX_SNAP_M ||
            (straightM > 0 && found.distanceM > straightM * MAX_DETOUR)
        return LegRoute(
            toStepId = request.toStepId, from = request.from, to = request.to, profile = request.profile,
            provider = provider,
            status = if (tooFar) LegRouteStatus.TOO_FAR else LegRouteStatus.OK,
            distanceM = found.distanceM, durationS = found.durationS,
            path = if (tooFar) emptyList() else Polyline.simplify(found.path, STORE_EPSILON_DEG),
            fetchedAt = now, usedProfile = usedProfile,
        )
    }

    /** The row to store when the router has no route: the leg stays straight until its inputs change. */
    fun noRoute(request: LegRouteRequest, provider: String, now: Long, usedProfile: RouteProfile = request.profile): LegRoute =
        LegRoute(
            request.toStepId, request.from, request.to, request.profile, provider, LegRouteStatus.NO_ROUTE,
            0.0, 0.0, emptyList(), now, usedProfile,
        )

    /**
     * The profiles asked for [request], in order: Xe máy / "Chưa chọn" ([RouteProfile.MOTORBIKE])
     * falls back to [RouteProfile.CAR] when the motorbike profile has no route; the others ask once.
     */
    fun profilesFor(request: LegRouteRequest): List<RouteProfile> =
        if (request.profile == RouteProfile.MOTORBIKE) listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR) else listOf(request.profile)

    /** Larger of the two gaps between a stop and the end of the road line next to it, in metres. */
    fun snapM(request: LegRouteRequest, path: List<GeoPoint>): Double {
        if (path.isEmpty()) return Double.POSITIVE_INFINITY
        return maxOf(Geo.distanceKm(request.from, path.first()), Geo.distanceKm(request.to, path.last())) * 1000
    }

    /**
     * The line drawn for [leg]: with a usable [route], stop → short straight connector → the road
     * line → connector → stop; otherwise the straight segment or flight arc ([LegGeometry.path]).
     */
    fun path(leg: Leg, route: LegRoute?): List<GeoPoint> = path(leg.from.location, leg.to.location, leg.mode, route)

    fun path(from: GeoPoint, to: GeoPoint, mode: TransportMode?, route: LegRoute?): List<GeoPoint> {
        if (route == null || route.status != LegRouteStatus.OK || route.path.isEmpty()) return LegGeometry.path(from, to, mode)
        val out = ArrayList<GeoPoint>(route.path.size + 2)
        if (route.path.first() != from) out += from
        out += route.path
        if (route.path.last() != to) out += to
        return out
    }

    /** Km of [leg]: the road distance of a usable [route], else the straight line (labelled "chim bay"). */
    fun measure(leg: Leg, route: LegRoute?): LegMeasure =
        if (route != null && route.status == LegRouteStatus.OK) LegMeasure(route.distanceM / 1000, true, route.durationS, route.carFallback)
        else LegMeasure(leg.distanceKm, false, null)

    private fun same(a: GeoPoint, b: GeoPoint) =
        abs(a.latitude - b.latitude) < SAME_DEG && abs(a.longitude - b.longitude) < SAME_DEG
}
