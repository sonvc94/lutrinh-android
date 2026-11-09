package vn.lutrinh.app.map.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
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
import vn.lutrinh.app.map.LegLine
import vn.lutrinh.app.map.MapBadge
import vn.lutrinh.app.map.MapLeg
import vn.lutrinh.app.map.MapPin
import vn.lutrinh.app.map.MapTripDot
import vn.lutrinh.app.map.MapTripLine
import vn.lutrinh.app.map.RouteColors
import vn.lutrinh.app.map.StopMarker
import vn.lutrinh.app.map.TRACK_COLOR
import vn.lutrinh.app.map.TrackLine

/*
 * The app's overlay layer stack on a MapLibre style (docs/05-map.md "Layers"), bottom to top:
 * trip lines (R14), route legs, recorded tracks (R16), stop suggestions (R17), my location,
 * mode badges, trip dots (R14), faded context pins (picker), step pins. Every map adds the whole
 * stack once per style load; an empty source draws nothing. Widths are in dp and interpolate
 * linearly with zoom between the stops; MapLibre clamps outside the first/last stop.
 */

internal object Overlay {
    const val SRC_TRIPS = "trips-src"
    const val SRC_ROUTE = "route-src"
    const val SRC_TRACKS = "tracks-src"
    const val SRC_STOPS = "stop-suggest-src"
    const val SRC_ME = "my-location-src"
    const val SRC_BADGES = "leg-badges-src"
    const val SRC_TRIP_DOTS = "trip-stops-src"
    const val SRC_CONTEXT = "context-pins-src"
    const val SRC_PINS = "steps-src"

    const val TRIPS_SHADOW = "trips-shadow-layer"
    const val TRIPS_CASING = "trips-casing-layer"
    const val TRIPS = "trips-layer"
    const val BADGES = "leg-badges-layer"
    const val TRIP_DOTS = "trip-stops-layer"
    const val CONTEXT_PINS = "context-pins-layer"
    const val PINS = "steps-layer"
}

/** Route layer ids, bottom to top. */
internal object RouteLayerIds {
    const val SHADOW = "route-shadow"
    const val CASING = "route-casing"
    const val GROUND = "route-ground"
    const val WALK = "route-walk"
    const val BOAT = "route-boat"
    const val FLIGHT = "route-flight"
    val ALL = arrayOf(SHADOW, CASING, GROUND, WALK, BOAT, FLIGHT)
}

/** Recorded GPS track layer ids, bottom to top (docs/05-map.md "Recorded tracks"). */
internal object TrackLayerIds {
    const val CASING = "track-casing"
    const val LINE = "track-line"
}

/** Stop-suggestion layer ids, bottom to top (docs/05-map.md "Stop suggestions"). */
internal object StopLayerIds {
    const val HALO = "stop-suggest-halo"
    const val DOT = "stop-suggest-dot"
}

/** Adds every overlay source and layer, on top of the basemap. */
internal fun Style.addOverlayLayers() {
    listOf(
        Overlay.SRC_TRIPS, Overlay.SRC_ROUTE, Overlay.SRC_TRACKS, Overlay.SRC_STOPS, Overlay.SRC_BADGES,
        Overlay.SRC_TRIP_DOTS, Overlay.SRC_CONTEXT, Overlay.SRC_PINS,
    ).forEach { addSource(GeoJsonSource(it)) }
    addTripLayers(Overlay.SRC_TRIPS)
    addRouteLayers(Overlay.SRC_ROUTE)
    addTrackLayers(Overlay.SRC_TRACKS)
    addStopSuggestionLayers(Overlay.SRC_STOPS)
    addMyLocationLayers(Overlay.SRC_ME)
    // Badges give way to pins and to each other (short legs lose theirs).
    addLayer(
        SymbolLayer(Overlay.BADGES, Overlay.SRC_BADGES).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(SYMBOL_SIZE_BY_ZOOM),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.iconPadding(1f),
            PropertyFactory.symbolSortKey(Expression.get("order")),
            PropertyFactory.symbolAvoidEdges(true),
        ),
    )
    addLayer(
        CircleLayer(Overlay.TRIP_DOTS, Overlay.SRC_TRIP_DOTS).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(byZoomAndSelection(Triple(4f, 3f, 4f), Triple(9f, 4.5f, 6f), Triple(13f, 6f, 7.5f))),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(byZoom(4f to 1.5f, 10f to 2.5f)),
            PropertyFactory.circleSortKey(Expression.switchCase(isSelected(), Expression.literal(1f), Expression.literal(0f))),
        ),
    )
    // Context pins of the picker: the trip's other steps, translucent, never tappable.
    addLayer(
        SymbolLayer(Overlay.CONTEXT_PINS, Overlay.SRC_CONTEXT).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconOpacity(0.75f),
        ),
    )
    // Pins never hide each other, but they do hide basemap labels and badges.
    addLayer(
        SymbolLayer(Overlay.PINS, Overlay.SRC_PINS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(SYMBOL_SIZE_BY_ZOOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.symbolSortKey(Expression.get("order")),
        ),
    )
}

internal fun Style.setFeatures(source: String, features: List<Feature>) {
    getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

internal fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun List<GeoPoint>.toLine(): LineString = LineString.fromLngLats(map { it.toPoint() })

internal fun List<MapPin>.toPinFeatures(): List<Feature> = map { pin ->
    Feature.fromGeometry(pin.point.toPoint()).apply {
        addStringProperty("id", pin.id)
        addStringProperty("icon", pin.icon.id)
        addNumberProperty("order", pin.order)
    }
}

internal fun List<MapLeg>.toLegFeatures(colors: RouteColors): List<Feature> = map { leg ->
    Feature.fromGeometry(leg.path.toLine()).apply {
        addStringProperty("kind", leg.line.code)
        addBooleanProperty("selected", leg.selected)
        addStringProperty("color", if (leg.selected) colors.selected else colors.line)
        addStringProperty("to", leg.to)
    }
}

internal fun List<MapBadge>.toBadgeFeatures(): List<Feature> = map { b ->
    Feature.fromGeometry(b.point.toPoint()).apply {
        addStringProperty("icon", b.icon.id)
        addStringProperty("to", b.to)
        addNumberProperty("order", b.order)
    }
}

internal fun List<MapTripLine>.toTripLineFeatures(): List<Feature> = filter { it.path.size >= 2 }.map { t ->
    Feature.fromGeometry(t.path.toLine()).apply {
        addStringProperty("trip", t.tripId)
        addStringProperty("color", t.color)
        addBooleanProperty("selected", t.selected)
    }
}

internal fun List<MapTripDot>.toTripDotFeatures(): List<Feature> = map { d ->
    Feature.fromGeometry(d.point.toPoint()).apply {
        addStringProperty("trip", d.tripId)
        addStringProperty("color", d.color)
        addBooleanProperty("selected", d.selected)
    }
}

internal fun List<TrackLine>.toTrackFeatures(): List<Feature> = filter { it.points.size >= 2 }.map { line ->
    Feature.fromGeometry(line.points.toLine()).apply { addStringProperty("track", line.trackId) }
}

internal fun List<StopMarker>.toStopFeatures(): List<Feature> = map { m ->
    Feature.fromGeometry(m.point.toPoint()).apply { addStringProperty("key", m.key) }
}

/** `interpolate(linear, zoom, z1, v1, z2, v2, …)`. */
internal fun byZoom(vararg stops: Pair<Float, Float>): Expression = Expression.interpolate(
    Expression.linear(), Expression.zoom(),
    *stops.map { (z, v) -> Expression.stop(z, v) }.toTypedArray(),
)

/** Like [byZoom] but each stop has a value for normal and for `selected == true` features. */
internal fun byZoomAndSelection(vararg stops: Triple<Float, Float, Float>): Expression = Expression.interpolate(
    Expression.linear(), Expression.zoom(),
    *stops.map { (z, normal, selected) ->
        Expression.stop(z, Expression.switchCase(isSelected(), Expression.literal(selected), Expression.literal(normal)))
    }.toTypedArray(),
)

internal fun isSelected(): Expression = Expression.eq(Expression.get("selected"), Expression.literal(true))

private fun kindIs(kind: LegLine): Expression = Expression.eq(Expression.get("kind"), Expression.literal(kind.code))

/** Pins and badges shrink a little at country zoom so a long trip does not turn into a blob. */
internal val SYMBOL_SIZE_BY_ZOOM: Expression get() = byZoom(4f to 0.75f, 8f to 0.9f, 10f to 1f)

/** "Bản đồ của tôi" (R14): same recipe as a trip's ground legs: soft shadow, white casing, coloured core. */
private fun Style.addTripLayers(source: String) {
    addLayer(
        LineLayer(Overlay.TRIPS_SHADOW, source).withProperties(
            PropertyFactory.lineColor("#0E2A20"),
            PropertyFactory.lineWidth(byZoom(4f to 4f, 9f to 8f, 13f to 16f)),
            PropertyFactory.lineBlur(byZoom(4f to 3f, 13f to 8f)),
            PropertyFactory.lineOpacity(byZoom(4f to 0.25f, 13f to 0.15f)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineTranslate(arrayOf(0f, 1f)),
        ),
    )
    addLayer(
        LineLayer(Overlay.TRIPS_CASING, source).withProperties(
            PropertyFactory.lineColor(android.graphics.Color.WHITE),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(4f, 4f, 5.6f), Triple(9f, 6.4f, 8.6f), Triple(13f, 9f, 12f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    addLayer(
        LineLayer(Overlay.TRIPS, source).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(4f, 2f, 3.2f), Triple(9f, 3.2f, 5f), Triple(13f, 5f, 7.5f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
}

/** Route core widths (dp) at z5 / z9 / z13, normal and selected. */
private val CORE = arrayOf(Triple(5f, 2.2f, 3.4f), Triple(9f, 3.4f, 5f), Triple(13f, 5f, 7.5f))

/**
 * Adds the leg layers for GeoJSON source [source] (one LineString per leg with properties
 * `kind` = LegLine.code, `selected` and `color`). `line-dasharray` is not data-driven in
 * MapLibre, so each stroke kind is its own layer filtered by `kind`.
 */
private fun Style.addRouteLayers(source: String) {
    val ground = kindIs(LegLine.GROUND)
    val color = Expression.toColor(Expression.get("color"))
    val sortKey = PropertyFactory.lineSortKey(Expression.switchCase(isSelected(), Expression.literal(2f), Expression.literal(1f)))

    // 1. Soft wide shadow under ground legs: lifts the line off the basemap, widest at street zoom.
    addLayer(
        LineLayer(RouteLayerIds.SHADOW, source).withProperties(
            PropertyFactory.lineColor("#0E2A20"),
            PropertyFactory.lineWidth(byZoom(5f to 5f, 9f to 8f, 13f to 16f)),
            PropertyFactory.lineBlur(byZoom(5f to 3f, 13f to 8f)),
            PropertyFactory.lineOpacity(byZoom(5f to 0.28f, 13f to 0.16f)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineTranslate(arrayOf(0f, 1f)),
        ).withFilter(ground),
    )
    // 2. White casing (border) under ground legs.
    addLayer(
        LineLayer(RouteLayerIds.CASING, source).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 4.6f, 6f), Triple(9f, 6.6f, 8.6f), Triple(13f, 9f, 12f))),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            sortKey,
        ).withFilter(ground),
    )
    // 3. Ground core: solid route green (accent when selected).
    addLayer(
        LineLayer(RouteLayerIds.GROUND, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(*CORE)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            sortKey,
        ).withFilter(ground),
    )
    // 4. Walking: thin dots (zero-length dashes + round caps), no casing.
    addLayer(
        LineLayer(RouteLayerIds.WALK, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 1.8f, 2.6f), Triple(9f, 2.8f, 4f), Triple(13f, 4f, 5.5f))),
            PropertyFactory.lineDasharray(arrayOf(0f, 2.2f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.WALK)),
    )
    // 5. Boat: bigger dots, no casing.
    addLayer(
        LineLayer(RouteLayerIds.BOAT, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 2.6f, 3.6f), Triple(9f, 3.8f, 5.2f), Triple(13f, 5.2f, 7.5f))),
            PropertyFactory.lineDasharray(arrayOf(0f, 1.7f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.BOAT)),
    )
    // 6. Flight: dashed arc, butt caps, no casing.
    addLayer(
        LineLayer(RouteLayerIds.FLIGHT, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 2f, 3f), Triple(9f, 3f, 4.5f), Triple(13f, 4.2f, 6.5f))),
            PropertyFactory.lineDasharray(arrayOf(2.4f, 1.6f)),
            PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.FLIGHT)),
    )
}

/**
 * Adds the recorded-track layers for GeoJSON source [source] (one LineString per track segment).
 * Thinner than a ground leg, solid, on a thin white casing: a GPS trace on top of the plan.
 */
private fun Style.addTrackLayers(source: String) {
    addLayer(
        LineLayer(TrackLayerIds.CASING, source).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(byZoom(5f to 3.2f, 9f to 4.6f, 13f to 6.5f)),
            PropertyFactory.lineOpacity(0.9f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    addLayer(
        LineLayer(TrackLayerIds.LINE, source).withProperties(
            PropertyFactory.lineColor(TRACK_COLOR),
            PropertyFactory.lineWidth(byZoom(5f to 1.6f, 9f to 2.6f, 13f to 4f)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
}

/**
 * Adds the stop-suggestion layers for GeoJSON source [source] (one Point per suggestion): a pale
 * track-coloured halo (≈ the 150 m stay radius at town zoom) around a hollow white dot with a
 * track-coloured ring. Hollow and unnumbered, so it never reads as a step pin.
 */
private fun Style.addStopSuggestionLayers(source: String) {
    addLayer(
        CircleLayer(StopLayerIds.HALO, source).withProperties(
            PropertyFactory.circleColor(TRACK_COLOR),
            PropertyFactory.circleOpacity(0.16f),
            PropertyFactory.circleRadius(byZoom(5f to 7f, 10f to 11f, 14f to 18f)),
            PropertyFactory.circleStrokeColor(TRACK_COLOR),
            PropertyFactory.circleStrokeOpacity(0.5f),
            PropertyFactory.circleStrokeWidth(1f),
        ),
    )
    addLayer(
        CircleLayer(StopLayerIds.DOT, source).withProperties(
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleRadius(byZoom(5f to 3.5f, 10f to 5f, 14f to 6f)),
            PropertyFactory.circleStrokeColor(TRACK_COLOR),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
}
