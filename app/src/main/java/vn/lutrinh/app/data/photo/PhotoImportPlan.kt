package vn.lutrinh.app.data.photo

import kotlin.math.roundToInt

/** Width x height in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    val longEdge: Int get() = maxOf(width, height)
}

/**
 * How to turn the stored pixels upright: rotate clockwise by [rotation] degrees, THEN mirror
 * horizontally when [mirror] is set (same order as `Matrix.postRotate` then `postScale(-1, 1)`).
 */
data class Orientation(val rotation: Int, val mirror: Boolean) {
    val isIdentity: Boolean get() = rotation == 0 && !mirror
    val swapsAxes: Boolean get() = rotation % 180 != 0

    companion object {
        val NORMAL = Orientation(0, false)

        /** EXIF `Orientation` tag value (1..8) -> transform. Unknown/undefined -> [NORMAL]. */
        fun fromExif(value: Int): Orientation = when (value) {
            2 -> Orientation(0, true) // flip horizontal
            3 -> Orientation(180, false)
            4 -> Orientation(180, true) // flip vertical
            5 -> Orientation(90, true) // transpose
            6 -> Orientation(90, false)
            7 -> Orientation(270, true) // transverse
            8 -> Orientation(270, false)
            else -> NORMAL
        }
    }
}

/**
 * Pure sizing/orientation rules for photo import (docs/06-photos.md "Import processing").
 * No Android types, so it is unit-tested on the JVM and ports 1:1 to Swift.
 */
object PhotoImportPlan {
    /** Long edge of the stored copy, in pixels. Smaller images keep their size. */
    const val MAX_LONG_EDGE = 2560

    /** JPEG quality used when an image has to be re-encoded. */
    const val JPEG_QUALITY = 85

    /** Size after downscaling (before rotation): aspect kept, long edge <= [maxLongEdge]. */
    fun scaledSize(source: PixelSize, maxLongEdge: Int = MAX_LONG_EDGE): PixelSize {
        if (source.longEdge <= maxLongEdge) return source
        val s = maxLongEdge.toDouble() / source.longEdge
        return PixelSize(
            (source.width * s).roundToInt().coerceAtLeast(1),
            (source.height * s).roundToInt().coerceAtLeast(1),
        )
    }

    /** Size of the stored file: downscaled, then width/height swapped for 90/270 rotations. */
    fun storedSize(source: PixelSize, orientation: Orientation, maxLongEdge: Int = MAX_LONG_EDGE): PixelSize {
        val s = scaledSize(source, maxLongEdge)
        return if (orientation.swapsAxes) PixelSize(s.height, s.width) else s
    }

    /**
     * Decoder subsample factor (power of two): the largest one that still decodes at least
     * [maxLongEdge] pixels on the long edge, so the final resize only ever shrinks.
     */
    fun sampleSize(source: PixelSize, maxLongEdge: Int = MAX_LONG_EDGE): Int {
        var s = 1
        while (source.longEdge / (s * 2) >= maxLongEdge) s *= 2
        return s
    }

    /**
     * True when the pixels must be decoded and written again: too large, not upright, or a
     * format whose location metadata cannot be removed in place ([canStripInPlace] = false).
     * Otherwise the original bytes are kept and only the GPS tags are removed.
     */
    fun needsReencode(
        source: PixelSize,
        orientation: Orientation,
        canStripInPlace: Boolean,
        maxLongEdge: Int = MAX_LONG_EDGE,
    ): Boolean = source.longEdge > maxLongEdge || !orientation.isIdentity || !canStripInPlace

    /**
     * Where the stored-image point ([x], [y]) (0..width, 0..height, y down) lands in the upright
     * image. Mirrors the Android matrix maths so tests can check the transform per EXIF value.
     */
    fun mapPoint(x: Double, y: Double, size: PixelSize, orientation: Orientation): Pair<Double, Double> {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        // Clockwise rotation in a y-down frame, translated back into positive coordinates.
        var (px, py) = when (orientation.rotation) {
            90 -> Pair(h - y, x)
            180 -> Pair(w - x, h - y)
            270 -> Pair(y, w - x)
            else -> Pair(x, y)
        }
        val uprightWidth = if (orientation.swapsAxes) h else w
        if (orientation.mirror) px = uprightWidth - px
        return Pair(px, py)
    }
}
