package vn.lutrinh.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.data.local.toDomain
import vn.lutrinh.app.data.local.toEntity
import vn.lutrinh.app.domain.catalog.RouteImportPlan
import vn.lutrinh.app.domain.catalog.RouteLink
import java.io.File

/**
 * R40 storage: downloaded routes become trips in ONE transaction, after every file was copied
 * from staging into the photo folder (a failure removes the copies and leaves nothing behind),
 * and `route_links` remembers where each came from.
 */
class RoomRouteRepository(
    private val db: AppDatabase,
    private val photoDir: File,
    private val deleteFiles: suspend (Collection<String>) -> Unit,
) {
    private val links = db.routeLinkDao()

    fun observeLink(tripId: String): Flow<RouteLink?> = links.observe(tripId).map { it?.toDomain() }

    fun observeLinkedTrips(): Flow<Set<String>> = links.observeTripIds().map { it.toSet() }

    suspend fun link(tripId: String): RouteLink? = links.get(tripId)?.toDomain()

    suspend fun allLinks(): List<RouteLink> = links.all().mapNotNull { it.toDomain() }

    suspend fun saveLink(link: RouteLink) = links.upsert(link.toEntity())

    /**
     * Writes [plan]: copies each staged file (`stagedFile(sha256)`) to its new photo name, then
     * trip + steps + photos + leg routes + link in one transaction. With [replacing] (the id of an
     * unedited downloaded trip, "Thay bản trong máy") that trip is deleted in the same transaction
     * and its old files removed afterwards.
     */
    suspend fun import(plan: RouteImportPlan, stagedFile: (String) -> File, replacing: String? = null): String {
        val copied = ArrayList<String>()
        try {
            withContext(Dispatchers.IO) {
                photoDir.mkdirs()
                for ((name, sha) in plan.files) {
                    stagedFile(sha).copyTo(File(photoDir, name), overwrite = false)
                    copied += name
                }
            }
            val oldFiles = db.withTransaction {
                val old = replacing?.let { id -> db.tripDao().photoFiles(id).also { db.tripDao().delete(id) } }.orEmpty()
                db.tripDao().upsert(plan.trip.toEntity())
                for (s in plan.steps) {
                    db.stepDao().upsert(s.toEntity())
                    db.stepDao().insertPhotos(s.photos.map { it.toEntity() })
                }
                for (r in plan.legRoutes) db.legRouteDao().upsert(r.toEntity())
                links.upsert(plan.link.toEntity())
                old
            }
            deleteFiles(oldFiles)
            return plan.trip.id
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { copied.forEach { File(photoDir, it).delete() } }
            throw e
        }
    }
}
