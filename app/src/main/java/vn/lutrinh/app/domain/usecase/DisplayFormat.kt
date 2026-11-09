package vn.lutrinh.app.domain.usecase

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The only place that turns km, durations and in-trip days into text (docs/03 "Quy ước hiển thị").
 * Pure Kotlin, mirrored in Swift (docs/07). Vietnamese decimal comma and "." thousands separator.
 */
object DisplayFormat {
    val VI: Locale = Locale.forLanguageTag("vi-VN")

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", VI)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", VI)
    private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI)

    /**
     * Distance without unit: one decimal under 10 ("9,3"), whole above ("51", "1.234"). A value
     * that rounds up to 10 ("9,96") is shown whole, never "10,0".
     */
    fun kmNumber(km: Double): String {
        val k = km.coerceAtLeast(0.0)
        return if (Math.round(k * 10) / 10.0 < 10.0) String.format(VI, "%.1f", k) else kmNumber(k.roundToLong())
    }

    /** Whole kilometres (already rounded, e.g. R18 share stats): "22", "1.234". */
    fun kmNumber(km: Long): String = String.format(VI, "%,d", km)

    /** "9,3 km". */
    fun km(km: Double): String = "${kmNumber(km)} km"

    /** Recorded track distance: "850 m" under 1 km, "2,4 km" under 100 km, "125 km" above. */
    fun trackDistance(m: Double): String = when {
        m < 1000 -> "${m.roundToLong()} m"
        m < 100_000 -> String.format(VI, "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToLong()} km"
    }

    /** "35 phút" under an hour, "1 giờ" / "6 giờ 45 phút" above (whole minutes, rounded down). */
    fun duration(ms: Long): String {
        val min = ms.coerceAtLeast(0) / 60_000
        val h = min / 60
        val m = min % 60
        return when {
            h == 0L -> "$m phút"
            m == 0L -> "$h giờ"
            else -> "$h giờ ${m.toString().padStart(2, '0')} phút"
        }
    }

    /** Running timer of a live recording: "0:45" (m:ss) under an hour, "2:05:09" (h:mm:ss) above. */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** A day inside a trip: "Thứ Hai, 20/10". */
    fun day(d: LocalDate): String = d.format(DAY).replaceFirstChar { it.titlecase(VI) }

    /** A day on the shared page, which leaves the app: "Thứ Hai, 20/10/2025". */
    fun dayWithYear(d: LocalDate): String = d.format(DAY_YEAR).replaceFirstChar { it.titlecase(VI) }

    /** Name of a step made without one (from a stop suggestion or from photos): "Điểm dừng 09:05" of its arrival. */
    fun defaultStepTitle(arrival: LocalDateTime): String = "Điểm dừng ${arrival.format(TIME)}"
}
