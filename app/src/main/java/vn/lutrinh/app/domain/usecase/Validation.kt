package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.PhotoSource
import java.time.LocalDate
import java.time.LocalDateTime

/** Limits shared by every platform (docs/04-business-rules.md). */
object Limits {
    const val TRIP_TITLE_MAX = 60
    const val TRIP_DESCRIPTION_MAX = 1000
    const val STEP_TITLE_MAX = 80
    const val STEP_NOTE_MAX = 2000
    /** R41: a leg note, in characters (Unicode code points, as the route package counts them). */
    const val LEG_NOTE_MAX = 1000
    const val PHOTOS_PER_STEP_MAX = 30
}

/** R41: the leg note's text rules, shared by the leg sheet and the route import. */
object LegNotes {
    /** Trimmed text, or null when nothing is left (= no note). */
    fun normalise(text: String): String? = text.trim().ifEmpty { null }

    /** Length in characters as the limit counts them (code points). */
    fun length(text: String): Int = text.codePointCount(0, text.length)

    fun fits(text: String): Boolean = length(text) <= Limits.LEG_NOTE_MAX
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
    /** Photo/video file names in display order (R27: as the user arranged them); the first one is the step cover. */
    val photoFiles: List<String>,
    /** EXIF capture time of newly imported files (R17); files not in the map keep what they had. */
    val photoTakenAt: Map<String, LocalDateTime> = emptyMap(),
    /** Origin of newly imported files (R19 de-duplication); files not in the map keep what they had. */
    val photoSources: Map<String, PhotoSource> = emptyMap(),
    /** R20: true = "Lưu nháp" (validated like any step), false = "Lưu" (published). */
    val draft: Boolean = false,
    /** R29: newly imported files that are videos -> their length in ms; files not in the map keep what they had (a new one is a photo). */
    val videoDurations: Map<String, Long> = emptyMap(),
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

/** Start and optional end date of a trip. */
data class TripDates(val start: LocalDate, val end: LocalDate?)

/** Which sentence the "Kéo dài chuyến đi?" dialog uses (R17). */
enum class ExtendCase {
    /** Closed trip, the step is before its start: the start moves back. */
    BEFORE_START,

    /** Closed trip, the step is after its end: the end moves forward. */
    AFTER_END,

    /** Trip without an end date, the step is before its start: the start moves back (never "đến"). */
    OPEN_ENDED,
}

/** R17: how far a trip must grow to hold a step suggested from a recorded track. */
object TripExtension {
    fun caseOf(current: TripDates, widened: TripDates): ExtendCase = when {
        widened.start.isBefore(current.start) -> if (current.end == null) ExtendCase.OPEN_ENDED else ExtendCase.BEFORE_START
        else -> ExtendCase.AFTER_END
    }


    /**
     * The smallest dates that contain [day]: the start moves back to it, or the end moves forward
     * to it. Null when [day] already lies inside. A trip without an end date (ongoing) never gets one.
     */
    fun widen(start: LocalDate, end: LocalDate?, day: LocalDate): TripDates? = when {
        day.isBefore(start) -> TripDates(day, end)
        end != null && day.isAfter(end) -> TripDates(start, day)
        else -> null
    }
}
