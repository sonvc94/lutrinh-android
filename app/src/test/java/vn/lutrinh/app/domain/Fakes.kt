package vn.lutrinh.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.PhotoFileStore
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TrackRepository
import vn.lutrinh.app.domain.repository.TripRepository
import vn.lutrinh.app.domain.usecase.TripOrder
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.TripCover
import java.time.LocalDate

/** In-memory storage with the same contract as the Room implementation (used for use-case tests). */
class FakeStore : TripRepository, StepRepository {
    val trips = MutableStateFlow<Map<String, Trip>>(emptyMap())
    val steps = MutableStateFlow<Map<String, Step>>(emptyMap())

    override fun observeTripSummaries(): Flow<List<TripSummary>> = trips.map { all ->
        all.values.sortedWith(TripOrder).map { t ->
            val s = steps.value.values.filter { it.tripId == t.id }.inTimelineOrder()
            val (drafts, pub) = s.partition { it.isDraft }
            TripSummary(
                t, pub.size, pub.sumOf { it.photos.size }, TripCover.resolve(t.coverPhotoId, s)?.fileName,
                drafts.size, drafts.sumOf { it.photos.size }, pub.sumOf { s -> s.photos.count { it.isVideo } },
            )
        }
    }

    override fun observeTrip(id: String) = trips.map { it[id] }
    override suspend fun getTrip(id: String) = trips.value[id]
    override suspend fun upsertTrip(trip: Trip) { trips.value = trips.value + (trip.id to trip) }

    override suspend fun setCover(tripId: String, photoId: String?, now: Long): Boolean {
        val t = trips.value[tripId] ?: return false
        trips.value = trips.value + (tripId to t.copy(coverPhotoId = photoId, updatedAt = now))
        return true
    }

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

    override suspend fun saveStepAndTrip(step: Step, trip: Trip): List<String> {
        upsertTrip(trip)
        return saveStep(step)
    }

    override suspend fun deleteStep(id: String): List<String> {
        val s = steps.value[id] ?: return emptyList()
        steps.value = steps.value - id
        return s.photos.map { it.fileName }
    }

    override fun observeAllSteps() = steps.map { all ->
        all.values.groupBy { it.tripId }.toSortedMap().values.flatMap { it.inTimelineOrder() }
    }

    override suspend fun setArrivalTransport(stepId: String, mode: TransportMode?, now: Long): Boolean {
        val s = steps.value[stepId] ?: return false
        steps.value = steps.value + (stepId to s.copy(arrivalTransport = mode, updatedAt = now))
        return true
    }

    override suspend fun setDraft(stepId: String, draft: Boolean, now: Long): Boolean {
        val s = steps.value[stepId] ?: return false
        steps.value = steps.value + (stepId to s.copy(isDraft = draft, updatedAt = now))
        return true
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

/** In-memory [TrackRepository] with the Room contract (ordering, delete of points). */
class FakeTrackStore : TrackRepository {
    val tracks = MutableStateFlow<Map<String, Track>>(emptyMap())
    val points = MutableStateFlow<List<TrackPoint>>(emptyList())

    override fun observeTracks(tripId: String) =
        tracks.map { all -> all.values.filter { it.tripId == tripId }.sortedByDescending { it.startedAt } }
    override fun observePoints(tripId: String) = points.map { all ->
        all.filter { tracks.value[it.trackId]?.tripId == tripId }
    }
    override fun observeAllPoints(): Flow<List<TrackPoint>> = points
    override suspend fun getTrack(id: String) = tracks.value[id]
    override suspend fun openTracks() = tracks.value.values.filter { it.isOpen }
    override suspend fun insertTrack(track: Track) { tracks.value = tracks.value + (track.id to track) }
    override suspend fun updateTrack(track: Track) {
        if (track.id in tracks.value) tracks.value = tracks.value + (track.id to track)
    }
    override suspend fun addPoint(track: Track, point: TrackPoint) {
        check(track.id in tracks.value) { "FOREIGN KEY constraint failed" }
        points.value = points.value + point
        tracks.value = tracks.value + (track.id to track)
    }
    override suspend fun deleteTrack(id: String): Boolean {
        points.value = points.value.filterNot { it.trackId == id }
        return (id in tracks.value).also { tracks.value = tracks.value - id }
    }
}
