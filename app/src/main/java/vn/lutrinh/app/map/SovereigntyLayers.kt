package vn.lutrinh.app.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/*
 * R30 on the map (docs/05 "Hoàng Sa / Trường Sa"): the SovereigntyLabels rule as a style filter
 * on every basemap label layer, plus our "Quần đảo Hoàng Sa / Trường Sa" labels. Used by the live
 * maps (loadBasemap + tuneBasemap) and the share snapshot (StaticTripMap), so offline packs, which
 * render the same style, get it too.
 */

internal const val SOVEREIGNTY_SOURCE = "r30-archipelagos"
internal const val SOVEREIGNTY_LAYER = "r30-archipelago-labels"

/** Adds the archipelago label source and layer (on top of the basemap, below our route layers). */
internal fun Style.Builder.withSovereigntyLabels(): Style.Builder = this
    .withSource(GeoJsonSource(SOVEREIGNTY_SOURCE, FeatureCollection.fromJson(SovereigntyLabels.labelsGeoJson())))
    .withLayer(
        SymbolLayer(SOVEREIGNTY_LAYER, SOVEREIGNTY_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Italic")),
            PropertyFactory.textSize(byZoom(4f to 11f, 8f to 14f)),
            PropertyFactory.textLetterSpacing(0.05f),
            PropertyFactory.textMaxWidth(7f),
            PropertyFactory.textColor(MapConfig.LABEL_COLOR),
            PropertyFactory.textHaloColor(MapConfig.LABEL_HALO),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textAllowOverlap(true),
        ).apply {
            minZoom = SovereigntyLabels.LABEL_MIN_ZOOM.toFloat()
            maxZoom = SovereigntyLabels.LABEL_MAX_ZOOM.toFloat()
        },
    )

/** Keeps [layer]'s own filter and hides the labels SovereigntyLabels.hides() would hide. */
internal fun applySovereigntyFilter(layer: Layer?) {
    val symbols = layer as? SymbolLayer ?: return
    val hidden = Expression.not(sovereigntyHidden())
    symbols.setFilter(symbols.filter?.let { Expression.all(it, hidden) } ?: hidden)
}

/** SovereigntyLabels.hides() as a style expression. */
internal fun sovereigntyHidden(): Expression {
    val inAreas = Expression.any(*SovereigntyLabels.AREAS.map { Expression.within(it.toPolygon()) }.toTypedArray())
    val blocked = Expression.literal(SovereigntyLabels.BLOCKED_NAMES.toTypedArray<Any>())
    val blockedName = Expression.any(*SovereigntyLabels.NAME_FIELDS.map { Expression.`in`(Expression.get(it), blocked) }.toTypedArray())
    val keptNatural = Expression.all(
        Expression.`in`(Expression.get(SovereigntyLabels.CLASS_FIELD), Expression.literal(SovereigntyLabels.NATURAL_CLASSES.toTypedArray<Any>())),
        Expression.has(SovereigntyLabels.VI_FIELD),
    )
    val embedded = Expression.any(
        *SovereigntyLabels.BLOCKED_SUBSTRINGS.flatMap { sub ->
            SovereigntyLabels.SUBSTRING_FIELDS.map { Expression.`in`(Expression.literal(sub), Expression.toString(Expression.get(it))) }
        }.toTypedArray(),
    )
    val foreignAdmin = Expression.all(
        Expression.`in`(Expression.get(SovereigntyLabels.CLASS_FIELD), Expression.literal(SovereigntyLabels.ADMIN_CLASSES.toTypedArray<Any>())),
        Expression.not(Expression.`in`(Expression.get(SovereigntyLabels.NAME_FIELD), Expression.literal(SovereigntyLabels.VIETNAMESE_ADMIN.toTypedArray<Any>()))),
    )
    return Expression.all(
        inAreas,
        Expression.any(
            blockedName,
            embedded,
            foreignAdmin,
            Expression.all(Expression.has(SovereigntyLabels.NONLATIN_FIELD), Expression.not(keptNatural)),
        ),
    )
}

private fun GeoBounds.toPolygon(): Polygon = Polygon.fromLngLats(
    listOf(
        listOf(
            Point.fromLngLat(west, south), Point.fromLngLat(east, south), Point.fromLngLat(east, north),
            Point.fromLngLat(west, north), Point.fromLngLat(west, south),
        ),
    ),
)
