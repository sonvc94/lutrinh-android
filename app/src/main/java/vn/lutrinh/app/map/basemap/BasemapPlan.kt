package vn.lutrinh.app.map.basemap

/** One layer of a loaded vector style: its id and style-spec type ("fill", "line", "symbol", …). */
data class StyleLayerInfo(val id: String, val type: String)

/**
 * What an engine does to a loaded [BasemapStyle] (docs/05-map.md "Basemap providers"), worked
 * out without any SDK so it is unit-tested and the iOS port reuses it:
 * * [imageryAbove]: insert the provider's raster imagery right above this layer (null = no imagery,
 *   or at the very bottom when the style lacks the named layer: [imageryAtBottom]);
 * * [hidden]: layers made invisible (the fills that would cover the imagery);
 * * [repaint]: layer id -> paint property -> colour, for layers the style has;
 * * [labelLayers]: label layers that get the Vietnamese-first text field and label colours;
 * * [sovereigntyFiltered]: layers that get R30's filter (every label layer, hidden or not);
 * * [archipelagoLabels]: our "Quần đảo Hoàng Sa/Trường Sa" labels are added (always, R30).
 */
data class BasemapPlan(
    val imageryAbove: String?,
    val imageryAtBottom: Boolean,
    val hidden: List<String>,
    val repaint: Map<String, Map<String, String>>,
    val labelLayers: List<String>,
    val sovereigntyFiltered: List<String>,
    val archipelagoLabels: Boolean = true,
)

object BasemapPlanner {
    /** The plan for [style] once loaded with [layers] (bottom to top). */
    fun plan(style: BasemapStyle, layers: List<StyleLayerInfo>): BasemapPlan {
        val ids = layers.map { it.id }.toSet()
        val labels = layers.filter { it.type == "symbol" && style.labelLayerPrefixes.any(it.id::startsWith) }.map { it.id }
        val hidden = layers
            .filter { it.id !in labels && (it.type in style.hiddenLayerTypes || it.id in style.hiddenLayerIds) }
            .map { it.id }
        val above = style.imagery?.let { style.imageryAbove?.takeIf { it in ids } }
        return BasemapPlan(
            imageryAbove = above,
            imageryAtBottom = style.imagery != null && above == null,
            hidden = hidden,
            repaint = style.repaint.filterKeys { it in ids && it !in hidden },
            labelLayers = labels,
            sovereigntyFiltered = labels,
        )
    }

    /**
     * The plan from what [style] declares, for renderers that cannot list a style's layers (the
     * share snapshot): label layers by [BasemapStyle.labelLayerIds], hidden layers by id only.
     * Layers missing from the loaded style are skipped by the engine.
     */
    fun declared(style: BasemapStyle): BasemapPlan = BasemapPlan(
        imageryAbove = style.imagery?.let { style.imageryAbove },
        imageryAtBottom = style.imagery != null && style.imageryAbove == null,
        hidden = style.hiddenLayerIds.toList(),
        repaint = style.repaint.filterKeys { it !in style.hiddenLayerIds },
        labelLayers = style.labelLayerIds,
        sovereigntyFiltered = style.labelLayerIds,
    )
}
