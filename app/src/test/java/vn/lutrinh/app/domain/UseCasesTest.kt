package vn.lutrinh.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.PhotoSource
import vn.lutrinh.app.domain.usecase.DeleteStep
import vn.lutrinh.app.domain.usecase.DeleteTrip
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.StepError
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripError
import vn.lutrinh.app.domain.usecase.TripInput
import java.time.LocalDate
import java.time.LocalDateTime

class UseCasesTest {
    private val store = FakeStore()
    private val files = RecordingFiles()
    private val clock = FixedClock()
    private val ids = SeqIds()
    private val saveTrip = SaveTrip(store, store, clock, ids)
    private val saveStep = SaveStep(store, store, files, clock, ids)
    private val deleteTrip = DeleteTrip(store, files)
    private val deleteStep = DeleteStep(store, files)

    private val start = LocalDate.of(2026, 10, 20)
    private val tripInput = TripInput("  Hà Giang  ", " loop ", start, start.plusDays(3))

    private suspend fun newTrip(): String = (saveTrip(null, tripInput) as SaveResult.Saved).id

    private fun stepInput(day: Int = 0, photos: List<String> = emptyList(), title: String = "Đồng Văn") =
        StepInput(title, "", GeoPoint(23.27, 105.36), LocalDateTime.of(start.plusDays(day.toLong()), java.time.LocalTime.NOON), photos)

    @Test fun createTripTrimsAndStampsTimes() = runTest {
        val id = newTrip()
        val t = store.getTrip(id)!!
        assertEquals("Hà Giang", t.title)
        assertEquals("loop", t.description)
        assertEquals(1_000L, t.createdAt)
        assertEquals(1_000L, t.updatedAt)
    }

    @Test fun editTripKeepsIdAndCreatedAt() = runTest {
        val id = newTrip()
        clock.millis = 5_000
        val r = saveTrip(id, tripInput.copy(title = "Hà Giang mùa lúa"))
        assertEquals(SaveResult.Saved(id), r)
        val t = store.getTrip(id)!!
        assertEquals("Hà Giang mùa lúa", t.title)
        assertEquals(1_000L, t.createdAt)
        assertEquals(5_000L, t.updatedAt)
        assertEquals(1, store.trips.value.size)
    }

    @Test fun invalidTripIsNotStored() = runTest {
        val r = saveTrip(null, tripInput.copy(title = ""))
        assertEquals(SaveResult.Invalid(setOf(TripError.TITLE_BLANK)), r)
        assertTrue(store.trips.value.isEmpty())
    }

    @Test fun shrinkingDatesBelowExistingStepsIsRejected() = runTest {
        val id = newTrip()
        saveStep(id, null, stepInput(day = 3))
        val r = saveTrip(id, tripInput.copy(endDate = start.plusDays(1)))
        assertEquals(SaveResult.Invalid(setOf(TripError.STEPS_OUTSIDE_DATES)), r)
    }

    @Test fun stepOutsideTripDatesIsRejected() = runTest {
        val id = newTrip()
        val r = saveStep(id, null, stepInput(day = 4))
        assertEquals(SaveResult.Invalid(setOf(StepError.AFTER_TRIP_END)), r)
        assertTrue(store.steps.value.isEmpty())
    }

    @Test fun extendTripWidensTheEndAndSavesTheStep() = runTest {
        val id = newTrip()
        clock.millis = 7_000
        val r = saveStep(id, null, stepInput(day = 10), extendTrip = true)
        assertTrue(r is SaveResult.Saved)
        val t = store.getTrip(id)!!
        assertEquals(start, t.startDate)
        assertEquals(start.plusDays(10), t.endDate)
        assertEquals(7_000L, t.updatedAt)
        assertEquals(1, store.steps.value.size)
    }

    @Test fun extendTripWidensTheStartOnly() = runTest {
        val id = newTrip()
        saveStep(id, null, stepInput(day = -5), extendTrip = true)
        val t = store.getTrip(id)!!
        assertEquals(start.minusDays(5), t.startDate)
        assertEquals(start.plusDays(3), t.endDate)
    }

    @Test fun extendTripLeavesAnOngoingTripOpen() = runTest {
        val id = (saveTrip(null, tripInput.copy(endDate = null)) as SaveResult.Saved).id
        saveStep(id, null, stepInput(day = -2), extendTrip = true)
        assertEquals(start.minusDays(2), store.getTrip(id)!!.startDate)
        assertNull(store.getTrip(id)!!.endDate)
    }

    @Test fun extendTripChangesNothingWhenTheStepIsInside() = runTest {
        val id = newTrip()
        val before = store.getTrip(id)
        clock.millis = 9_000
        saveStep(id, null, stepInput(day = 2), extendTrip = true)
        assertEquals(before, store.getTrip(id))
    }

    @Test fun invalidStepDoesNotWidenTheTrip() = runTest {
        val id = newTrip()
        val before = store.getTrip(id)
        val r = saveStep(id, null, stepInput(day = 10, title = ""), extendTrip = true)
        assertEquals(SaveResult.Invalid(setOf(StepError.TITLE_BLANK)), r)
        assertEquals(before, store.getTrip(id))
        assertTrue(store.steps.value.isEmpty())
    }

    @Test fun stepForMissingTripIsRejected() = runTest {
        assertEquals(SaveResult.Invalid(setOf(StepError.TRIP_NOT_FOUND)), saveStep("nope", null, stepInput()))
    }

    @Test fun savingStepNumbersPhotosAndKeepsIdsOfExistingOnes() = runTest {
        val tripId = newTrip()
        val stepId = (saveStep(tripId, null, stepInput(photos = listOf("a.jpg", "b.jpg"))) as SaveResult.Saved).id
        val before = store.getStep(stepId)!!.photos
        assertEquals(listOf(0, 1), before.map { it.position })

        saveStep(tripId, stepId, stepInput(photos = listOf("b.jpg", "c.jpg", "b.jpg")))
        val after = store.getStep(stepId)!!.photos
        assertEquals(listOf("b.jpg", "c.jpg"), after.map { it.fileName })
        assertEquals(listOf(0, 1), after.map { it.position })
        assertEquals(before.first { it.fileName == "b.jpg" }.id, after[0].id)
        assertEquals(listOf("a.jpg"), files.deleted)
    }

    /** R17: a new photo gets the EXIF time passed with it; an existing photo keeps its time on edit. */
    @Test fun savingStepStoresPhotoCaptureTimesAndKeepsThemOnEdit() = runTest {
        val tripId = newTrip()
        val taken = LocalDateTime.of(2026, 10, 20, 9, 15)
        val id = (saveStep(tripId, null, stepInput(photos = listOf("a.jpg", "b.jpg")).copy(photoTakenAt = mapOf("a.jpg" to taken))) as SaveResult.Saved).id
        assertEquals(listOf(taken, null), store.getStep(id)!!.photos.map { it.takenAt })
        saveStep(tripId, id, stepInput(photos = listOf("b.jpg", "a.jpg", "c.jpg")))
        assertEquals(listOf(null, taken, null), store.getStep(id)!!.photos.map { it.takenAt })
    }

    @Test fun savingStepStoresPhotoSourcesAndKeepsThemOnEdit() = runTest {
        val tripId = newTrip()
        val source = PhotoSource("1234", 2_048_000)
        val id = (saveStep(tripId, null, stepInput(photos = listOf("a.jpg", "b.jpg")).copy(photoSources = mapOf("a.jpg" to source))) as SaveResult.Saved).id
        assertEquals(listOf("1234" to 2_048_000L, null to null), store.getStep(id)!!.photos.map { it.sourceId to it.sourceSize })
        saveStep(tripId, id, stepInput(photos = listOf("b.jpg", "a.jpg")))
        assertEquals(listOf(null to null, "1234" to 2_048_000L), store.getStep(id)!!.photos.map { it.sourceId to it.sourceSize })
    }

    @Test fun deleteStepRemovesItsPhotoFilesOnly() = runTest {
        val tripId = newTrip()
        val s1 = (saveStep(tripId, null, stepInput(photos = listOf("a.jpg"))) as SaveResult.Saved).id
        saveStep(tripId, null, stepInput(day = 1, photos = listOf("b.jpg")))
        deleteStep(s1)
        assertNull(store.getStep(s1))
        assertEquals(1, store.getSteps(tripId).size)
        assertEquals(listOf("a.jpg"), files.deleted)
    }

    @Test fun deleteTripCascadesToStepsAndPhotoFiles() = runTest {
        val keep = (saveTrip(null, tripInput.copy(title = "Ninh Bình")) as SaveResult.Saved).id
        saveStep(keep, null, stepInput(photos = listOf("k.jpg")))
        val tripId = newTrip()
        saveStep(tripId, null, stepInput(photos = listOf("a.jpg", "b.jpg")))
        saveStep(tripId, null, stepInput(day = 1, photos = listOf("c.jpg")))

        deleteTrip(tripId)

        assertNull(store.getTrip(tripId))
        assertTrue(store.getSteps(tripId).isEmpty())
        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), files.deleted.toSet())
        assertEquals(1, store.getSteps(keep).size)
    }
}
