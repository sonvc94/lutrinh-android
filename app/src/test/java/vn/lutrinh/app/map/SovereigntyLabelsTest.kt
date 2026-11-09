package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint

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
        SovereigntyLabels.AREAS.forEach { assertTrue(it.toString(), expr.contains(it.east.toString()) && expr.contains(it.south.toString())) }
    }
}
