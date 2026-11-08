package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.PhotoFileStore
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TripRepository

sealed interface SaveResult<out E> {
    data class Saved(val id: String) : SaveResult<Nothing>
    data class Invalid<E>(val errors: Set<E>) : SaveResult<E>
}

/** Creates a trip when [tripId] is null, otherwise updates it (R4, R5). */
class SaveTrip(
    private val trips: TripRepository,
    private val steps: StepRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(tripId: String?, input: TripInput): SaveResult<TripError> {
        val existing = tripId?.let { trips.getTrip(it) }
        val stepDates = existing?.let { steps.stepDateRange(it.id) }
        val errors = TripValidator.validate(input, stepDates)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val now = clock.nowMillis()
        val trip = Trip(
            id = existing?.id ?: tripId ?: ids.newId(),
            title = input.title.trim(),
            description = input.description.trim(),
            startDate = input.startDate,
            endDate = input.endDate,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        trips.upsertTrip(trip)
        return SaveResult.Saved(trip.id)
    }
}

/** R7: deleting a trip removes its steps, photo rows and photo files. */
class DeleteTrip(private val trips: TripRepository, private val files: PhotoFileStore) {
    suspend operator fun invoke(tripId: String) {
        val orphaned = trips.deleteTrip(tripId)
        files.delete(orphaned)
    }
}

/** Creates a step when [stepId] is null, otherwise updates it; replaces its photo list (R6, R8). */
class SaveStep(
    private val trips: TripRepository,
    private val steps: StepRepository,
    private val files: PhotoFileStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(tripId: String, stepId: String?, input: StepInput): SaveResult<StepError> {
        val trip: Trip = trips.getTrip(tripId) ?: return SaveResult.Invalid(setOf(StepError.TRIP_NOT_FOUND))
        val errors = StepValidator.validate(input, trip.startDate, trip.endDate)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val existing = stepId?.let { steps.getStep(it) }
        val now = clock.nowMillis()
        val id = existing?.id ?: stepId ?: ids.newId()
        val known = existing?.photos.orEmpty().associateBy { it.fileName }
        val photos = input.photoFiles.distinct().mapIndexed { index, file ->
            known[file]?.copy(position = index)
                ?: Photo(id = ids.newId(), stepId = id, fileName = file, position = index, createdAt = now)
        }
        val step = Step(
            id = id,
            tripId = trip.id,
            title = input.title.trim(),
            note = input.note.trim(),
            location = input.location!!,
            arrivedAt = input.arrivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            photos = photos,
        )
        val removed = steps.saveStep(step)
        files.delete(removed)
        return SaveResult.Saved(id)
    }
}

/** R7: deleting a step removes its photo rows and files; other steps are untouched. */
class DeleteStep(private val steps: StepRepository, private val files: PhotoFileStore) {
    suspend operator fun invoke(stepId: String) {
        files.delete(steps.deleteStep(stepId))
    }
}
