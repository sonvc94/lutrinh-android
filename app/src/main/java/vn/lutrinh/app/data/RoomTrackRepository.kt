package vn.lutrinh.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toDomain
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.repository.TrackRepository

/** Local-only storage of recorded tracks (R16). */
class RoomTrackRepository(private val db: AppDatabase) : TrackRepository {
    private val dao = db.trackDao()

    override fun observeTracks(tripId: String): Flow<List<Track>> =
        dao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observePoints(tripId: String): Flow<List<TrackPoint>> =
        dao.observePointsForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllPoints(): Flow<List<TrackPoint>> =
        dao.observeAllPoints().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTrack(id: String): Track? = dao.get(id)?.toDomain()

    override suspend fun openTracks(): List<Track> = dao.open().map { it.toDomain() }


    override suspend fun insertTrack(track: Track) = dao.insert(track.toEntity())

    override suspend fun updateTrack(track: Track) {
        dao.update(track.toEntity())
    }

    override suspend fun addPoint(track: Track, point: TrackPoint) = db.withTransaction {
        dao.insertPoint(point.toEntity())
        dao.update(track.toEntity())
        Unit
    }

    /** Points are deleted explicitly too, so nothing depends on the foreign-key pragma. */
    override suspend fun deleteTrack(id: String): Boolean = db.withTransaction {
        dao.deletePoints(id)
        dao.delete(id) > 0
    }
}
