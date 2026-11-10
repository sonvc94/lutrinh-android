package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.lutrinh.app.domain.usecase.TripDays
import java.time.LocalDate

/** R37: "Ngày N/M" on the trip map. */
class TripDaysTest {
    private val start = LocalDate.of(2025, 6, 7)

    @Test fun dayNumberCountsFromOne() {
        assertEquals(1, TripDays.dayOf(start, start))
        assertEquals(3, TripDays.dayOf(start, LocalDate.of(2025, 6, 9)))
        assertEquals(1, TripDays.dayOf(start, LocalDate.of(2025, 6, 1)))
    }

    @Test fun totalIsTheTripsDays_orItsLatestStepWhenOpenEnded() {
        val steps = listOf(start, LocalDate.of(2025, 6, 10))
        assertEquals(6, TripDays.total(start, LocalDate.of(2025, 6, 12), steps))
        assertEquals(4, TripDays.total(start, null, steps))
        assertEquals(1, TripDays.total(start, null, emptyList()))
        // Never fewer than a step's day.
        assertEquals(4, TripDays.total(start, LocalDate.of(2025, 6, 8), steps))
    }
}
