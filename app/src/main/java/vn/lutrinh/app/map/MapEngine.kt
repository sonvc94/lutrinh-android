package vn.lutrinh.app.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import vn.lutrinh.app.map.basemap.BasemapStyle
import vn.lutrinh.app.map.maplibre.MapLibreEngine

/**
 * A map engine (renderer SDK) behind the app's map interface (docs/05-map.md "Map interface",
 * R35): live map views ([MapController]), offline areas ([OfflineMapStore]) and static snapshots
 * for sharing. Basemap *providers* (who serves the tiles) are separate: map.basemap.
 */
interface MapEngine {
    /** Once per process, before any map is shown (network client, User-Agent). */
    fun initialize(context: Context)

    /**
     * A live map filling [modifier]. [onReady] runs once with its controller; the view follows the
     * composition's lifecycle. [interactive] = false: no gestures (preview maps).
     */
    @Composable
    fun MapSurface(interactive: Boolean, modifier: Modifier, onReady: (MapController) -> Unit)

    /** The offline-area store, downloading tiles of [basemap] (a provider that allows offline use). */
    fun createOfflineStore(context: Context, network: NetworkMonitor, basemap: BasemapStyle): OfflineMapStore

    /**
     * The basemap alone at [camera], [width] × [height] logical px rendered at [pixelRatio], or
     * null when the style or tiles cannot be loaded. Served from the offline store when there is
     * no network.
     */
    suspend fun snapshot(context: Context, basemap: BasemapStyle, camera: MapCamera, width: Int, height: Int, pixelRatio: Float): Bitmap?
}

/**
 * Offline basemap areas (docs/04 R15, docs/05 "Offline maps"): download, pause, resume, delete,
 * and what the "Bản đồ offline" screen and the map indicator show. One per process.
 */
interface OfflineMapStore {
    /** Every known area by key: stored ones, plus ones being created or refused. */
    val states: StateFlow<Map<String, OfflineAreaState>>

    /** Bytes of the engine's whole tile store (offline areas + the cache of online viewing). */
    val storeBytes: StateFlow<Long>

    /** At app start: re-attaches stored areas, resumes interrupted downloads, revalidates once online. */
    fun start()

    /** Starts (or resumes) downloading [area]. Refused without network or storage, or above R31's cap. */
    fun download(area: OfflineArea)
    fun pause(key: String)
    fun resume(key: String)

    /** Cancels a download or deletes a downloaded area; its space is freed. */
    fun delete(key: String)

    /** Free bytes on the volume of the tile store. */
    fun freeBytes(): Long
}

/** THE registration point of the map engine (R35): change the engine here, nowhere else. */
object MapEngines {
    val active: MapEngine = MapLibreEngine
}

/** The engine the map composables use; tests may provide another. */
val LocalMapEngine = staticCompositionLocalOf<MapEngine> { MapEngines.active }
