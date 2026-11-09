package vn.lutrinh.app.map.basemap

import vn.lutrinh.app.BuildConfig

/*
 * Basemap providers (docs/05-map.md "Basemap providers", R32-R35). A provider adapter turns one
 * tile service (its URLs, key, terms, credit) into the app's [BasemapStyle]; the map engine draws
 * any BasemapStyle. Adding a provider = one adapter object + one line in [Basemaps.registry].
 */

/** One basemap the user can pick ("Bản đồ", "Vệ tinh"). */
interface BasemapProvider {
    /** Stable id, stored as the user's choice (`map_style`, R32): never rename it. */
    val id: String

    /** Name in the style sheet. */
    val displayName: String

    /** What the engine draws. */
    val style: BasemapStyle

    /** Required credit, shown on every map drawing this basemap (R34). */
    val attribution: String

    /** Its tiles may be stored in offline areas (R15). Offline packs and share images use such a provider. */
    val offlineAllowed: Boolean

    /** Without a network it shows nothing useful: the map falls back to an offline-capable one (R33). */
    val needsNetwork: Boolean
}

/**
 * A basemap as the engine sees it, in style-spec terms (layer ids/types and colours of the
 * MapLibre/Mapbox style specification, which both mobile engines read):
 * the vector style at [styleUrl], optionally with [imagery] tiles inserted right above layer
 * [imageryAbove], minus [hiddenLayerTypes]/[hiddenLayerIds], repainted by [repaint] (layer id ->
 * paint property -> colour). Symbol layers whose id starts with one of [labelLayerPrefixes] are
 * the place/road/water labels: their text becomes the first of [labelField] a feature has, drawn
 * in [labelColor] on [labelHalo], and R30's sovereignty filter is applied to them; our
 * "Quần đảo Hoàng Sa/Trường Sa" labels are added in the same colours.
 * [labelLayerIds] lists those label layers by id for renderers that cannot enumerate a style
 * (the share snapshot).
 */
data class BasemapStyle(
    val styleUrl: String,
    val labelLayerPrefixes: List<String>,
    val labelLayerIds: List<String>,
    val labelField: List<String>,
    val labelColor: String,
    val labelHalo: String,
    val repaint: Map<String, Map<String, String>> = emptyMap(),
    val imagery: RasterImagery? = null,
    val imageryAbove: String? = null,
    val hiddenLayerTypes: Set<String> = emptySet(),
    val hiddenLayerIds: Set<String> = emptySet(),
)

/** XYZ raster tiles ({z}/{x}/{y} placeholders, in whatever order the server wants). */
data class RasterImagery(
    val tiles: List<String>,
    val tileSize: Int,
    val minZoom: Int,
    val maxZoom: Int,
    val attribution: String,
)

/** The registered providers; the first one is the default and must work offline. */
class BasemapRegistry(val providers: List<BasemapProvider>) {
    init {
        require(providers.isNotEmpty() && providers.first().offlineAllowed) { "the default basemap must work offline" }
        require(providers.map { it.id }.toSet().size == providers.size) { "duplicate basemap id" }
    }

    val default: BasemapProvider get() = providers.first()

    /** Offline packs, share images/pages and the R33 fallback use this one ("Bản đồ"). */
    val offline: BasemapProvider get() = providers.first { it.offlineAllowed }

    /** The provider stored under [id]; unknown or missing ids (an old or removed provider) give the default. */
    fun byId(id: String?): BasemapProvider = providers.firstOrNull { it.id == id } ?: default

    /** R33: what a map shows for the user's [chosen] basemap; one that needs the network falls back offline. */
    fun shown(chosen: BasemapProvider, online: Boolean): BasemapProvider =
        if (chosen.needsNetwork && !online) offline else chosen
}

/** THE registration point of basemap providers (R35). Order = order in the style sheet. */
object Basemaps {
    val registry = BasemapRegistry(listOf(OpenFreeMap, EsriWorldImagery(BuildConfig.ESRI_API_KEY)))
}
