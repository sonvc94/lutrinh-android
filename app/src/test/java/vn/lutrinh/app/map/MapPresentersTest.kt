package vn.lutrinh.app.map

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.map.basemap.BasemapStyle

/**
 * The map interface contract (docs/05 "Map interface", R35): the map composables drive any
 * [MapController] adapter the same way. A fake adapter records what they ask for.
 */
class MapPresentersTest {
    private class FakeMap(override val density: Float = 2f, override val heightPx: Int = 2000) : MapController {
        override var camera: MapCamera? = MapCamera(GeoPoint(21.0, 105.0), 8.0)
        var shownBasemap: BasemapStyle? = null
        var shownPins = emptyList<MapPin>()
        var legs = emptyList<MapLeg>()
        var badges = emptyList<MapBadge>()
        var tripLines = emptyList<MapTripLine>()
        var tripDots = emptyList<MapTripDot>()
        var shownTracks = emptyList<TrackLine>()
        var stops = emptyList<StopMarker>()
        var me: MyLocationFix? = null
        val moves = mutableListOf<String>()

        override fun setBasemap(style: BasemapStyle) { shownBasemap = style }
        override fun setRouteColors(colors: RouteColors) = Unit
        override fun setPins(pins: List<MapPin>) { shownPins = pins }
        override fun setLegs(legs: List<MapLeg>, badges: List<MapBadge>) { this.legs = legs; this.badges = badges }
        override fun setTrips(lines: List<MapTripLine>, dots: List<MapTripDot>) { tripLines = lines; tripDots = dots }
        override fun setTracks(tracks: List<TrackLine>) { shownTracks = tracks }
        override fun setStopSuggestions(stops: List<StopMarker>) { this.stops = stops }
        override fun setMyLocation(fix: MyLocationFix?) { me = fix }
        override fun moveTo(center: GeoPoint, zoom: Double, padding: MapInsets?, animation: CameraAnimation) {
            moves += "move ${center.latitude},${center.longitude} z$zoom $animation pad=$padding"
            camera = MapCamera(center, zoom)
        }
        override fun fitPoints(points: List<GeoPoint>, padding: MapInsets, maxZoom: Double, animation: CameraAnimation) {
            moves += "fit ${points.size} max$maxZoom $animation pad=$padding"
        }
        override fun afterLayout(action: () -> Unit) = action()
        override fun setListener(listener: MapListener?) = Unit
    }

    private val icons = MapIcons(
        pin = { label, kind, selected -> MapIcon(pinImageId(label, kind, selected)) { error("not drawn in tests") } },
        badge = { mode, selected -> MapIcon(mode.badgeId(selected)) { error("not drawn in tests") } },
    )

    private val hg = GeoPoint(22.82, 104.98)
    private val qb = GeoPoint(23.07, 104.99)
    private val dv = GeoPoint(23.28, 105.36)
    private fun marker(id: String, label: String, p: GeoPoint, highlighted: Boolean = false, draft: Boolean = false) =
        MapMarker(id, label, p, highlighted, draft)

    @Test
    fun tripMap_pinsLegsAndBadges() {
        val map = FakeMap()
        val p = TripMapPresenter(icons)
        p.markers = listOf(marker("a", "1", hg), marker("b", "2", qb), marker("c", "3", dv), marker("d", "4", GeoPoint(23.2, 105.2), draft = true))
        p.legModes = listOf(TransportMode.MOTORBIKE, null)
        p.render(map, paddingDp = 56, extraBottomDp = 100, interactive = true)
        assertEquals(
            listOf("pin-1-START-false", "pin-2-STEP-false", "pin-3-END-false", "pin-4-DRAFT-false"),
            map.shownPins.map { it.icon.id },
        )
        // Start/end above the other steps.
        assertTrue(map.shownPins[0].order > map.shownPins[1].order && map.shownPins[2].order > map.shownPins[1].order)
        // R20: the draft is no route point.
        assertEquals(listOf("b", "c"), map.legs.map { it.to })
        assertEquals(listOf(LegLine.GROUND, LegLine.GROUND), map.legs.map { it.line })
        assertEquals(listOf("mode-motorbike"), map.badges.map { it.icon.id })
        // C3: the route fits inside the padding, with the bottom overlay kept free.
        assertEquals(listOf("fit ${map.legs.sumOf { it.path.size } + 1} max14.0 NONE pad=MapInsets(left=112, top=112, right=112, bottom=312)"), map.moves)
    }

    @Test
    fun tripMap_cameraRules() {
        val none = FakeMap()
        TripMapPresenter(icons).render(none, 56, 0, true)
        val c = vn.lutrinh.app.domain.usecase.NewStepDefaults.FALLBACK_CENTER
        assertEquals("move ${c.latitude},${c.longitude} z5.0 NONE pad=null", none.moves.single())
        val one = FakeMap()
        TripMapPresenter(icons).apply { markers = listOf(marker("a", "1", hg)) }.render(one, 56, 0, true)
        assertEquals("move 22.82,104.98 z12.0 NONE pad=null", one.moves.single())
    }

    @Test
    fun tripMap_selectingAStepFliesThere_onlyOnChange() {
        val map = FakeMap()
        val p = TripMapPresenter(icons)
        p.markers = listOf(marker("a", "1", hg), marker("b", "2", qb))
        p.render(map, 56, 0, true)
        map.moves.clear()
        p.render(map, 56, 0, true)
        assertTrue(map.moves.isEmpty())
        p.markers = listOf(marker("a", "1", hg), marker("b", "2", qb, highlighted = true))
        p.render(map, 56, 0, true)
        assertTrue(map.moves.single(), map.moves.single().startsWith("move 23.07,104.99") && map.moves.single().contains("FLY"))
        assertEquals("pin-2-END-true", map.shownPins[1].icon.id)
    }

    @Test
    fun tripMap_notInteractive_neverFlies() {
        val map = FakeMap()
        val p = TripMapPresenter(icons)
        p.markers = listOf(marker("a", "1", hg), marker("b", "2", qb))
        p.render(map, 56, 0, false)
        map.moves.clear()
        p.markers = listOf(marker("a", "1", hg, highlighted = true), marker("b", "2", qb))
        p.fitAllSignal = 1
        p.render(map, 56, 0, false)
        assertTrue(map.moves.isEmpty())
    }

    @Test
    fun tripMap_taps() {
        val p = TripMapPresenter(icons)
        var step: String? = null
        var leg: String? = null
        assertTrue(p.onTap(MapHit.Pin("b"), { step = it }, { leg = it }))
        assertTrue(p.onTap(MapHit.Leg("c"), { step = it }, { leg = it }))
        assertEquals("b", step)
        assertEquals("c", leg)
        assertFalse(p.onTap(MapHit.Pin("b"), null, { leg = it }))
        assertFalse(p.onTap(null, { step = it }, null))
        assertFalse(p.onTap(MapHit.Leg("x"), { step = it }, null))
    }

    @Test
    fun tripMap_myLocationFliesOncePerSignal() {
        val map = FakeMap()
        val p = TripMapPresenter(icons)
        p.markers = listOf(marker("a", "1", hg), marker("b", "2", qb))
        p.render(map, 56, 0, true)
        map.moves.clear()
        p.myLocation = MyLocationFix(dv, 10f)
        p.myLocationSignal = 1
        p.render(map, 56, 0, true)
        p.render(map, 56, 0, true)
        assertEquals(MyLocationFix(dv, 10f), map.me)
        assertEquals(1, map.moves.size)
        assertTrue(map.moves[0], map.moves[0].startsWith("move 23.28,105.36 z15.0") && map.moves[0].contains("FLY"))
    }

    @Test
    fun allTrips_linesDotsAndTaps() {
        val map = FakeMap()
        val p = AllTripsPresenter()
        p.trips = listOf(
            MapTrip("t1", Color(0xFF1F5C45), listOf(hg, qb, dv), listOf(TransportMode.MOTORBIKE, TransportMode.MOTORBIKE)),
            MapTrip("t2", Color(0xFFB85C2E), listOf(GeoPoint(20.25, 105.9)), emptyList()),
        )
        p.selectedId = "t1"
        p.padding = MapInsets(10, 20, 10, 30)
        p.render(map)
        assertEquals(listOf("t1"), map.tripLines.map { it.tripId })
        assertEquals("#1F5C45", map.tripLines.single().color)
        // The selected trip is drawn last (on top).
        assertEquals(listOf("t2", "t1", "t1", "t1"), map.tripDots.map { it.tripId })
        assertTrue(map.tripDots.last().selected)
        assertTrue(map.moves.single(), map.moves.single().startsWith("fit") && map.moves.single().endsWith("pad=MapInsets(left=10, top=20, right=10, bottom=30)"))
        var tapped: String? = null
        assertTrue(p.onTap(MapHit.Trip("t2")) { tapped = it })
        assertEquals("t2", tapped)
        assertFalse(p.onTap(MapHit.Pin("x")) { tapped = it })
    }

    @Test
    fun picker_startsOnce_andEasesToEachMove() {
        val map = FakeMap()
        val p = PickerPresenter(icons)
        val others = listOf(marker("a", "1", hg))
        p.render(map, dv, 13.0, others, null)
        p.render(map, dv, 13.0, others, null)
        assertEquals(listOf("move 23.28,105.36 z13.0 NONE pad=null"), map.moves)
        assertEquals(listOf("pin-1-CONTEXT-false"), map.shownPins.map { it.icon.id })
        assertTrue(map.shownPins.single().faded)
        val move = PickerMove(qb)
        p.render(map, dv, 13.0, others, move)
        p.render(map, dv, 13.0, others, move)
        assertEquals("move 23.07,104.99 z13.0 EASE pad=null", map.moves.last())
        assertEquals(2, map.moves.size)
        assertNull(map.shownBasemap)
    }
}
