package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.AutoName
import vn.lutrinh.app.domain.usecase.AutoTitle
import vn.lutrinh.app.domain.usecase.Gazetteer
import vn.lutrinh.app.domain.usecase.PlaceKind
import vn.lutrinh.app.domain.usecase.PlaceLabel
import vn.lutrinh.app.domain.usecase.PlaceText
import java.io.File
import java.text.Normalizer
import java.time.LocalDateTime

/** R23 offline place search and R24 auto place name (docs/04). */
class PlacesTest {
    /** A small fixture in the asset format (tools/gazetteer/build_gazetteer.py). */
    private val fixture = Gazetteer.parse(
        """
        # test
        R	0	Tuyên Quang	Hà Giang	2244515	10500394
        R	1	Ninh Bình	Hà Nam;Nam Định	2005783	10650385
        R	2	Thái Nguyên	Bắc Kạn	2203424	10579322
        t	0	2327830	10536152	n	Đồng Văn	
        a	0	2325688	10534244	-	Xã Đồng Văn	Đồng Văn Commune
        s	0	2324196	10539792	-	Mã Pí Lèng Pass	Ma Pi Leng Pass
        c	0	2282639	10498361	-	Hà Giang	
        a	1	2065355	10593286	-	Phường Đồng Văn	Đồng Văn Ward
        s	1	2025269	10591808	-	Khu du lịch sinh thái Tràng An	
        v	1	1941511	10551857	-	Đồng Văng	
        a	0	2240000	10600000	-	Xã Xa Xôi	
        """.trimIndent().lineSequence(),
    )

    private val hagiangTrip = listOf(GeoPoint(22.8233, 104.9836), GeoPoint(23.2786, 105.3621))

    @Test fun `normaliser drops diacritics, đ and case`() {
        assertEquals("dong van", PlaceText.normalize("Đồng Văn"))
        assertEquals("deo ma pi leng", PlaceText.normalize("ĐÈO MÃ PÍ LÈNG"))
        assertEquals("trang an", PlaceText.normalize("  Tràng-An! "))
        assertEquals("ba ria vung tau", PlaceText.normalize("Bà Rịa – Vũng Tàu"))
        // decomposed input (NFD) gives the same key as composed
        assertEquals("dong van", PlaceText.normalize(Normalizer.normalize("Đồng Văn", Normalizer.Form.NFD)))
        assertEquals("dong van", PlaceText.core("xa dong van"))
        assertEquals("trang an", PlaceText.core("khu du lich sinh thai trang an"))
        // "Đồng Văn", "Hồ Chí Minh", "Lạng Sơn" keep their first word
        assertEquals("dong van", PlaceText.core("dong van"))
        assertEquals("ho chi minh", PlaceText.core("ho chi minh"))
        assertEquals("lang son", PlaceText.core("lang son"))
    }

    @Test fun `ascii query finds the accented place, exact before prefix`() {
        val hits = fixture.search("dong van")
        assertEquals("Đồng Văn", hits.first().place.name)
        assertEquals("Tuyên Quang", hits.first().place.region.name)
        // "Đồng Văng" only starts with the query: ranked after every exact match
        val vang = hits.indexOfFirst { it.place.name == "Đồng Văng" }
        assertTrue(vang > hits.indexOfFirst { it.place.name == "Phường Đồng Văn" })
        assertEquals(1, hits[vang].tier)
    }

    @Test fun `generic prefix and alias words match`() {
        assertEquals("Khu du lịch sinh thái Tràng An", fixture.search("trang an").first().place.name)
        assertEquals("Mã Pí Lèng Pass", fixture.search("ma pi leng").first().place.name)
        assertEquals("Mã Pí Lèng Pass", fixture.search("MA PI").first().place.name)
        assertTrue(fixture.search("x").isEmpty())
        assertTrue(fixture.search("khong co dau").isEmpty())
    }

    @Test fun `former province name finds the merged province`() {
        val hit = fixture.search("ha giang").first { it.place.kind == PlaceKind.PROVINCE }
        assertEquals("Tuyên Quang", hit.place.name)
        assertEquals("Hà Giang", hit.viaFormer)
        assertEquals("Thái Nguyên", fixture.search("bac kan").first().place.name)
        assertEquals("Ninh Bình", fixture.search("Nam Định").first().place.name)
        // a place plus its former province: "dong van ha giang" → Đồng Văn in Tuyên Quang
        val dv = fixture.search("dong van ha giang").first()
        assertEquals("Tuyên Quang", dv.place.region.name)
        assertEquals("Hà Giang", dv.viaFormer)
    }

    @Test fun `ranking by importance, then distance to the trip, folding duplicates`() {
        // Without a trip: the notable town outranks the communes and ward of the same name.
        val plain = fixture.search("dong van").map { it.place.name }
        assertEquals("Đồng Văn", plain[0])
        // The commune "Xã Đồng Văn" 300 m from the town is folded into it.
        assertTrue("Xã Đồng Văn" !in plain)
        // Near a trip in Ninh Bình the ward there comes first.
        val nb = fixture.search("dong van", near = listOf(GeoPoint(20.6, 105.9)))
        assertEquals("Phường Đồng Văn", nb.first().place.name)
        // Near Hà Giang the Tuyên Quang one does.
        assertEquals("Tuyên Quang", fixture.search("dong van", near = hagiangTrip).first().place.region.name)
    }

    @Test fun `nearest place - sight or village within 3 km, else a commune centre within 15 km`() {
        val pass = fixture.nearest(GeoPoint(23.2405, 105.3990))!!
        assertEquals("Mã Pí Lèng Pass", pass.place.name)
        assertTrue(pass.km < 0.2)
        // 8 km from the "Xã Xa Xôi" centre and nothing within 3 km: the commune names it
        val commune = fixture.nearest(GeoPoint(22.47, 106.0))!!
        assertEquals("Xã Xa Xôi", commune.place.name)
        assertNull(fixture.nearest(GeoPoint(16.0, 108.0)))
    }

    @Test fun `auto title fills a blank or default name and never overwrites a typed one`() {
        assertTrue(AutoName.isDefault(""))
        assertTrue(AutoName.isDefault("Điểm dừng 14:05"))
        assertTrue(!AutoName.isDefault("Nhà nghỉ Hoa Đào"))

        val blank = AutoTitle("").placed("Đồng Văn")
        assertEquals("Đồng Văn", blank.title)
        val moved = blank.placed("Mã Pí Lèng Pass")
        assertEquals("Mã Pí Lèng Pass", moved.title) // our own name follows the pin
        assertEquals("", moved.placed(null).title) // and is taken back where there is no place

        val default = AutoTitle("Điểm dừng 14:05").placed("Lũng Cú")
        assertEquals("Lũng Cú", default.title)
        assertEquals("Điểm dừng 14:05", default.placed(null).title)

        val typed = AutoTitle("Nhà nghỉ Hoa Đào").placed("Đồng Văn")
        assertEquals("Nhà nghỉ Hoa Đào", typed.title)
        // typed over an auto name: stays when the location changes again
        val retyped = blank.copy(title = "Quán phở bà Lan").placed("Mã Pí Lèng Pass")
        assertEquals("Quán phở bà Lan", retyped.title)
        assertEquals("Quán phở bà Lan", retyped.placed(null).title)
    }

    @Test fun `place label keeps a near step, else names the gazetteer place`() {
        fun step(title: String, lat: Double, lng: Double) =
            Step(title, "t", title, "", GeoPoint(lat, lng), LocalDateTime.of(2025, 10, 20, 9, 0), 0, 0)
        val maPiLeng = GeoPoint(23.2405, 105.3990)
        // a step 1 km away still names the point
        assertEquals("gần Gần đèo, 1,1 km", PlaceLabel.label(maPiLeng, listOf(step("Gần đèo", 23.2505, 105.3990)), null, fixture))
        // the nearest step is 6 km away: the gazetteer names it
        assertEquals("Mã Pí Lèng Pass, Tuyên Quang", PlaceLabel.label(maPiLeng, listOf(step("Đồng Văn", 23.2786, 105.3621)), null, fixture))
        assertEquals("gần Mã Pí Lèng Pass (Tuyên Quang), 1,1 km", PlaceLabel.label(GeoPoint(23.2520, 105.3979), emptyList(), null, fixture))
        // no gazetteer: as before
        assertEquals("gần Đồng Văn, 5,7 km", PlaceLabel.label(maPiLeng, listOf(step("Đồng Văn", 23.2786, 105.3621)), null, null))
    }

    // ---- the bundled asset ----

    private val asset by lazy {
        val f = listOf("src/main/assets/places/vn_places.tsv", "app/src/main/assets/places/vn_places.tsv").map(::File).first { it.exists() }
        f.bufferedReader().useLines { Gazetteer.parse(it) }
    }

    @Test fun `bundled gazetteer - 34 provinces and the acceptance searches`() {
        assertEquals(34, asset.regions.size)
        assertTrue(asset.places.size > 15_000)
        val dv = asset.search("dong van", near = hagiangTrip).first()
        assertEquals("Đồng Văn", dv.place.name)
        assertEquals("Tuyên Quang", dv.place.region.name)
        assertEquals("Tuyên Quang", asset.search("dong van").first().place.region.name)
        val ta = asset.search("trang an").first()
        assertTrue(ta.place.name, ta.place.name.contains("Tràng An"))
        assertEquals("Ninh Bình", ta.place.region.name)
        val province = asset.search("ha giang").first { it.place.kind == PlaceKind.PROVINCE }
        assertEquals("Tuyên Quang", province.place.name)
        assertEquals("Lào Cai", asset.search("yen bai").first { it.place.kind == PlaceKind.PROVINCE }.place.name)
    }

    @Test fun `bundled gazetteer - a point near Ma Pi Leng gets a nearby name`() {
        val n = asset.nearest(GeoPoint(23.2405, 105.3990))
        assertNotNull(n)
        n!!
        assertTrue(n.km < 3.0)
        assertEquals("Tuyên Quang", n.place.region.name)
    }
}
