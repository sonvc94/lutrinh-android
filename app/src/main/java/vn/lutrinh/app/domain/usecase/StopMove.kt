package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TripRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * R44: the arrival time a stop gets when it is dragged between two others. Order stays "by
 * arrival time" (R2), so a move is a new time between the new neighbours; nothing is stored but it.
 */
object StopMove {
    /** Dropped before the first / after the last stop: this many minutes from it. */
    const val EDGE_GAP_MINUTES = 30L

    sealed interface Time {
        /** The stop's new arrival, a whole minute that sorts exactly between its neighbours. */
        data class At(val at: LocalDateTime) : Time

        /** No whole minute fits strictly between the neighbours (or the trip dates break the order). */
        data object NoRoom : Time
    }

    /**
     * [prev] / [next]: arrival of the stop before / after the drop place (null at the start / end
     * of the list), without the moved stop. Between two: the midpoint, rounded down to the minute,
     * and only when at least 2 minutes separate them. At an end: [EDGE_GAP_MINUTES] away, clamped
     * into [tripStart]..[tripEnd] (start of the first day / 23:59 of the last); no room when the
     * clamped time is not strictly before [next] / after [prev].
     */
    fun newArrival(prev: LocalDateTime?, next: LocalDateTime?, tripStart: LocalDate, tripEnd: LocalDate?): Time {
        val p = prev?.truncatedTo(ChronoUnit.MINUTES)
        val n = next?.truncatedTo(ChronoUnit.MINUTES)
        val candidate = when {
            p != null && n != null -> {
                val gap = ChronoUnit.MINUTES.between(p, n)
                if (gap < 2) return Time.NoRoom
                p.plusMinutes(gap / 2)
            }
            n != null -> n.minusMinutes(EDGE_GAP_MINUTES).let { if (it.toLocalDate().isBefore(tripStart)) tripStart.atStartOfDay() else it }
            p != null -> p.plusMinutes(EDGE_GAP_MINUTES).let {
                if (tripEnd != null && it.toLocalDate().isAfter(tripEnd)) tripEnd.atTime(LocalTime.of(23, 59)) else it
            }
            else -> return Time.NoRoom
        }
        val day = candidate.toLocalDate()
        val inDates = !day.isBefore(tripStart) && (tripEnd == null || !day.isAfter(tripEnd))
        val sorted = (p == null || candidate.isAfter(p)) && (n == null || candidate.isBefore(n))
        return if (inDates && sorted) Time.At(candidate) else Time.NoRoom
    }

    /** What dropping [stepId] at [slot] does; [slot] = how many of the OTHER stops come before it. */
    sealed interface Plan {
        /** Dropped where it already was. */
        data object Unchanged : Plan

        /** [neighbours] = the stops it was dropped between (before, after), for the message. */
        data class NoRoom(val neighbours: Pair<Step?, Step?>) : Plan

        data class Move(val step: Step, val arrival: LocalDateTime, val legs: LegReattach.Plan) : Plan
    }

    /** [steps]: every step of the trip in timeline order (drafts too), [slot] in 0..steps.size-1. */
    fun plan(steps: List<Step>, stepId: String, slot: Int, tripStart: LocalDate, tripEnd: LocalDate?): Plan {
        val ordered = steps.inTimelineOrder()
        val moved = ordered.firstOrNull { it.id == stepId } ?: return Plan.Unchanged
        val others = ordered.filter { it.id != stepId }
        val at = slot.coerceIn(0, others.size)
        if (at == ordered.indexOf(moved)) return Plan.Unchanged
        val before = others.getOrNull(at - 1)
        val after = others.getOrNull(at)
        return when (val t = newArrival(before?.arrivedAt, after?.arrivedAt, tripStart, tripEnd)) {
            Time.NoRoom -> Plan.NoRoom(before to after)
            is Time.At -> {
                val result = ordered.map { if (it.id == stepId) it.copy(arrivedAt = t.at) else it }
                Plan.Move(moved, t.at, LegReattach.plan(ordered, result))
            }
        }
    }
}

/**
 * R44: a leg's note and mode live on the step where the leg ends (R12, R41). When the order
 * changes, legs between other pairs of stops appear and some old ones go; the data of a broken
 * leg is never moved silently: the user picks which new leg takes it, or drops it.
 */
object LegReattach {
    data class Data(val mode: TransportMode?, val note: String?) {
        val isEmpty: Boolean get() = mode == null && note == null
    }

    /** An old leg that is gone and carried [data]. */
    data class Displaced(val from: Step, val to: Step, val data: Data)

    /** A leg of the new order that did not exist before (it can take a [Displaced] one's data). */
    data class Target(val from: Step, val to: Step)

    /**
     * [displaced]: broken legs with a note or mode, in old order. [targets]: new legs, in new order.
     * [clear]: the stops whose incoming leg changed and that still hold data of the old one.
     */
    data class Plan(val displaced: List<Displaced>, val targets: List<Target>, val clear: List<Step>) {
        /** True when the user has something to decide. */
        val needsChoice: Boolean get() = displaced.isNotEmpty()

        /**
         * The leg data to store, for [choices]: one per [displaced] (null = drop it, else an index into
         * [targets]). A cleared stop gets empty data unless a choice puts a leg there. A missing choice = drop.
         */
        fun writes(choices: List<Int?>): List<Write> {
            val attached = displaced.indices.mapNotNull { i ->
                choices.getOrNull(i)?.let { t -> targets.getOrNull(t)?.let { it.to.id to displaced[i].data } }
            }.toMap()
            val ids = (clear.map { it.id } + attached.keys).distinct()
            return ids.map { Write(it, attached[it] ?: Data(null, null)) }
        }
    }

    /** Store [data] as the incoming-leg data of [stepId]. */
    data class Write(val stepId: String, val data: Data)

    /** Legs of the published steps (R20: drafts are not on legs) of [before] and [after], each in timeline order. */
    fun plan(before: List<Step>, after: List<Step>): Plan {
        val old = before.published().inTimelineOrder()
        val new = after.published().inTimelineOrder()
        fun pair(a: Step, b: Step) = a.id to b.id
        val oldPairs = old.zipWithNext(::pair).toSet()
        val newPairs = new.zipWithNext(::pair).toSet()
        val displaced = old.zipWithNext().filter { (a, b) -> pair(a, b) !in newPairs }.mapNotNull { (a, b) ->
            Data(b.arrivalTransport, b.arrivalNote).takeUnless { it.isEmpty }?.let { Displaced(a, b, it) }
        }
        val targets = new.zipWithNext().filter { (a, b) -> pair(a, b) !in oldPairs }.map { (a, b) -> Target(a, b) }
        // The incoming leg of a step changed when its predecessor did; then what it holds is no longer its leg's.
        val oldPrev = old.zipWithNext().associate { (a, b) -> b.id to a.id }
        val newPrev = new.zipWithNext().associate { (a, b) -> b.id to a.id }
        val clear = new.filter { s ->
            oldPrev[s.id] != newPrev[s.id] && (s.arrivalTransport != null || s.arrivalNote != null)
        }
        return Plan(displaced, targets, clear)
    }
}

/** R44: stores a move of a stop: its new arrival, then the leg data the user chose (empty where cleared). */
class MoveStop(
    private val steps: StepRepository,
    private val trips: TripRepository,
    private val clock: Clock,
) {
    suspend fun plan(tripId: String, stepId: String, slot: Int): StopMove.Plan {
        val trip = trips.getTrip(tripId) ?: return StopMove.Plan.Unchanged
        return StopMove.plan(steps.getSteps(tripId), stepId, slot, trip.startDate, trip.endDate)
    }

    /** False when the step is gone. */
    suspend operator fun invoke(move: StopMove.Plan.Move, choices: List<Int?>): Boolean {
        val current = steps.getStep(move.step.id) ?: return false
        steps.saveStep(current.copy(arrivedAt = move.arrival, updatedAt = clock.nowMillis()))
        ApplyLegData(steps, clock)(move.legs.writes(choices))
        return true
    }
}

/** Writes [LegReattach.Write]s: mode and note of the leg that ends at each step. */
class ApplyLegData(private val steps: StepRepository, private val clock: Clock) {
    suspend operator fun invoke(writes: List<LegReattach.Write>) {
        val now = clock.nowMillis()
        writes.forEach { w ->
            steps.setArrivalTransport(w.stepId, w.data.mode, now)
            steps.setArrivalNote(w.stepId, w.data.note, now)
        }
    }
}
