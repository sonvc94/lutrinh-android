package vn.lutrinh.app.map

import vn.lutrinh.app.BuildConfig

/**
 * THE single place to configure the base map (docs/05-map.md "Basemap").
 *
 * Today: the OpenFreeMap "Positron" vector style (OpenStreetMap data in the OpenMapTiles schema,
 * free, no API key, no registration; https://openfreemap.org). Tiles, fonts and icons all come
 * from tiles.openfreemap.org, which was reachable from the development network in Vietnam on
 * 2026-11-09 (unlike *.openstreetmap.org, which resets TLS there). After the style has loaded,
 * [BASEMAP_TUNING] repaints a few of its layers in Lữ Trình's warm, muted colours and
 * [LABEL_FIELD] makes place labels prefer Vietnamese names, so our routes stand out.
 *
 * To change provider: point [STYLE_URL] at another MapLibre style (e.g. `liberty`/`bright` from
 * OpenFreeMap, a paid OSM provider or a self-hosted OpenMapTiles server) and update
 * [ATTRIBUTION]; layer ids in [BASEMAP_TUNING] that the new style lacks are skipped.
 */
object MapConfig {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

    /** Required credit for OpenFreeMap + OpenMapTiles + OpenStreetMap, shown on every map. */
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

    /** Vector tiles of this source stop at z14 and are over-zoomed above; streets stay sharp. */
    const val MAX_ZOOM = 19.0

    /** Painted while the style/tiles load (and if they fail), so the map is never black. */
    const val BACKGROUND = "#F3EFE6"

    /** Identifies the app to the tile server (no personal data). */
    val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    /**
     * Basemap repaint: Positron layer id -> (paint property -> colour). Warm cream land, sage
     * green parks/woods, soft blue-grey water; borders and roads stay Positron's light greys.
     */
    val BASEMAP_TUNING: Map<String, Map<String, String>> = mapOf(
        "background" to mapOf("background-color" to BACKGROUND),
        "park" to mapOf("fill-color" to "#E4E9DC"),
        "landcover_wood" to mapOf("fill-color" to "#DFE6D7"),
        "landuse_residential" to mapOf("fill-color" to "#EDE8DD"),
        "water" to mapOf("fill-color" to "#BFD4DC"),
        "waterway" to mapOf("line-color" to "#B3CBD4"),
        "building" to mapOf("fill-color" to "#EAE5DA", "fill-outline-color" to "#DDD7CB"),
        "boundary_2" to mapOf("line-color" to "#B9B2A6"),
        "boundary_3" to mapOf("line-color" to "#CFC8BC"),
    )

    /** Place/road/water label colour: soft ink instead of black, so labels sit behind routes. */
    const val LABEL_COLOR = "#5F6661"
    const val LABEL_HALO = "#F7F4EE"

    /** Label layers of the style whose text is replaced by [LABEL_FIELD] and recoloured. */
    val LABEL_LAYER_PREFIXES = listOf("label_", "water_name", "waterway_line_label", "highway-name", "airport")

    /**
     * The Positron label layers matching [LABEL_LAYER_PREFIXES], listed by id for the share
     * snapshot (StaticTripMap), which cannot enumerate a style's layers. Another style needs its own.
     */
    val LABEL_LAYER_IDS = listOf(
        "waterway_line_label", "water_name_point_label", "water_name_line_label", "highway-name-path", "highway-name-minor",
        "highway-name-major", "airport", "label_other", "label_village", "label_town", "label_state", "label_city",
        "label_city_capital", "label_country_3", "label_country_2", "label_country_1",
    )

    /** Feature properties tried in order for a label: Vietnamese, then Latin script, then local. */
    val LABEL_FIELD = listOf("name:vi", "name:latin", "name")

    /** Camera rules (docs/05-map.md). */
    const val SINGLE_POINT_ZOOM = 12.0
    const val FIT_MAX_ZOOM = 14.0
    const val OVERVIEW_ZOOM = 5.0
    const val PICKER_ZOOM = 13.0

    /** Selecting a step flies to it at this zoom at least (village/town scale). */
    const val STEP_FOCUS_ZOOM = 11.0

    /** Selecting a leg (its mode sheet opens) fits the leg, zoomed in at most this far. */
    const val LEG_FOCUS_MAX_ZOOM = 13.0
}
