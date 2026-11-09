package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint

/** R15 — offline map areas: region config, trip area (box + margin), size estimate, phases. */
class OfflineConfigTest {
    private val haGiangPlaces = mapOf(
        "TP Hà Giang" to GeoPoint(22.8233, 104.9836),
        "Quản Bạ" to GeoPoint(23.0688, 104.9930),
        "Yên Minh" to GeoPoint(23.1203, 105.1406),
        "Lũng Cú" to GeoPoint(23.3638, 105.3164),
        "Đồng Văn" to GeoPoint(23.2786, 105.3621),
        "Mã Pí Lèng" to GeoPoint(23.2397, 105.4096),
        "Mèo Vạc" to GeoPoint(23.1597, 105.4086),
        "Du Già" to GeoPoint(22.9333, 105.1667),
    )
    private val ninhBinhPlaces = mapOf(
        "Tràng An" to GeoPoint(20.2546, 105.8954),
        "Tam Cốc" to GeoPoint(20.2163, 105.9370),
        "Hang Múa" to GeoPoint(20.2299, 105.9327),
        "Hoa Lư" to GeoPoint(20.2862, 105.9063),
        "Bái Đính" to GeoPoint(20.2745, 105.8641),
        "Vân Long" to GeoPoint(20.3800, 105.8600),
        "Cúc Phương (trung tâm VQG)" to GeoPoint(20.3166, 105.6083),
        "Cúc Phương (cổng)" to GeoPoint(20.2470, 105.7140),
    )

    @Test
    fun readyMadeRegions_coverTheirPlaces() {
        assertEquals(listOf("ha-giang", "ninh-binh"), OfflineConfig.READY_MADE.map { it.key })
        haGiangPlaces.forEach { (name, p) -> assertTrue(name, p in OfflineConfig.HA_GIANG.bounds) }
        ninhBinhPlaces.forEach { (name, p) -> assertTrue(name, p in OfflineConfig.NINH_BINH.bounds) }
        assertFalse(GeoPoint(15.8801, 108.3380) in OfflineConfig.HA_GIANG.bounds) // Hội An
        assertEquals(OfflineConfig.HA_GIANG, OfflineConfig.readyMade("ha-giang"))
        assertNull(OfflineConfig.readyMade("hoi-an"))
    }

    @Test
    fun readyMadeRegions_stayWithinTheSizeTarget() {
        OfflineConfig.READY_MADE.forEach {
            val est = OfflineConfig.estimateBytes(it.bounds)
            assertTrue("${it.key} $est", est in 5_000_000..OfflineConfig.MAX_AREA_BYTES)
            assertTrue(OfflineConfig.withinLimit(it.bounds))
        }
        assertEquals(0, OfflineConfig.MIN_ZOOM)
        assertEquals(14, OfflineConfig.MAX_ZOOM)
        assertEquals(OfflineConfig.MAX_ZOOM + 1, OfflineConfig.BYTES_PER_TILE.size)
    }

    @Test
    fun tileCount_isOnePerZoomInsideOneTile_andGrowsWithZoom() {
        // ~100 m box well inside one z14 tile (Đồng Văn).
        val tiny = GeoBounds(23.2780, 105.3610, 23.2790, 105.3620)
        (0..14).forEach { assertEquals("z$it", 1L, OfflineConfig.tileCount(tiny, it)) }
        assertEquals(15L, OfflineConfig.tileCount(tiny))
        assertEquals(OfflineConfig.FIXED_BYTES + OfflineConfig.BYTES_PER_TILE.sum(), OfflineConfig.estimateBytes(tiny))
        val hg = OfflineConfig.HA_GIANG.bounds
        assertEquals(1L, OfflineConfig.tileCount(hg, 0))
        assertTrue((1..14).all { OfflineConfig.tileCount(hg, it) >= OfflineConfig.tileCount(hg, it - 1) })
        // 0.75° x 0.72° at z14: ~35 columns x ~34 rows.
        assertTrue(OfflineConfig.tileCount(hg, 14) in 1100L..1400L)
    }

    @Test
    fun tripArea_isTheStepsBoxPlusTenKmEachSide() {
        val steps = haGiangPlaces.values.toList()
        val area = OfflineConfig.tripArea("t1", "Vòng cung Hà Giang", steps)!!
        assertEquals("trip:t1", area.key)
        assertEquals("t1", OfflineConfig.tripIdOf(area.key))
        assertNull(OfflineConfig.tripIdOf("ha-giang"))
        assertEquals("Vòng cung Hà Giang", area.name)
        val dLat = 10.0 / 111.32
        assertEquals(22.8233 - dLat, area.bounds.south, 1e-9)
        assertEquals(23.3638 + dLat, area.bounds.north, 1e-9)
        // Longitude margin measured at the box's widest latitude (its north edge here).
        val dLng = 10.0 / (111.32 * Math.cos(Math.toRadians(23.3638)))
        assertEquals(104.9836 - dLng, area.bounds.west, 1e-9)
        assertEquals(105.4096 + dLng, area.bounds.east, 1e-9)
        assertTrue(steps.all { it in area.bounds })
        assertTrue(OfflineConfig.withinLimit(area.bounds))
    }

    @Test
    fun tripArea_ofOneStepIsAboutTwentyKmWide_andNoStepsGivesNone() {
        assertNull(OfflineConfig.tripArea("t", "Trống", emptyList()))
        val one = OfflineConfig.tripArea("t", "Một điểm", listOf(GeoPoint(20.2546, 105.8954)))!!.bounds
        assertEquals(20.0 / 111.32, one.north - one.south, 1e-9)
        val km = (one.east - one.west) * 111.32 * Math.cos(Math.toRadians(one.north))
        assertEquals(20.0, km, 0.1)
    }

    @Test
    fun tripArea_spanningTheCountryIsOverTheLimit() {
        val area = OfflineConfig.tripArea("t", "Xuyên Việt", listOf(GeoPoint(21.0285, 105.8542), GeoPoint(10.7769, 106.7009)))!!
        assertFalse(OfflineConfig.withinLimit(area.bounds))
        assertTrue(OfflineConfig.estimateBytes(area.bounds) > OfflineConfig.MAX_AREA_BYTES)
    }

    @Test
    fun storageCheck_keepsAReserve() {
        assertTrue(OfflineConfig.fitsInStorage(10_000_000, 60_000_000))
        assertFalse(OfflineConfig.fitsInStorage(10_000_001, 60_000_000))
    }

    @Test
    fun covering_findsTheAreaContainingThePoint() {
        val all = OfflineConfig.READY_MADE
        assertEquals(OfflineConfig.HA_GIANG, OfflineConfig.covering(all, haGiangPlaces.getValue("Đồng Văn")))
        assertEquals(OfflineConfig.NINH_BINH, OfflineConfig.covering(all, ninhBinhPlaces.getValue("Tam Cốc")))
        assertNull(OfflineConfig.covering(all, GeoPoint(15.8801, 108.3380)))
    }

    @Test
    fun phases_followTheStoredStatus() {
        fun of(stored: Boolean = true, complete: Boolean = false, paused: Boolean = false, problem: OfflineProblem? = null, online: Boolean = true) =
            OfflinePhases.of(stored, complete, paused, problem, online)
        assertEquals(OfflinePhase.NOT_DOWNLOADED, of(stored = false))
        assertEquals(OfflinePhase.FAILED, of(stored = false, problem = OfflineProblem.NO_NETWORK))
        assertEquals(OfflinePhase.DOWNLOADING, of())
        assertEquals(OfflinePhase.DOWNLOADING, of(problem = OfflineProblem.SERVER))
        assertEquals(OfflinePhase.WAITING_NETWORK, of(online = false))
        assertEquals(OfflinePhase.WAITING_NETWORK, of(problem = OfflineProblem.NO_NETWORK))
        assertEquals(OfflinePhase.PAUSED, of(paused = true, online = false))
        assertEquals(OfflinePhase.FAILED, of(problem = OfflineProblem.STORAGE_FULL))
        assertEquals(OfflinePhase.COMPLETE, of(complete = true, online = false, problem = OfflineProblem.NO_NETWORK))
    }

    @Test
    fun problems_areMappedFromOfflineErrors() {
        assertEquals(OfflineProblem.NO_NETWORK, OfflinePhases.problemOf("REASON_CONNECTION", "Unable to resolve host"))
        assertEquals(OfflineProblem.SERVER, OfflinePhases.problemOf("REASON_SERVER", "HTTP 503"))
        assertEquals(OfflineProblem.STORAGE_FULL, OfflinePhases.problemOf("REASON_OTHER", "database or disk is full"))
        assertEquals(OfflineProblem.OTHER, OfflinePhases.problemOf("REASON_OTHER", "parse error"))
    }

    @Test
    fun progress_isTheShareOfStoredResources() {
        val s = OfflineAreaState(OfflineConfig.HA_GIANG, OfflinePhase.DOWNLOADING, completedResources = 25, requiredResources = 100)
        assertEquals(0.25f, s.progress)
        assertEquals(0f, s.copy(requiredResources = 0).progress)
    }

    // R31: OpenFreeMap allows ≤ 3,000 tiles or 50 MB per offline download.
    @Test
    fun r31_limitsAreOpenFreeMapsTerms_inclusive() {
        assertEquals(50_000_000L, OfflineConfig.MAX_AREA_BYTES)
        assertEquals(3_000L, OfflineConfig.MAX_AREA_TILES)
        assertTrue(OfflineConfig.withinLimit(50_000_000, 3_000))
        assertTrue(OfflineConfig.withinLimit(17_000_000, 1))
        assertFalse(OfflineConfig.withinLimit(50_000_001, 3_000))
        assertFalse(OfflineConfig.withinLimit(50_000_000, 3_001))
        assertFalse(OfflineConfig.withinLimit(80_000_000, 5_000))
    }

    @Test
    fun r31_tileEstimate_countsVectorAndRasterSources() {
        val tiny = GeoBounds(23.2780, 105.3610, 23.2790, 105.3620)
        // 15 vector tiles (z0–14) + 7 relief raster tiles (z0–6).
        assertEquals(22L, OfflineConfig.estimateTiles(tiny))
        // Hà Giang: 1,757 vector + 7 raster estimated; the store held 1,763 tiles (2026-11-09).
        assertEquals(1_764L, OfflineConfig.estimateTiles(OfflineConfig.HA_GIANG.bounds))
    }

    @Test
    fun r31_readyMadeRegionsAndAHaGiangTripStayAllowed() {
        OfflineConfig.READY_MADE.forEach {
            assertTrue("${it.key} tiles", OfflineConfig.estimateTiles(it.bounds) <= OfflineConfig.MAX_AREA_TILES)
            assertTrue("${it.key} bytes", OfflineConfig.estimateBytes(it.bounds) <= OfflineConfig.MAX_AREA_BYTES)
            assertTrue(it.key, OfflineConfig.withinLimit(it.bounds))
        }
    }

    @Test
    fun r31_anAreaOverTheTileCapIsRefused_evenUnderTheByteCap() {
        // Hà Giang loop + Cao Bằng (Bản Giốc): ~1.6° wide, about twice Hà Giang's tiles.
        val area = OfflineConfig.tripArea(
            "t", "Hà Giang – Cao Bằng",
            listOf(GeoPoint(22.8233, 104.9836), GeoPoint(23.3638, 105.4096), GeoPoint(22.8547, 106.7234)),
        )!!
        assertTrue(OfflineConfig.estimateTiles(area.bounds) > OfflineConfig.MAX_AREA_TILES)
        assertTrue(OfflineConfig.estimateBytes(area.bounds) <= OfflineConfig.MAX_AREA_BYTES)
        assertFalse(OfflineConfig.withinLimit(area.bounds))
    }
}
