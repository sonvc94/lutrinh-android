package vn.lutrinh.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toDomain
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRouteRepository

/** Local storage of leg routes (R36); rows go with their arrival step (foreign key, cascade). */
class RoomLegRouteRepository(db: AppDatabase) : LegRouteRepository {
    private val dao = db.legRouteDao()

    override fun observeAll(): Flow<List<LegRoute>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeForTrip(tripId: String): Flow<List<LegRoute>> =
        dao.observeForTrip(tripId).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun save(route: LegRoute) = dao.upsert(route.toEntity())
}
