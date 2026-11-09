package vn.lutrinh.app.data

import androidx.room.withTransaction
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.repository.TripImportRepository

/** R25: an imported trip goes in as one transaction, so a failure leaves nothing behind. */
class RoomTripImportRepository(private val db: AppDatabase) : TripImportRepository {
    override suspend fun insertTrip(trip: Trip, steps: List<Step>, tracks: List<Pair<Track, List<TrackPoint>>>) = db.withTransaction {
        db.tripDao().upsert(trip.toEntity())
        for (s in steps) db.stepDao().upsert(s.toEntity())
        for ((t, points) in tracks) {
            db.trackDao().insert(t.toEntity())
            db.trackDao().insertPoints(points.map { it.toEntity() })
        }
    }
}
