package vn.lutrinh.app.map

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import vn.lutrinh.app.domain.usecase.Polyline

/*
 * What the map composables put on a [MapController] and how they move its camera
 * (docs/05-map.md "Camera rules", "Layers"). Engine-neutral: unit-tested with a fake controller
 * and ported as-is to iOS.
 */

/** Makes the map images: a numbered pin and a transport-mode badge. */
internal class MapIcons(
    val pin: (label: String, kind: PinKind, selected: Boolean) -> MapIcon,
    val badge: (mode: TransportMode, selected: Boolean) -> MapIcon,
)

/** Fraction of the map height kept free at the bottom while a leg's mode sheet is open. */
internal const val LEG_FOCUS_BOTTOM = 0.55

/** Camera rules C1-C3 and the fly-to moves shared by the maps. */
internal object MapCameraRules {
    /**
     * C1-C3: no point = overview of Vietnam; one point = centred at SINGLE_POINT_ZOOM in the whole
     * view; more = all [points] inside the map minus [padding], zoom at most FIT_MAX_ZOOM.
     */
    fun fit(map: MapController, points: List<GeoPoint>, padding: MapInsets) = when (points.size) {
        0 -> map.moveTo(NewStepDefaults.FALLBACK_CENTER, MapConfig.OVERVIEW_ZOOM)
        1 -> map.moveTo(points[0], MapConfig.SINGLE_POINT_ZOOM)
        else -> map.fitPoints(points, padding, MapConfig.FIT_MAX_ZOOM)
    }

    /**
     * R37: eases to [point] centred in the area left free by [padding], zoomed to show [region]
     * (the leg just travelled) around it, at most STEP_MAX_ZOOM; a jump when [reducedMotion].
     */
    fun focusStep(map: MapController, point: GeoPoint, region: List<GeoPoint>, padding: MapInsets, reducedMotion: Boolean) =
        map.animateTo(CameraMath.stepPlan(point, region, map.camera, map.widthPx, map.heightPx, map.density, padding, reducedMotion))

    /** Flies to [fix] at MY_LOCATION_ZOOM at least, centred in the area left free by [padding]. */
    fun flyToMyLocation(map: MapController, fix: MyLocationFix, padding: MapInsets, animation: CameraAnimation = CameraAnimation.FLY) {
        val zoom = maxOf(map.camera?.zoom ?: 0.0, MapConfig.MY_LOCATION_ZOOM).coerceAtMost(MapConfig.MAX_ZOOM)
        map.moveTo(fix.point, zoom, padding, animation)
    }
}

/** TripMap: pins, legs, badges, tracks, stops, my location and the camera rules of a trip. */
internal class TripMapPresenter(private val icons: MapIcons) {
    var markers: List<MapMarker> = emptyList()
    var legModes: List<TransportMode?> = emptyList()
    /** R36: the line of each leg (road route or straight/arc); a missing entry is drawn straight/arc. */
    var legPaths: List<List<GeoPoint>> = emptyList()
    var tracks: List<TrackLine> = emptyList()
    var stops: List<StopMarker> = emptyList()
    var showRoute: Boolean = true
    var selectedLegTo: String? = null
    /** Step the camera shows (R37: on the trip map it follows the carousel once it settles). */
    var focusStep: String? = null
    /** R37: emphasise the leg arriving at [focusStep] (trip map only). */
    var showTravelled: Boolean = false
    /** System animations off: camera moves jump (R37). */
    var reducedMotion: Boolean = false
    var fitAllSignal: Int = 0
    var myLocation: MyLocationFix? = null
    var myLocationSignal: Int = 0

    private var fittedAllSignal = 0
    private var shownMyLocationSignal = 0
    private var fittedKey: Pair<List<GeoPoint>, List<Int>>? = null
    private var padding = MapInsets.NONE
    private var focusedStep: String? = null
    private var focusedLeg: String? = null
    private var userFocused = false

    /** A tap: a pin selects the step, a leg (badge or line) opens its mode; only with a step handler, as before. */
    fun onTap(hit: MapHit?, onMarkerClick: ((String) -> Unit)?, onLegClick: ((String) -> Unit)?): Boolean {
        if (onMarkerClick == null) return false
        return when (hit) {
            is MapHit.Pin -> { onMarkerClick(hit.id); true }
            is MapHit.Leg -> onLegClick?.let { it(hit.to); true } ?: false
            else -> false
        }
    }

    fun render(map: MapController, paddingDp: Int, extraBottomDp: Int, interactive: Boolean, extraTopDp: Int = 0, extraRightDp: Int = 0) {
        // R20: drafts are pins, not route points.
        val route = markers.filterNot { it.draft }
        val routed = showRoute && route.size >= 2
        map.setPins(
            markers.mapIndexed { index, m ->
                val kind = when {
                    m.draft -> PinKind.DRAFT
                    !routed -> PinKind.STEP
                    m.id == route.first().id -> PinKind.START
                    m.id == route.last().id -> PinKind.END
                    else -> PinKind.STEP
                }
                // Later steps on top; start/end above the rest; the selected pin above all.
                val order = when {
                    m.highlighted -> markers.size + 2
                    kind == PinKind.START || kind == PinKind.END -> markers.size + 1
                    else -> index
                }
                MapPin(m.id, m.point, icons.pin(m.label, kind, m.highlighted), order)
            },
        )
        val legs = if (showRoute) route.zipWithNext() else emptyList()
        val lines = mutableListOf<MapLeg>()
        val badges = mutableListOf<MapBadge>()
        val paths = mutableListOf<List<GeoPoint>>()
        legs.forEachIndexed { i, (from, to) ->
            val mode = legModes.getOrNull(i)
            val selected = to.id == selectedLegTo
            val path = Polyline.simplify(legPaths.getOrNull(i)?.takeIf { it.size >= 2 } ?: LegGeometry.path(from.point, to.point, mode))
            paths += path
            lines += MapLeg(to.id, path, mode.legLine(), selected, travelled = showTravelled && to.id == focusStep)
            // The selected badge is placed first so it never gives way.
            if (mode != null) badges += MapBadge(to.id, Polyline.pointAt(path, 0.5), icons.badge(mode, selected), if (selected) -1 else i)
        }
        map.setLegs(lines, badges)
        map.setTracks(tracks)
        map.setStopSuggestions(stops)
        map.setMyLocation(myLocation)

        val density = map.density
        // R37: with system animations off every camera move of this map jumps.
        val fly = if (reducedMotion) CameraAnimation.NONE else CameraAnimation.FLY
        // "Vị trí của tôi": fly to the fix once per tap; the carousel resize must not re-fit afterwards.
        if (myLocationSignal != shownMyLocationSignal) {
            shownMyLocationSignal = myLocationSignal
            myLocation?.let { userFocused = true; MapCameraRules.flyToMyLocation(map, it, padding, fly) }
        }
        // Re-fit when the route changes or when the bottom overlay is first measured / resized.
        // Finished tracks count in the fit; the live one does not (a new fix every few seconds
        // must not move the camera under the user's finger).
        val routePoints = if (paths.isEmpty()) markers.map { it.point } else paths.flatten() + markers.filter { it.draft }.map { it.point }
        val points = routePoints + tracks.filterNot { it.live }.flatMap { it.points }
        val key = points to listOf(extraTopDp, extraRightDp, extraBottomDp)
        val selectedStep = focusStep
        if (key != fittedKey) {
            val routeChanged = key.first != fittedKey?.first
            fittedKey = key
            val p = (paddingDp * density).toInt()
            padding = MapInsets(p, p + (extraTopDp * density).toInt(), p + (extraRightDp * density).toInt(), p + (extraBottomDp * density).toInt())
            // A new route always re-fits; a new bottom overlay height (the carousel is measured, or
            // changes height while scrolling) only until the user has selected something.
            if (routeChanged || !userFocused) {
                val pad = padding
                map.afterLayout { MapCameraRules.fit(map, points, pad) }
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
            map.fitPoints(points, padding, MapConfig.FIT_MAX_ZOOM, fly)
            return
        }
        // Selecting a step or a leg flies the camera there (only on a change, never on the first fit).
        if (selectedStep != focusedStep) {
            focusedStep = selectedStep
            val m = markers.firstOrNull { it.id == selectedStep }
            if (m != null) {
                userFocused = true
                MapCameraRules.focusStep(map, m.point, stepRegion(route.map { it.id }, lines, selectedStep), padding, reducedMotion)
            }
        }
        if (selectedLegTo != focusedLeg) {
            focusedLeg = selectedLegTo
            val i = legs.indexOfFirst { it.second.id == selectedLegTo }
            if (i >= 0) {
                userFocused = true
                val p = (paddingDp * density).toInt()
                val bottom = maxOf(padding.bottom, (map.heightPx * LEG_FOCUS_BOTTOM).toInt())
                map.fitPoints(paths[i], MapInsets(p, p, p, bottom), MapConfig.LEG_FOCUS_MAX_ZOOM, fly)
            }
        }
    }
}

/**
 * R37: what the camera keeps in view around a focused step: the leg arriving at it, else the
 * leg leaving it ([lines][i] joins [routeIds][i] → [routeIds][i + 1]). A flight arc spans half
 * the country, so it is skipped. Empty = the step alone (a draft, a lone step, flights on both sides).
 */
internal fun stepRegion(routeIds: List<String>, lines: List<MapLeg>, step: String?): List<GeoPoint> {
    val j = routeIds.indexOf(step)
    if (j < 0) return emptyList()
    return listOfNotNull(lines.getOrNull(j - 1), lines.getOrNull(j)).firstOrNull { it.line != LegLine.FLIGHT }?.path.orEmpty()
}

/** AllTripsMap ("Bản đồ của tôi", R14): one line per trip in its colour, a dot per step, tracks, my location. */
internal class AllTripsPresenter {
    var trips: List<MapTrip> = emptyList()
    var tracks: List<TrackLine> = emptyList()
    var selectedId: String? = null
    var myLocation: MyLocationFix? = null
    var myLocationSignal: Int = 0
    var padding: MapInsets = MapInsets.NONE

    private var shownMyLocationSignal = 0
    private var fittedKey: Pair<List<GeoPoint>, MapInsets>? = null

    fun onTap(hit: MapHit?, onTripClick: (String) -> Unit): Boolean {
        val trip = hit as? MapHit.Trip ?: return false
        onTripClick(trip.id)
        return true
    }

    fun render(map: MapController) {
        // The selected trip is drawn last so it sits on top of the others.
        val ordered = trips.sortedBy { it.id == selectedId }
        val lines = ordered.filter { it.points.size >= 2 }.map { t ->
            val path = Polyline.simplify(
                t.points.zipWithNext().flatMapIndexed { i, (a, b) ->
                    val leg = t.legPath(i, a, b)
                    if (i == 0) leg else leg.drop(1)
                },
            )
            MapTripLine(t.id, t.color.hex(), path, t.id == selectedId)
        }
        val dots = ordered.flatMap { t -> t.points.map { MapTripDot(t.id, t.color.hex(), it, t.id == selectedId) } }
        map.setTrips(lines, dots)
        // Tracks are drawn but not fitted: the camera frames the trips (R14).
        map.setTracks(tracks)
        map.setMyLocation(myLocation)
        if (myLocationSignal != shownMyLocationSignal) {
            shownMyLocationSignal = myLocationSignal
            myLocation?.let { MapCameraRules.flyToMyLocation(map, it, padding) }
        }
        // Fit the whole drawn route (flight arcs included), not only the steps.
        val points = trips.flatMap { t ->
            if (t.points.size < 2) t.points
            else t.points.zipWithNext().flatMapIndexed { i, (a, b) -> t.legPath(i, a, b) }
        }
        val key = points to padding
        if (key != fittedKey) {
            fittedKey = key
            val pad = padding
            map.afterLayout { MapCameraRules.fit(map, points, pad) }
        }
    }
}

/** R36: the stored road line of leg [i], or its straight segment / flight arc. */
private fun MapTrip.legPath(i: Int, a: GeoPoint, b: GeoPoint): List<GeoPoint> =
    legPaths.getOrNull(i)?.takeIf { it.size >= 2 } ?: LegGeometry.path(a, b, legModes.getOrNull(i))

/** PickerMap: starts at the given place, shows the trip's other steps faded, flies to each new [PickerMove]. */
internal class PickerPresenter(private val icons: MapIcons) {
    private var started = false
    private var lastMoveTo: PickerMove? = null

    fun render(map: MapController, initial: GeoPoint, initialZoom: Double, contextMarkers: List<MapMarker>, moveTo: PickerMove?) {
        if (!started) {
            started = true
            map.moveTo(initial, initialZoom)
            map.setPins(contextMarkers.map { MapPin(it.id, it.point, icons.pin(it.label, PinKind.CONTEXT, false), 0, faded = true) })
        }
        if (moveTo != null && moveTo !== lastMoveTo) {
            lastMoveTo = moveTo
            map.moveTo(moveTo.point, moveTo.zoom, animation = CameraAnimation.EASE)
        }
    }
}
