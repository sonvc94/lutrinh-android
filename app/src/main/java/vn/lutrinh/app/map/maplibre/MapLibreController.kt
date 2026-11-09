package vn.lutrinh.app.map.maplibre

import android.graphics.RectF
import androidx.core.view.doOnLayout
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.map.CameraAnimation
import vn.lutrinh.app.map.CameraMath
import vn.lutrinh.app.map.MapBadge
import vn.lutrinh.app.map.MapCamera
import vn.lutrinh.app.map.MapController
import vn.lutrinh.app.map.MapHit
import vn.lutrinh.app.map.MapIcon
import vn.lutrinh.app.map.MapInsets
import vn.lutrinh.app.map.MapLeg
import vn.lutrinh.app.map.MapListener
import vn.lutrinh.app.map.MapPin
import vn.lutrinh.app.map.MapTripDot
import vn.lutrinh.app.map.MapTripLine
import vn.lutrinh.app.map.MyLocationFix
import vn.lutrinh.app.map.RouteColors
import vn.lutrinh.app.map.StopMarker
import vn.lutrinh.app.map.TrackLine
import vn.lutrinh.app.map.basemap.BasemapStyle
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex

/**
 * [MapController] on one MapLibre map view. Keeps the last value of every overlay and draws it
 * whenever a style has finished loading (first load and every basemap switch), so callers never
 * wait for the style. Tap targets (px boxes around the finger, in dp): pins 22, badges 22, leg
 * lines 11, trip lines/dots 18.
 */
internal class MapLibreController(
    private val view: MapView,
    private val map: MapLibreMap,
    override val density: Float,
) : MapController {
    private var basemap: BasemapStyle? = null
    private var style: Style? = null
    private var listener: MapListener? = null

    private var routeColors = RouteColors(Palette.Cham.rgbHex(), Palette.ChamBright.rgbHex())
    private var pins = emptyList<MapPin>()
    private var legs = emptyList<MapLeg>()
    private var badges = emptyList<MapBadge>()
    private var tripLines = emptyList<MapTripLine>()
    private var tripDots = emptyList<MapTripDot>()
    private var tracks = emptyList<TrackLine>()
    private var stops = emptyList<StopMarker>()
    private var myLocation: MyLocationFix? = null

    init {
        map.addOnMapClickListener { latLng -> onTap(latLng) }
        map.addOnCameraIdleListener { camera?.let { listener?.onCameraIdle(it) } }
    }

    override val heightPx: Int get() = view.height

    override val camera: MapCamera?
        get() {
            val pos = map.cameraPosition
            val t = pos.target ?: return null
            return MapCamera(GeoPoint(t.latitude, t.longitude), pos.zoom)
        }

    override fun setBasemap(style: BasemapStyle) {
        if (style == basemap) return
        basemap = style
        this.style = null
        map.setStyle(styleBuilder(style)) { loaded ->
            if (basemap != style) return@setStyle
            loaded.applyBasemap(style)
            loaded.addOverlayLayers()
            this.style = loaded
            drawAll(loaded)
        }
    }

    override fun setRouteColors(colors: RouteColors) {
        if (colors == routeColors) return
        routeColors = colors
        style?.let { drawLegs(it) }
    }

    override fun setPins(pins: List<MapPin>) {
        if (pins == this.pins) return
        this.pins = pins
        style?.let { drawPins(it) }
    }

    override fun setLegs(legs: List<MapLeg>, badges: List<MapBadge>) {
        if (legs == this.legs && badges == this.badges) return
        this.legs = legs
        this.badges = badges
        style?.let { drawLegs(it) }
    }

    override fun setTrips(lines: List<MapTripLine>, dots: List<MapTripDot>) {
        if (lines == tripLines && dots == tripDots) return
        tripLines = lines
        tripDots = dots
        style?.let { drawTrips(it) }
    }

    override fun setTracks(tracks: List<TrackLine>) {
        if (tracks == this.tracks) return
        this.tracks = tracks
        style?.setFeatures(Overlay.SRC_TRACKS, tracks.toTrackFeatures())
    }

    override fun setStopSuggestions(stops: List<StopMarker>) {
        if (stops == this.stops) return
        this.stops = stops
        style?.setFeatures(Overlay.SRC_STOPS, stops.toStopFeatures())
    }

    override fun setMyLocation(fix: MyLocationFix?) {
        if (fix == myLocation) return
        myLocation = fix
        style?.showMyLocation(Overlay.SRC_ME, fix)
    }

    private fun drawAll(s: Style) {
        drawTrips(s)
        drawLegs(s)
        s.setFeatures(Overlay.SRC_TRACKS, tracks.toTrackFeatures())
        s.setFeatures(Overlay.SRC_STOPS, stops.toStopFeatures())
        s.showMyLocation(Overlay.SRC_ME, myLocation)
        drawPins(s)
    }

    private fun drawPins(s: Style) {
        val (faded, normal) = pins.partition { it.faded }
        pins.forEach { s.ensureImage(it.icon) }
        s.setFeatures(Overlay.SRC_PINS, normal.toPinFeatures())
        s.setFeatures(Overlay.SRC_CONTEXT, faded.toPinFeatures())
    }

    private fun drawLegs(s: Style) {
        badges.forEach { s.ensureImage(it.icon) }
        s.setFeatures(Overlay.SRC_ROUTE, legs.toLegFeatures(routeColors))
        s.setFeatures(Overlay.SRC_BADGES, badges.toBadgeFeatures())
    }

    private fun drawTrips(s: Style) {
        s.setFeatures(Overlay.SRC_TRIPS, tripLines.toTripLineFeatures())
        s.setFeatures(Overlay.SRC_TRIP_DOTS, tripDots.toTripDotFeatures())
    }

    private fun Style.ensureImage(icon: MapIcon) {
        if (getImage(icon.id) == null) addImage(icon.id, icon.draw())
    }

    private fun onTap(latLng: LatLng): Boolean {
        val l = listener ?: return false
        if (style == null) return l.onTap(null)
        val p = map.projection.toScreenLocation(latLng)
        fun box(dp: Float): RectF {
            val r = dp * density
            return RectF(p.x - r, p.y - r, p.x + r, p.y + r)
        }
        fun first(dp: Float, property: String, vararg layers: String): String? =
            map.queryRenderedFeatures(box(dp), *layers).firstNotNullOfOrNull { it.getStringProperty(property) }

        first(22f, "id", Overlay.PINS)?.let { return l.onTap(MapHit.Pin(it)) }
        // A badge first, else the leg line itself (a short leg may have lost its badge).
        (first(22f, "to", Overlay.BADGES) ?: first(11f, "to", *RouteLayerIds.ALL))?.let { return l.onTap(MapHit.Leg(it)) }
        first(18f, "trip", Overlay.TRIP_DOTS, Overlay.TRIPS)?.let { return l.onTap(MapHit.Trip(it)) }
        return l.onTap(null)
    }

    override fun moveTo(center: GeoPoint, zoom: Double, padding: MapInsets?, animation: CameraAnimation) {
        val target = CameraPosition.Builder().target(LatLng(center.latitude, center.longitude)).zoom(zoom)
        padding?.let { target.padding(it.toDoubleArray()) }
        val update = CameraUpdateFactory.newCameraPosition(target.build())
        when (animation) {
            CameraAnimation.NONE -> map.moveCamera(update)
            CameraAnimation.EASE -> map.animateCamera(update)
            CameraAnimation.FLY -> map.animateCamera(update, flyDuration(center, zoom))
        }
    }

    override fun fitPoints(points: List<GeoPoint>, padding: MapInsets, maxZoom: Double, animation: CameraAnimation) {
        if (points.size < 2) return
        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
        val pad = padding.toIntArray()
        val pos = map.getCameraForLatLngBounds(bounds, pad)
        if (pos == null) {
            if (animation == CameraAnimation.NONE) map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad[0], pad[1], pad[2], pad[3]))
            return
        }
        val zoom = pos.zoom.coerceAtMost(maxZoom)
        when (animation) {
            CameraAnimation.NONE -> map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(pos).zoom(zoom).build()))
            else -> {
                val target = CameraPosition.Builder(pos).zoom(zoom).padding(padding.toDoubleArray()).build()
                val update = CameraUpdateFactory.newCameraPosition(target)
                val to = pos.target?.let { GeoPoint(it.latitude, it.longitude) }
                if (animation == CameraAnimation.FLY && to != null) map.animateCamera(update, flyDuration(to, zoom)) else map.animateCamera(update)
            }
        }
    }

    /** CameraMath's fly length from the current camera to [to] at [zoom]. */
    private fun flyDuration(to: GeoPoint, zoom: Double): Int {
        val from = map.cameraPosition
        val km = from.target?.let { Geo.distanceKm(GeoPoint(it.latitude, it.longitude), to) } ?: 0.0
        return CameraMath.flyDurationMs(km, from.zoom, zoom)
    }

    override fun afterLayout(action: () -> Unit) {
        view.doOnLayout { action() }
    }

    override fun setListener(listener: MapListener?) {
        this.listener = listener
    }
}

private fun MapInsets.toIntArray() = intArrayOf(left, top, right, bottom)
private fun MapInsets.toDoubleArray() = doubleArrayOf(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())
