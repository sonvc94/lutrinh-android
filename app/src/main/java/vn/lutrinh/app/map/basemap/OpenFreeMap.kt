package vn.lutrinh.app.map.basemap

import vn.lutrinh.app.map.MapConfig

/**
 * "Bản đồ" (docs/05-map.md "Basemap"): the OpenFreeMap "Positron" vector style (OpenStreetMap
 * data in the OpenMapTiles schema, free, no API key, no registration; https://openfreemap.org).
 * Tiles, fonts and icons come from tiles.openfreemap.org, reachable from Vietnam (unlike
 * *.openstreetmap.org, which resets TLS there). [Positron.REPAINT] turns a few layers into
 * Lữ Trình's warm, muted colours and labels prefer Vietnamese names, so our routes stand out.
 * Offline areas allowed within R31's cap (≤ 50 MB / 3,000 tiles per download).
 */
object OpenFreeMap : BasemapProvider {
    const val ID = "light"

    /** Required credit for OpenFreeMap + OpenMapTiles + OpenStreetMap. */
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

    override val id = ID
    override val displayName = "Bản đồ"
    override val attribution = ATTRIBUTION
    override val offlineAllowed = true
    override val needsNetwork = false
    override val style = BasemapStyle(
        styleUrl = Positron.STYLE_URL,
        labelLayerPrefixes = Positron.LABEL_LAYER_PREFIXES,
        labelLayerIds = Positron.LABEL_LAYER_IDS,
        labelField = Positron.LABEL_FIELD,
        labelColor = "#555C61",
        labelHalo = "#F5F7F8",
        repaint = Positron.REPAINT,
    )
}

/**
 * The OpenFreeMap Positron style: its URL and the layer ids the app touches. Shared by every
 * basemap built on it ("Bản đồ", and the roads/labels of "Vệ tinh"). Another vector style needs
 * its own ids (layers missing from a style are skipped).
 */
object Positron {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

    /** The style's bottom layer; satellite imagery goes right above it. */
    const val BACKGROUND_LAYER = "background"

    /**
     * Basemap repaint (docs/08 "Map"): layer id -> (paint property -> colour). Cool limestone
     * land and buildings, sage green parks/woods, soft blue-grey water, slate borders; roads stay
     * Positron's light greys. Labels ink-grey on a paper halo.
     */
    val REPAINT: Map<String, Map<String, String>> = mapOf(
        "background" to mapOf("background-color" to MapConfig.BACKGROUND),
        "park" to mapOf("fill-color" to "#E4E9DC"),
        "landcover_wood" to mapOf("fill-color" to "#DFE6D7"),
        "landuse_residential" to mapOf("fill-color" to "#E6EAEC"),
        "water" to mapOf("fill-color" to "#BFD4DC"),
        "waterway" to mapOf("line-color" to "#B3CBD4"),
        "building" to mapOf("fill-color" to "#E2E7E9", "fill-outline-color" to "#D5DBDE"),
        "boundary_2" to mapOf("line-color" to "#A8AFB4"),
        "boundary_3" to mapOf("line-color" to "#C5CBCF"),
    )

    /** Label layers (place, road, water, airport names): Vietnamese-first text + the R30 filter. */
    val LABEL_LAYER_PREFIXES = listOf("label_", "water_name", "waterway_line_label", "highway-name", "airport")

    /** The label layers matching [LABEL_LAYER_PREFIXES], by id, for the share snapshot (it cannot list layers). */
    val LABEL_LAYER_IDS = listOf(
        "waterway_line_label", "water_name_point_label", "water_name_line_label", "highway-name-path", "highway-name-minor",
        "highway-name-major", "airport", "label_other", "label_village", "label_town", "label_state", "label_city",
        "label_city_capital", "label_country_3", "label_country_2", "label_country_1",
    )

    /** Feature properties tried in order for a label: Vietnamese, then Latin script, then local. */
    val LABEL_FIELD = listOf("name:vi", "name:latin", "name")
}
