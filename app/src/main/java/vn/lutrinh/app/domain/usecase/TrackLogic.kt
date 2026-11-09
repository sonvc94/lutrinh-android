package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.LocationFix
import vn.lutrinh.app.domain.model.TrackPoint
import java.util.Locale
import kotlin.math.roundToLong

/** Sampling and filter constants of a recording (R16, docs/04-business-rules.md). */
object TrackRules {
    /** Requested interval between fixes. */
    const val SAMPLE_INTERVAL_MS = 5_000L

    /** Requested minimum movement between fixes; closer fixes are also dropped by [TrackFilter]. */
    const val MIN_DISTANCE_M = 10.0

    /** Fixes whose accuracy radius is larger than this are dropped. */
    const val MAX_ACCURACY_M = 50.0

    /** Faster than this (≈ 300 km/h) from the previous point of the segment = an impossible jump. */
    const val MAX_SPEED_MPS = 85.0

    /** A fix taken this long before its segment started is a stale cached fix. */
    const val STALE_TOLERANCE_MS = 2_000L

    /** Douglas–Peucker tolerance for drawing a track (≈ 5.5 m): keeps the bends of mountain roads. */
    const val DISPLAY_EPSILON_DEG = 5e-5
}

/** Why a fix was kept or dropped. */
enum class FixVerdict {
    ACCEPT, INVALID, INACCURATE, STALE, TOO_CLOSE, JUMP,

    /** Not recording (paused, stopped): the fix is not looked at. */
    IGNORED,
}

/**
 * R16 filter. [last] = the last stored point of the track, [segment] / [segmentStart] = the open
 * segment. Jump and distance checks only compare with a point of the same segment: after a pause
 * the traveller may have moved anywhere.
 */
object TrackFilter {
    fun check(fix: LocationFix, last: TrackPoint?, segment: Int, segmentStart: Long): FixVerdict {
        if (!fix.location.isValid) return FixVerdict.INVALID
        val acc = fix.accuracyM
        if (acc != null && (acc.isNaN() || acc > TrackRules.MAX_ACCURACY_M)) return FixVerdict.INACCURATE
        if (fix.time < segmentStart - TrackRules.STALE_TOLERANCE_MS) return FixVerdict.STALE
        if (last == null || last.segment != segment) return FixVerdict.ACCEPT
        val dtMs = fix.time - last.recordedAt
        if (dtMs <= 0) return FixVerdict.STALE
        val d = TrackMath.distanceM(last.location, fix.location)
        if (d < TrackRules.MIN_DISTANCE_M) return FixVerdict.TOO_CLOSE
        if (d / (dtMs / 1000.0) > TrackRules.MAX_SPEED_MPS) return FixVerdict.JUMP
        return FixVerdict.ACCEPT
    }
}

object TrackMath {
    fun distanceM(a: GeoPoint, b: GeoPoint): Double = Geo.distanceKm(a, b) * 1000

    /** Track length: sum over consecutive points of the same segment (a pause never adds distance). */
    fun lengthM(points: List<TrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> if (a.segment == b.segment) distanceM(a.location, b.location) else 0.0 }

    /**
     * Lines to draw: one per segment (points in stored order), each simplified with
     * [TrackRules.DISPLAY_EPSILON_DEG]. Segments with fewer than 2 points draw nothing.
     */
    fun displayLines(points: List<TrackPoint>, epsilonDeg: Double = TrackRules.DISPLAY_EPSILON_DEG): List<List<GeoPoint>> {
        val lines = mutableListOf<List<GeoPoint>>()
        var current = mutableListOf<GeoPoint>()
        var seg: Pair<String, Int>? = null
        for (p in points) {
            val key = p.trackId to p.segment
            if (key != seg) {
                if (current.size >= 2) lines += Polyline.simplify(current, epsilonDeg)
                current = mutableListOf()
                seg = key
            }
            current += p.location
        }
        if (current.size >= 2) lines += Polyline.simplify(current, epsilonDeg)
        return lines
    }
}

/** Display formats of a track (docs/03-screens.md "Ghi hành trình"); Vietnamese decimal comma. */
object TrackFormat {
    private val VI = Locale.forLanguageTag("vi-VN")

    /** "850 m" under 1 km, "2,4 km" under 100 km, "125 km" above. */
    fun distance(m: Double): String = when {
        m < 1000 -> "${m.roundToLong()} m"
        m < 100_000 -> String.format(VI, "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToLong()} km"
    }

    /** "0:45" (m:ss) under an hour, "2:05:09" (h:mm:ss) above. */
    fun duration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }
}
