package vn.lutrinh.app.domain.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.repository.Clock

/*
 * "Gần tôi" (R45, docs/04): the published stops of a trip listed nearest → farthest by ROAD distance
 * from where the user is now. Pure Kotlin, mirrored 1:1 in Swift (docs/07). The straight-line distance
 * is never computed here: a stop with no road distance goes last, labelled.
 */

/** Where the user is, as the screen found out (the screen asks for the permission and the fix). */
sealed interface Origin {
    data class Fix(val point: GeoPoint) : Origin

    /** The user refused the location permission. */
    data object NoPermission : Origin

    /** Location is switched off on the phone. */
    data object LocationOff : Origin

    /** Permission and switch are fine but no position came (indoors, timeout). */
    data object NoFix : Origin
}

/** What one stop's road distance is, so far. */
sealed interface StopRoad {
    /** Asked for, no answer yet. */
    data object Pending : StopRoad

    /** [byCar]: the motorbike profile had no route and the car one answered (labelled "tuyến ô tô"). */
    data class Found(val distance: RoadDistance, val byCar: Boolean) : StopRoad

    /** [noRoute] = the router says there is no road there; false = it could not be asked (offline, server). */
    data class Unroutable(val noRoute: Boolean) : StopRoad
}

/** One line of the "Gần tôi" list: a published stop (its timeline [number]) and its road distance. */
data class NearRow(val step: Step, val number: Int, val road: StopRoad)

/** What the "Gần tôi" view shows. */
sealed interface NearMeState {
    /** The view has not been opened yet. */
    data object Idle : NearMeState

    /** Routing is off or its notice was never accepted: the user's position must not go anywhere yet. */
    data object NeedsConsent : NearMeState

    /** Waiting for the permission answer or the position. */
    data object Locating : NearMeState

    data object NoPermission : NearMeState
    data object LocationOff : NearMeState
    data object NoFix : NearMeState

    /** The phone is offline or the routing server never answered: nothing could be computed. */
    data object Offline : NearMeState

    /** The trip has no published stop. */
    data object NoStops : NearMeState

    /** [rows] sorted for display; [pending] stops are still being asked (shown last as "đang tính"). */
    data class Rows(val origin: GeoPoint, val rows: List<NearRow>, val pending: Int) : NearMeState
}

object NearMe {
    /**
     * Nearest first by road metres (ties keep the timeline order), then the stops asked but still
     * pending, then the ones with no road distance, both in timeline order. No straight-line fallback.
     */
    fun sort(rows: List<NearRow>): List<NearRow> {
        val found = rows.filter { it.road is StopRoad.Found }.sortedWith(compareBy({ (it.road as StopRoad.Found).distance.distanceM }, { it.number }))
        val pending = rows.filter { it.road is StopRoad.Pending }.sortedBy { it.number }
        val none = rows.filter { it.road is StopRoad.Unroutable }.sortedBy { it.number }
        return found + pending + none
    }

    /** Published stops (drafts have no number, R20) as unanswered rows. */
    fun rowsOf(steps: List<Step>, numbers: Map<String, Int>): List<NearRow> =
        steps.mapNotNull { s -> numbers[s.id]?.let { NearRow(s, it, StopRoad.Pending) } }
}

/**
 * Computes the "Gần tôi" list for one origin (R45). One batch request first (the router's
 * one-to-many call, e.g. Valhalla `sources_to_targets`); a stop it could not answer is asked alone with
 * the motorbike profile and, when that has no route, the car one (like a leg, R36). Every request keeps
 * [minIntervalMs] (FOSSGIS: 1 per second) and a rate limit pauses for [RATE_LIMIT_PAUSE_MS].
 * Results arrive as the flow emits: the first [NearMeState.Rows] has every stop pending.
 */
class NearMeFinder(
    private val providers: RoutingProviders,
    private val clock: Clock,
    private val minIntervalMs: Long = RouteSync.MIN_INTERVAL_MS,
    private val log: (String) -> Unit = {},
) {
    private var lastRequestAt = Long.MIN_VALUE / 2
    private var pausedUntil = 0L

    fun run(origin: GeoPoint, stops: List<NearRow>): Flow<NearMeState> = flow {
        if (stops.isEmpty()) {
            emit(NearMeState.NoStops)
            return@flow
        }
        val rows = stops.toMutableList()
        var unavailable = 0
        suspend fun publish() = emit(NearMeState.Rows(origin, NearMe.sort(rows), rows.count { it.road is StopRoad.Pending }))
        fun set(i: Int, road: StopRoad) { rows[i] = rows[i].copy(road = road) }
        publish()

        // 1. The batch.
        var batchDown = false
        for (chunk in rows.indices.chunked(BATCH)) {
            when (val m = askBatch(origin, chunk.map { rows[it].step.location })) {
                is MatrixResult.Rows -> chunk.forEachIndexed { k, i -> m.distances[k]?.let { set(i, StopRoad.Found(it, byCar = false)) } }
                MatrixResult.NotSupported -> {}
                is MatrixResult.Unavailable -> { batchDown = true; break }
            }
        }
        publish()

        // 2. The stops the batch did not answer, one by one (not when the batch said the server is down).
        for (i in rows.indices) {
            if (rows[i].road !is StopRoad.Pending) continue
            if (batchDown) { set(i, StopRoad.Unroutable(noRoute = false)); unavailable++; continue }
            val road = askAlone(origin, rows[i].step.location)
            if (road is StopRoad.Unroutable && !road.noRoute) unavailable++
            set(i, road)
            publish()
        }
        if (rows.none { it.road is StopRoad.Found } && unavailable > 0) emit(NearMeState.Offline) else publish()
    }

    /** The batch for [targets] through the first router that has one for the motorbike profile. */
    private suspend fun askBatch(origin: GeoPoint, targets: List<GeoPoint>): MatrixResult {
        var last: MatrixResult = MatrixResult.NotSupported
        for (p in providers.all) {
            if (!p.supports(RouteProfile.MOTORBIKE)) continue
            pace()
            log("near-me batch ${p.id} ${targets.size} stop(s)")
            val r = withContext(NonCancellable) { safely { p.distancesFrom(origin, targets, RouteProfile.MOTORBIKE) } }
            when (r) {
                is MatrixResult.Rows -> return r
                MatrixResult.NotSupported -> {}
                is MatrixResult.Unavailable -> {
                    log("near-me batch unavailable ${p.id}: ${r.reason}")
                    if (r.rateLimited) pausedUntil = clock.nowMillis() + RATE_LIMIT_PAUSE_MS
                    last = r
                }
            }
        }
        return last
    }

    /** One stop: motorbike, then car when the motorbike has no route. */
    private suspend fun askAlone(origin: GeoPoint, to: GeoPoint): StopRoad {
        var noRoute = false
        for (profile in listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR)) {
            var answered = false
            for (p in providers.all) {
                if (!p.supports(profile)) continue
                pace()
                log("near-me request ${p.id} ${profile.id}")
                val r = withContext(NonCancellable) {
                    try {
                        p.route(origin, to, profile)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RouteResult.Unavailable(e.message ?: e.javaClass.simpleName)
                    }
                }
                when (r) {
                    is RouteResult.Found ->
                        return StopRoad.Found(RoadDistance(r.distanceM, r.durationS), byCar = profile == RouteProfile.CAR)
                    RouteResult.NoRoute -> { answered = true; break }
                    is RouteResult.Unavailable -> {
                        log("near-me unavailable ${p.id}: ${r.reason}")
                        if (r.rateLimited) pausedUntil = clock.nowMillis() + RATE_LIMIT_PAUSE_MS
                    }
                }
            }
            if (!answered) return StopRoad.Unroutable(noRoute = false)
            noRoute = true
        }
        return StopRoad.Unroutable(noRoute = noRoute)
    }

    private suspend fun safely(block: suspend () -> MatrixResult): MatrixResult = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        MatrixResult.Unavailable(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun pace() {
        val now = clock.nowMillis()
        val at = maxOf(lastRequestAt + minIntervalMs, pausedUntil)
        if (at > now) delay(at - now)
        lastRequestAt = clock.nowMillis()
    }

    companion object {
        const val BATCH = 50
        const val RATE_LIMIT_PAUSE_MS = RouteSync.RATE_LIMIT_PAUSE_MS
    }
}

/**
 * The "Gần tôi" view of one trip (R45): what [state] shows and what moves it. The screen finds the
 * position (permission, GPS: [locate] asks it to) and answers with [onOrigin]. The answer stays here, in
 * memory only, while the stops are unchanged: switching views does not ask again; [open] with `force`
 * (Thử lại / Làm mới) does. The position and the stops go to the routing server only when [consent]
 * says routing is on and its notice accepted.
 */
class NearMeSession(
    private val scope: CoroutineScope,
    private val finder: NearMeFinder,
    private val consent: () -> Boolean,
    private val online: () -> Boolean,
    private val locate: () -> Unit,
) {
    private val _state = MutableStateFlow<NearMeState>(NearMeState.Idle)
    val state: StateFlow<NearMeState> = _state.asStateFlow()
    private var job: Job? = null
    private var key: List<Triple<String, String, GeoPoint>>? = null
    private var stops: List<NearRow> = emptyList()

    /** The user opened "Gần tôi" with these published [rows]; [force] = a new position, a new calculation. */
    fun open(rows: List<NearRow>, force: Boolean = false) {
        val newKey = rows.map { Triple(it.step.id, it.step.title, it.step.location) }
        val now = _state.value
        if (!force && newKey == key && now != NearMeState.Idle && now != NearMeState.NeedsConsent) return
        job?.cancel()
        stops = rows
        key = newKey
        when {
            rows.isEmpty() -> _state.value = NearMeState.NoStops
            !consent() -> { key = null; _state.value = NearMeState.NeedsConsent }
            !online() -> _state.value = NearMeState.Offline
            else -> { _state.value = NearMeState.Locating; locate() }
        }
    }

    /** The screen's answer to a [locate] request. */
    fun onOrigin(origin: Origin) {
        job?.cancel()
        when (origin) {
            Origin.NoPermission -> _state.value = NearMeState.NoPermission
            Origin.LocationOff -> _state.value = NearMeState.LocationOff
            Origin.NoFix -> _state.value = NearMeState.NoFix
            is Origin.Fix -> job = scope.launch { finder.run(origin.point, stops).collect { _state.value = it } }
        }
    }
}
