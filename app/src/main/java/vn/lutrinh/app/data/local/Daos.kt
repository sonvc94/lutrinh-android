package vn.lutrinh.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query(
        """
        SELECT t.*,
          (SELECT COUNT(*) FROM steps s WHERE s.trip_id = t.id) AS step_count,
          (SELECT COUNT(*) FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id) AS photo_count,
          (SELECT p.file_name FROM photos p JOIN steps s ON p.step_id = s.id WHERE s.trip_id = t.id
             ORDER BY s.arrived_at, s.created_at, s.id, p.position LIMIT 1) AS cover_file
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

    @Query("SELECT file_name FROM photos WHERE step_id = :stepId")
    suspend fun photoFiles(stepId: String): List<String>

    @Query("DELETE FROM photos WHERE step_id = :stepId")
    suspend fun deletePhotos(stepId: String)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)
}
