package vn.lutrinh.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.map.basemap.BasemapProvider
import vn.lutrinh.app.map.basemap.Basemaps
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.reducedMotion

/*
 * The map composables of the app (docs/05-map.md). They draw through the map interface only
 * ([LocalMapEngine] -> [MapController]); the engine adapter (map.maplibre) does the rest (R35).
 */

/** One numbered pin on the map. A [draft] (R20) is a hollow pin outside the route: no legs to or from it. */
data class MapMarker(val id: String, val label: String, val point: GeoPoint, val highlighted: Boolean = false, val draft: Boolean = false)

/**
 * The basemap a map shows (R32/R33): the user's choice, or the offline-capable default while
 * the chosen one needs a network that is not there.
 */
@Composable
fun rememberShownBasemap(): BasemapProvider {
    val app = LocalContext.current.applicationContext as? LuTrinhApp ?: return Basemaps.registry.default
    val chosen by app.container.basemaps.chosen.collectAsStateWithLifecycle()
    val online by app.container.network.online.collectAsStateWithLifecycle()
    return app.container.basemaps.registry.shown(chosen, online)
}

/**
 * The required credit of the basemap shown (R34), on every map; on a map with road-following legs
 * also the router's credit (R36: OSM/ODbL), which opens the OSM "fix the map" page when tapped.
 */
@Composable
fun MapAttribution(basemap: BasemapProvider, modifier: Modifier = Modifier, routing: List<RoutingCredit> = emptyList()) {
    // Fixed 9 dp (not sp): the credit must not grow with the font scale and cover a small map (F25).
    val size = with(LocalDensity.current) { 9.dp.toSp() }
    val uri = LocalUriHandler.current
    Column(modifier.background(Palette.Card.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 1.dp), horizontalAlignment = Alignment.End) {
        routing.forEach { credit ->
            Text(
                text = credit.text + " · " + stringResource(R.string.routing_fix_map),
                fontSize = size,
                color = Palette.MapCredit,
                modifier = Modifier.clickable { runCatching { uri.openUri(credit.fixUrl) } },
            )
        }
        Text(text = basemap.attribution, fontSize = size, color = Palette.MapCredit)
    }
}

/** R36: the credits of the routers that made the shown road lines (by provider id). */
@Composable
fun rememberRoutingCredits(providerIds: Set<String>): List<RoutingCredit> {
    val app = LocalContext.current.applicationContext as? LuTrinhApp ?: return emptyList()
    return remember(providerIds) { providerIds.sorted().mapNotNull { app.container.routers.byId(it)?.credit }.distinct() }
}

/** Map images from the theme: numbered pins and transport-mode badges. */
@Composable
internal fun rememberMapIcons(): MapIcons {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val pinColors = PinColors(Palette.Waypoint.toArgb(), Palette.Trail.toArgb(), Palette.Forest.toArgb(), Palette.Signal.toArgb(), Palette.Forest.toArgb())
    val badges = rememberTransportBadges()
    return remember(pinColors, badges) {
        MapIcons(
            pin = { label, kind, selected ->
                MapIcon(pinImageId(label, kind, selected)) { pinBitmap(context, label, kind, selected, pinColors) }
            },
            badge = { mode, selected ->
                val id = mode.badgeId(selected)
                MapIcon(id) { badges.getValue(id) }
            },
        )
    }
}

/**
 * A live map of the active engine showing the current basemap; [render] runs after every
 * composition once the map exists. Draws the offline indicator and the basemap credit on top.
 */
@Composable
private fun EngineMap(
    interactive: Boolean,
    modifier: Modifier,
    listener: (MutableState<GeoPoint?>) -> MapListener,
    indicatorTop: Dp,
    routing: List<RoutingCredit> = emptyList(),
    render: (MapController) -> Unit,
) {
    val engine = LocalMapEngine.current
    val basemap = rememberShownBasemap()
    val center = remember { mutableStateOf<GeoPoint?>(null) }
    var controller by remember { mutableStateOf<MapController?>(null) }
    Box(modifier) {
        engine.MapSurface(interactive, Modifier) { c ->
            c.setListener(listener(center))
            controller = c
        }
        val c = controller
        SideEffect {
            if (c != null) {
                c.setBasemap(basemap.style)
                render(c)
            }
        }
        OfflineIndicator(center.value, Modifier.align(Alignment.TopStart).padding(top = indicatorTop))
        MapAttribution(basemap, Modifier.align(Alignment.BottomEnd), routing)
    }
}

/** Forwards taps to [onTap] and keeps [center] (for the offline indicator) at the camera centre. */
private fun mapListener(center: MutableState<GeoPoint?>, onTap: (MapHit?) -> Boolean) = object : MapListener {
    override fun onTap(hit: MapHit?) = onTap(hit)
    override fun onCameraIdle(camera: MapCamera) { center.value = camera.center }
}

/**
 * Trip map: numbered step pins + one line per leg joining them in the given order, styled by
 * the leg's transport mode ([legModes][i] = mode of the i-th leg between consecutive non-draft
 * markers, R12; draft markers are drawn hollow and skipped by the route, R20).
 * The camera fits the whole route whenever the set of pins changes (docs/05-map.md); on an
 * interactive map, a new [focusStepId] (default: the highlighted pin) or leg ([selectedLegTo])
 * moves the camera to it (R37: eased, framing the leg just travelled; a jump with system
 * animations off).
 */
@Composable
fun TripMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    legModes: List<TransportMode?> = emptyList(),
    /** R36: the line of each leg (road route or straight/arc, `TripDetail.legPaths`); empty = straight/arc. */
    legPaths: List<List<GeoPoint>> = emptyList(),
    /** R36: ids of the routers whose road lines are shown (their credit is drawn). */
    routedBy: Set<String> = emptySet(),
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
    /** Step the camera moves to (R37: the trip map passes the step its carousel settled on). */
    focusStepId: String? = markers.firstOrNull { it.highlighted }?.id,
    /** R37: emphasise the leg arriving at [focusStepId]. */
    showTravelledLeg: Boolean = false,
) {
    val reduced = reducedMotion()
    val routeColors = RouteColors(Palette.Signal.hex(), Palette.SignalDeep.hex())
    val icons = rememberMapIcons()
    val presenter = remember { TripMapPresenter(icons) }
    val markerClick = rememberUpdatedState(onMarkerClick)
    val legClick = rememberUpdatedState(onLegClick)
    EngineMap(
        interactive = interactive,
        modifier = modifier,
        listener = { center -> mapListener(center) { hit -> presenter.onTap(hit, markerClick.value, legClick.value) } },
        indicatorTop = 0.dp,
        routing = rememberRoutingCredits(if (showRoute) routedBy else emptySet()),
    ) { map ->
        presenter.markers = markers
        presenter.legModes = legModes
        presenter.legPaths = legPaths
        presenter.tracks = tracks
        presenter.stops = stopSuggestions
        presenter.showRoute = showRoute
        presenter.selectedLegTo = selectedLegTo
        presenter.fitAllSignal = fitAllSignal
        presenter.myLocation = myLocation
        presenter.myLocationSignal = myLocationSignal
        presenter.focusStep = focusStepId
        presenter.showTravelled = showTravelledLeg
        presenter.reducedMotion = reduced
        map.setRouteColors(routeColors)
        presenter.render(map, fitPaddingDp, fitExtraBottomDp, interactive)
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
    val icons = rememberMapIcons()
    val presenter = remember { PickerPresenter(icons) }
    val callback = rememberUpdatedState(onCenterChanged)
    EngineMap(
        interactive = true,
        modifier = modifier,
        listener = { center ->
            object : MapListener {
                override fun onCameraIdle(camera: MapCamera) {
                    center.value = camera.center
                    callback.value(camera.center)
                }
            }
        },
        indicatorTop = indicatorTop,
    ) { map -> presenter.render(map, initial, initialZoom, contextMarkers, moveTo) }
}

/** A request to fly the picker to [point]; a new instance each time, so the same place can be asked again. */
class PickerMove(val point: GeoPoint, val zoom: Double = MapConfig.PICKER_ZOOM)

/** One trip on the overall map: its steps in timeline order and the mode of each leg. */
data class MapTrip(
    val id: String,
    val color: Color,
    val points: List<GeoPoint>,
    /** legModes[i] = mode of points[i] -> points[i+1]; only flights change the shape (arc). */
    val legModes: List<TransportMode?>,
    /** R36: legPaths[i] = stored road line of leg i (with connectors), or empty/missing = straight/arc. */
    val legPaths: List<List<GeoPoint>> = emptyList(),
    /** R36: ids of the routers whose lines are in [legPaths]. */
    val routedBy: Set<String> = emptySet(),
)

/**
 * "Bản đồ của tôi": every trip as one line in its own colour plus a dot per step (R14).
 * Tapping a line or a dot reports the trip id. The camera fits all steps of all trips.
 */
@Composable
fun AllTripsMap(
    trips: List<MapTrip>,
    selectedId: String?,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fitPaddingDp: Int = 48,
    fitExtraBottomDp: Int = 0,
    fitExtraTopDp: Int = 0,
    /** Every recorded GPS track (R16), drawn above the trip lines and below the step dots. */
    tracks: List<TrackLine> = emptyList(),
    /** "Vị trí của tôi": the fix to draw (null = none) and a counter whose change flies the camera to it. */
    myLocation: MyLocationFix? = null,
    myLocationSignal: Int = 0,
) {
    val presenter = remember { AllTripsPresenter() }
    val click = rememberUpdatedState(onTripClick)
    EngineMap(
        interactive = true,
        modifier = modifier,
        listener = { center -> mapListener(center) { hit -> presenter.onTap(hit, click.value) } },
        // Below the overlay at the top of the map (e.g. the stats card of "Bản đồ của tôi").
        indicatorTop = if (fitExtraTopDp > 0) (fitExtraTopDp + 16).dp else 0.dp,
        routing = rememberRoutingCredits(trips.flatMapTo(HashSet()) { it.routedBy }),
    ) { map ->
        val density = map.density
        val p = (fitPaddingDp * density).toInt()
        presenter.trips = trips
        presenter.tracks = tracks
        presenter.selectedId = selectedId
        presenter.myLocation = myLocation
        presenter.myLocationSignal = myLocationSignal
        presenter.padding = MapInsets(p, p + (fitExtraTopDp * density).toInt(), p, p + (fitExtraBottomDp * density).toInt())
        presenter.render(map)
    }
}
