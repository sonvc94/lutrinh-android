package vn.lutrinh.app.map.maplibre

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.RasterLayer
import vn.lutrinh.app.map.MapCamera
import vn.lutrinh.app.map.MapConfig
import vn.lutrinh.app.map.MapController
import vn.lutrinh.app.map.MapEngine
import vn.lutrinh.app.map.NetworkMonitor
import vn.lutrinh.app.map.OfflineMapStore
import vn.lutrinh.app.map.basemap.BasemapPlanner
import vn.lutrinh.app.map.basemap.BasemapStyle
import kotlin.coroutines.resume

/**
 * The MapLibre Android adapter of [MapEngine] (docs/05-map.md "Map interface", R35): the only
 * package of the app that imports MapLibre.
 */
object MapLibreEngine : MapEngine {
    private const val TAG = "StaticTripMap"

    /**
     * Initialises MapLibre once per process. The only network traffic of the app is the
     * tile download done here by MapLibre; we attach our own User-Agent to it.
     */
    override fun initialize(context: Context) {
        MapLibre.getInstance(context)
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", MapConfig.USER_AGENT).build())
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }

    @Composable
    override fun MapSurface(interactive: Boolean, modifier: Modifier, onReady: (MapController) -> Unit) {
        val mapView = rememberMapView()
        val ready = rememberUpdatedState(onReady)
        val density = LocalContext.current.resources.displayMetrics.density
        AndroidView(
            modifier = modifier,
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(interactive)
                    ready.value(MapLibreController(mapView, map, density))
                }
                mapView
            },
        )
    }

    override fun createOfflineStore(context: Context, network: NetworkMonitor, basemap: BasemapStyle): OfflineMapStore =
        MapLibreOfflineStore(context, network, basemap.styleUrl)

    /** A MapSnapshotter of the same style and tile store as the live maps (so an offline area works in airplane mode). */
    override suspend fun snapshot(
        context: Context, basemap: BasemapStyle, camera: MapCamera, width: Int, height: Int, pixelRatio: Float,
    ): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            // The snapshotter cannot list a style's layers: the declared plan (layer ids) is used.
            val plan = BasemapPlanner.declared(basemap)
            val builder = styleBuilder(basemap)
            basemap.imagery?.let { imagery ->
                builder.withSource(imagery.toSource())
                val layer = RasterLayer(IMAGERY_LAYER, IMAGERY_SOURCE)
                if (plan.imageryAbove != null) builder.withLayerAbove(layer, plan.imageryAbove) else builder.withLayerAt(layer, 0)
            }
            val options = MapSnapshotter.Options(width, height)
                .withStyleBuilder(builder)
                .withCameraPosition(CameraPosition.Builder().target(LatLng(camera.center.latitude, camera.center.longitude)).zoom(camera.zoom).build())
                .withPixelRatio(pixelRatio)
                .withLogo(false)
            val snapshotter = MapSnapshotter(context.applicationContext, options)
            snapshotter.setObserver(object : MapSnapshotter.Observer {
                // Same repaint, Vietnamese-first labels and R30 filter as the live maps; the R30
                // archipelago labels come with the style builder.
                override fun onDidFinishLoadingStyle() = applyPlan(plan, basemap) { snapshotter.getLayer(it) }
                override fun onStyleImageMissing(imageName: String) = Unit
            })
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { runCatching { snapshotter.cancel() } } }
            snapshotter.start(
                { snap -> if (cont.isActive) cont.resume(snap.bitmap.copy(Bitmap.Config.ARGB_8888, false)) },
                { error ->
                    Log.w(TAG, "snapshot failed: $error")
                    if (cont.isActive) cont.resume(null)
                },
            )
        }
    }
}

/** Creates a MapView bound to the composition's lifecycle. */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        // Basemap colour while the style and tiles load (instead of MapLibre's grey).
        val options = MapLibreMapOptions.createFromAttributes(context)
            .foregroundLoadColor(android.graphics.Color.parseColor(MapConfig.BACKGROUND))
        MapView(context, options).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun MapLibreMap.applyCommonSettings(interactive: Boolean) {
    uiSettings.isLogoEnabled = false
    // Attribution is drawn by MapAttribution so it is always visible as plain text (R34).
    uiSettings.isAttributionEnabled = false
    uiSettings.isCompassEnabled = false
    uiSettings.isRotateGesturesEnabled = false
    uiSettings.isTiltGesturesEnabled = false
    uiSettings.setAllGesturesEnabled(interactive)
    if (!interactive) {
        uiSettings.isRotateGesturesEnabled = false
        uiSettings.isTiltGesturesEnabled = false
    }
    setMaxZoomPreference(MapConfig.MAX_ZOOM)
}
