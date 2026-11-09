package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.lutrinh.app.domain.usecase.DisplayFormat
import java.time.LocalDate

class DisplayFormatTest {
    @Test
    fun km_usesTheVietnameseDecimalComma() {
        assertEquals("9,3 km", DisplayFormat.km(9.31))
        assertEquals("0,4 km", DisplayFormat.km(0.42))
        assertEquals("51 km", DisplayFormat.km(51.4))
        assertEquals("1.234 km", DisplayFormat.km(1234.2))
    }

    @Test
    fun km_neverShowsTenPointZero() {
        assertEquals("10 km", DisplayFormat.km(9.96))
        assertEquals("9,9 km", DisplayFormat.km(9.94))
    }

    @Test
    fun km_wholeNumberForShareStatsGroupsThousands() {
        assertEquals("22", DisplayFormat.kmNumber(22L))
        assertEquals("1.234", DisplayFormat.kmNumber(1234L))
    }

    @Test
    fun duration_isWordsNotAClock() {
        assertEquals("35 phút", DisplayFormat.duration(35 * 60_000L))
        assertEquals("6 giờ 45 phút", DisplayFormat.duration((6 * 60 + 45) * 60_000L))
        assertEquals("1 giờ", DisplayFormat.duration(60 * 60_000L))
        assertEquals("2 giờ 05 phút", DisplayFormat.duration(125 * 60_000L))
        assertEquals("0 phút", DisplayFormat.duration(-5))
    }

    @Test
    fun clock_isForTheLiveTimerOnly() {
        assertEquals("0:45", DisplayFormat.clock(45_000))
        assertEquals("6:45:00", DisplayFormat.clock((6 * 3600 + 45 * 60) * 1000L))
    }

    @Test
    fun day_isWeekdayAndDayMonthWithTwoDigits() {
        assertEquals("Thứ Hai, 20/10", DisplayFormat.day(LocalDate.of(2025, 10, 20)))
        assertEquals("Chủ Nhật, 05/10", DisplayFormat.day(LocalDate.of(2025, 10, 5)))
        assertEquals("Thứ Hai, 20/10/2025", DisplayFormat.dayWithYear(LocalDate.of(2025, 10, 20)))
    }

    @Test
    fun defaultStepTitle_isTheArrivalTime() {
        assertEquals("Điểm dừng 09:05", DisplayFormat.defaultStepTitle(java.time.LocalDateTime.of(2026, 10, 20, 9, 5, 59)))
    }
}
