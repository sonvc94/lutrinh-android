package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.DistanceSource
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import vn.lutrinh.app.domain.usecase.TripOrder
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.routeOf
import vn.lutrinh.app.domain.usecase.statsOf
import java.time.LocalDate
import java.time.LocalDateTime

class OrderingTest {
    private val trip = Trip("t", "Hà Giang", "", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23), 0, 0)

    private fun step(id: String, at: String, created: Long = 0, lat: Double = 23.0, lng: Double = 105.0) =
        Step(id, "t", id, "", GeoPoint(lat, lng), LocalDateTime.parse(at), created, created)

    @Test fun stepsSortByArrivalThenCreationThenId() {
        val steps = listOf(
            step("c", "2026-10-21T09:00", created = 5),
            step("a", "2026-10-20T08:00"),
            step("b2", "2026-10-21T09:00", created = 1),
            step("b1", "2026-10-21T09:00", created = 1),
        )
        assertEquals(listOf("a", "b1", "b2", "c"), steps.inTimelineOrder().map { it.id })
    }

    @Test fun routeFollowsTimelineNotInsertionOrder() {
        val late = step("late", "2026-10-22T08:00", lat = 23.2)
        val early = step("early", "2026-10-20T08:00", lat = 22.8)
        assertEquals(listOf(22.8, 23.2), routeOf(listOf(late, early)).map { it.latitude })
    }

    @Test fun tripsNewestStartFirstThenNewestCreated() {
        val a = trip.copy(id = "a", startDate = LocalDate.of(2025, 6, 7), createdAt = 1)
        val b = trip.copy(id = "b", startDate = LocalDate.of(2025, 10, 20), createdAt = 1)
        val c = trip.copy(id = "c", startDate = LocalDate.of(2025, 10, 20), createdAt = 9)
        assertEquals(listOf("c", "b", "a"), listOf(a, b, c).sortedWith(TripOrder).map { it.id })
    }

    @Test fun haversineHaGiangToNinhBinh() {
        val km = Geo.distanceKm(GeoPoint(22.8233, 104.9836), GeoPoint(20.2506, 105.9745))
        assertEquals(303.0, km, 5.0)
        assertEquals(0.0, Geo.routeLengthKm(listOf(GeoPoint(1.0, 1.0))), 0.0)
    }

    @Test fun statsCountDaysInclusive() {
        val s = statsOf(trip, listOf(step("a", "2026-10-20T08:00")))
        assertEquals(4L, s.days)
        assertEquals(1, s.stepCount)
        assertNull(statsOf(trip.copy(endDate = null), emptyList()).days)
    }

    private fun track(km: Double, status: TrackStatus = TrackStatus.ENDED) =
        Track("k", "t", status, 0, null, 0, null, 0, km * 1000, 10, false, 0, 0)

    @Test fun distanceIsTheStraightLineUnlessGpsTracksCoverTheRoute() {
        val steps = listOf(step("a", "2026-10-20T08:00", lat = 22.8), step("b", "2026-10-21T08:00", lat = 23.8)) // ≈ 111 km
        val straight = statsOf(trip, steps)
        assertEquals(DistanceSource.STRAIGHT_LINE, straight.distanceSource)
        // 51,5 km recorded on one day of a 111 km route: not the trip's length.
        assertEquals(DistanceSource.STRAIGHT_LINE, statsOf(trip, steps, listOf(track(51.5))).distanceSource)
        // tracks as long as the route (open ones never count): the real road is shown.
        val gps = statsOf(trip, steps, listOf(track(70.0), track(60.0), track(500.0, TrackStatus.RECORDING)))
        assertEquals(DistanceSource.GPS, gps.distanceSource)
        assertEquals(130.0, gps.distanceKm, 0.001)
        // no steps to compare with: any recorded distance is the best number there is
        assertEquals(DistanceSource.GPS, statsOf(trip, emptyList(), listOf(track(5.0))).distanceSource)
    }

    @Test fun newStepDefaultsToNowWhenTravellingToday() {
        val now = LocalDateTime.of(2026, 10, 21, 14, 37, 12)
        assertEquals(LocalDateTime.of(2026, 10, 21, 14, 37), NewStepDefaults.arrival(trip, emptyList(), now))
    }

    @Test fun newStepDefaultsToDayAfterLastStepClampedToTrip() {
        val now = LocalDateTime.of(2027, 1, 1, 12, 0)
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), NewStepDefaults.arrival(trip, emptyList(), now))
        val steps = listOf(step("a", "2026-10-21T18:00"))
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 0), NewStepDefaults.arrival(trip, steps, now))
        val atEnd = listOf(step("z", "2026-10-23T18:00"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), NewStepDefaults.arrival(trip, atEnd, now))
    }

    @Test fun pickerOpensAtLastStep() {
        assertNull(NewStepDefaults.location(emptyList()))
        val steps = listOf(step("b", "2026-10-22T08:00", lat = 23.2), step("a", "2026-10-20T08:00", lat = 22.8))
        assertEquals(23.2, NewStepDefaults.location(steps)!!.latitude, 0.0)
    }
}
