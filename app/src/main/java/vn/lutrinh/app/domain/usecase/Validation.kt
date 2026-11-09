package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import java.time.LocalDate
import java.time.LocalDateTime

/** Limits shared by every platform (docs/04-business-rules.md). */
object Limits {
    const val TRIP_TITLE_MAX = 60
    const val TRIP_DESCRIPTION_MAX = 1000
    const val STEP_TITLE_MAX = 80
    const val STEP_NOTE_MAX = 2000
    const val PHOTOS_PER_STEP_MAX = 30
}

data class TripInput(
    val title: String,
    val description: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
)

enum class TripError {
    TITLE_BLANK,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    END_BEFORE_START,
    /** Editing dates would leave existing steps outside the trip. */
    STEPS_OUTSIDE_DATES,
}

data class StepInput(
    val title: String,
    val note: String,
    val location: GeoPoint?,
    val arrivedAt: LocalDateTime,
    /** Photo file names in display order; the first one is the step cover. */
    val photoFiles: List<String>,
    /** EXIF capture time of newly imported files (R17); files not in the map keep what they had. */
    val photoTakenAt: Map<String, LocalDateTime> = emptyMap(),
)

enum class StepError {
    TITLE_BLANK,
    TITLE_TOO_LONG,
    NOTE_TOO_LONG,
    LOCATION_MISSING,
    LOCATION_INVALID,
    BEFORE_TRIP_START,
    AFTER_TRIP_END,
    TOO_MANY_PHOTOS,
    TRIP_NOT_FOUND,
}

object TripValidator {
    /**
     * @param stepDates earliest..latest step date of the trip being edited, null when new or empty.
     */
    fun validate(input: TripInput, stepDates: ClosedRange<LocalDate>? = null): Set<TripError> {
        val errors = mutableSetOf<TripError>()
        val title = input.title.trim()
        if (title.isEmpty()) errors += TripError.TITLE_BLANK
        if (title.length > Limits.TRIP_TITLE_MAX) errors += TripError.TITLE_TOO_LONG
        if (input.description.trim().length > Limits.TRIP_DESCRIPTION_MAX) errors += TripError.DESCRIPTION_TOO_LONG
        val end = input.endDate
        if (end != null && end.isBefore(input.startDate)) errors += TripError.END_BEFORE_START
        if (stepDates != null) {
            val outside = stepDates.start.isBefore(input.startDate) ||
                (end != null && stepDates.endInclusive.isAfter(end))
            if (outside) errors += TripError.STEPS_OUTSIDE_DATES
        }
        return errors
    }
}

object StepValidator {
    fun validate(input: StepInput, tripStart: LocalDate, tripEnd: LocalDate?): Set<StepError> {
        val errors = mutableSetOf<StepError>()
        val title = input.title.trim()
        if (title.isEmpty()) errors += StepError.TITLE_BLANK
        if (title.length > Limits.STEP_TITLE_MAX) errors += StepError.TITLE_TOO_LONG
        if (input.note.trim().length > Limits.STEP_NOTE_MAX) errors += StepError.NOTE_TOO_LONG
        when {
            input.location == null -> errors += StepError.LOCATION_MISSING
            !input.location.isValid -> errors += StepError.LOCATION_INVALID
        }
        val day = input.arrivedAt.toLocalDate()
        if (day.isBefore(tripStart)) errors += StepError.BEFORE_TRIP_START
        if (tripEnd != null && day.isAfter(tripEnd)) errors += StepError.AFTER_TRIP_END
        if (input.photoFiles.size > Limits.PHOTOS_PER_STEP_MAX) errors += StepError.TOO_MANY_PHOTOS
        return errors
    }
}
