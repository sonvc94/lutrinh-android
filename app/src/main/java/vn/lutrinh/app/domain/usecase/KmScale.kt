package vn.lutrinh.app.domain.usecase

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The km readout's scale bar (docs/08 §5 "Stats"): a cartographic scale bar whose whole length is
 * the trip's [totalKm]. It is cut into alternating ink / paper blocks every [step] km, a round
 * 1 · 2 · 5 × 10ⁿ length that gives at most [MAX_BLOCKS] blocks; [ticks] are the block edges that
 * get a label (0, step, 2·step, … ≤ total). The end of the bar is not labelled: the total is the
 * big number above it. Pure Kotlin, mirrored in Swift (docs/07).
 */
data class KmScale(val totalKm: Double, val step: Double, val ticks: List<Double>) {
    /** Where [km] sits along the bar, 0..1. */
    fun fraction(km: Double): Double = (km / totalKm).coerceIn(0.0, 1.0)

    /** Label of a tick: "0", "50", "1.000"; a step under 1 km keeps one decimal ("0,5"). */
    fun label(km: Double): String =
        if (step >= 1.0) DisplayFormat.kmNumber(km.roundToLong()) else DisplayFormat.kmNumber(km)

    companion object {
        const val MAX_BLOCKS = 5

        /** The scale for [totalKm], or null when there is no distance to draw (0 km, no steps). */
        fun of(totalKm: Double): KmScale? {
            if (!(totalKm > 0.0) || totalKm.isInfinite()) return null
            val min = totalKm / MAX_BLOCKS
            val mag = 10.0.pow(floor(log10(min)))
            val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * mag }.first { it >= min * (1 - 1e-9) }
            val count = floor(totalKm / step + 1e-9).toInt()
            val ticks = (0..count).map { i -> round(i * step) }
            return KmScale(totalKm, step, ticks)
        }

        /** Strips float noise (0.30000000000000004 -> 0.3). */
        private fun round(v: Double) = Math.round(v * 1e6) / 1e6
    }
}
