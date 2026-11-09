package vn.lutrinh.app.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.storage.FileSource
import java.io.File

/**
 * Offline basemap areas (docs/04 R15, docs/05 "Offline maps"), on top of MapLibre's offline
 * store: MapLibre keeps style, sprite, fonts and tiles in its own SQLite database and serves
 * them to every map when the network is gone. This class only adds the app's bookkeeping:
 * which [OfflineArea] a stored region is (JSON metadata: key, name, description, paused) and
 * the [states] the "Bản đồ offline" screen and the map indicator show.
 *
 * Process-wide (one per app, in AppContainer), so downloads go on after leaving the screen.
 * [start] runs at app start: it re-attaches stored regions, resumes downloads the app was
 * killed in the middle of (unless paused) and, once per process when online, revalidates the
 * complete ones so their tiles keep matching the style's current tileset.
 * All MapLibre callbacks arrive on the main thread; state is only touched there.
 */
class OfflineMaps(private val context: Context, private val network: NetworkMonitor) {
    private class Entry(val region: OfflineRegion, val area: OfflineArea, var paused: Boolean) {
        var status: OfflineRegionStatus? = null
        var problem: OfflineProblem? = null
        /** Sticky: once complete, a later revalidation does not make the area "missing" again. */
        var complete = false
        var completeBytes = 0L
        var deleting = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { OfflineManager.getInstance(context) }
    private val entries = linkedMapOf<String, Entry>()
    /** Areas being created, or refused before anything was stored (no network, storage full). */
    private val pending = linkedMapOf<String, OfflineAreaState>()
    private var started = false
    private var revalidated = false

    private val _states = MutableStateFlow<Map<String, OfflineAreaState>>(emptyMap())
    /** Every known area by key: stored ones, plus ones being created or refused. */
    val states: StateFlow<Map<String, OfflineAreaState>> = _states.asStateFlow()

    private val _storeBytes = MutableStateFlow(0L)
    /** Size of MapLibre's database file (offline areas + the ambient cache of online viewing). */
    val storeBytes: StateFlow<Long> = _storeBytes.asStateFlow()

    fun start() {
        if (started) return
        started = true
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions.orEmpty().forEach { region ->
                    val entry = attach(region) ?: return@forEach
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status == null) return
                            entry.status = status
                            if (isComplete(status)) {
                                entry.complete = true
                                entry.completeBytes = status.completedResourceSize
                                revalidate()
                            } else if (!entry.paused) {
                                // Killed in the middle of a download: go on (R15).
                                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                            }
                            publish()
                        }
                        override fun onError(error: String?) { Log.w(TAG, "status ${entry.area.key}: $error") }
                    })
                }
                publish()
            }
            override fun onError(error: String) { Log.w(TAG, "list: $error") }
        })
        scope.launch {
            network.online.collect { online ->
                if (online) {
                    // Nudge downloads that were waiting for the network.
                    entries.values.filter { !it.complete && !it.paused && it.problem in RETRYABLE }.forEach {
                        it.problem = null
                        it.region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }
                    revalidate()
                }
                publish()
            }
        }
    }

    /** Starts (or resumes) downloading [area]. Refused without network or storage (R15). */
    fun download(area: OfflineArea) {
        entries[area.key]?.let { resume(area.key); return }
        if (pending[area.key]?.phase == OfflinePhase.DOWNLOADING) return
        val problem = when {
            !network.online.value -> OfflineProblem.NO_NETWORK
            !OfflineConfig.fitsInStorage(OfflineConfig.estimateBytes(area.bounds), storeDir().usableSpace) -> OfflineProblem.STORAGE_FULL
            !OfflineConfig.withinLimit(area.bounds) -> OfflineProblem.OTHER
            else -> null
        }
        if (problem != null) {
            pending[area.key] = OfflineAreaState(area, OfflinePhase.FAILED, problem = problem)
            publish()
            return
        }
        pending[area.key] = OfflineAreaState(area, OfflinePhase.DOWNLOADING)
        publish()
        val b = area.bounds
        val definition = OfflineTilePyramidRegionDefinition(
            MapConfig.STYLE_URL,
            LatLngBounds.from(b.north, b.east, b.south, b.west),
            OfflineConfig.MIN_ZOOM.toDouble(),
            OfflineConfig.MAX_ZOOM.toDouble(),
            context.resources.displayMetrics.density,
            false,
        )
        manager.createOfflineRegion(definition, metadata(area, paused = false), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                if (pending.remove(area.key) == null) {
                    // Cancelled while it was being created.
                    offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = Unit
                        override fun onError(error: String) { Log.w(TAG, "delete ${area.key}: $error") }
                    })
                    return
                }
                attach(offlineRegion)?.region?.setDownloadState(OfflineRegion.STATE_ACTIVE)
                publish()
            }
            override fun onError(error: String) {
                Log.w(TAG, "create ${area.key}: $error")
                pending[area.key] = OfflineAreaState(area, OfflinePhase.FAILED, problem = OfflinePhases.problemOf("", error))
                publish()
            }
        })
    }

    fun pause(key: String) {
        val e = entries[key] ?: return
        e.paused = true
        e.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        e.region.updateMetadata(metadata(e.area, paused = true), metadataCallback)
        publish()
    }

    fun resume(key: String) {
        val e = entries[key] ?: return
        if (e.complete) return
        e.paused = false
        e.problem = null
        e.region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        e.region.updateMetadata(metadata(e.area, paused = false), metadataCallback)
        publish()
    }

    /** Cancels a download or deletes a downloaded area; its space is freed (R15). */
    fun delete(key: String) {
        if (pending.remove(key) != null) publish()
        val e = entries[key] ?: return
        if (e.deleting) return
        e.deleting = true
        e.region.setObserver(null)
        e.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        e.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                entries.remove(key)
                publish()
                freeSpace()
            }
            override fun onError(error: String) {
                Log.w(TAG, "delete $key: $error")
                e.deleting = false
                publish()
            }
        })
        publish()
    }

    /**
     * MapLibre does not erase a deleted region's resources: they become ambient cache (kept up to
     * 50 MB). Clearing the ambient cache and packing the database is what actually frees the
     * space (R15); downloaded areas are untouched, only tiles cached by online viewing go.
     */
    private fun freeSpace() {
        manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {
                manager.packDatabase(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = publish()
                    override fun onError(message: String) { Log.w(TAG, "pack: $message"); publish() }
                })
            }
            override fun onError(message: String) { Log.w(TAG, "clear cache: $message") }
        })
    }

    /** Free bytes on the volume of MapLibre's database. */
    fun freeBytes(): Long = storeDir().usableSpace

    private fun attach(region: OfflineRegion): Entry? {
        val meta = runCatching { JSONObject(String(region.metadata)) }.getOrNull()
        val bounds = (region.definition as? OfflineTilePyramidRegionDefinition)?.bounds
        val key = meta?.optString("key").orEmpty()
        if (meta == null || bounds == null || key.isEmpty()) {
            Log.w(TAG, "region ${region.id} without metadata; left alone")
            return null
        }
        val area = OfflineConfig.readyMade(key) ?: OfflineArea(
            key = key,
            name = meta.optString("name"),
            description = meta.optString("description"),
            bounds = GeoBounds(bounds.latitudeSouth, bounds.longitudeWest, bounds.latitudeNorth, bounds.longitudeEast),
        )
        val entry = Entry(region, area, paused = meta.optBoolean("paused"))
        entries[key] = entry
        region.setDeliverInactiveMessages(false)
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                entry.status = status
                if (status.completedResourceCount > 0 && entry.problem in RETRYABLE) entry.problem = null
                if (isComplete(status)) {
                    entry.complete = true
                    entry.completeBytes = status.completedResourceSize
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                }
                publish()
            }

            override fun onError(error: OfflineRegionError) {
                val problem = OfflinePhases.problemOf(error.reason, error.message)
                Log.w(TAG, "download $key: ${error.reason} ${error.message}")
                entry.problem = problem
                // MapLibre retries network/server errors by itself; others stop the download.
                if (problem !in RETRYABLE) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                publish()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                entry.problem = OfflineProblem.OTHER
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                publish()
            }
        })
        return entry
    }

    /** Once per process when online: re-checks complete areas against the server (R15). */
    private fun revalidate() {
        if (revalidated || !network.online.value) return
        val complete = entries.values.filter { it.complete && it.status != null }
        if (complete.isEmpty()) return
        revalidated = true
        complete.forEach { it.region.setDownloadState(OfflineRegion.STATE_ACTIVE) }
    }

    private fun publish() {
        val online = network.online.value
        val stored = entries.filterValues { !it.deleting }.mapValues { (_, e) ->
            val s = e.status
            OfflineAreaState(
                area = e.area,
                phase = OfflinePhases.of(stored = true, complete = e.complete, paused = e.paused, problem = e.problem, online = online),
                completedBytes = if (e.complete) maxOf(e.completeBytes, s?.completedResourceSize ?: 0) else s?.completedResourceSize ?: 0,
                completedResources = s?.completedResourceCount ?: 0,
                requiredResources = s?.requiredResourceCount ?: 0,
                problem = e.problem,
            )
        }
        _states.value = pending + stored
        _storeBytes.value = File(storeDir(), DB_FILE).length()
    }

    private fun storeDir() = File(FileSource.getResourcesCachePath(context))

    private fun isComplete(s: OfflineRegionStatus) =
        s.isRequiredResourceCountPrecise && s.requiredResourceCount > 0 && s.completedResourceCount >= s.requiredResourceCount

    private fun metadata(area: OfflineArea, paused: Boolean): ByteArray = JSONObject()
        .put("key", area.key)
        .put("name", area.name)
        .put("description", area.description)
        .put("paused", paused)
        .toString()
        .toByteArray()

    private val metadataCallback = object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
        override fun onUpdate(metadata: ByteArray) = Unit
        override fun onError(error: String) { Log.w(TAG, "metadata: $error") }
    }

    private companion object {
        const val TAG = "OfflineMaps"
        const val DB_FILE = "mbgl-offline.db"
        val RETRYABLE = setOf(null, OfflineProblem.NO_NETWORK, OfflineProblem.SERVER)
    }
}
