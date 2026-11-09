package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.map.basemap.BasemapPlanner
import vn.lutrinh.app.map.basemap.BasemapProvider
import vn.lutrinh.app.map.basemap.BasemapRegistry
import vn.lutrinh.app.map.basemap.BasemapSettings
import vn.lutrinh.app.map.basemap.Basemaps
import vn.lutrinh.app.map.basemap.EsriWorldImagery
import vn.lutrinh.app.map.basemap.MapStyleStore
import vn.lutrinh.app.map.basemap.OpenFreeMap
import vn.lutrinh.app.map.basemap.Positron
import vn.lutrinh.app.map.basemap.StyleLayerInfo

/** Basemap providers, their registry and the user's choice (docs/04 R32-R34). */
class BasemapTest {
    private val esri = EsriWorldImagery("")
    private val registry = BasemapRegistry(listOf(OpenFreeMap, esri))

    private class MemoryStore(override var mapStyle: String? = null) : MapStyleStore

    /** The layers of the OpenFreeMap Positron style the planner sees (ids and types as in the style JSON). */
    private val positronLayers = listOf(
        StyleLayerInfo("background", "background"),
        StyleLayerInfo("park", "fill"),
        StyleLayerInfo("water", "fill"),
        StyleLayerInfo("landcover_wood", "fill"),
        StyleLayerInfo("landuse_residential", "fill"),
        StyleLayerInfo("waterway", "line"),
        StyleLayerInfo("building", "fill"),
        StyleLayerInfo("aeroway-area", "fill"),
        StyleLayerInfo("highway_path", "line"),
        StyleLayerInfo("highway_minor", "line"),
        StyleLayerInfo("highway_major_casing", "line"),
        StyleLayerInfo("highway_motorway", "line"),
        StyleLayerInfo("railway", "line"),
        StyleLayerInfo("boundary_3", "line"),
        StyleLayerInfo("boundary_2", "line"),
    ) + Positron.LABEL_LAYER_IDS.map { StyleLayerInfo(it, "symbol") }

    // --- registry (R32) ---

    @Test
    fun registry_defaultIsTheOfflineMap() {
        assertEquals(listOf("light", "satellite"), Basemaps.registry.providers.map { it.id })
        assertEquals(listOf("Bản đồ", "Vệ tinh"), Basemaps.registry.providers.map { it.displayName })
        assertSame(OpenFreeMap, Basemaps.registry.default)
        assertSame(OpenFreeMap, Basemaps.registry.offline)
        assertTrue(OpenFreeMap.offlineAllowed)
        assertFalse(OpenFreeMap.needsNetwork)
        assertFalse(esri.offlineAllowed)
        assertTrue(esri.needsNetwork)
    }

    @Test
    fun registry_unknownOrMissingIdIsTheDefault() {
        assertSame(OpenFreeMap, registry.byId(null))
        assertSame(OpenFreeMap, registry.byId("mapbox-satellite"))
        assertSame(esri, registry.byId("satellite"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun registry_defaultMustWorkOffline() {
        BasemapRegistry(listOf(esri, OpenFreeMap))
    }

    @Test(expected = IllegalArgumentException::class)
    fun registry_idsAreUnique() {
        BasemapRegistry(listOf(OpenFreeMap, esri, EsriWorldImagery("x")))
    }

    @Test
    fun registry_newProviderNeedsNoOtherChange() {
        val topo = object : BasemapProvider {
            override val id = "topo"
            override val displayName = "Địa hình"
            override val style = OpenFreeMap.style.copy(styleUrl = "https://example.invalid/topo.json")
            override val attribution = "Topo"
            override val offlineAllowed = false
            override val needsNetwork = true
        }
        val withTopo = BasemapRegistry(listOf(OpenFreeMap, esri, topo))
        val settings = BasemapSettings(MemoryStore(), withTopo)
        settings.choose("topo")
        assertSame(topo, settings.chosen.value)
        assertSame(OpenFreeMap, withTopo.shown(topo, online = false))
    }

    // --- persistence (R32) ---

    @Test
    fun mapStyle_choiceIsPersisted() {
        val store = MemoryStore()
        val settings = BasemapSettings(store, registry)
        assertSame(OpenFreeMap, settings.chosen.value)
        settings.choose("satellite")
        assertEquals("satellite", store.mapStyle)
        assertSame(esri, settings.chosen.value)
        // Next app start reads it back.
        assertSame(esri, BasemapSettings(store, registry).chosen.value)
        settings.choose("light")
        assertEquals("light", store.mapStyle)
    }

    @Test
    fun mapStyle_unknownStoredValueReadsAsDefault() {
        assertSame(OpenFreeMap, BasemapSettings(MemoryStore("dark"), registry).chosen.value)
        val store = MemoryStore()
        BasemapSettings(store, registry).choose("dark")
        assertEquals("light", store.mapStyle)
    }

    // --- offline fallback (R33) ---

    @Test
    fun satellite_offlineFallsBackToTheMap() {
        assertSame(OpenFreeMap, registry.shown(esri, online = false))
        assertSame(esri, registry.shown(esri, online = true))
        assertSame(OpenFreeMap, registry.shown(OpenFreeMap, online = false))
        assertSame(OpenFreeMap, registry.shown(OpenFreeMap, online = true))
    }

    @Test
    fun offlinePacksAndShare_useTheOfflineMap() {
        // Offline packs (AppContainer) and the share image (StaticTripMap default) take registry.offline.
        assertSame(OpenFreeMap, Basemaps.registry.offline)
        assertEquals(Positron.STYLE_URL, Basemaps.registry.offline.style.styleUrl)
        assertNull(Basemaps.registry.offline.style.imagery)
    }

    // --- attribution (R34) ---

    @Test
    fun attribution_followsTheStyle() {
        assertEquals("OpenFreeMap © OpenMapTiles © OpenStreetMap contributors", OpenFreeMap.attribution)
        assertFalse(OpenFreeMap.attribution.contains("Esri"))
        // Satellite = Esri imagery + OpenFreeMap roads/labels: both credits.
        assertTrue(esri.attribution.startsWith("Esri, Vantor, Earthstar Geographics, and the GIS User Community"))
        assertTrue(esri.attribution.contains(OpenFreeMap.ATTRIBUTION))
        assertEquals(EsriWorldImagery.IMAGERY_CREDIT, esri.style.imagery!!.attribution)
    }

    // --- Esri tiles and key ---

    @Test
    fun esri_keylessWithoutKey_tokenWithKey() {
        val keyless = EsriWorldImagery("").style.imagery!!
        assertEquals(listOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"), keyless.tiles)
        assertEquals(EsriWorldImagery.MAX_ZOOM, keyless.maxZoom)
        assertEquals(EsriWorldImagery.KEYLESS_URL, EsriWorldImagery.tileUrl("  "))
        val keyed = EsriWorldImagery.tileUrl("k-123")
        assertTrue(keyed, keyed.startsWith("https://") && keyed.contains("{z}/{y}/{x}") && keyed.endsWith("?token=k-123"))
    }

    // --- hybrid style and R30 ---

    @Test
    fun hybrid_keepsSovereigntyFilterOnEveryLabelLayer() {
        val plan = BasemapPlanner.plan(esri.style, positronLayers)
        assertEquals(Positron.LABEL_LAYER_IDS.toSet(), plan.sovereigntyFiltered.toSet())
        assertTrue(plan.archipelagoLabels)
        Positron.LABEL_LAYER_IDS.forEach { assertFalse(it, it in plan.hidden) }
    }

    @Test
    fun hybrid_imageryUnderRoadsAndLabels() {
        val plan = BasemapPlanner.plan(esri.style, positronLayers)
        assertEquals("background", plan.imageryAbove)
        assertFalse(plan.imageryAtBottom)
        // Area fills would cover the photo; roads, rail and borders stay.
        listOf("park", "water", "landcover_wood", "landuse_residential", "building", "aeroway-area", "waterway").forEach {
            assertTrue(it, it in plan.hidden)
        }
        listOf("highway_minor", "highway_motorway", "railway", "boundary_2", "boundary_3").forEach { assertFalse(it, it in plan.hidden) }
        assertFalse(plan.repaint.keys.any { it in plan.hidden })
        assertTrue("boundary_2" in plan.repaint)
    }

    @Test
    fun hybrid_withoutBackgroundLayer_imageryGoesToTheBottom() {
        val plan = BasemapPlanner.plan(esri.style, positronLayers.filterNot { it.id == "background" })
        assertNull(plan.imageryAbove)
        assertTrue(plan.imageryAtBottom)
    }

    @Test
    fun light_keepsTodaysLook() {
        val plan = BasemapPlanner.plan(OpenFreeMap.style, positronLayers)
        assertTrue(plan.hidden.isEmpty())
        assertNull(plan.imageryAbove)
        assertFalse(plan.imageryAtBottom)
        assertEquals(Positron.LABEL_LAYER_IDS.toSet(), plan.sovereigntyFiltered.toSet())
        assertEquals(Positron.REPAINT.keys.filter { id -> positronLayers.any { it.id == id } }.toSet(), plan.repaint.keys)
    }

    @Test
    fun snapshot_declaredPlanFiltersTheKnownLabelLayers() {
        // The share snapshot cannot list layers: it uses the declared ids (R30 on the share image).
        val plan = BasemapPlanner.declared(OpenFreeMap.style)
        assertEquals(Positron.LABEL_LAYER_IDS, plan.sovereigntyFiltered)
        assertTrue(plan.archipelagoLabels)
    }
}
