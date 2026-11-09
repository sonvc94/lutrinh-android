package vn.lutrinh.app.map.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import vn.lutrinh.app.map.basemap.BasemapPlan
import vn.lutrinh.app.map.basemap.BasemapPlanner
import vn.lutrinh.app.map.basemap.BasemapStyle
import vn.lutrinh.app.map.basemap.RasterImagery
import vn.lutrinh.app.map.basemap.StyleLayerInfo

/*
 * A BasemapStyle on MapLibre (docs/05-map.md "Basemap providers"): load the vector style with our
 * R30 archipelago labels, then execute the provider-neutral BasemapPlan (imagery, hidden fills,
 * repaint, Vietnamese-first labels, R30 filter).
 */

internal const val IMAGERY_SOURCE = "basemap-imagery-src"
internal const val IMAGERY_LAYER = "basemap-imagery"

/** The style to load for [basemap]: its URL plus our archipelago labels in its label colours. */
internal fun styleBuilder(basemap: BasemapStyle): Style.Builder =
    Style.Builder().fromUri(basemap.styleUrl).withSovereigntyLabels(basemap.labelColor, basemap.labelHalo)

/** Applies [basemap] to a loaded live-map style (which can list its layers). */
internal fun Style.applyBasemap(basemap: BasemapStyle) {
    val plan = BasemapPlanner.plan(basemap, layers.map { StyleLayerInfo(it.id, it.styleType()) })
    basemap.imagery?.let { imagery ->
        addSource(imagery.toSource())
        val layer = RasterLayer(IMAGERY_LAYER, IMAGERY_SOURCE)
        when {
            plan.imageryAbove != null -> addLayerAbove(layer, plan.imageryAbove)
            plan.imageryAtBottom -> addLayerAt(layer, 0)
        }
    }
    applyPlan(plan, basemap) { getLayer(it) }
}

/**
 * Applies [plan] through [layer] lookups (live style or snapshotter); layers the style lacks are
 * skipped. Imagery is not added here.
 */
internal fun applyPlan(plan: BasemapPlan, basemap: BasemapStyle, layer: (String) -> Layer?) {
    plan.hidden.forEach { layer(it)?.setProperties(PropertyFactory.visibility(Property.NONE)) }
    plan.repaint.forEach { (id, paint) ->
        val l = layer(id) ?: return@forEach
        paint.forEach { (property, color) -> colorProperty(property, color)?.let { l.setProperties(it) } }
    }
    val text = Expression.coalesce(*basemap.labelField.map { Expression.get(it) }.toTypedArray())
    plan.labelLayers.forEach { id ->
        (layer(id) as? SymbolLayer)?.setProperties(
            PropertyFactory.textField(text),
            PropertyFactory.textColor(basemap.labelColor),
            PropertyFactory.textHaloColor(basemap.labelHalo),
        )
    }
    plan.sovereigntyFiltered.forEach { applySovereigntyFilter(layer(it)) }
}

internal fun RasterImagery.toSource(): RasterSource =
    RasterSource(IMAGERY_SOURCE, TileSet("2.2.0", *tiles.toTypedArray()).also {
        it.minZoom = minZoom.toFloat()
        it.maxZoom = maxZoom.toFloat()
        it.attribution = attribution
    }, tileSize)

private fun colorProperty(name: String, color: String): PropertyValue<*>? = when (name) {
    "background-color" -> PropertyFactory.backgroundColor(color)
    "fill-color" -> PropertyFactory.fillColor(color)
    "fill-outline-color" -> PropertyFactory.fillOutlineColor(color)
    "line-color" -> PropertyFactory.lineColor(color)
    else -> null
}

/** The style-spec type name of a MapLibre layer. */
private fun Layer.styleType(): String = when (this) {
    is BackgroundLayer -> "background"
    is FillLayer -> "fill"
    is FillExtrusionLayer -> "fill-extrusion"
    is LineLayer -> "line"
    is SymbolLayer -> "symbol"
    is CircleLayer -> "circle"
    is RasterLayer -> "raster"
    is HillshadeLayer -> "hillshade"
    is HeatmapLayer -> "heatmap"
    else -> "other"
}
