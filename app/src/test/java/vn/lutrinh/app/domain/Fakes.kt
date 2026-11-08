package vn.lutrinh.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.PhotoFileStore
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TripRepository
import vn.lutrinh.app.domain.usecase.TripOrder
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import java.time.LocalDate

/** In-memory storage with the same contract as the Room implementation (used for use-case tests). */
class FakeStore : TripRepository, StepRepository {
    val trips = MutableStateFlow<Map<String, Trip>>(emptyMap())
    val steps = MutableStateFlow<Map<String, Step>>(emptyMap())

    override fun observeTripSummaries(): Flow<List<TripSummary>> = trips.map { all ->
        all.values.sortedWith(TripOrder).map { t ->
            val s = steps.value.values.filter { it.tripId == t.id }.inTimelineOrder()
            TripSummary(t, s.size, s.sumOf { it.photos.size }, s.flatMap { it.photos }.firstOrNull()?.fileName)
        }
    }

    override fun observeTrip(id: String) = trips.map { it[id] }
    override suspend fun getTrip(id: String) = trips.value[id]
    override suspend fun upsertTrip(trip: Trip) { trips.value = trips.value + (trip.id to trip) }

    override suspend fun deleteTrip(id: String): List<String> {
        val owned = steps.value.values.filter { it.tripId == id }
        trips.value = trips.value - id
        steps.value = steps.value - owned.map { it.id }.toSet()
        return owned.flatMap { s -> s.photos.map { it.fileName } }
    }

    override fun observeSteps(tripId: String) = steps.map { all -> all.values.filter { it.tripId == tripId }.inTimelineOrder() }
    override fun observeStep(id: String) = steps.map { it[id] }
    override suspend fun getStep(id: String) = steps.value[id]
    override suspend fun getSteps(tripId: String) = steps.value.values.filter { it.tripId == tripId }.inTimelineOrder()

    override suspend fun stepDateRange(tripId: String): ClosedRange<LocalDate>? {
        val days = getSteps(tripId).map { it.arrivedAt.toLocalDate() }
        return if (days.isEmpty()) null else days.min()..days.max()
    }

    override suspend fun saveStep(step: Step): List<String> {
        require(trips.value.containsKey(step.tripId)) { "FK: trip missing" }
        val before = steps.value[step.id]?.photos.orEmpty().map { it.fileName }
        steps.value = steps.value + (step.id to step)
        val after = step.photos.map { it.fileName }.toSet()
        return before.filterNot { it in after }
    }

    override suspend fun deleteStep(id: String): List<String> {
        val s = steps.value[id] ?: return emptyList()
        steps.value = steps.value - id
        return s.photos.map { it.fileName }
    }
}

class RecordingFiles : PhotoFileStore {
    val deleted = mutableListOf<String>()
    override suspend fun delete(fileNames: Collection<String>) { deleted += fileNames }
}

class FixedClock(var millis: Long = 1_000L, var day: LocalDate = LocalDate.of(2026, 11, 8)) : Clock {
    override fun nowMillis() = millis
    override fun today() = day
}

class SeqIds : IdGenerator {
    private var n = 0
    override fun newId() = "id-${++n}"
}
