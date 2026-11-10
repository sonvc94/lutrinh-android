package vn.lutrinh.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.routing.LegMeasure
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRouteRepository
import vn.lutrinh.app.domain.routing.LegRouteStatus
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.routing.RouteSync
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.routing.RoutingProvider
import vn.lutrinh.app.domain.routing.RoutingProviders
import vn.lutrinh.app.domain.routing.RoutingSettings
import vn.lutrinh.app.domain.routing.RoutingStore
import vn.lutrinh.app.domain.usecase.legsOf
import java.time.LocalDate
import java.time.LocalDateTime

/** R36: the fetch queue (notice first, switch, offline, 1 request/s, fallback) and the flags. */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteSyncTest {
    private class MemoryStore(override var routingEnabled: Boolean = true, override var routingNoticeSeen: Boolean = false) : RoutingStore

    private class MemoryRoutes : LegRouteRepository {
        val rows = MutableStateFlow<Map<String, LegRoute>>(emptyMap())
        override fun observeAll(): Flow<List<LegRoute>> = rows.map { it.values.toList() }
        override fun observeForTrip(tripId: String): Flow<List<LegRoute>> = observeAll()
        override suspend fun save(route: LegRoute) { rows.value = rows.value + (route.toStepId to route) }
    }

    /** A router that answers [answer] and records when it was asked. */
    private class FakeRouter(
        override val id: String,
        private val only: RouteProfile? = null,
        private val clock: Clock,
        var answer: (GeoPoint, GeoPoint) -> RouteResult = { a, b -> RouteResult.Found(10_000.0, 900.0, listOf(a, b)) },
    ) : RoutingProvider {
        val calls = mutableListOf<Pair<Long, RouteProfile>>()

        /** Answers per profile before [answer] is used (null = no special answer). */
        var byProfile: (RouteProfile) -> RouteResult? = { null }
        override val credit = RoutingCredit(id, "https://www.openstreetmap.org/fixthemap")
        override fun supports(profile: RouteProfile) = only == null || profile == only
        override suspend fun route(from: GeoPoint, to: GeoPoint, profile: RouteProfile): RouteResult {
            calls += clock.nowMillis() to profile
            return byProfile(profile) ?: answer(from, to)
        }
    }

    /** The queue runs in backgroundScope, which advanceUntilIdle skips: let ten virtual minutes pass. */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(10 * 60_000)
        runCurrent()
    }

    private fun step(id: String, at: String, lat: Double, mode: TransportMode? = null) =
        Step(id, "t", id, "", GeoPoint(lat, 105.0), LocalDateTime.parse(at), 0, 0, emptyList(), mode)

    private val fourStops = listOf(
        step("a", "2026-10-20T08:00", 22.80),
        step("b", "2026-10-20T09:00", 22.85),
        step("c", "2026-10-20T10:00", 22.90, TransportMode.CAR),
        step("d", "2026-10-20T11:00", 22.95, TransportMode.PLANE),
    )

    private inner class Rig(scope: TestScope, store: MemoryStore = MemoryStore(), online: Boolean = true) {
        val clock = object : Clock {
            override fun nowMillis() = scope.testScheduler.currentTime
            override fun today(): LocalDate = LocalDate.of(2026, 11, 10)
        }
        val steps = MutableStateFlow(fourStops)
        val routes = MemoryRoutes()
        val settings = RoutingSettings(store)
        val online = MutableStateFlow(online)
        val valhalla = FakeRouter("valhalla", clock = clock)
        val osrm = FakeRouter("osrm", RouteProfile.CAR, clock)
        val sync = RouteSync(steps, routes, settings, this.online, RoutingProviders(valhalla, listOf(osrm)), clock)
            .also { it.start(scope.backgroundScope) }
    }

    @Test fun nothingIsSentBeforeTheNoticeIsShownThenEveryRoutedLegOnce() = runTest {
        val rig = Rig(this)
        runCurrent()
        assertTrue(rig.sync.noticeDue.value)
        advanceTimeBy(60_000)
        assertEquals(emptyList<Any>(), rig.valhalla.calls)

        rig.settings.markNoticeSeen()
        settle()
        assertFalse(rig.sync.noticeDue.value)
        // b ("Chưa chọn" = Xe máy) and c (Ô tô); d (Máy bay) is never routed.
        assertEquals(listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR), rig.valhalla.calls.map { it.second })
        assertEquals(setOf("b", "c"), rig.routes.rows.value.keys)
        assertTrue(rig.routes.rows.value.values.all { it.status == LegRouteStatus.OK })
        assertEquals(emptyList<Any>(), rig.osrm.calls)
    }

    @Test fun requestsAreAtLeastOneSecondApart() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.steps.value = (0 until 6).map { i -> step("s$i", "2026-10-20T0$i:00", 22.0 + i * 0.05) }
        settle()
        val times = rig.valhalla.calls.map { it.first }
        assertEquals(5, times.size)
        times.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", b - a >= RouteSync.MIN_INTERVAL_MS) }
    }

    @Test fun switchOffMeansNoNetworkCallsAndStraightLines() = runTest {
        val store = MemoryStore(routingEnabled = false, routingNoticeSeen = false)
        val rig = Rig(this, store)
        settle()
        assertFalse(rig.sync.noticeDue.value)
        assertEquals(emptyList<Any>(), rig.valhalla.calls)

        // Routes stored earlier are not shown while off.
        val stored = LegRoute("b", GeoPoint(22.80, 105.0), GeoPoint(22.85, 105.0), RouteProfile.MOTORBIKE, "valhalla", LegRouteStatus.OK, 9000.0, 600.0, emptyList(), 1L)
        assertEquals(emptyMap<String, LegRoute>(), rig.settings.shown(listOf(stored)))
        rig.settings.setEnabled(true)
        assertEquals(setOf("b"), rig.settings.shown(listOf(stored)).keys)
        assertTrue(store.routingEnabled)
    }

    @Test fun turningOffMidwayStopsTheQueue() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.steps.value = (0 until 6).map { i -> step("s$i", "2026-10-20T0$i:00", 22.0 + i * 0.05) }
        runCurrent()
        advanceTimeBy(1_500)
        rig.settings.setEnabled(false)
        settle()
        assertTrue("${rig.valhalla.calls.size}", rig.valhalla.calls.size <= 2)
    }

    @Test fun offlineWaitsAndFetchesWhenBackOnline() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true), online = false)
        advanceTimeBy(120_000)
        assertEquals(emptyList<Any>(), rig.valhalla.calls)
        rig.online.value = true
        settle()
        assertEquals(setOf("b", "c"), rig.routes.rows.value.keys)
    }

    @Test fun unavailableCarLegFallsBackToOsrmMotorbikeLegIsRetriedLater() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        var down = true
        rig.valhalla.answer = { a, b -> if (down) RouteResult.Unavailable("HTTP 502") else RouteResult.Found(9000.0, 700.0, listOf(a, b)) }
        runCurrent()
        advanceTimeBy(5_000)
        // Car leg: OSRM answered; motorbike leg: OSRM cannot, so it waits.
        assertEquals(listOf(RouteProfile.CAR), rig.osrm.calls.map { it.second })
        assertEquals("osrm", rig.routes.rows.value.getValue("c").provider)
        assertFalse("b" in rig.routes.rows.value)
        down = false
        advanceTimeBy(RouteSync.RETRY_BASE_MS + 5_000)
        assertEquals("valhalla", rig.routes.rows.value.getValue("b").provider)
    }

    @Test fun noRouteIsStoredAndNotAskedAgain() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.valhalla.answer = { _, _ -> RouteResult.NoRoute }
        settle()
        val asked = rig.valhalla.calls.size
        assertEquals(LegRouteStatus.NO_ROUTE, rig.routes.rows.value.getValue("b").status)
        advanceTimeBy(3_600_000)
        assertEquals(asked, rig.valhalla.calls.size)
    }

    private val noMotorbikeRoute: (RouteProfile) -> RouteResult? =
        { if (it == RouteProfile.MOTORBIKE) RouteResult.NoRoute else null }

    @Test fun motorbikeLegWithoutMotorbikeRouteIsStoredWithTheCarProfile() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.valhalla.byProfile = noMotorbikeRoute
        settle()
        // b ("Chưa chọn" = Xe máy): motorbike → no route, then car → found; c (Ô tô) asked once.
        assertEquals(
            listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR, RouteProfile.CAR),
            rig.valhalla.calls.map { it.second },
        )
        val b = rig.routes.rows.value.getValue("b")
        assertEquals(LegRouteStatus.OK, b.status)
        assertEquals(RouteProfile.MOTORBIKE, b.profile)
        assertEquals(RouteProfile.CAR, b.usedProfile)
        assertTrue(b.carFallback)
        assertFalse(rig.routes.rows.value.getValue("c").carFallback)
        // The leg is drawn along the road with road km and ~time, flagged "tuyến ô tô".
        val leg = legsOf(fourStops).first()
        val used = LegRoutes.usable(leg, rig.routes.rows.value)!!
        assertEquals(LegMeasure(10.0, true, 900.0, carRoute = true), LegRoutes.measure(leg, used))
        // Not asked again.
        val asked = rig.valhalla.calls.size
        advanceTimeBy(3_600_000)
        assertEquals(asked, rig.valhalla.calls.size)
    }

    @Test fun carAnswerStillFacesTheOffRoadGuard() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.valhalla.byProfile = {
            if (it == RouteProfile.MOTORBIKE) RouteResult.NoRoute
            else RouteResult.Found(10_000.0, 900.0, listOf(GeoPoint(22.80, 105.02), GeoPoint(22.85, 105.0)))
        }
        settle()
        val b = rig.routes.rows.value.getValue("b")
        assertEquals(LegRouteStatus.TOO_FAR, b.status)
        assertEquals(RouteProfile.CAR, b.usedProfile)
        assertNull(LegRoutes.usable(legsOf(fourStops).first(), rig.routes.rows.value))
    }

    @Test fun bothProfilesWithoutRouteKeepTheStraightLegAndAreNotAskedAgain() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        rig.valhalla.answer = { _, _ -> RouteResult.NoRoute }
        settle()
        assertEquals(
            listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR, RouteProfile.CAR),
            rig.valhalla.calls.map { it.second },
        )
        val b = rig.routes.rows.value.getValue("b")
        assertEquals(LegRouteStatus.NO_ROUTE, b.status)
        assertEquals(RouteProfile.CAR, b.usedProfile)
        val leg = legsOf(fourStops).first()
        assertNull(LegRoutes.usable(leg, rig.routes.rows.value))
        assertEquals(listOf(leg.from.location, leg.to.location), LegRoutes.path(leg, LegRoutes.usable(leg, rig.routes.rows.value)))
        assertFalse(LegRoutes.measure(leg, null).byRoad)
        val asked = rig.valhalla.calls.size
        advanceTimeBy(3_600_000)
        assertEquals(asked, rig.valhalla.calls.size)
    }

    @Test fun motorbikeNoRouteStoredBeforeTheFallbackIsRetriedOnceWithCar() = runTest {
        val rig = Rig(this, MemoryStore(routingNoticeSeen = true))
        // What v8 stored: motorbike asked, no route (usedProfile defaults to motorbike).
        val old = LegRoute("b", GeoPoint(22.80, 105.0), GeoPoint(22.85, 105.0), RouteProfile.MOTORBIKE, "valhalla", LegRouteStatus.NO_ROUTE, 0.0, 0.0, emptyList(), 1L)
        assertTrue(old.needsCarRetry)
        rig.routes.rows.value = mapOf("b" to old)
        rig.valhalla.byProfile = noMotorbikeRoute
        settle()
        assertEquals(RouteProfile.CAR, rig.routes.rows.value.getValue("b").usedProfile)
        assertEquals(LegRouteStatus.OK, rig.routes.rows.value.getValue("b").status)
        val asked = rig.valhalla.calls.size
        advanceTimeBy(3_600_000)
        assertEquals("once, then never again", asked, rig.valhalla.calls.size)
        // Same when the car profile has no route either: stored, then left alone.
        rig.valhalla.calls.clear()
        rig.routes.rows.value = rig.routes.rows.value + ("b" to old)
        rig.valhalla.answer = { _, _ -> RouteResult.NoRoute }
        rig.valhalla.byProfile = { null }
        settle()
        val again = rig.valhalla.calls.size
        assertEquals(listOf(RouteProfile.MOTORBIKE, RouteProfile.CAR), rig.valhalla.calls.map { it.second })
        assertFalse(rig.routes.rows.value.getValue("b").needsCarRetry)
        advanceTimeBy(3_600_000)
        assertEquals(again, rig.valhalla.calls.size)
    }

    @Test fun noticeAndSwitchFlagsArePersisted() {
        val store = MemoryStore()
        val first = RoutingSettings(store)
        assertTrue(first.enabled.value)
        assertFalse(first.noticeSeen.value)
        first.markNoticeSeen()
        first.setEnabled(false)
        val again = RoutingSettings(store)
        assertTrue(again.noticeSeen.value)
        assertFalse(again.enabled.value)
    }
}
