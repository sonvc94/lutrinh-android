package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.PhotoFileStore
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TrackRepository
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
            // Not part of the trip fields: the cover is set with SetTripCover (R21).
            coverPhotoId = existing?.coverPhotoId,
        )
        trips.upsertTrip(trip)
        return SaveResult.Saved(trip.id)
    }
}

/** R7: deleting a trip removes its steps, photo rows and photo files. */
class DeleteTrip(
    private val trips: TripRepository,
    private val files: PhotoFileStore,
    /** A recording of the trip is stopped first; its tracks go with the trip (R7, R16). */
    private val recorder: TrackRecorder? = null,
) {
    suspend operator fun invoke(tripId: String) {
        recorder?.stopIfTrip(tripId)
        val orphaned = trips.deleteTrip(tripId)
        files.delete(orphaned)
    }
}

/**
 * Creates a step when [stepId] is null, otherwise updates it; replaces its photo list (R5, R8).
 * With [extendTrip] (a step from a stop suggestion, R17) an arrival outside the trip dates widens
 * the trip by as many days as needed instead of failing R5; trip and step are stored together.
 */
class SaveStep(
    private val trips: TripRepository,
    private val steps: StepRepository,
    private val files: PhotoFileStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(tripId: String, stepId: String?,
        input: StepInput,
        extendTrip: Boolean = false,
    ): SaveResult<StepError> {
        val stored: Trip = trips.getTrip(tripId) ?: return SaveResult.Invalid(setOf(StepError.TRIP_NOT_FOUND))
        val widened = if (extendTrip) {
            TripExtension.widen(stored.startDate, stored.endDate, input.arrivedAt.toLocalDate())
        } else null
        val trip = if (widened == null) stored else stored.copy(startDate = widened.start, endDate = widened.end)
        val errors = StepValidator.validate(input, trip.startDate, trip.endDate)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val existing = stepId?.let { steps.getStep(it) }
        val now = clock.nowMillis()
        val id = existing?.id ?: stepId ?: ids.newId()
        val known = existing?.photos.orEmpty().associateBy { it.fileName }
        val photos = input.photoFiles.distinct().mapIndexed { index, file ->
            known[file]?.copy(position = index)
                ?: Photo(
                    id = ids.newId(), stepId = id, fileName = file, position = index, createdAt = now,
                    takenAt = input.photoTakenAt[file],
                    sourceId = input.photoSources[file]?.id,
                    sourceSize = input.photoSources[file]?.sizeBytes,
                    mediaType = if (file in input.videoDurations) MediaType.VIDEO else MediaType.PHOTO,
                    durationMs = input.videoDurations[file],
                )
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
            // Not part of the step form: the leg transport is set from the timeline/map (R12).
            arrivalTransport = existing?.arrivalTransport,
            arrivalNote = existing?.arrivalNote,
            // R20: "Lưu nháp" stores a draft, "Lưu" a published step (a draft is published by it).
            isDraft = input.draft,
        )
        val removed = if (widened == null) steps.saveStep(step) else steps.saveStepAndTrip(step, trip.copy(updatedAt = now))
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

/** R12: sets (or clears with null) the transport of the leg that ends at [toStepId]. */
class SetLegTransport(private val steps: StepRepository, private val clock: Clock) {
    suspend operator fun invoke(toStepId: String, mode: TransportMode?): Boolean =
        steps.setArrivalTransport(toStepId, mode, clock.nowMillis())
}

/**
 * R41: sets the note of the leg that ends at [toStepId]; the text is trimmed and an empty one
 * clears the note. False when the step does not exist or the note is longer than
 * [Limits.LEG_NOTE_MAX] characters (nothing is stored then).
 */
class SetLegNote(private val steps: StepRepository, private val clock: Clock) {
    suspend operator fun invoke(toStepId: String, note: String): Boolean {
        val text = LegNotes.normalise(note)
        if (text != null && !LegNotes.fits(text)) return false
        return steps.setArrivalNote(toStepId, text, clock.nowMillis())
    }
}

/** R20 "Đăng": publishes a draft step. False when the step does not exist. */
class PublishStep(private val steps: StepRepository, private val clock: Clock) {
    suspend operator fun invoke(stepId: String): Boolean = steps.setDraft(stepId, false, clock.nowMillis())
}

/** R21: sets the trip cover to [photoId] (a photo of one of its steps), or null = "Tự động". */
class SetTripCover(private val trips: TripRepository, private val clock: Clock) {
    suspend operator fun invoke(tripId: String, photoId: String?): Boolean = trips.setCover(tripId, photoId, clock.nowMillis())
}

/** Deletes a recorded track and its points (R16). An open track must be stopped first. */
class DeleteTrack(private val tracks: TrackRepository, private val recorder: TrackRecorder) {
    suspend operator fun invoke(trackId: String): Boolean {
        if (recorder.active.value?.id == trackId) return false
        return tracks.deleteTrack(trackId)
    }
}
