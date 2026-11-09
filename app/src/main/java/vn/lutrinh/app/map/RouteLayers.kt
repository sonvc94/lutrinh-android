package vn.lutrinh.app.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import vn.lutrinh.app.domain.model.GeoPoint

/*
 * Basemap loading + the route/pin layer stack shared by the map composables
 * (docs/05-map.md "Layers"). Widths are in dp and interpolate linearly with zoom between the
 * stops; MapLibre clamps outside the first/last stop.
 */

/** Loads the basemap style (MapConfig.STYLE_URL), tunes it, then hands it to [onLoaded]. */
internal fun MapLibreMap.loadBasemap(onLoaded: (Style) -> Unit) {
    setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { style ->
        style.tuneBasemap()
        onLoaded(style)
    }
}

/** Applies MapConfig.BASEMAP_TUNING colours and Vietnamese-first, soft-ink labels. */
internal fun Style.tuneBasemap() {
    MapConfig.BASEMAP_TUNING.forEach { (id, paint) ->
        val layer = getLayer(id) ?: return@forEach
        paint.forEach { (property, color) -> colorProperty(property, color)?.let { layer.setProperties(it) } }
    }
    val label = Expression.coalesce(*MapConfig.LABEL_FIELD.map { Expression.get(it) }.toTypedArray())
    layers.filterIsInstance<SymbolLayer>()
        .filter { l -> MapConfig.LABEL_LAYER_PREFIXES.any { l.id.startsWith(it) } }
        .forEach { l ->
            l.setProperties(
                PropertyFactory.textField(label),
                PropertyFactory.textColor(MapConfig.LABEL_COLOR),
                PropertyFactory.textHaloColor(MapConfig.LABEL_HALO),
            )
        }
}

private fun colorProperty(name: String, color: String): PropertyValue<*>? = when (name) {
    "background-color" -> PropertyFactory.backgroundColor(color)
    "fill-color" -> PropertyFactory.fillColor(color)
    "fill-outline-color" -> PropertyFactory.fillOutlineColor(color)
    "line-color" -> PropertyFactory.lineColor(color)
    else -> null
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

/** Colours of the route lines (from the theme). */
internal data class RouteColors(val line: String, val selected: String)

/** Route core widths (dp) at z5 / z9 / z13, normal and selected. */
private val CORE = arrayOf(Triple(5f, 2.2f, 3.4f), Triple(9f, 3.4f, 5f), Triple(13f, 5f, 7.5f))

/**
 * Adds the leg layers for GeoJSON source [source] (one LineString per leg with properties
 * `kind` = LegLine.code and `selected`). `line-dasharray` is not data-driven in MapLibre, so each
 * stroke kind is its own layer filtered by `kind`.
 */
internal fun Style.addRouteLayers(source: String, colors: RouteColors, below: String? = null) {
    val ground = kindIs(LegLine.GROUND)
    val color = Expression.switchCase(isSelected(), Expression.color(android.graphics.Color.parseColor(colors.selected)), Expression.color(android.graphics.Color.parseColor(colors.line)))
    val sortKey = PropertyFactory.lineSortKey(Expression.switchCase(isSelected(), Expression.literal(2f), Expression.literal(1f)))
    fun add(layer: Layer) = if (below != null) addLayerBelow(layer, below) else addLayer(layer)

    // 1. Soft wide shadow under ground legs: lifts the line off the basemap, widest at street zoom.
    add(
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
    add(
        LineLayer(RouteLayerIds.CASING, source).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 4.6f, 6f), Triple(9f, 6.6f, 8.6f), Triple(13f, 9f, 12f))),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            sortKey,
        ).withFilter(ground),
    )
    // 3. Ground core: solid route green (accent when selected).
    add(
        LineLayer(RouteLayerIds.GROUND, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(*CORE)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            sortKey,
        ).withFilter(ground),
    )
    // 4. Walking: thin dots (zero-length dashes + round caps), no casing.
    add(
        LineLayer(RouteLayerIds.WALK, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 1.8f, 2.6f), Triple(9f, 2.8f, 4f), Triple(13f, 4f, 5.5f))),
            PropertyFactory.lineDasharray(arrayOf(0f, 2.2f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.WALK)),
    )
    // 5. Boat: bigger dots, no casing.
    add(
        LineLayer(RouteLayerIds.BOAT, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 2.6f, 3.6f), Triple(9f, 3.8f, 5.2f), Triple(13f, 5.2f, 7.5f))),
            PropertyFactory.lineDasharray(arrayOf(0f, 1.7f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.BOAT)),
    )
    // 6. Flight: dashed arc, butt caps, no casing.
    add(
        LineLayer(RouteLayerIds.FLIGHT, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(byZoomAndSelection(Triple(5f, 2f, 3f), Triple(9f, 3f, 4.5f), Triple(13f, 4.2f, 6.5f))),
            PropertyFactory.lineDasharray(arrayOf(2.4f, 1.6f)),
            PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(kindIs(LegLine.FLIGHT)),
    )
}

/** Pins and badges shrink a little at country zoom so a long trip does not turn into a blob. */
internal val SYMBOL_SIZE_BY_ZOOM: Expression get() = byZoom(4f to 0.75f, 8f to 0.9f, 10f to 1f)

/** Recorded GPS track layer ids, bottom to top (docs/05-map.md "Recorded tracks"). */
internal object TrackLayerIds {
    const val CASING = "track-casing"
    const val LINE = "track-line"
}

/**
 * Colour of a recorded track (R16): crimson, a hue no planned route uses (route green, selected
 * gold, the "Bản đồ của tôi" trip palette), so "where I really went" never reads as a planned leg.
 */
internal const val TRACK_COLOR = "#D7263D"

/**
 * Adds the recorded-track layers for GeoJSON source [source] (one LineString per track segment).
 * Thinner than a ground leg, solid, on a thin white casing: a GPS trace on top of the plan.
 */
internal fun Style.addTrackLayers(source: String, below: String? = null) {
    fun add(layer: Layer) = if (below != null) addLayerBelow(layer, below) else addLayer(layer)
    add(
        LineLayer(TrackLayerIds.CASING, source).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(byZoom(5f to 3.2f, 9f to 4.6f, 13f to 6.5f)),
            PropertyFactory.lineOpacity(0.9f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    add(
        LineLayer(TrackLayerIds.LINE, source).withProperties(
            PropertyFactory.lineColor(TRACK_COLOR),
            PropertyFactory.lineWidth(byZoom(5f to 1.6f, 9f to 2.6f, 13f to 4f)),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
}

/** One drawable track line (a segment of a recorded track, already simplified). */
data class TrackLine(val trackId: String, val points: List<GeoPoint>, val live: Boolean = false)

internal fun List<TrackLine>.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(
        filter { it.points.size >= 2 }.map { line ->
            Feature.fromGeometry(
                LineString.fromLngLats(line.points.map { Point.fromLngLat(it.longitude, it.latitude) }),
            ).apply { addStringProperty("track", line.trackId) }
        },
    )

/** A stop suggestion on the map (R17): drawn as a ring, never a numbered pin (it is not a step yet). */
data class StopMarker(val key: String, val point: GeoPoint)

/** Stop-suggestion layer ids, bottom to top (docs/05-map.md "Stop suggestions"). */
internal object StopLayerIds {
    const val HALO = "stop-suggest-halo"
    const val DOT = "stop-suggest-dot"
}

/**
 * Adds the stop-suggestion layers for GeoJSON source [source] (one Point per suggestion): a pale
 * track-coloured halo (≈ the 150 m stay radius at town zoom) around a hollow white dot with a
 * track-coloured ring. Hollow and unnumbered, so it never reads as a step pin.
 */
internal fun Style.addStopSuggestionLayers(source: String) {
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

internal fun List<StopMarker>.toStopFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(
        map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.point.longitude, m.point.latitude)).apply { addStringProperty("key", m.key) }
        },
    )
