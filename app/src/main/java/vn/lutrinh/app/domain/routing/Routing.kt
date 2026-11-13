package vn.lutrinh.app.domain.routing

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode

/*
 * The routing interface (R36, docs/05-map.md "Routing"): what the app asks a router and what it
 * gets back, in the app's own words. Each router is an adapter in the
 * `routing` package that converts its API to these types; nothing outside its adapter knows its
 * URL, JSON or profile names (RoutingBoundaryTest). Pure Kotlin, mirrored 1:1 in Swift (docs/07).
 */

/** How a leg travels on roads. The adapter maps it to its own profile (its own costing or profile name). */
enum class RouteProfile(val id: String) {
    MOTORBIKE("motorbike"),
    CAR("car"),
    BUS("bus"),
    WALK("walk"),
    ;

    companion object {
        /**
         * R36: which legs follow roads. "Chưa chọn" (null) is routed exactly like Xe máy, the most
         * common way to travel in Vietnam; trains, flights and boats are not routed (straight,
         * arc, dotted as before).
         */
        fun of(mode: TransportMode?): RouteProfile? = when (mode) {
            null, TransportMode.MOTORBIKE -> MOTORBIKE
            TransportMode.CAR -> CAR
            TransportMode.BUS -> BUS
            TransportMode.WALK -> WALK
            TransportMode.TRAIN, TransportMode.PLANE, TransportMode.BOAT -> null
        }

        fun fromId(id: String?): RouteProfile? = entries.firstOrNull { it.id == id }
    }
}

/** What a router answered for one leg. */
sealed interface RouteResult {
    /** A route: its length, the router's travel-time estimate and the line along the roads (start to end). */
    data class Found(val distanceM: Double, val durationS: Double, val path: List<GeoPoint>) : RouteResult

    /** The router has no route between the two places (e.g. no road nearby). Not retried for the same inputs. */
    data object NoRoute : RouteResult

    /** No answer this time (offline, server error, rate limit): retried later. */
    data class Unavailable(val reason: String, val rateLimited: Boolean = false) : RouteResult
}

/** Road length and the router's time estimate of one trip (no line): what "Gần tôi" (R45) needs. */
data class RoadDistance(val distanceM: Double, val durationS: Double)

/** What a router answered for one origin and several destinations at once (R45). */
sealed interface MatrixResult {
    /** One entry per destination, in order; null = no road distance for that one (ask it alone). */
    data class Rows(val distances: List<RoadDistance?>) : MatrixResult

    /** This router cannot answer this request as a batch (no such call, too far, too many): ask each alone. */
    data object NotSupported : MatrixResult

    /** No answer this time (offline, server error, rate limit). */
    data class Unavailable(val reason: String, val rateLimited: Boolean = false) : MatrixResult
}

/** The credit a router's data needs wherever its lines are shown (OSM data is ODbL). */
data class RoutingCredit(
    /** Short text drawn next to the map credit, e.g. "Đường đi: <router> · <host> · © OpenStreetMap (ODbL)". */
    val text: String,
    /** Where users report a wrong road ("Sửa bản đồ"). */
    val fixUrl: String,
) {
    /**
     * The credit on a live map: the router and its host only ("Valhalla · FOSSGIS"); the OSM words are in the
     * basemap's credit line and "Đường đi:" is implied by the leg, so the whole credit stays on one line.
     */
    val onMapText: String get() = text.removePrefix(ROUTE_PREFIX).removeSuffix(OSM_ODBL)

    private companion object {
        const val ROUTE_PREFIX = "Đường đi: "
        const val OSM_ODBL = " · © OpenStreetMap (ODbL)"
    }
}

/** One router behind the app's routing interface (R36). */
interface RoutingProvider {
    /** Stable id stored with each route (`leg_routes.provider`). */
    val id: String
    val credit: RoutingCredit

    fun supports(profile: RouteProfile): Boolean

    /** One request for the leg [from] → [to]. Never throws: network trouble is [RouteResult.Unavailable]. */
    suspend fun route(from: GeoPoint, to: GeoPoint, profile: RouteProfile): RouteResult

    /**
     * R45: road distances from [from] to each of [targets] in ONE request, when the router has such a
     * call (Valhalla `sources_to_targets`). The default has none: [MatrixResult.NotSupported]. Never throws.
     */
    suspend fun distancesFrom(from: GeoPoint, targets: List<GeoPoint>, profile: RouteProfile): MatrixResult =
        MatrixResult.NotSupported
}

/**
 * The routers the app uses, in order: [primary] first; a [fallbacks] entry is asked only when the
 * primary is unavailable (not when it found no route) and supports the profile.
 */
class RoutingProviders(val primary: RoutingProvider, val fallbacks: List<RoutingProvider> = emptyList()) {
    val all: List<RoutingProvider> get() = listOf(primary) + fallbacks

    fun byId(id: String?): RoutingProvider? = all.firstOrNull { it.id == id }
}
