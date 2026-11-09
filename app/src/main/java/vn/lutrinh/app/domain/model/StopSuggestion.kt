package vn.lutrinh.app.domain.model

/*
 * Stop suggestions from recorded tracks ("Gợi ý điểm dừng", R17). Pure Kotlin, mirrored 1:1 in
 * Swift (docs/02-data-model.md "Stop suggestions", docs/07-ios-port.md).
 */

/**
 * A place where a recorded track stayed (R17). Derived from the track's points, never stored;
 * identified by ([trackId], [startAt]). Times are epoch milliseconds.
 */
data class Dwell(
    val trackId: String,
    /** Time of the first point of the stay. */
    val startAt: Long,
    /** Time of the last point of the stay. */
    val endAt: Long,
    /** Mean position of the points of the stay. */
    val center: GeoPoint,
    /** Number of track points in the stay (≥ 1). */
    val pointCount: Int,
) {
    val durationMs: Long get() = endAt - startAt
}

/** A dwell shown to the user: not matching a step, not dismissed or already added (R17). */
data class StopSuggestion(
    val dwell: Dwell,
    /** Photos of the trip whose [Photo.takenAt] lies inside the dwell's window (minute precision). */
    val photoCount: Int,
) {
    /** Stable key for lists and map features. */
    val key: String get() = "${dwell.trackId}@${dwell.startAt}"
}

/** What the user did with a suggestion. [code] is the stored value; never rename a code. */
enum class StopDecisionKind(val code: String) {
    /** "Bỏ qua": hidden for good. */
    DISMISSED("dismissed"),

    /** "Thêm điểm dừng" saved a step from it: hidden while that step exists. */
    ADDED("added"),
    ;

    companion object {
        /** Unknown codes read as dismissed: a decision the app cannot read still hides the suggestion. */
        fun fromCode(code: String?): StopDecisionKind = entries.firstOrNull { it.code == code } ?: DISMISSED
    }
}

/**
 * The user's answer to one suggestion (stored, DB version 4). Matched to a recomputed dwell of
 * the same track whose window overlaps [startAt]..[endAt] (R17).
 */
data class StopDecision(
    val trackId: String,
    val startAt: Long,
    val endAt: Long,
    val kind: StopDecisionKind,
    /** The step created from the suggestion (ADDED only). */
    val stepId: String?,
    val decidedAt: Long,
)
