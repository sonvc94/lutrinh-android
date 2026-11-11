package vn.lutrinh.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.SetLegNote
import vn.lutrinh.app.domain.usecase.SetLegTransport
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.overallStatsOf
import vn.lutrinh.app.domain.usecase.tripColorSlots
import java.time.LocalDate
import java.time.LocalDateTime

/** R12 (leg transport) and R14 (overall map) rules. */
class LegsTest {
    private val hg = GeoPoint(22.8233, 104.9836)
    private val dv = GeoPoint(23.2786, 105.3621)

    private fun step(id: String, at: String, p: GeoPoint = hg, mode: TransportMode? = null, trip: String = "t") =
        Step(id, trip, id, "", p, LocalDateTime.parse(at), 0, 0, emptyList(), mode)

    @Test fun legsFollowTimelineOrderAndTakeTheModeOfTheArrivalStep() {
        val a = step("a", "2026-10-20T08:00", mode = TransportMode.PLANE) // first step: mode ignored
        val b = step("b", "2026-10-20T12:00", dv, TransportMode.MOTORBIKE)
        val c = step("c", "2026-10-21T09:00", hg)
        val legs = legsOf(listOf(c, b, a))
        assertEquals(listOf("a" to "b", "b" to "c"), legs.map { it.from.id to it.to.id })
        assertEquals(listOf(TransportMode.MOTORBIKE, null), legs.map { it.mode })
        assertEquals(Geo.distanceKm(hg, dv), legs[0].distanceKm, 1e-9)
        assertTrue(legsOf(listOf(a)).isEmpty())
    }

    @Test fun codesRoundTripAndUnknownCodesReadAsNotSet() {
        TransportMode.entries.forEach { assertEquals(it, TransportMode.fromCode(it.code)) }
        assertNull(TransportMode.fromCode("hoverboard"))
        assertNull(TransportMode.fromCode(null))
        assertEquals(TransportMode.entries.size, TransportMode.entries.map { it.code }.toSet().size)
    }

    @Test fun onlyFlightsAreDrawnAsArcs() {
        TransportMode.entries.filter { it != TransportMode.PLANE }.forEach {
            assertEquals(listOf(hg, dv), LegGeometry.path(hg, dv, it))
        }
        assertEquals(listOf(hg, dv), LegGeometry.path(hg, dv, null))
        val arc = LegGeometry.path(hg, dv, TransportMode.PLANE)
        assertEquals(LegGeometry.ARC_POINTS, arc.size)
        assertEquals(hg.latitude, arc.first().latitude, 1e-9)
        assertEquals(hg.longitude, arc.first().longitude, 1e-9)
        assertEquals(dv.latitude, arc.last().latitude, 1e-9)
        assertEquals(dv.longitude, arc.last().longitude, 1e-9)
        // The middle of the arc is off the straight line, on the left of the travel direction
        // (here: going north-east, left = north-west of the midpoint).
        val mid = arc[arc.size / 2]
        val straightMid = GeoPoint((hg.latitude + dv.latitude) / 2, (hg.longitude + dv.longitude) / 2)
        assertTrue(Geo.distanceKm(mid, straightMid) > 0.05 * Geo.distanceKm(hg, dv))
        assertTrue(mid.longitude < straightMid.longitude)
        assertTrue(mid.latitude > straightMid.latitude)
    }

    @Test fun setLegTransportUpdatesAndClearsAndSaveStepKeepsIt() = runTest {
        val store = FakeStore()
        val clock = FixedClock()
        val ids = SeqIds()
        val start = LocalDate.of(2026, 10, 20)
        val tripId = (SaveTrip(store, store, clock, ids)(null, TripInput("HG", "", start, null)) as SaveResult.Saved).id
        val saveStep = SaveStep(store, store, RecordingFiles(), clock, ids)
        val input = StepInput("Đồng Văn", "", dv, LocalDateTime.of(start, java.time.LocalTime.NOON), emptyList())
        val id = (saveStep(tripId, null, input) as SaveResult.Saved).id
        val set = SetLegTransport(store, clock)

        clock.millis = 2_000
        assertTrue(set(id, TransportMode.BUS))
        assertEquals(TransportMode.BUS, store.getStep(id)!!.arrivalTransport)
        assertEquals(2_000, store.getStep(id)!!.updatedAt)

        // Editing the step in the form does not lose the leg transport.
        saveStep(tripId, id, input.copy(title = "Phố cổ Đồng Văn"))
        assertEquals(TransportMode.BUS, store.getStep(id)!!.arrivalTransport)

        assertTrue(set(id, null))
        assertNull(store.getStep(id)!!.arrivalTransport)
        assertFalse(set("missing", TransportMode.CAR))
    }

    /** R41: a leg note is trimmed, cleared by an empty text, refused over 1000 characters, kept by the step form. */
    @Test fun setLegNoteTrimsClearsLimitsAndSaveStepKeepsIt() = runTest {
        val store = FakeStore()
        val clock = FixedClock()
        val ids = SeqIds()
        val start = LocalDate.of(2026, 10, 20)
        val tripId = (SaveTrip(store, store, clock, ids)(null, TripInput("HG", "", start, null)) as SaveResult.Saved).id
        val saveStep = SaveStep(store, store, RecordingFiles(), clock, ids)
        val input = StepInput("Đồng Văn", "Ghi chú điểm", dv, LocalDateTime.of(start, java.time.LocalTime.NOON), emptyList())
        val id = (saveStep(tripId, null, input) as SaveResult.Saved).id
        val set = SetLegNote(store, clock)

        clock.millis = 3_000
        assertTrue(set(id, "  Đường dốc khó đi, gửi xe phía đối diện 30k \n"))
        assertEquals("Đường dốc khó đi, gửi xe phía đối diện 30k", store.getStep(id)!!.arrivalNote)
        assertEquals(3_000, store.getStep(id)!!.updatedAt)

        // Editing the step in the form keeps the leg note and the stop note apart.
        saveStep(tripId, id, input.copy(title = "Phố cổ Đồng Văn"))
        assertEquals("Đường dốc khó đi, gửi xe phía đối diện 30k", store.getStep(id)!!.arrivalNote)
        assertEquals("Ghi chú điểm", store.getStep(id)!!.note)

        // 1000 characters (code points, here 2-char emoji) fit; 1001 are refused and change nothing.
        assertTrue(set(id, "🛵".repeat(1000)))
        assertFalse(set(id, "Đ".repeat(1001)))
        assertEquals("🛵".repeat(1000), store.getStep(id)!!.arrivalNote)

        assertTrue(set(id, "   "))
        assertNull(store.getStep(id)!!.arrivalNote)
        assertFalse(set("missing", "x"))
    }

    @Test fun overallStatsSumEachTripSeparately() {
        val t1 = Trip("t1", "A", "", LocalDate.of(2025, 1, 1), null, 1, 1)
        val t2 = Trip("t2", "B", "", LocalDate.of(2025, 2, 1), null, 2, 2)
        val steps = listOf(
            step("a", "2025-01-01T08:00", hg, trip = "t1"),
            step("b", "2025-01-02T08:00", dv, trip = "t1"),
            step("c", "2025-02-01T08:00", dv, trip = "t2"),
            step("orphan", "2025-02-01T08:00", hg, trip = "gone"),
        )
        val stats = overallStatsOf(listOf(t1, t2), steps)
        assertEquals(2, stats.tripCount)
        assertEquals(3, stats.stepCount)
        // a->b only; nothing is counted between the end of t1 and the start of t2.
        assertEquals(Geo.distanceKm(hg, dv), stats.distanceKm, 1e-9)
    }

    @Test fun overallDistanceEqualsSumOfPerTripDistancesOnTheSampleRoutes() {
        val hgRoute = listOf(
            22.8233 to 104.9836, 23.0688 to 104.9930, 23.1203 to 105.1406, 23.3638 to 105.3164, 23.3557 to 105.3266,
            23.2786 to 105.3621, 23.2397 to 105.4096, 23.2128 to 105.4366, 23.1597 to 105.4086, 22.9333 to 105.1667,
        )
        val nbRoute = listOf(20.2862 to 105.9063, 20.2546 to 105.8954, 20.2299 to 105.9327, 20.2163 to 105.9370)
        val trips = listOf(Trip("hg", "HG", "", LocalDate.of(2025, 10, 20), null, 1, 1), Trip("nb", "NB", "", LocalDate.of(2025, 6, 7), null, 2, 2))
        fun steps(id: String, r: List<Pair<Double, Double>>) =
            r.mapIndexed { i, (la, ln) -> step("$id$i", "2025-10-20T%02d:00".format(8 + i), GeoPoint(la, ln), trip = id) }
        val all = steps("hg", hgRoute) + steps("nb", nbRoute)
        val perTrip = trips.map { t -> vn.lutrinh.app.domain.usecase.statsOf(t, all.filter { it.tripId == t.id }).distanceKm }
        val total = overallStatsOf(trips, all).distanceKm
        println("PER-TRIP km: hg=${perTrip[0]} nb=${perTrip[1]} total=$total")
        assertEquals(perTrip.sum(), total, 1e-9)
    }

    @Test fun tripColoursFollowCreationOrderAndStayWhenTripsAreAdded() {
        fun trip(id: String, created: Long) = Trip(id, id, "", LocalDate.of(2025, 1, 1), null, created, created)
        val before = tripColorSlots(listOf(trip("new", 20), trip("old", 10)), 3)
        assertEquals(mapOf("old" to 0, "new" to 1), before)
        val after = tripColorSlots(listOf(trip("x", 40), trip("new", 20), trip("y", 30), trip("old", 10), trip("z", 50)), 3)
        assertEquals(before["old"], after["old"])
        assertEquals(before["new"], after["new"])
        assertEquals(mapOf("old" to 0, "new" to 1, "y" to 2, "x" to 0, "z" to 1), after)
    }
}
