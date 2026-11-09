package vn.lutrinh.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toDomain
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TripRepository
import java.time.LocalDate

/** Local-only implementation. A future sync layer can wrap or replace these classes. */
class RoomTripRepository(private val db: AppDatabase) : TripRepository {
    private val dao = db.tripDao()

    override fun observeTripSummaries(): Flow<List<TripSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeTrip(id: String): Flow<Trip?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getTrip(id: String): Trip? = dao.get(id)?.toDomain()

    override suspend fun upsertTrip(trip: Trip) = dao.upsert(trip.toEntity())

    override suspend fun deleteTrip(id: String): List<String> = db.withTransaction {
        val files = dao.photoFiles(id)
        dao.delete(id) // steps + photos go by ON DELETE CASCADE
        files
    }
}

class RoomStepRepository(private val db: AppDatabase) : StepRepository {
    private val dao = db.stepDao()

    override fun observeSteps(tripId: String): Flow<List<Step>> =
        dao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observeStep(id: String): Flow<Step?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getStep(id: String): Step? = dao.get(id)?.toDomain()

    override suspend fun getSteps(tripId: String): List<Step> = dao.getForTrip(tripId).map { it.toDomain() }

    override suspend fun stepDateRange(tripId: String): ClosedRange<LocalDate>? {
        val row = dao.dateRange(tripId)
        val first = row.firstDay ?: return null
        val last = row.lastDay ?: return null
        return LocalDate.parse(first)..LocalDate.parse(last)
    }

    override suspend fun saveStep(step: Step): List<String> = db.withTransaction { writeStep(step) }

    override suspend fun saveStepAndTrip(step: Step, trip: Trip): List<String> = db.withTransaction {
        db.tripDao().upsert(trip.toEntity())
        writeStep(step)
    }

    private suspend fun writeStep(step: Step): List<String> {
        val before = dao.photoFiles(step.id)
        dao.upsert(step.toEntity())
        dao.deletePhotos(step.id)
        dao.insertPhotos(step.photos.map { it.copy(stepId = step.id).toEntity() })
        val after = step.photos.map { it.fileName }.toSet()
        return before.filterNot { it in after }
    }

    override suspend fun deleteStep(id: String): List<String> = db.withTransaction {
        val files = dao.photoFiles(id)
        dao.delete(id)
        files
    }

    override fun observeAllSteps(): Flow<List<Step>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setArrivalTransport(stepId: String, mode: TransportMode?, now: Long): Boolean =
        dao.setTransportMode(stepId, mode?.code, now) > 0
}
