package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.StopDetection
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.StopSuggestions
import vn.lutrinh.app.domain.usecase.TrackMath
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** R17: stays in a track and the suggestions made from them (docs/04-business-rules.md). */
class StopDetectionTest {
    private val zone: ZoneId = ZoneOffset.ofHours(7)

    /** 2025-10-20 08:00 in [zone]. */
    private val t0 = LocalDateTime.of(2025, 10, 20, 8, 0).atZone(zone).toInstant().toEpochMilli()
    private val base = GeoPoint(23.0, 105.0)
    private fun min(m: Int) = t0 + m * 60_000L

    /** [north] metres north and [east] metres east of [base] (approximately). */
    private fun at(north: Double, east: Double = 0.0) =
        GeoPoint(base.latitude + north / 111_195.0, base.longitude + east / (111_195.0 * Math.cos(Math.toRadians(base.latitude))))

    private fun p(north: Double, minute: Int, segment: Int = 0, east: Double = 0.0, track: String = "k") =
        TrackPoint(track, segment, min(minute), at(north, east))

    /** Driving north at 500 m a minute for [minutes], starting [fromNorth] m north at [startMin]. */
    private fun drive(fromNorth: Double, startMin: Int, minutes: Int, segment: Int = 0) =
        (1..minutes).map { k -> p(fromNorth + 500.0 * k, startMin + k, segment) }

    private fun local(minute: Int) = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(min(minute)), zone)

    private fun step(id: String, location: GeoPoint, minute: Int, photosTakenAt: List<Int?> = emptyList()) = Step(
        id, "t", "s", "", location, local(minute), 0, 0,
        photos = photosTakenAt.mapIndexed { i, m -> Photo("$id-p$i", id, "$id-$i.jpg", i, 0, m?.let { local(it) }) },
    )

    private fun track(id: String = "k", status: TrackStatus = TrackStatus.ENDED) =
        Track(id, "t", status, t0, null, 0, null, 0, 0.0, 0, false, 0, 0)

    /** Drive 10 min, stand still 25 min (one fix: the recorder drops fixes closer than 10 m), drive on. */
    private val oneStop: List<TrackPoint> =
        drive(-5_700.0, -11, 10) + p(-450.0, 0) + p(-440.0, 25) + drive(-440.0, 25, 10)

    @Test fun `standing still for 20 min or more is one stop`() {
        val stops = StopDetection.detect(oneStop)
        assertEquals(1, stops.size)
        val s = stops.single()
        assertEquals(min(0), s.startAt)
        assertEquals(min(25), s.endAt)
        assertTrue(TrackMath.distanceM(s.center, at(-445.0)) < 5)
    }

    @Test fun `exactly 20 min is a stop, under 20 min is none`() {
        assertEquals(1, StopDetection.detect(drive(-5_000.0, -10, 10) + p(20.0, 20) + drive(20.0, 20, 5)).size)
        val short = drive(-5_000.0, -10, 10) + p(20.0, 19) + drive(20.0, 19, 5)
        assertTrue(StopDetection.detect(short).isEmpty())
    }

    @Test fun `GPS jitter within the radius stays one stop`() {
        val rnd = Random(7)
        val jitter = (0..40).map { k -> p(rnd.nextDouble(-45.0, 45.0), k, east = rnd.nextDouble(-45.0, 45.0)) }
        val stops = StopDetection.detect(drive(-6_000.0, -12, 11) + jitter + drive(0.0, 40, 10))
        assertEquals(1, stops.size)
        assertTrue(stops.single().durationMs >= 40 * 60_000L)
        assertTrue(TrackMath.distanceM(stops.single().center, base) < 40)
    }

    @Test fun `steady movement is never a stop`() {
        // Walking 100 m every 2 minutes for 2 hours: never 20 min within 150 m.
        val walk = (0..60).map { k -> p(100.0 * k, 2 * k) }
        assertTrue(StopDetection.detect(walk).isEmpty())
    }

    @Test fun `a pause between segments at the same place counts as a stop`() {
        // Segment 0 ends at 0 m (minute 0); paused; segment 1 starts 30 m away 31 min later.
        val points = drive(-5_000.0, -10, 10) + p(30.0, 31, segment = 1) + drive(30.0, 31, 5, segment = 1)
        val stops = StopDetection.detect(points)
        assertEquals(1, stops.size)
        assertEquals(min(0), stops.single().startAt)
        assertEquals(min(31), stops.single().endAt)
    }

    @Test fun `a pause between far-apart positions is not a stop`() {
        val points = drive(-5_000.0, -10, 10) + p(800.0, 40, segment = 1) + drive(800.0, 40, 5, segment = 1)
        assertTrue(StopDetection.detect(points).isEmpty())
    }

    @Test fun `two stays at the same place a few minutes apart merge into one`() {
        // 25 min at the café, a 4-min wander 200 m away, 25 min back at the café.
        val first = (0..5).map { k -> p(5.0 * k, 5 * k) }
        val wander = listOf(p(200.0, 27), p(210.0, 28))
        val second = (0..5).map { k -> p(140.0 - 5.0 * k, 29 + 5 * k) }
        val stops = StopDetection.detect(first + wander + second)
        assertEquals(1, stops.size)
        assertEquals(min(0), stops.single().startAt)
        assertEquals(min(54), stops.single().endAt)
    }

    @Test fun `points are ordered by segment and time before detection`() {
        assertEquals(StopDetection.detect(oneStop), StopDetection.detect(oneStop.shuffled(Random(3))))
    }

    @Test fun `a stay near an existing step in place and time is not suggested`() {
        val near = step("s1", at(-200.0), minute = 10)
        assertTrue(StopSuggestions.build(listOf(track()), oneStop, listOf(near), emptyList(), zone).isEmpty())

        // Same place but another day, or same time 1 km away: still suggested.
        val otherDay = step("s2", at(-445.0), minute = 24 * 60)
        val farAway = step("s3", at(600.0), minute = 5)
        assertEquals(1, StopSuggestions.build(listOf(track()), oneStop, listOf(otherDay, farAway), emptyList(), zone).size)
    }

    @Test fun `step arriving within an hour around the stay matches, later does not`() {
        val d = StopDetection.detect(oneStop).single()
        assertTrue(StopSuggestions.matchesStep(d, step("a", at(-445.0), minute = -60), zone))
        assertTrue(StopSuggestions.matchesStep(d, step("b", at(-445.0), minute = 85), zone))
        assertFalse(StopSuggestions.matchesStep(d, step("c", at(-445.0), minute = 86), zone))
        assertTrue(StopSuggestions.matchesStep(d, step("d", at(-445.0 + 290), minute = 10), zone))
        assertFalse(StopSuggestions.matchesStep(d, step("e", at(-445.0 - 310), minute = 10), zone))
    }

    @Test fun `a recording in progress suggests nothing`() {
        assertTrue(StopSuggestions.build(listOf(track(status = TrackStatus.RECORDING)), oneStop, emptyList(), emptyList(), zone).isEmpty())
        assertTrue(StopSuggestions.build(listOf(track(status = TrackStatus.PAUSED)), oneStop, emptyList(), emptyList(), zone).isEmpty())
    }

    @Test fun `a dismissed stay stays hidden`() {
        val d = StopDetection.detect(oneStop).single()
        val dismissed = StopDecision("k", d.startAt, d.endAt, StopDecisionKind.DISMISSED, null, 1)
        assertTrue(StopSuggestions.build(listOf(track()), oneStop, emptyList(), listOf(dismissed), zone).isEmpty())
        // A decision on another track or another time does not hide it.
        val other = dismissed.copy(trackId = "x")
        val earlier = dismissed.copy(startAt = min(-90), endAt = min(-60))
        assertEquals(1, StopSuggestions.build(listOf(track()), oneStop, emptyList(), listOf(other, earlier), zone).size)
    }

    @Test fun `an added stay is hidden while its step exists`() {
        val d = StopDetection.detect(oneStop).single()
        val added = StopDecision("k", d.startAt, d.endAt, StopDecisionKind.ADDED, "new", 1)
        // The user moved the new step elsewhere: hidden by the decision, not by the match.
        val moved = step("new", at(5_000.0), minute = 300)
        assertTrue(StopSuggestions.build(listOf(track()), oneStop, listOf(moved), listOf(added), zone).isEmpty())
        // The step was deleted later: the suggestion is back.
        assertEquals(1, StopSuggestions.build(listOf(track()), oneStop, emptyList(), listOf(added), zone).size)
    }

    @Test fun `photo count uses capture times inside the window`() {
        // Photos of a step elsewhere: taken at 07:59, 08:00, 08:12, 08:25, 08:26 and one without a time.
        val elsewhere = step("s", at(9_000.0), minute = -120, photosTakenAt = listOf(-1, 0, 12, 25, 26, null))
        val s = StopSuggestions.build(listOf(track()), oneStop, listOf(elsewhere), emptyList(), zone).single()
        assertEquals(3, s.photoCount)
    }

    @Test fun `suggestions of several tracks are sorted by time`() {
        val second = oneStop.map { it.copy(trackId = "k2", recordedAt = it.recordedAt + 3_600_000L * 5) }
        val list = StopSuggestions.build(listOf(track("k2"), track("k")), second + oneStop, emptyList(), emptyList(), zone)
        assertEquals(listOf("k", "k2"), list.map { it.dwell.trackId })
    }

    @Test fun `duration reads in minutes and hours`() {
        assertEquals("20 phút", DisplayFormat.duration(20 * 60_000L + 59_000))
        assertEquals("1 giờ", DisplayFormat.duration(60 * 60_000L))
        assertEquals("2 giờ 05 phút", DisplayFormat.duration(125 * 60_000L))
    }
}
