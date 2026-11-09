package vn.lutrinh.app.domain.model

/*
 * Recorded GPS tracks ("Ghi hành trình", R16). Pure Kotlin, mirrored 1:1 in Swift
 * (docs/02-data-model.md "Track", docs/07-ios-port.md).
 */

/** Lifecycle of a track. [code] is the stored value; never rename a code. */
enum class TrackStatus(val code: String) {
    /** GPS is on, accepted fixes are appended to the open segment. */
    RECORDING("recording"),

    /** The user paused: no fixes are taken and the pause does not count in the duration. */
    PAUSED("paused"),

    /** Stopped by the user, or closed after the app process died while recording (R16). */
    ENDED("ended"),
    ;

    companion object {
        /** Unknown codes read as ended: an unknown state must never look like a live recording. */
        fun fromCode(code: String?): TrackStatus = entries.firstOrNull { it.code == code } ?: ENDED
    }
}

/**
 * One recording session of a trip. Times are epoch milliseconds (UTC instants).
 * Duration excludes pauses: [activeMs] is the time of every closed segment; while
 * [status] is RECORDING the open segment runs from [resumedAt] to now.
 */
data class Track(
    val id: String,
    val tripId: String,
    val status: TrackStatus,
    val startedAt: Long,
    /** When the user stopped (or the last saved fix, after a process death); null while open. */
    val endedAt: Long?,
    /** Recording time of the closed segments, pauses excluded. */
    val activeMs: Long,
    /** Start of the open segment while RECORDING; null when paused or ended. */
    val resumedAt: Long?,
    /** Index of the current (last) segment; 0 for the first, +1 on every resume. */
    val segment: Int,
    /** Sum of the distances between consecutive points of the same segment, metres. */
    val distanceM: Double,
    val pointCount: Int,
    /** True when the track was closed because the app died while recording (R16). */
    val interrupted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isOpen: Boolean get() = status != TrackStatus.ENDED

    /** Recording time up to [now], pauses excluded. */
    fun durationMs(now: Long): Long = activeMs + (resumedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

/** A stored GPS point of a track (already filtered, never simplified). */
data class TrackPoint(
    val trackId: String,
    val segment: Int,
    /** Epoch milliseconds when the fix was taken. */
    val recordedAt: Long,
    val location: GeoPoint,
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    val speedMps: Float? = null,
)

/** A raw fix from the platform location service, before the filter (R16). */
data class LocationFix(
    val location: GeoPoint,
    /** Epoch milliseconds when the fix was taken. */
    val time: Long,
    /** Horizontal accuracy radius (68 %), metres; null when the platform gives none. */
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    val speedMps: Float? = null,
)
