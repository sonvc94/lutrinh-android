package vn.lutrinh.app.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** "Ngày N/M" (R37, trip map): day N of the trip out of M. Pure. */
object TripDays {
    /** Day number of [date] in a trip starting [start] (the start day = 1; never below 1). */
    fun dayOf(start: LocalDate, date: LocalDate): Int = (ChronoUnit.DAYS.between(start, date) + 1).toInt().coerceAtLeast(1)

    /**
     * Days of the trip: start to end inclusive; an open-ended trip ([end] null) counts to its
     * latest step ([stepDates]). Never fewer than the day of any step.
     */
    fun total(start: LocalDate, end: LocalDate?, stepDates: List<LocalDate>): Int {
        val last = stepDates.maxOfOrNull { dayOf(start, it) } ?: 1
        return maxOf(end?.let { dayOf(start, it) } ?: last, last)
    }
}
