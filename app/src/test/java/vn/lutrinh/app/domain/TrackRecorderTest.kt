package vn.lutrinh.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.LocationFix
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.DeleteTrack
import vn.lutrinh.app.domain.usecase.DeleteTrip
import vn.lutrinh.app.domain.usecase.FixVerdict
import vn.lutrinh.app.domain.usecase.StopOutcome
import vn.lutrinh.app.domain.usecase.TrackMath
import vn.lutrinh.app.domain.usecase.TrackRecorder
import vn.lutrinh.app.domain.usecase.TrackRules
import java.time.LocalDate

/** R16: start → pause/resume → stop, duration without pauses, process-death recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackRecorderTest {
    private val store = FakeStore()
    private val tracks = FakeTrackStore()
    private val clock = FixedClock(millis = 1_000_000)
    private val recorder = TrackRecorder(tracks, store, clock, SeqIds())

    private suspend fun trip(id: String = "t1") =
        store.upsertTrip(Trip(id, "Hà Giang", "", LocalDate.of(2026, 10, 20), null, 0, 0))

    /** A fix [dLat] degrees north of TP Hà Giang, taken now. */
    private fun fix(dLat: Double) = LocationFix(GeoPoint(22.8233 + dLat, 104.9836), clock.millis, 5f)

    private fun advance(seconds: Long) { clock.millis += seconds * 1000 }

    @Test fun `duration excludes pauses and distance only joins points of one segment`() = runTest {
        trip()
        val started = recorder.start("t1") as TrackRecorder.StartResult.Started
        assertEquals(TrackStatus.RECORDING, started.track.status)
        assertEquals(FixVerdict.ACCEPT, recorder.onFix(fix(0.0)))
        advance(60)
        assertEquals(FixVerdict.ACCEPT, recorder.onFix(fix(0.001)))
        advance(40) // 100 s recorded
        recorder.pause()
        advance(600) // 10 min pause: no time, no fixes
        assertEquals(FixVerdict.IGNORED, recorder.onFix(fix(0.05)))
        recorder.resume()
        advance(5)
        assertEquals(FixVerdict.ACCEPT, recorder.onFix(fix(0.05))) // far away, but a new segment
        advance(25)
        assertEquals(FixVerdict.ACCEPT, recorder.onFix(fix(0.051)))
        advance(20) // + 50 s
        assertEquals(150_000L, recorder.active.value!!.durationMs(clock.millis))
        val ended = recorder.stop()!!

        assertEquals(TrackStatus.ENDED, ended.status)
        assertEquals(150_000L, ended.activeMs)
        assertEquals(clock.millis, ended.endedAt)
        assertEquals(150_000L, ended.durationMs(clock.millis + 999_999)) // ended: no longer grows
        assertNull(recorder.active.value)
        val pts = tracks.points.value
        assertEquals(4, pts.size)
        assertEquals(listOf(0, 0, 1, 1), pts.map { it.segment })
        assertEquals(TrackMath.lengthM(pts), ended.distanceM, 1e-6)
        assertEquals(222.4, ended.distanceM, 0.5) // two ≈111 m steps, the 5 km gap not counted
        assertEquals(4, tracks.getTrack(ended.id)!!.pointCount)
        assertEquals(ended, tracks.getTrack(ended.id))
    }

    @Test fun `filtered fixes are not stored`() = runTest {
        trip()
        recorder.start("t1")
        recorder.onFix(fix(0.0))
        advance(5)
        assertEquals(FixVerdict.TOO_CLOSE, recorder.onFix(fix(0.00002)))
        assertEquals(FixVerdict.INACCURATE, recorder.onFix(fix(0.001).copy(accuracyM = 120f)))
        assertEquals(FixVerdict.JUMP, recorder.onFix(fix(0.05)))
        assertEquals(1, tracks.points.value.size)
        assertEquals(1, recorder.active.value!!.pointCount)
    }

    @Test fun `only one recording at a time and only for an existing trip`() = runTest {
        assertEquals(TrackRecorder.StartResult.TripNotFound, recorder.start("nope"))
        trip("t1"); trip("t2")
        val first = recorder.start("t1") as TrackRecorder.StartResult.Started
        val second = recorder.start("t2")
        assertTrue(second is TrackRecorder.StartResult.AlreadyRecording)
        assertEquals(first.track.id, (second as TrackRecorder.StartResult.AlreadyRecording).track.id)
        assertEquals(1, tracks.tracks.value.size)
    }

    @Test fun `process death ends the open track with its saved points`() = runTest {
        trip()
        recorder.start("t1")
        recorder.onFix(fix(0.0))
        advance(30)
        recorder.onFix(fix(0.001))
        val lastSave = clock.millis
        advance(500) // the process is killed here; nothing else is saved
        val savedBefore = tracks.points.value.size

        // A new process: a new recorder over the same storage.
        val afterRestart = TrackRecorder(tracks, store, clock, SeqIds())
        assertEquals(1, afterRestart.recoverInterrupted())
        val t = tracks.tracks.value.values.single()
        assertEquals(TrackStatus.ENDED, t.status)
        assertTrue(t.interrupted)
        assertEquals(lastSave, t.endedAt)
        assertEquals(30_000L, t.activeMs) // counted up to the last saved fix
        assertEquals(savedBefore, tracks.points.value.size)
        assertNull(afterRestart.active.value)
        assertEquals(0, afterRestart.recoverInterrupted())
    }

    @Test fun `a paused track interrupted keeps the time before the pause`() = runTest {
        trip()
        recorder.start("t1")
        advance(90)
        recorder.pause()
        advance(1000)
        TrackRecorder(tracks, store, clock, SeqIds()).recoverInterrupted()
        val t = tracks.tracks.value.values.single()
        assertEquals(90_000L, t.activeMs)
        assertTrue(t.interrupted)
    }

    @Test fun `delete removes an ended track and its points but never the open one`() = runTest {
        trip()
        val delete = DeleteTrack(tracks, recorder)
        val id = (recorder.start("t1") as TrackRecorder.StartResult.Started).track.id
        recorder.onFix(fix(0.0))
        advance(30)
        recorder.onFix(fix(0.001))
        assertFalse(delete(id))
        recorder.stop()
        assertTrue(delete(id))
        assertTrue(tracks.tracks.value.isEmpty())
        assertTrue(tracks.points.value.isEmpty())
    }

    @Test fun `deleting the trip stops its recording`() = runTest {
        trip()
        recorder.start("t1")
        DeleteTrip(store, RecordingFiles(), recorder)("t1")
        assertNull(recorder.active.value)
        assertEquals(FixVerdict.IGNORED, recorder.onFix(fix(0.0)))
    }

    @Test fun `a track deleted under the recorder ends the recording instead of crashing`() = runTest {
        trip()
        val id = (recorder.start("t1") as TrackRecorder.StartResult.Started).track.id
        tracks.deleteTrack(id)
        assertEquals(FixVerdict.IGNORED, recorder.onFix(fix(0.0)))
        assertNull(recorder.active.value)
    }

    @Test fun `stopping drops a track with fewer than 2 points or under 50 m and says so`() = runTest {
        trip()
        val events = mutableListOf<StopOutcome>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.stopped.toList(events) }

        // one point
        recorder.start("t1"); recorder.onFix(fix(0.0))
        val onePoint = recorder.stop()!!
        assertTrue(tracks.tracks.value.isEmpty())
        // two points 33 m apart (0.0003°)
        recorder.start("t1"); recorder.onFix(fix(0.0)); advance(30); recorder.onFix(fix(0.0003))
        recorder.stop()
        assertTrue(tracks.tracks.value.isEmpty())
        // two points 111 m apart are kept
        val keptId = (recorder.start("t1") as TrackRecorder.StartResult.Started).track.id
        recorder.onFix(fix(0.0)); advance(30); recorder.onFix(fix(0.001))
        val kept = recorder.stop()!!

        assertEquals(setOf(keptId), tracks.tracks.value.keys)
        assertEquals(3, events.size)
        assertTrue(events[0] is StopOutcome.Dropped && (events[0] as StopOutcome.Dropped).track.id == onePoint.id)
        assertTrue(events[1] is StopOutcome.Dropped)
        assertEquals(StopOutcome.Saved(kept), events[2])
        job.cancel()
    }

    @Test fun `the keep rule needs 2 points and 50 m`() {
        assertFalse(TrackRules.isWorthKeeping(0, 0.0))
        assertFalse(TrackRules.isWorthKeeping(1, 500.0))
        assertFalse(TrackRules.isWorthKeeping(2, 49.9))
        assertTrue(TrackRules.isWorthKeeping(2, 50.0))
    }

    @Test fun `recording is offered unless the trip ended before today`() {
        val today = LocalDate.of(2026, 11, 9)
        fun trip(end: LocalDate?) = Trip("t", "x", "", LocalDate.of(2025, 10, 20), end, 0, 0)
        assertTrue(TrackRules.canRecordOn(trip(null), today))
        assertTrue(TrackRules.canRecordOn(trip(today), today))
        assertTrue(TrackRules.canRecordOn(trip(today.plusDays(3)), today))
        assertFalse(TrackRules.canRecordOn(trip(today.minusDays(1)), today))
        assertFalse(TrackRules.canRecordOn(trip(LocalDate.of(2025, 10, 23)), today))
    }
}
