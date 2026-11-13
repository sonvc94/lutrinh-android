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
import vn.lutrinh.app.domain.usecase.ApplyLegData
import vn.lutrinh.app.domain.usecase.LegReattach
import vn.lutrinh.app.domain.usecase.MoveStop
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.StopMove
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import java.time.LocalDate
import java.time.LocalDateTime

/** R44: new arrival of a dragged stop, and where the notes/modes of the broken legs go. */
class StopMoveTest {
    private val d1 = LocalDate.of(2026, 10, 20)
    private val d2 = LocalDate.of(2026, 10, 21)
    private fun at(h: Int, m: Int = 0, day: LocalDate = d1) = LocalDateTime.of(day, java.time.LocalTime.of(h, m))

    // --- the new time -------------------------------------------------------------------------

    @Test fun middleIsTheMidpointRoundedDownToTheMinute() {
        assertEquals(StopMove.Time.At(at(10)), StopMove.newArrival(at(9), at(11), d1, d2))
        assertEquals(StopMove.Time.At(at(9, 1)), StopMove.newArrival(at(9), at(9, 3), d1, d2)) // 1.5 -> 1
        assertEquals(StopMove.Time.At(at(9, 1)), StopMove.newArrival(at(9), at(9, 2), d1, d2))
    }

    @Test fun startAndEndAreThirtyMinutesAwayFromTheEdgeStop() {
        assertEquals(StopMove.Time.At(at(8, 30)), StopMove.newArrival(null, at(9), d1, d2))
        assertEquals(StopMove.Time.At(at(12, 30)), StopMove.newArrival(at(12), null, d1, d2))
    }

    @Test fun clampedIntoTheTripDates() {
        // 00:10 on day 1: 30 min before is on the day before the trip -> start of the first day.
        assertEquals(StopMove.Time.At(at(0, 0)), StopMove.newArrival(null, at(0, 10), d1, d2))
        // 23:50 on the last day: 30 min after is the day after -> 23:59 of the last day.
        assertEquals(StopMove.Time.At(at(23, 59, d2)), StopMove.newArrival(at(23, 50, d2), null, d1, d2))
        // open-ended trip: no clamp at the end.
        assertEquals(StopMove.Time.At(at(0, 20, d2)), StopMove.newArrival(at(23, 50), null, d1, null))
    }

    @Test fun noRoomWhenNoWholeMinuteFitsStrictlyBetween() {
        assertEquals(StopMove.Time.NoRoom, StopMove.newArrival(at(9), at(9), d1, d2))
        assertEquals(StopMove.Time.NoRoom, StopMove.newArrival(at(9), at(9, 1), d1, d2))
        assertEquals(StopMove.Time.NoRoom, StopMove.newArrival(at(9, 0), at(9, 0).plusSeconds(50), d1, d2))
    }

    @Test fun noRoomWhenClampingBreaksTheOrder() {
        // first stop at 00:00 of the first day: nothing earlier fits inside the trip.
        assertEquals(StopMove.Time.NoRoom, StopMove.newArrival(null, at(0, 0), d1, d2))
        // last stop at 23:59 of the last day.
        assertEquals(StopMove.Time.NoRoom, StopMove.newArrival(at(23, 59, d2), null, d1, d2))
    }

    // --- the plan: slot, neighbours, legs -------------------------------------------------------

    private fun step(id: String, h: Int, mode: TransportMode? = null, note: String? = null, draft: Boolean = false) = Step(
        id, "t", id.uppercase(), "", GeoPoint(20.0 + h, 105.0), at(h), h.toLong(), h.toLong(),
        arrivalTransport = mode, arrivalNote = note, isDraft = draft,
    )

    // A(8) B(10, car+"b") C(12, bus+"c") D(14)
    private val abcd = listOf(
        step("a", 8), step("b", 10, TransportMode.CAR, "b"), step("c", 12, TransportMode.BUS, "c"), step("d", 14),
    )

    private fun plan(id: String, slot: Int, steps: List<Step> = abcd) = StopMove.plan(steps, id, slot, d1, d2)

    @Test fun droppedWhereItWasChangesNothing() {
        assertEquals(StopMove.Plan.Unchanged, plan("b", 1))
        assertEquals(StopMove.Plan.Unchanged, plan("a", 0))
        assertEquals(StopMove.Plan.Unchanged, plan("d", 3))
    }

    @Test fun movingDBetweenAAndBGetsTheMidpointAndSplitsTheLegB() {
        val p = plan("d", 1) as StopMove.Plan.Move
        assertEquals(at(9), p.arrival)
        // old legs A-B, B-C, C-D; new A-D, D-B, B-C  -> A-B and C-D are broken: only B (data on b) matters.
        assertEquals(listOf("a" to "b"), p.legs.displaced.map { it.from.id to it.to.id })
        assertEquals(listOf("a" to "d", "d" to "b"), p.legs.targets.map { it.from.id to it.to.id })
        assertEquals(listOf("b"), p.legs.clear.map { it.id })
    }

    @Test fun moveToTheStartAndEnd() {
        val first = plan("c", 0) as StopMove.Plan.Move
        assertEquals(at(7, 30), first.arrival)
        val last = plan("a", 3) as StopMove.Plan.Move
        assertEquals(at(14, 30), last.arrival)
    }

    @Test fun noRoomPlanNamesTheNeighboursAndMovesNothing() {
        val tight = listOf(step("a", 8), step("b", 9).copy(arrivedAt = at(8, 1)), step("c", 12))
        val p = plan("c", 1, tight)
        assertTrue(p is StopMove.Plan.NoRoom)
        assertEquals("a" to "b", (p as StopMove.Plan.NoRoom).neighbours.let { it.first!!.id to it.second!!.id })
    }

    @Test fun legsWithoutNoteOrModeNeedNoChoice() {
        val bare = listOf(step("a", 8), step("b", 10), step("c", 12), step("d", 14))
        val p = StopMove.plan(bare, "d", 1, d1, d2) as StopMove.Plan.Move
        assertFalse(p.legs.needsChoice)
        assertTrue(p.legs.clear.isEmpty())
    }

    @Test fun aDraftOnTheMoveIsNotOnAnyLeg() {
        val withDraft = listOf(step("a", 8), step("x", 9, draft = true), step("b", 10, TransportMode.CAR, "b"), step("c", 12))
        // dragging the draft changes no leg
        val p = StopMove.plan(withDraft, "x", 3, d1, d2) as StopMove.Plan.Move
        assertFalse(p.legs.needsChoice)
        assertTrue(p.legs.targets.isEmpty())
    }

    // --- choices -> writes ----------------------------------------------------------------------

    @Test fun choosingATargetMovesNoteAndModeThere() {
        val p = (plan("d", 1) as StopMove.Plan.Move).legs
        val w = p.writes(listOf(1)) // old A-B data -> new D-B (index 1)
        assertEquals(listOf("b"), w.map { it.stepId })
        assertEquals(LegReattach.Data(TransportMode.CAR, "b"), w.single().data)
        val w0 = p.writes(listOf(0)) // -> A-D: data on d, and b is emptied
        assertEquals(setOf("d", "b"), w0.map { it.stepId }.toSet())
        assertEquals(LegReattach.Data(TransportMode.CAR, "b"), w0.first { it.stepId == "d" }.data)
        assertTrue(w0.first { it.stepId == "b" }.data.isEmpty)
    }

    @Test fun droppingLeavesTheNewLegsEmpty() {
        val p = (plan("d", 1) as StopMove.Plan.Move).legs
        val w = p.writes(listOf(null))
        assertEquals(listOf("b"), w.map { it.stepId })
        assertTrue(w.single().data.isEmpty)
    }

    // --- insert through the form ----------------------------------------------------------------

    @Test fun insertingAStopInTheMiddleOffersTheBrokenLegBToC() {
        val x = step("x", 11)
        val p = LegReattach.plan(abcd, abcd + x)
        assertEquals(listOf("b" to "c"), p.displaced.map { it.from.id to it.to.id })
        assertEquals(LegReattach.Data(TransportMode.BUS, "c"), p.displaced.single().data)
        assertEquals(listOf("b" to "x", "x" to "c"), p.targets.map { it.from.id to it.to.id })
        assertEquals(listOf("c"), p.clear.map { it.id })
        // choose X -> C (index 1): data stays on c; choose B -> X (index 0): moves to x, c emptied
        assertEquals(LegReattach.Data(TransportMode.BUS, "c"), p.writes(listOf(1)).single { it.stepId == "c" }.data)
        assertEquals(LegReattach.Data(TransportMode.BUS, "c"), p.writes(listOf(0)).single { it.stepId == "x" }.data)
        assertTrue(p.writes(listOf(0)).single { it.stepId == "c" }.data.isEmpty)
    }

    @Test fun appendingAtTheEndOrAddingADraftAsksNothing() {
        assertFalse(LegReattach.plan(abcd, abcd + step("e", 16)).needsChoice)
        assertFalse(LegReattach.plan(abcd, abcd + step("x", 11, draft = true)).needsChoice)
    }

    @Test fun insertingBeforeTheFirstStopBreaksNoLegAndClearsOnlyHiddenData() {
        val p = LegReattach.plan(abcd, listOf(step("x", 7)) + abcd)
        assertTrue(p.displaced.isEmpty()) // old legs A-B, B-C, C-D all still exist
        assertTrue(p.clear.isEmpty()) // A held nothing
        // A held data while it was the first stop (hidden, R12): it must not show up on X -> A.
        val hidden = listOf(step("a", 8, TransportMode.WALK, "hidden")) + abcd.drop(1)
        val q = LegReattach.plan(hidden, listOf(step("x", 7)) + hidden)
        assertFalse(q.needsChoice)
        assertEquals(listOf("a"), q.clear.map { it.id })
    }

    // --- the use case on a store ----------------------------------------------------------------

    private val store = FakeStore()
    private val clock = FixedClock(millis = 9_000)
    private val ids = SeqIds()

    private suspend fun seeded(): String {
        val tripId = (SaveTrip(store, store, clock, ids)(null, TripInput("T", "", d1, d2)) as SaveResult.Saved).id
        val save = SaveStep(store, store, RecordingFiles(), clock, ids)
        listOf("a" to 8, "b" to 10, "c" to 12, "d" to 14).forEach { (title, h) ->
            save(tripId, null, StepInput(title, "", GeoPoint(20.0 + h, 105.0), at(h), emptyList()))
        }
        return tripId
    }

    private suspend fun idOf(tripId: String, title: String) = store.getSteps(tripId).first { it.title == title }.id

    @Test fun moveStopSavesTheNewTimeAndTheChosenLegData() = runTest {
        val tripId = seeded()
        ApplyLegData(store, clock)(
            listOf(
                LegReattach.Write(idOf(tripId, "b"), LegReattach.Data(TransportMode.CAR, "b")),
                LegReattach.Write(idOf(tripId, "c"), LegReattach.Data(TransportMode.BUS, "c")),
            ),
        )
        val move = MoveStop(store, store, clock)
        val d = idOf(tripId, "d")
        val plan = move.plan(tripId, d, 1) as StopMove.Plan.Move
        assertEquals(at(9), plan.arrival)
        assertTrue(plan.legs.needsChoice)
        // old leg A-B (CAR, "b") is broken; the user picks the new leg D-B (index 1)
        assertEquals(listOf("a" to "b"), plan.legs.displaced.map { it.from.title to it.to.title })
        assertTrue(move(plan, listOf(1)))
        val after = store.getSteps(tripId)
        assertEquals(listOf("a", "d", "b", "c"), after.map { it.title })
        assertEquals(at(9), after.first { it.title == "d" }.arrivedAt)
        assertEquals(TransportMode.CAR, after.first { it.title == "b" }.arrivalTransport)
        assertEquals("b", after.first { it.title == "b" }.arrivalNote)
        assertEquals(TransportMode.BUS, after.first { it.title == "c" }.arrivalTransport) // B-C kept as it was
    }

    @Test fun movingAStopWithDataOnItsOwnLegAsksAndAttachesTheChoice() = runTest {
        val tripId = seeded()
        val b = idOf(tripId, "b")
        ApplyLegData(store, clock)(listOf(LegReattach.Write(b, LegReattach.Data(TransportMode.CAR, "đường dốc"))))
        val move = MoveStop(store, store, clock)
        // drag A (first) between C and D (slot 2): the new order is B C A D.
        val a = idOf(tripId, "a")
        val plan = move.plan(tripId, a, 2) as StopMove.Plan.Move
        // B becomes the first step: its data (leg A-B) is displaced and must be offered, targets: C-A, A-D.
        assertEquals(listOf("a" to "b"), plan.legs.displaced.map { it.from.title to it.to.title })
        assertEquals(listOf("c" to "a", "a" to "d"), plan.legs.targets.map { it.from.title to it.to.title })
        move(plan, listOf(0)) // C -> A: data goes on A
        val steps = store.getSteps(tripId)
        assertEquals(listOf("b", "c", "a", "d"), steps.map { it.title })
        val na = steps.first { it.title == "a" }
        assertEquals(TransportMode.CAR, na.arrivalTransport)
        assertEquals("đường dốc", na.arrivalNote)
        assertNull(steps.first { it.title == "b" }.arrivalNote)
        assertNull(steps.first { it.title == "b" }.arrivalTransport)
    }

    @Test fun dropChoiceStoresNoLegData() = runTest {
        val tripId = seeded()
        val b = idOf(tripId, "b")
        ApplyLegData(store, clock)(listOf(LegReattach.Write(b, LegReattach.Data(null, "ghi chú"))))
        val move = MoveStop(store, store, clock)
        val plan = move.plan(tripId, idOf(tripId, "d"), 1) as StopMove.Plan.Move
        move(plan, listOf(null))
        assertTrue(store.getSteps(tripId).all { it.arrivalNote == null && it.arrivalTransport == null })
    }
}
