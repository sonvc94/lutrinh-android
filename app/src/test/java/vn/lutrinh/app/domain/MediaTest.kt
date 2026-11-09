package vn.lutrinh.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.MediaOrder
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.SetTripCover
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripCover
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.domain.usecase.VideoProblem
import vn.lutrinh.app.domain.usecase.VideoRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** R27 photo order, R29 short videos (limits, storage, never the trip cover). */
class MediaTest {
    private val store = FakeStore()
    private val clock = FixedClock()
    private val saveTrip = SaveTrip(store, store, clock, SeqIds())
    private val saveStep = SaveStep(store, store, RecordingFiles(), clock, SeqIds())
    private val start = LocalDate.of(2026, 10, 20)

    private fun input(files: List<String>, videos: Map<String, Long> = emptyMap()) =
        StepInput("Đồng Văn", "", GeoPoint(23.27, 105.36), LocalDateTime.of(start, LocalTime.NOON), files, videoDurations = videos)

    // ---- R27 order -------------------------------------------------------------------------

    @Test fun moveTakesAnItemToAnyPlaceAndKeepsTheOthersInOrder() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("d", "a", "b", "c"), MediaOrder.move(l, 3, 0))
        assertEquals(listOf("b", "c", "a", "d"), MediaOrder.move(l, 0, 2))
        assertEquals(listOf("b", "c", "d", "a"), MediaOrder.move(l, 0, 99))
        assertEquals(l, MediaOrder.move(l, 2, 2))
        assertEquals(l, MediaOrder.move(l, -1, 0))
        assertEquals(l, MediaOrder.move(l, 4, 0))
        assertEquals(emptyList<String>(), MediaOrder.move(emptyList<String>(), 0, 0))
    }

    @Test fun moveEarlierAndLaterStepOnePlaceAndStopAtTheEnds() {
        val l = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), MediaOrder.moveEarlier(l, 2))
        assertEquals(listOf("b", "a", "c"), MediaOrder.moveLater(l, 0))
        assertEquals(l, MediaOrder.moveEarlier(l, 0))
        assertEquals(l, MediaOrder.moveLater(l, 2))
    }

    /** Dragging the last photo first and saving stores positions 0..n-1, keeps every id, and leaves the chosen trip cover alone (R21). */
    @Test fun reorderedPhotosPersistWithTheirIdsAndTheCoverIsUnaffected() = runTest {
        val tripId = (saveTrip(null, TripInput("Hà Giang", "", start, start.plusDays(3))) as SaveResult.Saved).id
        val stepId = (saveStep(tripId, null, input(listOf("a.jpg", "b.jpg", "c.jpg"))) as SaveResult.Saved).id
        val before = store.getStep(stepId)!!.photos
        val chosen = before.first { it.fileName == "b.jpg" }
        assertTrue(SetTripCover(store, clock)(tripId, chosen.id))

        val dragged = MediaOrder.move(before.map { it.fileName }, 2, 0)
        saveStep(tripId, stepId, input(dragged))

        val after = store.getStep(stepId)!!.photos.sortedBy { it.position }
        assertEquals(listOf("c.jpg", "a.jpg", "b.jpg"), after.map { it.fileName })
        assertEquals(listOf(0, 1, 2), after.map { it.position })
        assertEquals(before.associate { it.fileName to it.id }, after.associate { it.fileName to it.id })
        assertEquals(chosen.id, store.getTrip(tripId)!!.coverPhotoId)
        assertEquals(chosen.id, TripCover.resolve(chosen.id, listOf(store.getStep(stepId)!!))?.id)
    }

    // ---- R29 videos ------------------------------------------------------------------------

    @Test fun videosUpTo60SecondsAnd200MbAreAcceptedLongerOrLargerRefused() {
        assertNull(VideoRules.check(1_000, 1_000))
        assertNull(VideoRules.check(60_000, VideoRules.MAX_BYTES))
        // The camera's 60 s limit stops at 60.0–60.9 s: still 60 whole seconds.
        assertNull(VideoRules.check(60_999, 50_000_000))
        assertEquals(VideoProblem.TOO_LONG, VideoRules.check(61_000, 1_000))
        assertEquals(VideoProblem.TOO_LONG, VideoRules.check(95_000, null))
        assertEquals(VideoProblem.TOO_LARGE, VideoRules.check(10_000, VideoRules.MAX_BYTES + 1))
        assertEquals(VideoProblem.TOO_LARGE, VideoRules.check(null, 300_000_000))
        assertEquals(VideoProblem.UNREADABLE, VideoRules.check(null, 1_000))
        assertEquals(VideoProblem.UNREADABLE, VideoRules.check(-1, null))
        assertEquals(200_000_000L, VideoRules.MAX_BYTES)
        assertEquals(60, VideoRules.CAPTURE_LIMIT_SECONDS)
    }

    @Test fun aSavedVideoKeepsItsTypeAndLengthThroughReorder() = runTest {
        val tripId = (saveTrip(null, TripInput("Hà Giang", "", start, start.plusDays(3))) as SaveResult.Saved).id
        val stepId = (saveStep(tripId, null, input(listOf("a.jpg", "v.mp4"), mapOf("v.mp4" to 12_000L))) as SaveResult.Saved).id
        val saved = store.getStep(stepId)!!.photos.associateBy { it.fileName }
        assertEquals(MediaType.PHOTO to null, saved.getValue("a.jpg").let { it.mediaType to it.durationMs })
        assertEquals(MediaType.VIDEO to 12_000L, saved.getValue("v.mp4").let { it.mediaType to it.durationMs })

        // The editor of an existing step does not pass durations again: the stored row keeps them.
        saveStep(tripId, stepId, input(listOf("v.mp4", "a.jpg")))
        val after = store.getStep(stepId)!!.photos.sortedBy { it.position }
        assertEquals(listOf("v.mp4", "a.jpg"), after.map { it.fileName })
        assertTrue(after.first().isVideo)
        assertEquals(12_000L, after.first().durationMs)
    }

    @Test fun aVideoIsNeverTheTripCover() {
        val video = Photo("v", "s1", "v.mp4", 0, 0, mediaType = MediaType.VIDEO, durationMs = 5_000)
        val photo = Photo("p", "s1", "p.jpg", 1, 0)
        val step = Step(
            id = "s1", tripId = "t", title = "A", note = "", location = GeoPoint(22.8, 105.0),
            arrivedAt = start.atTime(9, 0), createdAt = 0, updatedAt = 0, photos = listOf(video, photo),
        )
        assertEquals(listOf("p"), TripCover.candidates(listOf(step)).map { it.id })
        // Automatic cover skips a video that comes first; a stale video id falls back too.
        assertEquals("p", TripCover.resolve(null, listOf(step))?.id)
        assertEquals("p", TripCover.resolve("v", listOf(step))?.id)
        assertNull(TripCover.resolve(null, listOf(step.copy(photos = listOf(video)))))
    }
}
