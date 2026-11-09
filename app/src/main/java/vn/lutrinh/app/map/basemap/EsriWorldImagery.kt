package vn.lutrinh.app.map.basemap

import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex

/**
 * "Vệ tinh" (docs/05-map.md "Basemap providers"): Esri World Imagery under the OpenFreeMap
 * Positron roads, borders and labels (a hybrid). Positron's land/water/building fills are hidden
 * so the imagery shows; its label layers are kept with their R30 filter and our archipelago
 * labels, drawn white on a dark halo to read on photos.
 *
 * Tiles: with an ArcGIS Location Platform key ([apiKey], from the `esriApiKey` Gradle/local
 * property into BuildConfig, never committed) the keyed basemap tile service; without one the
 * public keyless endpoint (development/prototype use only, see docs/05). Esri's terms forbid
 * storing the imagery for offline use, so this basemap is online-only (R33) and never goes into
 * offline areas or share images.
 */
class EsriWorldImagery(apiKey: String) : BasemapProvider {
    override val id = ID
    override val displayName = "Vệ tinh"
    override val attribution = "$IMAGERY_CREDIT · ${OpenFreeMap.ATTRIBUTION}"
    override val offlineAllowed = false
    override val needsNetwork = true
    override val style = BasemapStyle(
        styleUrl = Positron.STYLE_URL,
        labelLayerPrefixes = Positron.LABEL_LAYER_PREFIXES,
        labelLayerIds = Positron.LABEL_LAYER_IDS,
        labelField = Positron.LABEL_FIELD,
        labelColor = "#FFFFFF",
        labelHalo = "#26332C",
        repaint = mapOf(
            // Cool limestone (docs/08 Map), light enough to read on imagery; no warm sand.
            "boundary_2" to mapOf("line-color" to Palette.Limestone.rgbHex()),
            "boundary_3" to mapOf("line-color" to Palette.LimestoneDim.rgbHex()),
        ),
        imagery = RasterImagery(listOf(tileUrl(apiKey)), tileSize = 256, minZoom = 0, maxZoom = MAX_ZOOM, attribution = IMAGERY_CREDIT),
        imageryAbove = Positron.BACKGROUND_LAYER,
        hiddenLayerTypes = setOf("fill", "fill-extrusion", "hillshade"),
        hiddenLayerIds = setOf("waterway"),
    )

    companion object {
        const val ID = "satellite"

        /** Esri's required credit for World Imagery (its "Copyright Text", read 2026-11-09). */
        const val IMAGERY_CREDIT = "Esri, Vantor, Earthstar Geographics, and the GIS User Community"

        /** Public endpoint, no key: development and prototypes only. */
        const val KEYLESS_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

        /** ArcGIS Location Platform basemap tiles (answers 499 "Token Required" without a token). */
        const val KEYED_URL = "https://ibasemaps-api.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

        /** Deepest imagery zoom requested; the engine over-zooms beyond (sub-metre imagery ≈ z18 over Vietnam). */
        const val MAX_ZOOM = 18

        fun tileUrl(apiKey: String): String = if (apiKey.isBlank()) KEYLESS_URL else "$KEYED_URL?token=${apiKey.trim()}"
    }
}
