package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Dwell
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.StopSuggestion
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Thresholds of stop suggestions (R17, docs/04-business-rules.md). One object so the iOS port
 * reuses the same numbers.
 */
object StopRules {
    /** A stay = every point within this distance of the stay's first point. */
    const val RADIUS_M = 150.0

    /** Minimum time between the first and the last point of a stay. */
    const val MIN_DWELL_MS = 20 * 60_000L

    /** Two stays closer than [RADIUS_M] (centres) with at most this much time between them are one stop. */
    const val MERGE_MAX_GAP_MS = 10 * 60_000L

    /** A step within this distance of a stay's centre ... */
    const val STEP_MATCH_RADIUS_M = 300.0

    /** ... arriving between (stay start − this) and (stay end + this) is the same stop: no suggestion. */
    const val STEP_MATCH_TIME_MS = 60 * 60_000L
}

/** Finds stays in one track's points (R17). Pure; unit-tested in StopDetectionTest. */
object StopDetection {
    /**
     * Stays of one track. [points] are sorted by (segment, time) here; segments follow each other
     * in time, so a pause between segments counts as staying when the last point before it and the
     * first point after it are within [StopRules.RADIUS_M] (the recorder takes no fix while
     * paused, and drops fixes closer than 10 m, so a long time gap between two near points is a stay).
     */
    fun detect(points: List<TrackPoint>): List<Dwell> {
        if (points.isEmpty()) return emptyList()
        val pts = points.sortedWith(compareBy<TrackPoint>({ it.segment }, { it.recordedAt }))
        val stays = mutableListOf<List<TrackPoint>>()
        var i = 0
        while (i < pts.size) {
            val anchor = pts[i]
            var j = i + 1
            while (j < pts.size && TrackMath.distanceM(anchor.location, pts[j].location) <= StopRules.RADIUS_M) j++
            if (pts[j - 1].recordedAt - anchor.recordedAt >= StopRules.MIN_DWELL_MS) {
                stays += pts.subList(i, j)
                i = j
            } else {
                i++
            }
        }
        return merge(stays).map { it.toDwell() }
    }

    /** Joins consecutive stays at the same place separated by a short wander (R17 "merge"). */
    private fun merge(stays: List<List<TrackPoint>>): List<List<TrackPoint>> {
        val out = mutableListOf<List<TrackPoint>>()
        for (s in stays) {
            val prev = out.lastOrNull()
            if (prev != null &&
                s.first().recordedAt - prev.last().recordedAt <= StopRules.MERGE_MAX_GAP_MS &&
                TrackMath.distanceM(center(prev), center(s)) <= StopRules.RADIUS_M
            ) {
                out[out.lastIndex] = prev + s
            } else {
                out += s
            }
        }
        return out
    }

    private fun List<TrackPoint>.toDwell() =
        Dwell(first().trackId, first().recordedAt, last().recordedAt, center(this), size)

    private fun center(points: List<TrackPoint>) =
        GeoPoint(points.sumOf { it.location.latitude } / points.size, points.sumOf { it.location.longitude } / points.size)
}

/** Turns stays into the suggestions shown on a trip (R17). Pure; unit-tested in StopDetectionTest. */
object StopSuggestions {
    /**
     * Suggestions of a trip, oldest first: stays of every ENDED track (a recording in progress
     * suggests nothing), minus stays matching a step ([matchesStep]) or hidden by a decision
     * ([isHidden]). [zone] converts track instants to the steps'/photos' local wall-clock times.
     */
    fun build(
        tracks: List<Track>,
        points: List<TrackPoint>,
        steps: List<Step>,
        decisions: List<StopDecision>,
        zone: ZoneId,
    ): List<StopSuggestion> {
        val ended = tracks.filter { it.status == TrackStatus.ENDED }.map { it.id }.toSet()
        val stepIds = steps.map { it.id }.toSet()
        val photoTimes = steps.flatMap { s -> s.photos.mapNotNull { it.takenAt } }
        return points.filter { it.trackId in ended }
            .groupBy { it.trackId }
            .flatMap { (_, pts) -> StopDetection.detect(pts) }
            .filterNot { d -> steps.any { matchesStep(d, it, zone) } }
            .filterNot { d -> decisions.any { isHidden(d, it, stepIds) } }
            .sortedBy { it.startAt }
            .map { d -> StopSuggestion(d, photoTimes.count { inWindow(it, d, zone) }) }
    }

    /** Same place (≤ 300 m) and near in time (arrival within the stay ± 60 min). */
    fun matchesStep(d: Dwell, step: Step, zone: ZoneId): Boolean {
        if (TrackMath.distanceM(d.center, step.location) > StopRules.STEP_MATCH_RADIUS_M) return false
        val arrival = step.arrivedAt.atZone(zone).toInstant().toEpochMilli()
        return arrival in (d.startAt - StopRules.STEP_MATCH_TIME_MS)..(d.endAt + StopRules.STEP_MATCH_TIME_MS)
    }

    /**
     * A decision of the same track whose window overlaps the stay hides it: DISMISSED always,
     * ADDED while its step still exists (deleting that step brings the suggestion back).
     */
    fun isHidden(d: Dwell, decision: StopDecision, existingStepIds: Set<String>): Boolean {
        if (decision.trackId != d.trackId) return false
        if (decision.startAt > d.endAt || d.startAt > decision.endAt) return false
        return when (decision.kind) {
            StopDecisionKind.DISMISSED -> true
            StopDecisionKind.ADDED -> decision.stepId == null || decision.stepId in existingStepIds
        }
    }

    /** [takenAt] (minute precision) between the stay's start and end, both truncated to the minute. */
    fun inWindow(takenAt: LocalDateTime, d: Dwell, zone: ZoneId): Boolean {
        val from = localMinute(d.startAt, zone)
        val to = localMinute(d.endAt, zone)
        val t = takenAt.truncatedTo(ChronoUnit.MINUTES)
        return !t.isBefore(from) && !t.isAfter(to)
    }

    fun localMinute(epochMs: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone).truncatedTo(ChronoUnit.MINUTES)
}

/** Display formats of a suggestion (docs/03-screens.md "Gợi ý điểm dừng"). */
object StopFormat {
    /** "35 phút" under an hour, "1 giờ" / "2 giờ 05 phút" above (whole minutes, rounded down). */
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
}
