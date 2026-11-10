package vn.lutrinh.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    /**
     * R20: counts are of published steps (drafts counted apart). R21: the cover is the chosen
     * photo while it still belongs to one of the trip's steps, else the first photo of the first
     * step in timeline order (drafts included, as before drafts existed). R29: a video is never the cover.
     */
    @Query(
        """
        SELECT t.*,
          (SELECT COUNT(*) FROM steps s WHERE s.trip_id = t.id AND s.is_draft = 0) AS step_count,
          (SELECT COUNT(*) FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id AND s.is_draft = 0) AS photo_count,
          (SELECT COUNT(*) FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id AND s.is_draft = 0 AND p.media_type = 'video') AS video_count,
          (SELECT COUNT(*) FROM steps s WHERE s.trip_id = t.id AND s.is_draft = 1) AS draft_count,
          (SELECT COUNT(*) FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id AND s.is_draft = 1) AS draft_photo_count,
          COALESCE(
            (SELECT p.file_name FROM photos p JOIN steps s ON p.step_id = s.id
               WHERE s.trip_id = t.id AND p.id = t.cover_photo_id AND p.media_type = 'photo'),
            (SELECT p.file_name FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id AND p.media_type = 'photo'
               ORDER BY s.arrived_at, s.created_at, s.id, p.position, p.created_at, p.id LIMIT 1)
          ) AS cover_file
        FROM trips t
        ORDER BY t.start_date DESC, t.created_at DESC, t.id
        """,
    )
    fun observeSummaries(): Flow<List<TripSummaryRow>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripEntity?

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE trips SET cover_photo_id = :photoId, updated_at = :now WHERE id = :id")
    suspend fun setCover(id: String, photoId: String?, now: Long): Int

    @Query("SELECT p.file_name FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = :tripId")
    suspend fun photoFiles(tripId: String): List<String>
}

@Dao
interface StepDao {
    @Transaction
    @Query("SELECT * FROM steps WHERE trip_id = :tripId ORDER BY arrived_at, created_at, id")
    fun observeForTrip(tripId: String): Flow<List<StepWithPhotos>>

    @Transaction
    @Query("SELECT * FROM steps WHERE trip_id = :tripId ORDER BY arrived_at, created_at, id")
    suspend fun getForTrip(tripId: String): List<StepWithPhotos>

    @Transaction
    @Query("SELECT * FROM steps WHERE id = :id")
    fun observe(id: String): Flow<StepWithPhotos?>

    @Transaction
    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun get(id: String): StepWithPhotos?

    @Query(
        "SELECT MIN(substr(arrived_at, 1, 10)) AS first_day, MAX(substr(arrived_at, 1, 10)) AS last_day " +
            "FROM steps WHERE trip_id = :tripId",
    )
    suspend fun dateRange(tripId: String): DateRangeRow

    @Upsert
    suspend fun upsert(step: StepEntity)

    @Query("DELETE FROM steps WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    @Query("SELECT * FROM steps ORDER BY trip_id, arrived_at, created_at, id")
    fun observeAll(): Flow<List<StepWithPhotos>>

    @Query("UPDATE steps SET transport_mode = :mode, updated_at = :now WHERE id = :id")
    suspend fun setTransportMode(id: String, mode: String?, now: Long): Int

    @Query("UPDATE steps SET is_draft = :draft, updated_at = :now WHERE id = :id")
    suspend fun setDraft(id: String, draft: Boolean, now: Long): Int

    @Query("SELECT file_name FROM photos WHERE step_id = :stepId")
    suspend fun photoFiles(stepId: String): List<String>

    @Query("DELETE FROM photos WHERE step_id = :stepId")
    suspend fun deletePhotos(stepId: String)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE trip_id = :tripId ORDER BY started_at DESC, id")
    fun observeForTrip(tripId: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT p.* FROM track_points p JOIN tracks t ON p.track_id = t.id WHERE t.trip_id = :tripId " +
            "ORDER BY t.started_at, p.track_id, p.segment, p.recorded_at, p.id",
    )
    fun observePointsForTrip(tripId: String): Flow<List<TrackPointEntity>>

    @Query(
        "SELECT p.* FROM track_points p JOIN tracks t ON p.track_id = t.id " +
            "ORDER BY t.trip_id, t.started_at, p.track_id, p.segment, p.recorded_at, p.id",
    )
    fun observeAllPoints(): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE status != 'ended'")
    suspend fun open(): List<TrackEntity>


    @Insert
    suspend fun insert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity): Int

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE track_id = :trackId")
    suspend fun deletePoints(trackId: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface StopDecisionDao {
    @Query(
        "SELECT d.* FROM stop_decisions d JOIN tracks t ON d.track_id = t.id WHERE t.trip_id = :tripId " +
            "ORDER BY d.track_id, d.start_at",
    )
    fun observeForTrip(tripId: String): Flow<List<StopDecisionEntity>>

    @Upsert
    suspend fun upsert(decision: StopDecisionEntity)
}

@Dao
interface LegRouteDao {
    @Query("SELECT * FROM leg_routes")
    fun observeAll(): Flow<List<LegRouteEntity>>

    @Query("SELECT r.* FROM leg_routes r JOIN steps s ON r.to_step_id = s.id WHERE s.trip_id = :tripId")
    fun observeForTrip(tripId: String): Flow<List<LegRouteEntity>>

    @Upsert
    suspend fun upsert(route: LegRouteEntity)
}
