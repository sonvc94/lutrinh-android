package vn.lutrinh.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.routing.MatrixResult
import vn.lutrinh.app.domain.routing.NearMe
import vn.lutrinh.app.domain.routing.NearMeFinder
import vn.lutrinh.app.domain.routing.NearMeSession
import vn.lutrinh.app.domain.routing.NearMeState
import vn.lutrinh.app.domain.routing.Origin
import vn.lutrinh.app.domain.routing.NearRow
import vn.lutrinh.app.domain.routing.RoadDistance
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.routing.RoutingProvider
import vn.lutrinh.app.domain.routing.RoutingProviders
import vn.lutrinh.app.domain.routing.StopRoad
import vn.lutrinh.app.domain.usecase.stepNumbers
import java.time.LocalDate
import java.time.LocalDateTime

/** R45 "Gần tôi": nearest → farthest by ROAD distance, car fallback, unroutable last, error states, request pace. */
@OptIn(ExperimentalCoroutinesApi::class)
class NearMeTest {
    private val origin = GeoPoint(21.0, 105.0)

    private fun step(id: String, at: String, lat: Double, lon: Double = 105.0, draft: Boolean = false) =
        Step(id, "t", "Điểm $id", "", GeoPoint(lat, lon), LocalDateTime.parse(at), 0, 0, emptyList(), null, isDraft = draft)

    /**
     * a: 5 km away in a straight line but 30 km by road (a river with one bridge); b: 20 km straight,
     * 12 km by road; c: 10 km straight, 18 km by road. By road: b < c < a; straight line: a < c < b.
     */
    private val a = step("a", "2026-10-20T08:00", 21.045)
    private val b = step("b", "2026-10-20T09:00", 21.18)
    private val c = step("c", "2026-10-20T10:00", 21.09)
    private val roadKm = mapOf(a.id to 30.0, b.id to 12.0, c.id to 18.0)

    private fun TestScope.clock() = object : Clock {
        override fun nowMillis() = testScheduler.currentTime
        override fun today(): LocalDate = LocalDate.of(2026, 11, 13)
    }

    private fun idOf(p: GeoPoint, steps: List<Step>) = steps.first { it.location == p }.id

    private class Fake(
        override val id: String,
        private val clock: Clock,
        private val onlyCar: Boolean = false,
    ) : RoutingProvider {
        override val credit = RoutingCredit(id, "https://example.org")
        val requests = mutableListOf<Triple<Long, String, RouteProfile>>()
        var batch: (List<GeoPoint>, RouteProfile) -> MatrixResult = { _, _ -> MatrixResult.NotSupported }
        var single: (GeoPoint, RouteProfile) -> RouteResult = { _, _ -> RouteResult.Unavailable("offline") }
        override fun supports(profile: RouteProfile) = !onlyCar || profile == RouteProfile.CAR
        override suspend fun route(from: GeoPoint, to: GeoPoint, profile: RouteProfile): RouteResult {
            requests += Triple(clock.nowMillis(), "route", profile)
            return single(to, profile)
        }
        override suspend fun distancesFrom(from: GeoPoint, targets: List<GeoPoint>, profile: RouteProfile): MatrixResult {
            requests += Triple(clock.nowMillis(), "batch", profile)
            return batch(targets, profile)
        }
    }

    private fun rows(vararg s: Step) = NearMe.rowsOf(s.toList(), stepNumbers(s.toList()))

    private fun found(km: Double) = RouteResult.Found(km * 1000, km * 90, listOf(origin, origin))

    private suspend fun run(f: Fake, vararg s: Step, car: Fake? = null, scope: TestScope): List<NearMeState> =
        NearMeFinder(RoutingProviders(f, listOfNotNull(car)), scope.clock()).run(origin, rows(*s)).toList()

    private fun List<NearMeState>.lastRows() = this[lastIndex] as NearMeState.Rows

    @Test fun listsByRoadDistanceNotByStraightLine() = runTest {
        val f = Fake("valhalla", clock())
        val all = listOf(a, b, c)
        f.batch = { t, _ -> MatrixResult.Rows(t.map { RoadDistance(roadKm.getValue(idOf(it, all)) * 1000, 3600.0) }) }
        val states = run(f, a, b, c, scope = this)
        val done = states.lastRows()
        assertEquals(listOf("b", "c", "a"), done.rows.map { it.step.id })
        assertEquals(0, done.pending)
        assertEquals(12_000.0, (done.rows[0].road as StopRoad.Found).distance.distanceM, 1e-6)
        // The first thing shown has every stop pending; one batch request answered all of them.
        assertEquals(3, (states.first() as NearMeState.Rows).pending)
        assertEquals(listOf("batch"), f.requests.map { it.second })
        // What a straight-line sort would have shown is NOT what is shown.
        val straight = listOf(a, b, c).sortedBy { vn.lutrinh.app.domain.usecase.Geo.distanceKm(origin, it.location) }.map { it.id }
        assertEquals(listOf("a", "c", "b"), straight)
    }

    @Test fun aStopTheBatchCouldNotAnswerIsAskedAlone() = runTest {
        val f = Fake("valhalla", clock())
        val all = listOf(a, b, c)
        // Past the matrix distance limit Valhalla answers null for that cell.
        f.batch = { t, _ -> MatrixResult.Rows(t.map { if (idOf(it, all) == "a") null else RoadDistance(roadKm.getValue(idOf(it, all)) * 1000, 3600.0) }) }
        f.single = { to, _ -> found(roadKm.getValue(idOf(to, all))) }
        val done = run(f, a, b, c, scope = this).lastRows()
        assertEquals(listOf("b", "c", "a"), done.rows.map { it.step.id })
        assertEquals(listOf("batch", "route"), f.requests.map { it.second })
    }

    @Test fun withoutABatchEveryStopIsAskedAloneOneRequestPerSecond() = runTest {
        val f = Fake("valhalla", clock())
        val all = listOf(a, b, c)
        f.single = { to, _ -> found(roadKm.getValue(idOf(to, all))) }
        val done = run(f, a, b, c, scope = this).lastRows()
        assertEquals(listOf("b", "c", "a"), done.rows.map { it.step.id })
        assertEquals(3, f.requests.count { it.second == "route" })
        val times = f.requests.map { it.first }
        assertTrue("requests ≥ 1 s apart: $times", times.zipWithNext().all { (x, y) -> y - x >= 1_000 })
    }

    @Test fun noMotorbikeRouteFallsBackToCarAndSaysSo() = runTest {
        val valhalla = Fake("valhalla", clock())
        val osrm = Fake("osrm", clock(), onlyCar = true)
        valhalla.single = { to, p ->
            if (to == a.location && p == RouteProfile.MOTORBIKE) RouteResult.NoRoute
            else if (to == a.location) RouteResult.Unavailable("down")
            else found(roadKm.getValue(idOf(to, listOf(a, b, c))))
        }
        osrm.single = { _, _ -> found(40.0) }
        val done = run(valhalla, a, b, scope = this, car = osrm).lastRows()
        val rowA = done.rows.first { it.step.id == "a" }
        val road = rowA.road as StopRoad.Found
        assertTrue(road.byCar)
        assertEquals(40_000.0, road.distance.distanceM, 1e-6)
        assertEquals(listOf("b", "a"), done.rows.map { it.step.id })
        assertTrue(!(done.rows.first { it.step.id == "b" }.road as StopRoad.Found).byCar)
    }

    @Test fun unroutableStopsGoLastEvenWhenNearest() = runTest {
        val f = Fake("valhalla", clock())
        val all = listOf(a, b, c)
        f.batch = { _, _ -> MatrixResult.Rows(listOf(null, null, null)) }
        // a: no road at all; b: found; c: the server fails for it alone.
        f.single = { to, p ->
            when (idOf(to, all)) {
                "a" -> RouteResult.NoRoute
                "b" -> found(12.0)
                else -> if (p == RouteProfile.MOTORBIKE) RouteResult.Unavailable("503") else RouteResult.Unavailable("503")
            }
        }
        val done = run(f, a, b, c, scope = this).lastRows()
        assertEquals(listOf("b", "a", "c"), done.rows.map { it.step.id })
        assertEquals(StopRoad.Unroutable(noRoute = true), done.rows[1].road)
        assertEquals(StopRoad.Unroutable(noRoute = false), done.rows[2].road)
    }

    @Test fun nothingAnsweredMeansOffline() = runTest {
        val f = Fake("valhalla", clock())
        f.batch = { _, _ -> MatrixResult.Unavailable("timeout") }
        val states = run(f, a, b, scope = this)
        assertEquals(NearMeState.Offline, states.last())
        // The server is down: no per-stop requests after the failed batch.
        assertEquals(listOf("batch"), f.requests.map { it.second })
    }

    @Test fun noStopsIsItsOwnState() = runTest {
        val f = Fake("valhalla", clock())
        assertEquals(listOf<NearMeState>(NearMeState.NoStops), NearMeFinder(RoutingProviders(f), clock()).run(origin, emptyList()).toList())
        assertTrue(f.requests.isEmpty())
    }

    @Test fun draftsAreLeftOut() {
        val d = step("d", "2026-10-20T11:00", 21.2, draft = true)
        assertEquals(listOf("a", "b"), rows(a, b, d).map { it.step.id })
    }

    @Test fun sortKeepsTimelineOrderOnTies() {
        val r = listOf(
            NearRow(a, 1, StopRoad.Found(RoadDistance(5_000.0, 60.0), false)),
            NearRow(b, 2, StopRoad.Unroutable(true)),
            NearRow(c, 3, StopRoad.Found(RoadDistance(5_000.0, 60.0), false)),
            NearRow(step("p", "2026-10-20T12:00", 21.3), 4, StopRoad.Pending),
        )
        assertEquals(listOf("a", "c", "p", "b"), NearMe.sort(r).map { it.step.id })
    }

    // ---- the view's states (NearMeSession) ----

    private class Rig(scope: TestScope, val fake: Fake, val clock: Clock, var consent: Boolean = true, var online: Boolean = true) {
        var located = 0
        val session = NearMeSession(
            scope, NearMeFinder(RoutingProviders(fake), clock), consent = { consent }, online = { online }, locate = { located++ },
        )
    }

    private fun TestScope.rig(consent: Boolean = true, online: Boolean = true): Rig {
        val f = Fake("valhalla", clock())
        f.batch = { t, _ -> MatrixResult.Rows(t.map { RoadDistance(roadKm.getValue(idOf(it, listOf(a, b, c))) * 1000, 600.0) }) }
        return Rig(this, f, clock(), consent, online)
    }

    @Test fun withoutConsentNothingIsLocatedOrSent() = runTest {
        val r = rig(consent = false)
        r.session.open(rows(a, b, c))
        assertEquals(NearMeState.NeedsConsent, r.session.state.value)
        assertEquals(0, r.located)
        assertTrue(r.fake.requests.isEmpty())
    }

    @Test fun offlineShowsTheMessageBeforeAskingForAPosition() = runTest {
        val r = rig(online = false)
        r.session.open(rows(a, b, c))
        assertEquals(NearMeState.Offline, r.session.state.value)
        assertEquals(0, r.located)
        // Back online: Thử lại starts over.
        r.online = true
        r.session.open(rows(a, b, c), force = true)
        assertEquals(NearMeState.Locating, r.session.state.value)
        assertEquals(1, r.located)
    }

    @Test fun permissionDeniedLocationOffAndNoFixEachShowAndRetryLocatesAgain() = runTest {
        val r = rig()
        r.session.open(rows(a, b, c))
        assertEquals(NearMeState.Locating, r.session.state.value)
        for ((origin, state) in listOf(
            Origin.NoPermission to NearMeState.NoPermission, Origin.LocationOff to NearMeState.LocationOff, Origin.NoFix to NearMeState.NoFix,
        )) {
            r.session.onOrigin(origin)
            assertEquals(state, r.session.state.value)
            val before = r.located
            r.session.open(rows(a, b, c), force = true)
            assertEquals(NearMeState.Locating, r.session.state.value)
            assertEquals(before + 1, r.located)
        }
        assertTrue(r.fake.requests.isEmpty())
    }

    @Test fun aFixComputesAndTheResultIsKeptWhileTheStopsAreTheSame() = runTest {
        val r = rig()
        r.session.open(rows(a, b, c))
        r.session.onOrigin(Origin.Fix(origin))
        advanceUntilIdle()
        val done = r.session.state.value as NearMeState.Rows
        assertEquals(listOf("b", "c", "a"), done.rows.map { it.step.id })
        // Back to "Gần tôi" with the same stops: no new position, no new request.
        r.session.open(rows(a, b, c))
        assertEquals(1, r.located)
        assertEquals(1, r.fake.requests.size)
        // Làm mới: a new position and a new calculation.
        r.session.open(rows(a, b, c), force = true)
        assertEquals(2, r.located)
        // A changed stop list recomputes too.
        r.session.onOrigin(Origin.Fix(origin))
        advanceUntilIdle()
        r.session.open(rows(a, b))
        assertEquals(3, r.located)
    }

    @Test fun aTripWithoutPublishedStopsHasNothingToList() = runTest {
        val r = rig()
        r.session.open(emptyList())
        assertEquals(NearMeState.NoStops, r.session.state.value)
    }
}
