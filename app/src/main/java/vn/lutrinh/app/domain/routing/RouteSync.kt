package vn.lutrinh.app.domain.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.usecase.legsOf
import kotlin.coroutines.cancellation.CancellationException

/** Stored routes of legs (R36, table `leg_routes`). */
interface LegRouteRepository {
    /** Every stored route (all trips). */
    fun observeAll(): Flow<List<LegRoute>>

    /** Stored routes of the legs of one trip. */
    fun observeForTrip(tripId: String): Flow<List<LegRoute>>

    /** Inserts or replaces the route of the leg ending at [LegRoute.toStepId]. */
    suspend fun save(route: LegRoute)
}

/** Where the two routing flags live (on-device preferences). */
interface RoutingStore {
    /** R36: routing on (default) or off (straight lines, no network calls). */
    var routingEnabled: Boolean

    /** R36: has the one-time notice "stop positions are sent to the routing server" been shown? */
    var routingNoticeSeen: Boolean
}

/** The routing switch ("Cài đặt") and the one-time notice flag (R36), observable. */
class RoutingSettings(private val store: RoutingStore) {
    private val _enabled = MutableStateFlow(store.routingEnabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _noticeSeen = MutableStateFlow(store.routingNoticeSeen)
    val noticeSeen: StateFlow<Boolean> = _noticeSeen.asStateFlow()

    fun setEnabled(on: Boolean) {
        store.routingEnabled = on
        _enabled.value = on
    }

    /** The notice was shown (whatever the user answered): it never shows again. */
    fun markNoticeSeen() {
        store.routingNoticeSeen = true
        _noticeSeen.value = true
    }

    /** The routes a screen uses: the stored ones while routing is on, none (straight lines) while it is off. */
    fun shown(stored: List<LegRoute>): Map<String, LegRoute> =
        if (_enabled.value) stored.associateBy { it.toStepId } else emptyMap()
}

/**
 * R36 fetch queue: keeps every routed leg of every trip supplied with a stored route.
 *
 * * Requests go out only while routing is on, the one-time notice has been shown and the device is
 *   online; one at a time, at most one per [minIntervalMs] (the FOSSGIS limit is 1 per second).
 * * A leg whose inputs (stops, profile) have no stored answer is pending; an answer (route, no
 *   route, too far) is stored and the leg leaves the queue. Moving a stop or changing the mode
 *   makes exactly that leg pending again.
 * * "Unavailable" (offline, server error) is retried later with a growing pause; a rate limit
 *   pauses the whole queue. The primary router's fallbacks are asked only when it is unavailable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteSync(
    private val steps: Flow<List<Step>>,
    private val routes: LegRouteRepository,
    private val settings: RoutingSettings,
    private val online: Flow<Boolean>,
    private val providers: RoutingProviders,
    private val clock: Clock,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val log: (String) -> Unit = {},
) {
    private val gate = Mutex()
    private var lastRequestAt = Long.MIN_VALUE / 2
    private var pausedUntil = 0L
    private val retryAt = HashMap<LegRouteRequest, Long>()
    private val failures = HashMap<LegRouteRequest, Int>()

    /** Legs waiting for a route while routing is on (empty while it is off). */
    private val pending: Flow<List<LegRouteRequest>> = settings.enabled.flatMapLatest { on ->
        if (!on) flowOf(emptyList())
        else combine(steps, routes.observeAll()) { all, stored ->
            val byLeg = stored.associateBy { it.toStepId }
            all.groupBy { it.tripId }.values.flatMap { LegRoutes.pending(legsOf(it), byLeg) }
        }
    }.distinctUntilChanged()

    private val _noticeDue = MutableStateFlow(false)

    /** True when the one-time notice must be shown now: routing is on, never noticed, and a leg waits for a route. */
    val noticeDue: StateFlow<Boolean> = _noticeDue.asStateFlow()

    fun start(scope: CoroutineScope) {
        val shared = pending.stateIn(scope, SharingStarted.Eagerly, emptyList())
        scope.launch {
            combine(shared, settings.noticeSeen) { p, seen -> p.isNotEmpty() && !seen }
                .collect { _noticeDue.value = it }
        }
        scope.launch {
            combine(shared, settings.noticeSeen, online) { p, seen, net -> if (seen && net) p else emptyList() }
                .distinctUntilChanged()
                .collectLatest { drain(it) }
        }
    }

    /** Fetches [requests] one by one; failed ones go back to the end after their pause. */
    private suspend fun drain(requests: List<LegRouteRequest>) {
        if (requests.isEmpty()) return
        log("queue: ${requests.size} leg(s)")
        val left = ArrayDeque(requests)
        while (left.isNotEmpty()) {
            val now = clock.nowMillis()
            val ready = left.firstOrNull { (retryAt[it] ?: 0L) <= now }
            if (ready == null) {
                delay(left.minOf { retryAt[it] ?: now } - now)
                continue
            }
            left.remove(ready)
            if (!fetch(ready)) left.addLast(ready)
        }
    }

    /** One leg: asks the routers and stores the answer. False when it must be retried later. */
    internal suspend fun fetch(request: LegRouteRequest): Boolean = gate.withLock {
        for (provider in providers.all) {
            if (!provider.supports(request.profile)) continue
            pace()
            log("request ${provider.id} ${request.profile.id} -> ${request.toStepId}")
            val result = try {
                provider.route(request.from, request.to, request.profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RouteResult.Unavailable(e.message ?: e.javaClass.simpleName)
            }
            val now = clock.nowMillis()
            val row = when (result) {
                is RouteResult.Found -> LegRoutes.judge(request, result, provider.id, now)
                RouteResult.NoRoute -> LegRoutes.noRoute(request, provider.id, now)
                is RouteResult.Unavailable -> {
                    log("unavailable ${provider.id}: ${result.reason}")
                    if (result.rateLimited) pausedUntil = now + RATE_LIMIT_PAUSE_MS
                    null
                }
            }
            if (row != null) {
                log("stored ${row.status.id} ${"%.1f".format(row.distanceM / 1000)} km ${row.durationS.toLong()} s -> ${row.toStepId}")
                try {
                    routes.save(row)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The step was deleted meanwhile (foreign key): nothing to keep.
                    log("not stored: ${e.message}")
                }
                failures.remove(request)
                retryAt.remove(request)
                return@withLock true
            }
        }
        val n = (failures[request] ?: 0) + 1
        failures[request] = n
        retryAt[request] = clock.nowMillis() + (RETRY_BASE_MS shl (n - 1).coerceAtMost(5)).coerceAtMost(RETRY_MAX_MS)
        false
    }

    /** Waits until the next request respects [minIntervalMs] and any rate-limit pause. */
    private suspend fun pace() {
        val now = clock.nowMillis()
        val at = maxOf(lastRequestAt + minIntervalMs, pausedUntil)
        if (at > now) delay(at - now)
        lastRequestAt = clock.nowMillis()
    }

    companion object {
        /** FOSSGIS: at most one request per second. */
        const val MIN_INTERVAL_MS = 1_000L
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 10 * 60_000L
        const val RATE_LIMIT_PAUSE_MS = 60_000L
    }
}
