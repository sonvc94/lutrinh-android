package vn.lutrinh.app.map

import android.content.Context
import android.graphics.RectF
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.domain.usecase.NewStepDefaults


/** One numbered pin on the map. */
data class MapMarker(val id: String, val label: String, val point: GeoPoint, val highlighted: Boolean = false)

private const val SRC_STEPS = "steps-src"
private const val SRC_ROUTE = "route-src"
private const val SRC_TRACKS = "tracks-src"
private const val SRC_STOPS = "stop-suggest-src"
private const val SRC_ME = "my-location-src"
private const val LAYER_STEPS = "steps-layer"
private const val SRC_LEG_BADGES = "leg-badges-src"
private const val LAYER_LEG_BADGES = "leg-badges-layer"

/** Value of the "mode" feature property for a leg without a transport mode. */
private const val NO_MODE = "none"

/** Fraction of the map height kept free at the bottom while a leg's mode sheet is open. */
private const val LEG_FOCUS_BOTTOM = 0.55

/** Creates a MapView bound to the composition's lifecycle. */
@Composable
internal fun rememberMapView(): MapView {
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


/** Required basemap credit (MapConfig.ATTRIBUTION), shown on every map. */
@Composable
fun OsmAttribution(modifier: Modifier = Modifier) {
    Text(
        text = MapConfig.ATTRIBUTION,
        // Fixed 9 dp (not sp): the credit must not grow with the font scale and cover a small map (F25).
        fontSize = with(LocalDensity.current) { 9.dp.toSp() },
        color = Color(0xFF4A524E),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.75f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Trip map: numbered step pins + one line per leg joining them in the given order, styled by
 * the leg's transport mode ([legModes][i] = mode of markers[i] -> markers[i+1], R12).
 * The camera fits the whole route whenever the set of pins changes (docs/05-map.md); on an
 * interactive map, selecting a pin ([MapMarker.highlighted]) or a leg ([selectedLegTo]) flies
 * the camera to it.
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
    /** Id of the marker where the selected leg ends (its mode sheet is open), or null. */
    selectedLegTo: String? = null,
    onMarkerClick: ((String) -> Unit)? = null,
    /** Tap on a leg's mode badge; receives the id of the marker where the leg ends. */
    onLegClick: ((String) -> Unit)? = null,
    /** Recorded GPS tracks of the trip (R16), drawn above the legs, below badges and pins. */
    tracks: List<TrackLine> = emptyList(),
    /** Stop suggestions (R17): rings above the tracks, below badges and pins. */
    stopSuggestions: List<StopMarker> = emptyList(),
    /** Each change flies the camera back to the whole route ("Xem toàn bộ hành trình"). */
    fitAllSignal: Int = 0,
    /** "Vị trí của tôi": the fix to draw (null = none) and a counter whose change flies the camera to it. */
    myLocation: MyLocationFix? = null,
    myLocationSignal: Int = 0,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val scheme = MaterialTheme.colorScheme
    val state = remember { TripMapState() }
    state.pinColors = PinColors(scheme.primary.toArgb(), scheme.secondary.toArgb(), scheme.tertiary.toArgb(), scheme.secondary.toArgb())
    val routeColors = RouteColors(scheme.primary.hex(), scheme.tertiary.hex())
    val badges = rememberTransportBadges()
    val clickHandler = rememberUpdatedState(onMarkerClick)
    val legClickHandler = rememberUpdatedState(onLegClick)
    val center = rememberCameraCenter()

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(interactive)
                    map.trackCenter(center)
                    map.loadBasemap { style ->
                        style.addSource(GeoJsonSource(SRC_ROUTE))
                        style.addSource(GeoJsonSource(SRC_TRACKS))
                        style.addSource(GeoJsonSource(SRC_STOPS))
                        style.addSource(GeoJsonSource(SRC_LEG_BADGES))
                        style.addSource(GeoJsonSource(SRC_STEPS))
                        badges.forEach { (id, bmp) -> style.addImage(id, bmp) }
                        style.addRouteLayers(SRC_ROUTE, routeColors)
                        style.addTrackLayers(SRC_TRACKS)
                        style.addStopSuggestionLayers(SRC_STOPS)
                        style.addMyLocationLayers(SRC_ME)
                        // Badges give way to pins and to each other (short legs lose theirs).
                        style.addLayer(
                            SymbolLayer(LAYER_LEG_BADGES, SRC_LEG_BADGES).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconSize(SYMBOL_SIZE_BY_ZOOM),
                                PropertyFactory.iconAllowOverlap(false),
                                PropertyFactory.iconIgnorePlacement(false),
                                PropertyFactory.iconPadding(1f),
                                PropertyFactory.symbolSortKey(Expression.get("order")),
                                PropertyFactory.symbolAvoidEdges(true),
                            ),
                        )
                        // Pins never hide each other, but they do hide basemap labels and badges.
                        style.addLayer(
                            SymbolLayer(LAYER_STEPS, SRC_STEPS).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconSize(SYMBOL_SIZE_BY_ZOOM),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(false),
                                PropertyFactory.symbolSortKey(Expression.get("order")),
                            ),
                        )
                        state.map = map
                        state.style = style
                        state.render(context, mapView, fitPaddingDp, fitExtraBottomDp, interactive)
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
                        // A badge first, else the leg line itself (a short leg may have lost its badge).
                        val to = map.queryRenderedFeatures(box, LAYER_LEG_BADGES).firstOrNull()?.getStringProperty("to")
                            ?: map.queryRenderedFeatures(RectF(p.x - r / 2, p.y - r / 2, p.x + r / 2, p.y + r / 2), *RouteLayerIds.ALL)
                                .firstOrNull()?.getStringProperty("to")
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
                state.tracks = tracks
                state.stops = stopSuggestions
                state.showRoute = showRoute
                state.selectedLegTo = selectedLegTo
                state.fitAllSignal = fitAllSignal
                state.myLocation = myLocation
                state.myLocationSignal = myLocationSignal
                state.render(context, mapView, fitPaddingDp, fitExtraBottomDp, interactive)
            },
        )
        OfflineIndicator(center.value, Modifier.align(Alignment.TopStart))
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class TripMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var markers: List<MapMarker> = emptyList()
    var legModes: List<TransportMode?> = emptyList()
    var tracks: List<TrackLine> = emptyList()
    var stops: List<StopMarker> = emptyList()
    var showRoute: Boolean = true
    var selectedLegTo: String? = null
    var fitAllSignal: Int = 0
    private var fittedAllSignal: Int = 0
    var myLocation: MyLocationFix? = null
    var myLocationSignal: Int = 0
    private var shownMyLocationSignal: Int = 0
    var pinColors = PinColors(0, 0, 0, 0)
    private var fittedKey: Pair<List<GeoPoint>, Int>? = null
    private var padding = intArrayOf(0, 0, 0, 0)
    private var focusedStep: String? = null
    private var focusedLeg: String? = null
    private var userFocused = false

    fun render(context: Context, mapView: MapView, paddingDp: Int, extraBottomDp: Int, interactive: Boolean) {
        val map = map ?: return
        val style = style ?: return
        if (!style.isFullyLoaded) return
        val routed = showRoute && markers.size >= 2
        val features = markers.mapIndexed { index, m ->
            val kind = when {
                !routed -> PinKind.STEP
                index == 0 -> PinKind.START
                index == markers.lastIndex -> PinKind.END
                else -> PinKind.STEP
            }
            val icon = pinImageId(m.label, kind, m.highlighted)
            if (style.getImage(icon) == null) style.addImage(icon, pinBitmap(context, m.label, kind, m.highlighted, pinColors))
            Feature.fromGeometry(Point.fromLngLat(m.point.longitude, m.point.latitude)).apply {
                addStringProperty("id", m.id)
                addStringProperty("icon", icon)
                // Later steps on top; start/end above the rest; the selected pin above all.
                val order = when {
                    m.highlighted -> markers.size + 2
                    kind == PinKind.START || kind == PinKind.END -> markers.size + 1
                    else -> index
                }
                addNumberProperty("order", order)
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_STEPS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val legs = if (showRoute) markers.zipWithNext() else emptyList()
        val lines = mutableListOf<Feature>()
        val badges = mutableListOf<Feature>()
        val paths = mutableListOf<List<GeoPoint>>()
        legs.forEachIndexed { i, (from, to) ->
            val mode = legModes.getOrNull(i)
            val selected = to.id == selectedLegTo
            val path = Polyline.simplify(LegGeometry.path(from.point, to.point, mode))
            paths += path
            lines += Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addStringProperty("mode", mode?.code ?: NO_MODE)
                addStringProperty("kind", mode.legLine().code)
                addBooleanProperty("selected", selected)
                addStringProperty("to", to.id)
            }
            if (mode != null) {
                val mid = Polyline.pointAt(path, 0.5)
                badges += Feature.fromGeometry(Point.fromLngLat(mid.longitude, mid.latitude)).apply {
                    addStringProperty("icon", mode.badgeId(selected))
                    addStringProperty("to", to.id)
                    // The selected badge is placed first so it never gives way.
                    addNumberProperty("order", if (selected) -1 else i)
                }
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        style.getSourceAs<GeoJsonSource>(SRC_LEG_BADGES)?.setGeoJson(FeatureCollection.fromFeatures(badges))
        style.getSourceAs<GeoJsonSource>(SRC_TRACKS)?.setGeoJson(tracks.toFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(stops.toStopFeatureCollection())

        style.showMyLocation(SRC_ME, myLocation)
        val density = context.resources.displayMetrics.density
        // "Vị trí của tôi": fly to the fix once per tap; the carousel resize must not re-fit afterwards.
        if (myLocationSignal != shownMyLocationSignal) {
            shownMyLocationSignal = myLocationSignal
            myLocation?.let { userFocused = true; flyToMyLocation(map, it, padding) }
        }
        // Re-fit when the route changes or when the bottom overlay is first measured / resized.
        // Finished tracks count in the fit; the live one does not (a new fix every few seconds
        // must not move the camera under the user's finger).
        val routePoints = if (paths.isEmpty()) markers.map { it.point } else paths.flatten()
        val points = routePoints + tracks.filterNot { it.live }.flatMap { it.points }
        val key = points to extraBottomDp
        val selectedStep = markers.firstOrNull { it.highlighted }?.id
        if (key != fittedKey) {
            val routeChanged = key.first != fittedKey?.first
            fittedKey = key
            val p = (paddingDp * density).toInt()
            padding = intArrayOf(p, p, p, p + (extraBottomDp * density).toInt())
            // A new route always re-fits; a new bottom overlay height (the carousel is measured, or
            // changes height while scrolling) only until the user has selected something.
            if (routeChanged || !userFocused) {
                val pad = padding.copyOf()
                mapView.doOnLayout { fitCamera(map, points, pad) }
            }
            if (routeChanged) {
                focusedStep = selectedStep
                focusedLeg = selectedLegTo
                userFocused = false
                return
            }
        }
        if (!interactive) return
        // "Xem toàn bộ hành trình": fly back to the whole route (the selection was cleared by the caller).
        if (fitAllSignal != fittedAllSignal) {
            fittedAllSignal = fitAllSignal
            focusedStep = selectedStep
            focusedLeg = selectedLegTo
            focusPoints(map, points, padding, MapConfig.FIT_MAX_ZOOM)
            return
        }
        // Selecting a step or a leg flies the camera there (only on a change, never on the first fit).
        if (selectedStep != focusedStep) {
            focusedStep = selectedStep
            val m = markers.firstOrNull { it.id == selectedStep }
            if (m != null) {
                userFocused = true
                focusStep(map, m.point, padding)
            }
        }
        if (selectedLegTo != focusedLeg) {
            focusedLeg = selectedLegTo
            val i = legs.indexOfFirst { it.second.id == selectedLegTo }
            if (i >= 0) {
                userFocused = true
                val p = (paddingDp * density).toInt()
                val bottom = maxOf(padding[3], (mapView.height * LEG_FOCUS_BOTTOM).toInt())
                focusPoints(map, paths[i], intArrayOf(p, p, p, bottom), MapConfig.LEG_FOCUS_MAX_ZOOM)
            }
        }
    }
}

/** Flies to [point] at CameraMath.focusZoom, centred in the area left free by [padding]. */
internal fun focusStep(map: MapLibreMap, point: GeoPoint, padding: IntArray) {
    val from = map.cameraPosition
    val zoom = CameraMath.focusZoom(from.zoom)
    val target = CameraPosition.Builder()
        .target(LatLng(point.latitude, point.longitude))
        .zoom(zoom)
        .padding(padding.map { it.toDouble() }.toDoubleArray())
        .build()
    val km = from.target?.let { Geo.distanceKm(GeoPoint(it.latitude, it.longitude), point) } ?: 0.0
    map.animateCamera(CameraUpdateFactory.newCameraPosition(target), CameraMath.flyDurationMs(km, from.zoom, zoom))
}

/** Flies so that all [points] fit inside the area left free by [padding], zoom at most [maxZoom]. */
internal fun focusPoints(map: MapLibreMap, points: List<GeoPoint>, padding: IntArray, maxZoom: Double) {
    if (points.size < 2) return
    val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
    val pos = map.getCameraForLatLngBounds(bounds, padding) ?: return
    val from = map.cameraPosition
    val zoom = pos.zoom.coerceAtMost(maxZoom)
    val target = CameraPosition.Builder(pos).zoom(zoom).padding(padding.map { it.toDouble() }.toDoubleArray()).build()
    val km = from.target?.let { t -> pos.target?.let { Geo.distanceKm(GeoPoint(t.latitude, t.longitude), GeoPoint(it.latitude, it.longitude)) } } ?: 0.0
    map.animateCamera(CameraUpdateFactory.newCameraPosition(target), CameraMath.flyDurationMs(km, from.zoom, zoom))
}

/**
 * Camera rule C1-C3 (docs/05-map.md). [padding] = left, top, right, bottom in px; the visible
 * area is the map minus this padding, so the route never sits under overlays. [points] = every
 * point drawn (steps and flight arcs).
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
    moveTo: PickerMove? = null,
    /** Space the caller keeps at the top (quick-place chips) so the offline indicator stays visible. */
    indicatorTop: Dp = 0.dp,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val callback = rememberUpdatedState(onCenterChanged)
    val scheme = MaterialTheme.colorScheme
    val pinColors = PinColors(scheme.primary.toArgb(), scheme.secondary.toArgb(), scheme.tertiary.toArgb(), scheme.secondary.toArgb())
    val state = remember { PickerState() }
    val center = rememberCameraCenter()
    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(true)
                    map.trackCenter(center)
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(initial.latitude, initial.longitude), initialZoom),
                    )
                    map.loadBasemap { style ->
                        style.addSource(
                            GeoJsonSource(
                                SRC_STEPS,
                                FeatureCollection.fromFeatures(
                                    contextMarkers.map { m ->
                                        val icon = pinImageId(m.label, PinKind.CONTEXT, false)
                                        if (style.getImage(icon) == null) style.addImage(icon, pinBitmap(context, m.label, PinKind.CONTEXT, false, pinColors))
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
                if (target != null && target !== state.lastMoveTo && map != null) {
                    state.lastMoveTo = target
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(target.point.latitude, target.point.longitude), MapConfig.PICKER_ZOOM),
                    )
                }
            },
        )
        OfflineIndicator(center.value, Modifier.align(Alignment.TopStart).padding(top = indicatorTop))
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class PickerState {
    var map: MapLibreMap? = null
    var lastMoveTo: PickerMove? = null
}

/** A request to fly the picker to [point]; a new instance each time, so the same place can be asked again. */
class PickerMove(val point: GeoPoint)
