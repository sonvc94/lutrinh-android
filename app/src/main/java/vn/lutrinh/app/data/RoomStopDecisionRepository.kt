package vn.lutrinh.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toDomain
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.repository.StopDecisionRepository

/** Local-only storage of the answers to stop suggestions (R17). */
class RoomStopDecisionRepository(db: AppDatabase) : StopDecisionRepository {
    private val dao = db.stopDecisionDao()

    override fun observeDecisions(tripId: String): Flow<List<StopDecision>> =
        dao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveDecision(decision: StopDecision) = dao.upsert(decision.toEntity())
}
