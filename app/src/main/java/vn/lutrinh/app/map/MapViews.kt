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
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.NewStepDefaults

/** One numbered pin on the map. */
data class MapMarker(val id: String, val label: String, val point: GeoPoint, val highlighted: Boolean = false)

private const val SRC_STEPS = "steps-src"
private const val SRC_ROUTE = "route-src"
private const val LAYER_STEPS = "steps-layer"
private const val LAYER_ROUTE = "route-layer"
private const val LAYER_ROUTE_DASHED = "route-dashed-layer"
private const val LAYER_ROUTE_DOTTED = "route-dotted-layer"
private const val LAYER_ROUTE_CASING = "route-casing-layer"
private const val SRC_LEG_BADGES = "leg-badges-src"
private const val LAYER_LEG_BADGES = "leg-badges-layer"

/** Value of the "mode" feature property for a leg without a transport mode. */
private const val NO_MODE = "none"

private fun modesWith(dash: LineDash): Array<String> =
    TransportMode.entries.filter { TransportStyle.of(it).dash == dash }.map { it.code }.toTypedArray()

/** Creates a MapView bound to the composition's lifecycle. */
@Composable
internal fun rememberMapView(): MapView {
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

internal fun MapLibreMap.applyCommonSettings(interactive: Boolean) {
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
 * Trip map: numbered step pins + one line per leg joining them in the given order, styled by
 * the leg's transport mode ([legModes][i] = mode of markers[i] -> markers[i+1], R12).
 * The camera fits all pins whenever the set of pins changes (docs/05-map.md).
 */
@Composable
fun TripMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    legModes: List<TransportMode?> = emptyList(),
    showRoute: Boolean = true,
    interactive: Boolean = true,
    fitPaddingDp: Int = 56,
    /** Extra room at the bottom for UI drawn over the map (e.g. a card carousel), in dp. */
    fitExtraBottomDp: Int = 0,
    onMarkerClick: ((String) -> Unit)? = null,
    /** Tap on a leg's mode badge; receives the id of the marker where the leg ends. */
    onLegClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val routeColor = MaterialTheme.colorScheme.primary
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val state = remember { TripMapState() }
    state.noModeColor = routeColor.hex()
    val badges = rememberTransportBadges()
    val clickHandler = rememberUpdatedState(onMarkerClick)
    val legClickHandler = rememberUpdatedState(onLegClick)

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(interactive)
                    map.setStyle(Style.Builder().fromJson(MapConfig.styleJson())) { style ->
                        style.addSource(GeoJsonSource(SRC_ROUTE))
                        style.addSource(GeoJsonSource(SRC_LEG_BADGES))
                        style.addSource(GeoJsonSource(SRC_STEPS))
                        badges.forEach { (mode, bmp) -> style.addImage(mode.badgeId(), bmp) }
                        style.addLayer(
                            LineLayer(LAYER_ROUTE_CASING, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.WHITE),
                                PropertyFactory.lineWidth(7f),
                                PropertyFactory.lineOpacity(0.9f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        // Line dash patterns are not data-driven in MapLibre Native: one layer per dash kind.
                        style.addLayer(
                            LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                                PropertyFactory.lineWidth(4f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ).withFilter(
                                Expression.`in`(Expression.get("mode"), Expression.literal(modesWith(LineDash.SOLID) + NO_MODE)),
                            ),
                        )
                        style.addLayer(
                            LineLayer(LAYER_ROUTE_DASHED, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                                PropertyFactory.lineWidth(4f),
                                PropertyFactory.lineDasharray(arrayOf(2.2f, 1.6f)),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ).withFilter(Expression.`in`(Expression.get("mode"), Expression.literal(modesWith(LineDash.DASHED)))),
                        )
                        style.addLayer(
                            LineLayer(LAYER_ROUTE_DOTTED, SRC_ROUTE).withProperties(
                                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                                PropertyFactory.lineWidth(4.5f),
                                // Zero-length dashes with round caps = dots.
                                PropertyFactory.lineDasharray(arrayOf(0f, 1.8f)),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            ).withFilter(Expression.`in`(Expression.get("mode"), Expression.literal(modesWith(LineDash.DOTTED)))),
                        )
                        style.addLayer(
                            SymbolLayer(LAYER_LEG_BADGES, SRC_LEG_BADGES).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
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
                        state.render(context, mapView, pinColor, highlightColor, fitPaddingDp, fitExtraBottomDp)
                    }
                    map.addOnMapClickListener { latLng ->
                        val handler = clickHandler.value ?: return@addOnMapClickListener false
                        val p = map.projection.toScreenLocation(latLng)
                        val r = 22f * context.resources.displayMetrics.density
                        val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                        val hit = map.queryRenderedFeatures(box, LAYER_STEPS)
                        val id = hit.firstOrNull()?.getStringProperty("id")
                        if (id != null) {
                            handler(id)
                            return@addOnMapClickListener true
                        }
                        val legHandler = legClickHandler.value ?: return@addOnMapClickListener false
                        val to = map.queryRenderedFeatures(box, LAYER_LEG_BADGES).firstOrNull()?.getStringProperty("to")
                            ?: return@addOnMapClickListener false
                        legHandler(to)
                        true
                    }
                }
                mapView
            },
            update = {
                state.markers = markers
                state.legModes = legModes
                state.showRoute = showRoute
                state.render(context, mapView, pinColor, highlightColor, fitPaddingDp, fitExtraBottomDp)
            },
        )
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class TripMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var markers: List<MapMarker> = emptyList()
    var legModes: List<TransportMode?> = emptyList()
    var noModeColor: String = "#1F5C45"
    var showRoute: Boolean = true
    private var fittedKey: Pair<List<GeoPoint>, Int>? = null

    fun render(context: Context, mapView: MapView, pinColor: Int, highlightColor: Int, paddingDp: Int, extraBottomDp: Int) {
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
        val legs = if (showRoute) markers.zipWithNext() else emptyList()
        val lines = mutableListOf<Feature>()
        val badges = mutableListOf<Feature>()
        legs.forEachIndexed { i, (from, to) ->
            val mode = legModes.getOrNull(i)
            val path = LegGeometry.path(from.point, to.point, mode)
            lines += Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addStringProperty("mode", mode?.code ?: NO_MODE)
                addStringProperty("color", mode?.let { TransportStyle.of(it).hex } ?: noModeColor)
                addStringProperty("to", to.id)
            }
            if (mode != null) {
                val mid = path[path.size / 2].takeIf { path.size > 2 }
                    ?: GeoPoint((from.point.latitude + to.point.latitude) / 2, (from.point.longitude + to.point.longitude) / 2)
                badges += Feature.fromGeometry(Point.fromLngLat(mid.longitude, mid.latitude)).apply {
                    addStringProperty("icon", mode.badgeId())
                    addStringProperty("to", to.id)
                }
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        style.getSourceAs<GeoJsonSource>(SRC_LEG_BADGES)?.setGeoJson(FeatureCollection.fromFeatures(badges))
        // Re-fit when the pins change or when the bottom overlay is first measured / resized.
        val points = markers.map { it.point }
        val key = points to extraBottomDp
        if (key != fittedKey) {
            fittedKey = key
            val density = context.resources.displayMetrics.density
            val p = (paddingDp * density).toInt()
            val padding = intArrayOf(p, p, p, p + (extraBottomDp * density).toInt())
            mapView.doOnLayout { fitCamera(map, points, padding) }
        }
    }
}

/**
 * Camera rule C1-C3 (docs/05-map.md). [padding] = left, top, right, bottom in px; the visible
 * area is the map minus this padding, so pins never sit under overlays.
 */
internal fun fitCamera(map: MapLibreMap, points: List<GeoPoint>, padding: IntArray) {
    when (points.size) {
        0 -> map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(NewStepDefaults.FALLBACK_CENTER.latitude, NewStepDefaults.FALLBACK_CENTER.longitude)).zoom(MapConfig.OVERVIEW_ZOOM).build(),
            ),
        )
        // One pin: centred in the whole view, which is far above a bottom overlay.
        1 -> map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), MapConfig.SINGLE_POINT_ZOOM),
        )
        else -> {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
            val pos = map.getCameraForLatLngBounds(bounds, padding)
            if (pos != null) {
                val zoom = pos.zoom.coerceAtMost(MapConfig.FIT_MAX_ZOOM)
                map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(pos).zoom(zoom).build()))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding[0], padding[1], padding[2], padding[3]))
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
