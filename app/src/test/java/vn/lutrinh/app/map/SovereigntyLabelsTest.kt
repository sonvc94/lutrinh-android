package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.map.maplibre.sovereigntyHidden

/** R30 (docs/04): Chinese admin names over Hoàng Sa / Trường Sa are hidden; ours are drawn. */
class SovereigntyLabelsTest {
    private val woody = GeoPoint(16.83, 112.34) // Đảo Phú Lâm
    private val spratly = GeoPoint(8.64, 111.92) // Đảo Trường Sa
    private val hanoi = GeoPoint(21.03, 105.85)
    private val guangzhouNansha = GeoPoint(22.80, 113.53) // the other 南沙区, in Guangzhou

    /** Place features as OpenFreeMap tiles 2026-11-01 carry them (name:nonlatin = Chinese script). */
    private fun place(cls: String, name: String, vi: String? = null, latin: String? = null, nonLatin: Boolean = true) =
        mapOf("class" to cls, "name" to name, "name:vi" to vi, "name:latin" to latin, "name:nonlatin" to (if (nonLatin) name else null))

    @Test
    fun chineseSeamountNames_inTheSeaAroundTheReefs_areHidden() {
        fun sea(name: String) = mapOf("class" to "strait", "name" to name, "name:nonlatin" to name)
        listOf(
            sea("晋卿西海山") to GeoPoint(15.30, 112.49), sea("琵琶海山") to GeoPoint(13.89, 111.97),
            sea("闲潭海台") to GeoPoint(11.48, 110.23), sea("江畔海山") to GeoPoint(10.68, 110.52),
        ).forEach { (p, at) -> assertTrue(p.toString(), SovereigntyLabels.hides(p, at)) }
        // Land and the Vietnamese coast stay out of the boxes; Haikou keeps its label.
        val haikou = place("city", "海口市")
        assertFalse(SovereigntyLabels.hides(haikou, GeoPoint(20.04, 110.33)))
        assertFalse(SovereigntyLabels.hides(place("city", "Đà Nẵng", nonLatin = false), GeoPoint(16.05, 108.2)))
        assertFalse(SovereigntyLabels.hides(place("city", "Nha Trang", nonLatin = false), GeoPoint(12.24, 109.19)))
    }

    @Test
    fun blockedNames_coverTheThreeAdminUnits_inBothScripts() {
        listOf("三沙市", "Tam Sa", "Sansha", "西沙区", "Quận Tây Sa", "Xisha", "南沙区", "Quận Nam Sa", "Nansha").forEach {
            assertTrue(it, it in SovereigntyLabels.BLOCKED_NAMES)
        }
    }

    @Test
    fun chineseAdminUnits_areHidden_byEachOfTheirNames() {
        listOf(
            place("city", "三沙市", "Tam Sa", "Sansha") to woody,
            place("city", "西沙区", "Quận Tây Sa", "Xisha") to woody,
            place("city", "南沙区", "Quận Nam Sa", "Nansha") to spratly,
        ).forEach { (p, at) -> assertTrue(p.toString(), SovereigntyLabels.hides(p, at)) }
        // Each blocked name alone hides a label in an area, even without a Chinese local name.
        SovereigntyLabels.BLOCKED_NAMES.forEach { n ->
            SovereigntyLabels.NAME_FIELDS.forEach { field ->
                assertTrue("$field=$n", SovereigntyLabels.hides(mapOf("class" to "city", field to n), woody))
                assertTrue("$field=$n", SovereigntyLabels.hides(mapOf("class" to "city", field to n), spratly))
            }
        }
    }

    @Test
    fun otherChineseScriptLabels_inTheAreas_areHidden() {
        assertTrue(SovereigntyLabels.hides(place("town", "永兴镇", latin = "Yongxing"), woody))
        assertTrue(SovereigntyLabels.hides(place("village", "甘泉社区", "Xã khu Cam Tuyền", "Xã khu Cam Tuyền"), woody))
        assertTrue(SovereigntyLabels.hides(place("village", "赤瓜岛"), spratly))
        assertTrue(SovereigntyLabels.hides(place("island", "羚羊岛"), woody)) // no name:vi
        assertTrue(SovereigntyLabels.hides(place("other", "三沙永兴机场", "Sân bay Tam Sa Vĩnh Hưng", "Sansha Yongxing Airport"), woody))
    }

    @Test
    fun vietnameseAndLatinLabels_stay() {
        assertFalse(SovereigntyLabels.hides(place("island", "永兴岛", "Đảo Phú Lâm", "Woody Island"), woody))
        assertFalse(SovereigntyLabels.hides(place("town", "Trường Sa", "Trường Sa", "Trường Sa", nonLatin = false), spratly))
        assertFalse(SovereigntyLabels.hides(place("village", "Song Tử Tây", latin = "Song Tử Tây", nonLatin = false), spratly))
        assertFalse(SovereigntyLabels.hides(place("island", "Đảo Nam Yết", "Đảo Nam Yết", nonLatin = false), spratly))
    }

    @Test
    fun otherCountriesAdminNames_inTheAreas_areHidden() {
        // Philippine municipality (Latin script, name:vi is just the same word) and its quarter.
        assertTrue(SovereigntyLabels.hides(place("town", "Kalayaan", "Kalayaan", "Kalayaan", nonLatin = false), spratly))
        assertTrue(SovereigntyLabels.hides(place("quarter", "Pag-asa", latin = "Pag-asa", nonLatin = false), spratly))
        // Not in the blocked list at all: a Malaysian and a Taiwanese admin unit are hidden by class alone.
        assertTrue(SovereigntyLabels.hides(place("village", "Kampung Layang", latin = "Kampung Layang", nonLatin = false), spratly))
        assertTrue(SovereigntyLabels.hides(place("village", "Taiping Village", latin = "Taiping Village", nonLatin = false), spratly))
        assertTrue(SovereigntyLabels.hides(place("hamlet", "太平村", "Thôn Thái Bình", "Taiping"), spratly))
        assertTrue(SovereigntyLabels.hides(place("town", "Kalayaan", nonLatin = false), GeoPoint(11.05, 114.28)))
        // A Vietnamese water name that embeds the Chinese admin name.
        assertTrue(SovereigntyLabels.hides(place("lake", "三沙永乐龙洞", "Hố Rồng (Tam Sa Vĩnh Lạc Long Động)", "Sansha Yongle Dragon Hole"), woody))
        // Other label layers: Philippine harbour and airfield names.
        assertTrue(SovereigntyLabels.hides(mapOf("class" to "lake", "name" to "Pag-asa Island Harbor"), spratly))
        assertTrue(SovereigntyLabels.hides(mapOf("class" to "military", "name" to "Rancudo Airfield"), spratly))
    }

    @Test
    fun vietnameseAdminUnits_andNaturalFeatures_stay() {
        SovereigntyLabels.VIETNAMESE_ADMIN.forEach {
            assertFalse(it, SovereigntyLabels.hides(place("village", it, latin = it, nonLatin = false), spratly))
            assertFalse(it, SovereigntyLabels.hides(place("town", it, it, it, nonLatin = false), woody))
        }
        // Islands and reefs shown through their Vietnamese name:vi, even with a foreign local name.
        assertFalse(SovereigntyLabels.hides(place("island", "Terumbu Siput", "Đá En Ca", "Terumbu Siput", nonLatin = false), spratly))
        assertFalse(SovereigntyLabels.hides(place("island", "Swallow Reef", "Đá Hoa Lau", "Swallow Reef", nonLatin = false), spratly))
        assertFalse(SovereigntyLabels.hides(place("island", "太平島", "Đảo Thái Bình", "Taiping Island"), spratly))
        assertFalse(SovereigntyLabels.hides(mapOf("class" to "lake", "name" to "Âu tàu Trường Sa"), spratly))
        assertFalse(SovereigntyLabels.hides(mapOf("class" to "ferry", "name" to "Sinh Tồn – Nam Yết"), spratly))
        // The same foreign names outside the boxes are untouched.
        assertFalse(SovereigntyLabels.hides(place("town", "Kalayaan", "Kalayaan", nonLatin = false), GeoPoint(9.75, 118.73)))
        assertFalse(SovereigntyLabels.hides(place("village", "Kampung Layang", nonLatin = false), hanoi))
    }

    @Test
    fun outsideTheAreas_nothingIsHidden() {
        assertFalse(SovereigntyLabels.hides(place("city", "南沙区", "Quận Nam Sa", "Nansha"), guangzhouNansha))
        assertFalse(SovereigntyLabels.hides(place("city", "北京市", "Bắc Kinh", "Beijing"), GeoPoint(39.9, 116.4)))
        assertFalse(SovereigntyLabels.hides(mapOf("class" to "city", "name" to "Tam Sa"), hanoi))
        // Mainland Vietnam, Hainan and Palawan stay outside both boxes.
        listOf(hanoi, GeoPoint(16.05, 108.2), GeoPoint(18.25, 109.5), GeoPoint(9.75, 118.73), GeoPoint(6.88, 116.84))
            .forEach { assertFalse(it.toString(), SovereigntyLabels.inAreas(it)) }
    }

    @Test
    fun ourLabels_areInTheirAreas_andInTheGeoJson() {
        assertEquals(listOf("Quần đảo Hoàng Sa", "Quần đảo Trường Sa"), SovereigntyLabels.ARCHIPELAGOS.map { it.label })
        SovereigntyLabels.ARCHIPELAGOS.forEach { assertTrue(it.label, it.at in it.area) }
        assertTrue(woody in SovereigntyLabels.HOANG_SA.area)
        assertTrue(spratly in SovereigntyLabels.TRUONG_SA.area)
        val json = SovereigntyLabels.labelsGeoJson()
        assertTrue(json.startsWith("{\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"name\":\"Quần đảo Hoàng Sa\""))
        assertTrue(json.contains("\"name\":\"Quần đảo Trường Sa\""))
        assertTrue(json.contains("[111.95,16.45]"))
        assertTrue(json.contains("[114.3,10.0]"))
        assertEquals(2, Regex("\"Point\"").findAll(json).count())
    }

    @Test
    fun mapFilter_isTheSameRule() {
        val expr = sovereigntyHidden().toString()
        assertTrue(expr, expr.contains("within"))
        assertTrue(expr.contains("name:nonlatin"))
        SovereigntyLabels.BLOCKED_NAMES.forEach { assertTrue(it, expr.contains(it)) }
        SovereigntyLabels.NAME_FIELDS.forEach { assertTrue(it, expr.contains("\"$it\"") || expr.contains(it)) }
        SovereigntyLabels.ADMIN_CLASSES.forEach { assertTrue(it, expr.contains(it)) }
        SovereigntyLabels.VIETNAMESE_ADMIN.forEach { assertTrue(it, expr.contains(it)) }
        SovereigntyLabels.BLOCKED_SUBSTRINGS.forEach { assertTrue(it, expr.contains(it)) }
        SovereigntyLabels.AREAS.forEach { assertTrue(it.toString(), expr.contains(it.east.toString()) && expr.contains(it.south.toString())) }
    }
}
