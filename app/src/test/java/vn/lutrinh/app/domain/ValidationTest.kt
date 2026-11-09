package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.domain.usecase.StepError
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.StepValidator
import vn.lutrinh.app.domain.usecase.TripDates
import vn.lutrinh.app.domain.usecase.TripError
import vn.lutrinh.app.domain.usecase.TripExtension
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.domain.usecase.TripValidator
import java.time.LocalDate
import java.time.LocalDateTime

class ValidationTest {
    private val d1 = LocalDate.of(2026, 10, 20)
    private val d4 = LocalDate.of(2026, 10, 23)

    private fun trip(title: String = "Hà Giang", start: LocalDate = d1, end: LocalDate? = d4, desc: String = "") =
        TripInput(title, desc, start, end)

    @Test fun validTripHasNoErrors() = assertEquals(emptySet<TripError>(), TripValidator.validate(trip()))

    @Test fun blankTitleIsRejectedAfterTrim() =
        assertEquals(setOf(TripError.TITLE_BLANK), TripValidator.validate(trip(title = "   ")))

    @Test fun titleLimitCountsTrimmedCharacters() {
        assertEquals(emptySet<TripError>(), TripValidator.validate(trip(title = " " + "a".repeat(Limits.TRIP_TITLE_MAX) + " ")))
        assertEquals(setOf(TripError.TITLE_TOO_LONG), TripValidator.validate(trip(title = "a".repeat(Limits.TRIP_TITLE_MAX + 1))))
    }

    @Test fun descriptionLimit() = assertEquals(
        setOf(TripError.DESCRIPTION_TOO_LONG),
        TripValidator.validate(trip(desc = "x".repeat(Limits.TRIP_DESCRIPTION_MAX + 1))),
    )

    @Test fun endBeforeStartIsRejected_sameDayAndOpenEndAreFine() {
        assertEquals(setOf(TripError.END_BEFORE_START), TripValidator.validate(trip(end = d1.minusDays(1))))
        assertEquals(emptySet<TripError>(), TripValidator.validate(trip(end = d1)))
        assertEquals(emptySet<TripError>(), TripValidator.validate(trip(end = null)))
    }

    @Test fun newDatesMustStillContainExistingSteps() {
        val steps = d1.plusDays(1)..d4
        assertEquals(emptySet<TripError>(), TripValidator.validate(trip(), steps))
        assertEquals(setOf(TripError.STEPS_OUTSIDE_DATES), TripValidator.validate(trip(start = d1.plusDays(2)), steps))
        assertEquals(setOf(TripError.STEPS_OUTSIDE_DATES), TripValidator.validate(trip(end = d4.minusDays(1)), steps))
        assertEquals(emptySet<TripError>(), TripValidator.validate(trip(end = null), steps))
    }

    private fun step(
        title: String = "Đồng Văn",
        at: LocalDateTime = LocalDateTime.of(2026, 10, 21, 9, 0),
        loc: GeoPoint? = GeoPoint(23.2786, 105.3621),
        photos: Int = 0,
        note: String = "",
    ) = StepInput(title, note, loc, at, List(photos) { "p$it.jpg" })

    @Test fun validStep() = assertEquals(emptySet<StepError>(), StepValidator.validate(step(), d1, d4))

    @Test fun stepNeedsTitleAndLocation() {
        val e = StepValidator.validate(step(title = " ", loc = null), d1, d4)
        assertEquals(setOf(StepError.TITLE_BLANK, StepError.LOCATION_MISSING), e)
    }

    @Test fun stepRejectsOutOfRangeCoordinates() {
        assertEquals(setOf(StepError.LOCATION_INVALID), StepValidator.validate(step(loc = GeoPoint(91.0, 0.0)), d1, d4))
        assertEquals(setOf(StepError.LOCATION_INVALID), StepValidator.validate(step(loc = GeoPoint(0.0, 181.0)), d1, d4))
        assertEquals(setOf(StepError.LOCATION_INVALID), StepValidator.validate(step(loc = GeoPoint(Double.NaN, 0.0)), d1, d4))
    }

    @Test fun stepDateMustBeInsideTripDatesInclusive() {
        assertEquals(emptySet<StepError>(), StepValidator.validate(step(at = d1.atStartOfDay()), d1, d4))
        assertEquals(emptySet<StepError>(), StepValidator.validate(step(at = d4.atTime(23, 59)), d1, d4))
        assertEquals(setOf(StepError.BEFORE_TRIP_START), StepValidator.validate(step(at = d1.minusDays(1).atTime(23, 59)), d1, d4))
        assertEquals(setOf(StepError.AFTER_TRIP_END), StepValidator.validate(step(at = d4.plusDays(1).atStartOfDay()), d1, d4))
        assertTrue(StepValidator.validate(step(at = d4.plusYears(1).atStartOfDay()), d1, null).isEmpty())
    }

    @Test fun stepLimits() {
        assertEquals(setOf(StepError.TOO_MANY_PHOTOS), StepValidator.validate(step(photos = Limits.PHOTOS_PER_STEP_MAX + 1), d1, d4))
        assertEquals(setOf(StepError.NOTE_TOO_LONG), StepValidator.validate(step(note = "n".repeat(Limits.STEP_NOTE_MAX + 1)), d1, d4))
        assertEquals(setOf(StepError.TITLE_TOO_LONG), StepValidator.validate(step(title = "t".repeat(Limits.STEP_TITLE_MAX + 1)), d1, d4))
    }

    @Test fun extensionMovesStartBackWhenDayIsBeforeIt() =
        assertEquals(TripDates(d1.minusDays(9), d4), TripExtension.widen(d1, d4, d1.minusDays(9)))

    @Test fun extensionMovesEndForwardWhenDayIsAfterIt() =
        assertEquals(TripDates(d1, d4.plusDays(30)), TripExtension.widen(d1, d4, d4.plusDays(30)))

    @Test fun extensionIsNullInsideTheDatesInclusive() {
        assertNull(TripExtension.widen(d1, d4, d1))
        assertNull(TripExtension.widen(d1, d4, d4))
        assertNull(TripExtension.widen(d1, d4, d1.plusDays(1)))
        assertNull(TripExtension.widen(d1, d1, d1))
    }

    @Test fun ongoingTripOnlyWidensItsStart() {
        assertEquals(TripDates(d1.minusDays(1), null), TripExtension.widen(d1, null, d1.minusDays(1)))
        assertNull(TripExtension.widen(d1, null, d4.plusYears(5)))
    }
}
