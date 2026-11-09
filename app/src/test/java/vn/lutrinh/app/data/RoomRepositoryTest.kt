package vn.lutrinh.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import java.time.LocalDate
import java.time.LocalDateTime

/** Exercises the real SQLite schema: ordering, counters, photo replacement and ON DELETE CASCADE. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoomRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var trips: RoomTripRepository
    private lateinit var steps: RoomStepRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        trips = RoomTripRepository(db)
        steps = RoomStepRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun trip(id: String, start: String, created: Long = 0) =
        Trip(id, "Trip $id", "", LocalDate.parse(start), null, created, created)

    private fun step(id: String, tripId: String, at: String, photos: List<String> = emptyList(), created: Long = 0) = Step(
        id, tripId, "Step $id", "", GeoPoint(22.8, 105.0), LocalDateTime.parse(at), created, created,
        photos.mapIndexed { i, f -> Photo("$id-$f", id, f, i, 0) },
    )

    @Test fun tripCrudRoundTrip() = runTest {
        val t = trip("t1", "2026-10-20").copy(endDate = LocalDate.parse("2026-10-23"), description = "loop")
        trips.upsertTrip(t)
        assertEquals(t, trips.getTrip("t1"))
        trips.upsertTrip(t.copy(title = "Hà Giang", endDate = null))
        assertEquals("Hà Giang", trips.getTrip("t1")!!.title)
        assertNull(trips.getTrip("t1")!!.endDate)
        trips.deleteTrip("t1")
        assertNull(trips.getTrip("t1"))
    }

    @Test fun saveStepAndTripStoresBothTogether() = runTest {
        trips.upsertTrip(trip("t", "2026-10-20").copy(endDate = LocalDate.parse("2026-10-23")))
        val moved = trips.getTrip("t")!!.copy(endDate = LocalDate.parse("2026-11-02"), updatedAt = 5)
        steps.saveStepAndTrip(step("s", "t", "2026-11-02T08:00", listOf("a.jpg")), moved)
        assertEquals(moved, trips.getTrip("t"))
        assertEquals(listOf("a.jpg"), steps.getStep("s")!!.photos.map { it.fileName })
    }

    @Test fun summariesAreOrderedAndCounted() = runTest {
        trips.upsertTrip(trip("old", "2025-06-07"))
        trips.upsertTrip(trip("new", "2025-10-20", created = 1))
        trips.upsertTrip(trip("newer-created", "2025-10-20", created = 2))
        steps.saveStep(step("s2", "new", "2025-10-21T09:00", listOf("x.jpg")))
        steps.saveStep(step("s1", "new", "2025-10-20T09:00", listOf("cover.jpg", "y.jpg")))

        val list = trips.observeTripSummaries().first()
        assertEquals(listOf("newer-created", "new", "old"), list.map { it.trip.id })
        val s = list[1]
        assertEquals(2, s.stepCount)
        assertEquals(3, s.photoCount)
        assertEquals("cover.jpg", s.coverPhotoFile)
        assertNull(list[2].coverPhotoFile)
    }

    @Test fun stepsComeBackInTimelineOrderWithOrderedPhotos() = runTest {
        trips.upsertTrip(trip("t", "2026-10-20"))
        steps.saveStep(step("c", "t", "2026-10-22T08:00"))
        steps.saveStep(step("a", "t", "2026-10-20T08:00"))
        steps.saveStep(step("b", "t", "2026-10-20T08:00", created = 1))
        steps.saveStep(step("p", "t", "2026-10-23T08:00", listOf("1.jpg", "2.jpg", "3.jpg")))
        assertEquals(listOf("a", "b", "c", "p"), steps.observeSteps("t").first().map { it.id })
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), steps.getStep("p")!!.photos.map { it.fileName })
        assertEquals(LocalDate.parse("2026-10-20")..LocalDate.parse("2026-10-23"), steps.stepDateRange("t"))
        assertNull(steps.stepDateRange("missing"))
    }

    @Test fun savingStepReplacesPhotosAndReportsRemovedFiles() = runTest {
        trips.upsertTrip(trip("t", "2026-10-20"))
        steps.saveStep(step("s", "t", "2026-10-20T08:00", listOf("a.jpg", "b.jpg")))
        val removed = steps.saveStep(step("s", "t", "2026-10-20T09:00", listOf("b.jpg", "c.jpg")))
        assertEquals(listOf("a.jpg"), removed)
        val s = steps.getStep("s")!!
        assertEquals(listOf("b.jpg", "c.jpg"), s.photos.map { it.fileName })
        assertEquals(LocalDateTime.parse("2026-10-20T09:00"), s.arrivedAt)
    }

    @Test fun draftsAreCountedApartAndCanBePublished() = runTest {
        trips.upsertTrip(trip("t", "2025-10-20"))
        steps.saveStep(step("s1", "t", "2025-10-20T09:00", listOf("a.jpg")))
        steps.saveStep(step("s2", "t", "2025-10-21T09:00", listOf("b.jpg", "c.jpg")).copy(isDraft = true))

        var s = trips.observeTripSummaries().first().single()
        assertEquals(1 to 1, s.stepCount to s.photoCount)
        assertEquals(1 to 2, s.draftCount to s.draftPhotoCount)
        assertTrue(steps.getStep("s2")!!.isDraft)

        assertTrue(steps.setDraft("s2", false, 9))
        assertEquals(9L, steps.getStep("s2")!!.updatedAt)
        s = trips.observeTripSummaries().first().single()
        assertEquals(2 to 3, s.stepCount to s.photoCount)
        assertEquals(0 to 0, s.draftCount to s.draftPhotoCount)
        assertEquals(false, steps.setDraft("missing", false, 9))
    }

    @Test fun chosenCoverWinsAndFallsBackToAutomaticWhenItsPhotoIsGone() = runTest {
        trips.upsertTrip(trip("t", "2025-10-20"))
        trips.upsertTrip(trip("o", "2025-06-07"))
        steps.saveStep(step("s1", "t", "2025-10-20T09:00", listOf("a.jpg")))
        steps.saveStep(step("s2", "t", "2025-10-21T09:00", listOf("b.jpg", "c.jpg")))
        steps.saveStep(step("o1", "o", "2025-06-07T09:00", listOf("o.jpg")))
        suspend fun cover() = trips.observeTripSummaries().first().first { it.trip.id == "t" }.coverPhotoFile

        assertEquals("a.jpg", cover())
        assertTrue(trips.setCover("t", "s2-c.jpg", 5))
        assertEquals("s2-c.jpg", trips.getTrip("t")!!.coverPhotoId)
        assertEquals("c.jpg", cover())
        // Editing the trip keeps the choice when the caller passes it on; re-saving the step keeps photo ids.
        trips.upsertTrip(trips.getTrip("t")!!.copy(title = "Hà Giang"))
        steps.saveStep(step("s2", "t", "2025-10-21T10:00", listOf("b.jpg", "c.jpg")))
        assertEquals("c.jpg", cover())
        // A photo of another trip is never this trip's cover.
        assertTrue(trips.setCover("t", "o1-o.jpg", 6))
        assertEquals("a.jpg", cover())
        // Photo removed from its step, or its step deleted: automatic again.
        trips.setCover("t", "s2-c.jpg", 7)
        steps.saveStep(step("s2", "t", "2025-10-21T10:00", listOf("b.jpg")))
        assertEquals("a.jpg", cover())
        trips.setCover("t", "s2-b.jpg", 8)
        assertEquals("b.jpg", cover())
        steps.deleteStep("s2")
        assertEquals("a.jpg", cover())
        // "Tự động".
        trips.setCover("t", "s1-a.jpg", 9)
        assertTrue(trips.setCover("t", null, 10))
        assertNull(trips.getTrip("t")!!.coverPhotoId)
        assertEquals("a.jpg", cover())
    }

    @Test fun deletingStepDeletesItsPhotos() = runTest {
        trips.upsertTrip(trip("t", "2026-10-20"))
        steps.saveStep(step("s", "t", "2026-10-20T08:00", listOf("a.jpg")))
        steps.saveStep(step("k", "t", "2026-10-21T08:00", listOf("k.jpg")))
        assertEquals(listOf("a.jpg"), steps.deleteStep("s"))
        assertNull(steps.getStep("s"))
        assertEquals(listOf("k.jpg"), steps.getSteps("t").flatMap { it.photos }.map { it.fileName })
    }

    @Test fun deletingTripCascadesToStepsAndPhotos() = runTest {
        trips.upsertTrip(trip("t", "2026-10-20"))
        trips.upsertTrip(trip("other", "2026-06-07"))
        steps.saveStep(step("s1", "t", "2026-10-20T08:00", listOf("a.jpg", "b.jpg")))
        steps.saveStep(step("s2", "t", "2026-10-21T08:00", listOf("c.jpg")))
        steps.saveStep(step("o1", "other", "2026-06-07T08:00", listOf("o.jpg")))

        val files = trips.deleteTrip("t")

        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), files.toSet())
        assertTrue(steps.getSteps("t").isEmpty())
        assertNull(steps.getStep("s1"))
        val photoRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM photos").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, photoRows)
        assertEquals(1, steps.getSteps("other").size)
    }
}
