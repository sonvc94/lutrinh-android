package vn.lutrinh.app.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.PublishStep
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.SetTripCover
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripCover
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.domain.usecase.TripShare
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.overallStatsOf
import vn.lutrinh.app.domain.usecase.routeOf
import vn.lutrinh.app.domain.usecase.statsOf
import vn.lutrinh.app.domain.usecase.stepNumbers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** R20 draft steps (left out of stats, share, legs and "Bản đồ của tôi") and R21 trip cover. */
class DraftsAndCoverTest {
    private val start = LocalDate.of(2025, 11, 3)
    private val trip = Trip("t", "Hà Giang", "", start, start.plusDays(3), 0, 0)

    private fun step(n: Int, photos: Int = 0, draft: Boolean = false, tripId: String = "t", mode: TransportMode? = null) = Step(
        id = "s$n", tripId = tripId, title = "Điểm $n", note = "", location = GeoPoint(22.8 + n * 0.1, 105.0),
        arrivedAt = start.atTime(8 + n, 0), createdAt = n.toLong(), updatedAt = 0,
        photos = (0 until photos).map { Photo("s$n-p$it", "s$n", "s$n-p$it.jpg", it, 0) },
        arrivalTransport = mode, isDraft = draft,
    )

    // s1, s3 published; s2 (between them, far away) is a draft with photos.
    private val withDraft = listOf(
        step(1, photos = 1),
        step(2, photos = 3, draft = true).copy(location = GeoPoint(10.8, 106.7)),
        step(3, photos = 2, mode = TransportMode.MOTORBIKE),
    )
    private val withoutDraft = withDraft.filterNot { it.isDraft }

    // ---- R20: trip stats ----

    @Test fun tripStatsLeaveDraftsOut() {
        val s = statsOf(trip, withDraft)
        assertEquals(2, s.stepCount)
        assertEquals(3, s.photoCount)
        assertEquals(statsOf(trip, withoutDraft), s)
    }

    @Test fun legsAndRouteSkipDrafts() {
        val legs = legsOf(withDraft)
        assertEquals(listOf("s1" to "s3"), legs.map { it.from.id to it.to.id })
        assertEquals(TransportMode.MOTORBIKE, legs.single().mode)
        assertEquals(listOf("s1", "s3").map { id -> withDraft.first { it.id == id }.location }, routeOf(withDraft))
    }

    @Test fun onlyPublishedStepsAreNumbered() {
        assertEquals(mapOf("s1" to 1, "s3" to 2), stepNumbers(withDraft.reversed()))
    }

    // ---- R20: share image / page ----

    @Test fun shareLeavesDraftsOut() {
        assertEquals(TripShare.stats(trip, withoutDraft), TripShare.stats(trip, withDraft))
        assertEquals(listOf("s1", "s3"), TripShare.sharedSteps(withDraft.reversed()).map { it.id })
        val photos = TripShare.imagePhotos(withDraft)
        assertTrue(photos.none { it.stepId == "s2" })
        assertEquals(listOf("s1-p0.jpg", "s3-p0.jpg", "s3-p1.jpg"), photos.map { it.fileName })
    }

    @Test fun openEndedShareDaysIgnoreALaterDraft() {
        val open = trip.copy(endDate = null)
        val late = step(9, draft = true).copy(arrivedAt = start.plusDays(5).atTime(9, 0))
        assertEquals(1, TripShare.stats(open, withoutDraft + late).days)
    }

    // ---- R20: "Bản đồ của tôi" ----

    @Test fun myMapTotalsLeaveDraftsOut() {
        val other = Trip("o", "Ninh Bình", "", start, null, 1, 1)
        val all = withDraft + step(7, tripId = "o", draft = true)
        val s = overallStatsOf(listOf(trip, other), all)
        assertEquals(2, s.stepCount)
        assertEquals(overallStatsOf(listOf(trip, other), withoutDraft).distanceKm, s.distanceKm, 1e-9)
    }

    // ---- R21: cover ----

    @Test fun coverIsTheChosenPhotoElseTheFirstPhoto() {
        assertEquals("s1-p0", TripCover.resolve(null, withDraft)?.id)
        assertEquals("s3-p1", TripCover.resolve("s3-p1", withDraft)?.id)
        assertTrue(TripCover.isChosen("s3-p1", withDraft))
        // Any photo of the trip can be chosen, a draft's too.
        assertEquals("s2-p2", TripCover.resolve("s2-p2", withDraft)?.id)
        assertEquals(6, TripCover.candidates(withDraft).size)
    }

    @Test fun coverFallsBackToAutomaticWhenTheChosenPhotoIsGone() {
        val removed = withDraft.map { if (it.id == "s3") it.copy(photos = it.photos.take(1)) else it }
        assertEquals("s1-p0", TripCover.resolve("s3-p1", removed)?.id)
        assertFalse(TripCover.isChosen("s3-p1", removed))
        // Its step deleted too: still automatic; a trip without photos has no cover.
        assertEquals("s1-p0", TripCover.resolve("s3-p1", withDraft.filterNot { it.id == "s3" })?.id)
        assertNull(TripCover.resolve("s3-p1", listOf(step(4))))
    }

    // ---- use cases ----

    private val store = FakeStore()
    private val clock = FixedClock()
    private val ids = SeqIds()
    private val saveTrip = SaveTrip(store, store, clock, ids)
    private val saveStep = SaveStep(store, store, RecordingFiles(), clock, ids)

    private fun input(hour: Int, photos: List<String> = emptyList(), draft: Boolean = false) =
        StepInput("Đồng Văn", "", GeoPoint(23.27, 105.36), LocalDateTime.of(start, LocalTime.of(hour, 0)), photos, draft = draft)

    @Test fun saveDraftStoresADraftAndSaveOrPublishPublishesIt() = runTest {
        val t = (saveTrip(null, TripInput("HG", "", start, null)) as SaveResult.Saved).id
        val id = (saveStep(t, null, input(9, draft = true)) as SaveResult.Saved).id
        assertTrue(store.getStep(id)!!.isDraft)
        // Re-saved as a draft keeps it a draft; "Lưu" publishes.
        saveStep(t, id, input(10, draft = true))
        assertTrue(store.getStep(id)!!.isDraft)
        saveStep(t, id, input(10))
        assertFalse(store.getStep(id)!!.isDraft)
        // "Đăng" from the step detail.
        saveStep(t, id, input(10, draft = true))
        clock.millis = 7_000
        assertTrue(PublishStep(store, clock)(id))
        assertFalse(store.getStep(id)!!.isDraft)
        assertEquals(7_000L, store.getStep(id)!!.updatedAt)
        assertFalse(PublishStep(store, clock)("missing"))
    }

    @Test fun draftsAreCountedApartInTheTripList() = runTest {
        val t = (saveTrip(null, TripInput("HG", "", start, null)) as SaveResult.Saved).id
        saveStep(t, null, input(9, listOf("a.jpg")))
        saveStep(t, null, input(10, listOf("b.jpg", "c.jpg"), draft = true))
        val s = store.observeTripSummaries().first().single()
        assertEquals(1 to 1, s.stepCount to s.photoCount)
        assertEquals(1 to 2, s.draftCount to s.draftPhotoCount)
    }

    @Test fun setTripCoverIsKeptByTripEditsAndClearedByAutomatic() = runTest {
        val t = (saveTrip(null, TripInput("HG", "", start, null)) as SaveResult.Saved).id
        saveStep(t, null, input(9, listOf("a.jpg", "b.jpg")))
        val b = store.getSteps(t).single().photos.first { it.fileName == "b.jpg" }
        assertTrue(SetTripCover(store, clock)(t, b.id))
        assertEquals("b.jpg", store.observeTripSummaries().first().single().coverPhotoFile)
        saveTrip(t, TripInput("Hà Giang", "", start, null))
        assertEquals(b.id, store.getTrip(t)!!.coverPhotoId)
        assertTrue(SetTripCover(store, clock)(t, null))
        assertEquals("a.jpg", store.observeTripSummaries().first().single().coverPhotoFile)
        assertFalse(SetTripCover(store, clock)("missing", null))
    }
}
