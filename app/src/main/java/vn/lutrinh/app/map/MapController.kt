package vn.lutrinh.app.map

import android.graphics.Bitmap
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.map.basemap.BasemapStyle
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex

/*
 * The app's map interface (docs/05-map.md "Map interface", R35). Screens, view models and the
 * map composables (TripMap, AllTripsMap, PickerMap, StaticTripMap) only talk to these types; a
 * map engine (today MapLibre, package map.maplibre) implements them. Nothing here names an SDK
 * type, so swapping the engine = one new adapter + its registration in MapEngines.
 * The iOS port mirrors these as Swift protocols (docs/07).
 */

/** Camera: centre and zoom (Web Mercator, 512-px world tiles: the scale MapFit uses). */
data class MapCamera(val center: GeoPoint, val zoom: Double)

/** Space kept free at the map edges, in px: the route never sits under overlays drawn there. */
data class MapInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val NONE = MapInsets(0, 0, 0, 0)

        /** From the [left, top, right, bottom] px array the camera rules use. */
        fun of(px: IntArray) = MapInsets(px[0], px[1], px[2], px[3])
    }
}

/** How a camera change runs: at once, the engine's short ease, or a fly whose length follows CameraMath. */
enum class CameraAnimation { NONE, EASE, FLY }

/** An image the map draws (pin, mode badge). Drawn by [draw] the first time a style needs it; equal by [id]. */
class MapIcon(val id: String, val draw: () -> Bitmap) {
    override fun equals(other: Any?) = other is MapIcon && other.id == id
    override fun hashCode() = id.hashCode()
    override fun toString() = "MapIcon($id)"
}

/**
 * A point symbol: a step pin, or a [faded] context pin of the picker (other steps of the trip,
 * drawn translucent). A higher [order] is drawn on top.
 */
data class MapPin(val id: String, val point: GeoPoint, val icon: MapIcon, val order: Int, val faded: Boolean = false)

/** One leg of a trip route; [to] = id of the pin where it ends. */
data class MapLeg(val to: String, val path: List<GeoPoint>, val line: LegLine, val selected: Boolean)

/** The transport-mode badge at the middle of a leg; a lower [order] is placed first (never gives way). */
data class MapBadge(val to: String, val point: GeoPoint, val icon: MapIcon, val order: Int)

/** "Bản đồ của tôi" (R14): one line per trip in its colour ("#RRGGBB") and a dot per step. */
data class MapTripLine(val tripId: String, val color: String, val path: List<GeoPoint>, val selected: Boolean)
data class MapTripDot(val tripId: String, val color: String, val point: GeoPoint, val selected: Boolean)

/** What a tap landed on, in priority order: a pin, then a leg (its badge or line), then a trip. */
sealed interface MapHit {
    data class Pin(val id: String) : MapHit
    data class Leg(val to: String) : MapHit
    data class Trip(val id: String) : MapHit
}

/** Events of one map. */
interface MapListener {
    /** A tap; [hit] = null when it hit nothing of ours. Returns true when the tap was used. */
    fun onTap(hit: MapHit?): Boolean = false

    /** The camera stopped moving (pan, fly, fit). */
    fun onCameraIdle(camera: MapCamera) = Unit
}

/**
 * One live map. Every setter replaces the whole layer content and may be called before the
 * basemap has loaded: the adapter keeps the last value and draws it once the style is ready, and
 * again after a basemap switch. Camera calls work at any time.
 */
interface MapController {
    /** Screen px per dp. */
    val density: Float

    /** Height of the map view in px (0 before layout). */
    val heightPx: Int

    /** Current camera, or null before the first frame. */
    val camera: MapCamera?

    /** Shows [style] (no-op when it is already shown). Overlays, camera and listener stay. */
    fun setBasemap(style: BasemapStyle)

    /** Colours of the route legs (from the app theme). */
    fun setRouteColors(colors: RouteColors)

    fun setPins(pins: List<MapPin>)
    fun setLegs(legs: List<MapLeg>, badges: List<MapBadge>)
    fun setTrips(lines: List<MapTripLine>, dots: List<MapTripDot>)
    fun setTracks(tracks: List<TrackLine>)
    fun setStopSuggestions(stops: List<StopMarker>)
    fun setMyLocation(fix: MyLocationFix?)

    /** Moves the camera to [center] at [zoom]; with [padding] the centre sits in the area left free. */
    fun moveTo(center: GeoPoint, zoom: Double, padding: MapInsets? = null, animation: CameraAnimation = CameraAnimation.NONE)

    /** Frames every one of [points] (≥ 2) inside the map minus [padding], zoomed in at most [maxZoom]. */
    fun fitPoints(points: List<GeoPoint>, padding: MapInsets, maxZoom: Double, animation: CameraAnimation = CameraAnimation.NONE)

    /** Runs [action] once the view has a size (at once if it already has one). */
    fun afterLayout(action: () -> Unit)

    fun setListener(listener: MapListener?)
}

/** Colours of the route lines ("#RRGGBB"): normal and selected leg. */
data class RouteColors(val line: String, val selected: String)

/** One drawable track line (a segment of a recorded track, already simplified). */
data class TrackLine(val trackId: String, val points: List<GeoPoint>, val live: Boolean = false)

/** A stop suggestion on the map (R17): drawn as a ring, never a numbered pin (it is not a step yet). */
data class StopMarker(val key: String, val point: GeoPoint)

/**
 * "Vị trí của tôi" (docs/05-map.md): one device fix drawn as a blue dot with its accuracy circle.
 * Nothing tracks the device while the screen is open: the dot is the fix of the last tap.
 */
data class MyLocationFix(val point: GeoPoint, val accuracyM: Float)

/**
 * Colour of a recorded track (R16): crimson, a hue no planned route uses (route green, selected
 * gold, the "Bản đồ của tôi" trip palette), so "where I really went" never reads as a planned leg.
 */
internal val TRACK_COLOR: String = Palette.Track.rgbHex()
