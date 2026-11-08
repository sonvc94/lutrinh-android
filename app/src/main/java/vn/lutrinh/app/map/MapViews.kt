package vn.lutrinh.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import vn.lutrinh.app.domain.model.GeoPoint

/** One numbered pin on the map. */
data class MapMarker(val id: String, val label: String, val point: GeoPoint, val highlighted: Boolean = false)

private const val SRC_STEPS = "steps-src"
private const val SRC_ROUTE = "route-src"
private const val LAYER_STEPS = "steps-layer"
private const val LAYER_ROUTE = "route-layer"
private const val LAYER_ROUTE_CASING = "route-casing-layer"

/** Creates a MapView bound to the composition's lifecycle. */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
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
    // Attribution is drawn by [OsmAttribution] so it is always visible as plain text.
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

/** Required "© OpenStreetMap contributors" label, shown on every map. */
@Composable
fun OsmAttribution(modifier: Modifier = Modifier) {
    Text(
        text = MapConfig.ATTRIBUTION,
        fontSize = 10.sp,
        color = Color(0xFF333333),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.8f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Trip map: numbered step pins + a route line joining them in the given order.
 * The camera fits all pins whenever the set of pins changes (docs/05-map.md).
 */
@Composable
fun TripMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    showRoute: Boolean = true,
    interactive: Boolean = true,
    fitPaddingDp: Int = 56,
    onMarkerClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val state = remember { TripMapState() }
    val clickHandler = rememberUpdatedState(onMarkerClick)

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(interactive)
                    map.setStyle(Style.Builder().fromJson(MapConfig.styleJson())) { style ->
                        style.addSource(GeoJsonSource(SRC_ROUTE))
                        style.addSource(GeoJsonSource(SRC_STEPS))
                        style.addLayer(
                            LineLayer(LAYER_ROUTE_CASING, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.WHITE),
                                PropertyFactory.lineWidth(7f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        style.addLayer(
                            LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(routeColor),
                                PropertyFactory.lineWidth(4f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        style.addLayer(
                            SymbolLayer(LAYER_STEPS, SRC_STEPS).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                PropertyFactory.symbolSortKey(Expression.get("order")),
                            ),
                        )
                        state.map = map
                        state.style = style
                        state.render(context, mapView, pinColor, highlightColor, fitPaddingDp)
                    }
                    map.addOnMapClickListener { latLng ->
                        val handler = clickHandler.value ?: return@addOnMapClickListener false
                        val p = map.projection.toScreenLocation(latLng)
                        val r = 22f * context.resources.displayMetrics.density
                        val hit = map.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), LAYER_STEPS)
                        val id = hit.firstOrNull()?.getStringProperty("id") ?: return@addOnMapClickListener false
                        handler(id)
                        true
                    }
                }
                mapView
            },
            update = {
                state.markers = markers
                state.showRoute = showRoute
                state.render(context, mapView, pinColor, highlightColor, fitPaddingDp)
            },
        )
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class TripMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var markers: List<MapMarker> = emptyList()
    var showRoute: Boolean = true
    private var fittedKey: List<GeoPoint>? = null

    fun render(context: Context, mapView: MapView, pinColor: Int, highlightColor: Int, paddingDp: Int) {
        val map = map ?: return
        val style = style ?: return
        if (!style.isFullyLoaded) return
        val features = markers.mapIndexed { index, m ->
            val icon = "pin-${m.label}-${m.highlighted}"
            if (style.getImage(icon) == null) {
                style.addImage(icon, pinBitmap(context, m.label, if (m.highlighted) highlightColor else pinColor, m.highlighted))
            }
            Feature.fromGeometry(Point.fromLngLat(m.point.longitude, m.point.latitude)).apply {
                addStringProperty("id", m.id)
                addStringProperty("icon", icon)
                addNumberProperty("order", if (m.highlighted) markers.size + 1 else index)
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_STEPS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val route = style.getSourceAs<GeoJsonSource>(SRC_ROUTE)
        if (showRoute && markers.size >= 2) {
            route?.setGeoJson(
                Feature.fromGeometry(
                    LineString.fromLngLats(markers.map { Point.fromLngLat(it.point.longitude, it.point.latitude) }),
                ),
            )
        } else {
            route?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
        val key = markers.map { it.point }
        if (key != fittedKey) {
            fittedKey = key
            mapView.doOnLayout { fitCamera(map, key, paddingDp * context.resources.displayMetrics.density) }
        }
    }
}

/** Camera rule C1-C3 (docs/05-map.md). */
private fun fitCamera(map: MapLibreMap, points: List<GeoPoint>, paddingPx: Float) {
    when (points.size) {
        0 -> map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(16.0, 106.0)).zoom(MapConfig.OVERVIEW_ZOOM).build(),
            ),
        )
        1 -> map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), MapConfig.SINGLE_POINT_ZOOM),
        )
        else -> {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
            val p = paddingPx.toInt()
            val pos = map.getCameraForLatLngBounds(bounds, intArrayOf(p, p, p, p))
            if (pos != null) {
                val zoom = pos.zoom.coerceAtMost(MapConfig.FIT_MAX_ZOOM)
                map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(pos).zoom(zoom).build()))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, p))
            }
        }
    }
}

/** Round numbered pin drawn in code (no image assets). */
private fun pinBitmap(context: Context, label: String, color: Int, big: Boolean): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = ((if (big) 34 else 28) * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r, paint)
    paint.color = color
    c.drawCircle(r, r, r - 2.5f * d, paint)
    paint.color = android.graphics.Color.WHITE
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = (if (label.length > 2) 10 else 13) * d * (if (big) 1.15f else 1f)
    val y = r - (paint.descent() + paint.ascent()) / 2
    c.drawText(label, r, y, paint)
    return bmp
}

/**
 * Location picker map: the user pans the map under a fixed centre pin (drawn by the caller).
 * [onCenterChanged] fires whenever the camera stops moving.
 */
@Composable
fun PickerMap(
    initial: GeoPoint,
    initialZoom: Double,
    contextMarkers: List<MapMarker>,
    onCenterChanged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    moveTo: GeoPoint? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val callback = rememberUpdatedState(onCenterChanged)
    val pinColor = MaterialTheme.colorScheme.secondary.toArgb()
    val state = remember { PickerState() }
    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(true)
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(initial.latitude, initial.longitude), initialZoom),
                    )
                    map.setStyle(Style.Builder().fromJson(MapConfig.styleJson())) { style ->
                        style.addSource(
                            GeoJsonSource(
                                SRC_STEPS,
                                FeatureCollection.fromFeatures(
                                    contextMarkers.map { m ->
                                        val icon = "ctx-${m.label}"
                                        if (style.getImage(icon) == null) style.addImage(icon, pinBitmap(context, m.label, pinColor, false))
                                        Feature.fromGeometry(Point.fromLngLat(m.point.longitude, m.point.latitude)).apply {
                                            addStringProperty("icon", icon)
                                        }
                                    },
                                ),
                            ),
                        )
                        style.addLayer(
                            SymbolLayer(LAYER_STEPS, SRC_STEPS).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconOpacity(0.75f),
                            ),
                        )
                    }
                    map.addOnCameraIdleListener {
                        val t = map.cameraPosition.target ?: return@addOnCameraIdleListener
                        callback.value(GeoPoint(t.latitude, t.longitude))
                    }
                    state.map = map
                }
                mapView
            },
            update = {
                val target = moveTo
                val map = state.map
                if (target != null && target != state.lastMoveTo && map != null) {
                    state.lastMoveTo = target
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), MapConfig.PICKER_ZOOM),
                    )
                }
            },
        )
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class PickerState {
    var map: MapLibreMap? = null
    var lastMoveTo: GeoPoint? = null
}
