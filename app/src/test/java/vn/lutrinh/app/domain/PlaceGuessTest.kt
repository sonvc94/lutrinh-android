package vn.lutrinh.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Gazetteer
import vn.lutrinh.app.domain.usecase.LinkFollower
import vn.lutrinh.app.domain.usecase.PlaceGuess
import vn.lutrinh.app.domain.usecase.PlaceKind
import vn.lutrinh.app.domain.usecase.SharedPlaces
import vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved
import java.io.File
import java.io.IOException

/** R43: a shared place without coordinates is guessed from its name in the bundled gazetteer (offline). */
class PlaceGuessTest {
    private val asset by lazy {
        val f = listOf("src/main/assets/places/vn_places.tsv", "app/src/main/assets/places/vn_places.tsv").map(::File).first { it.exists() }
        f.useLines { Gazetteer.parse(it) }
    }

    /** "name of the place found" (or null) for a share's name, address and the name in the final URL's path. */
    private fun guess(name: String, address: String? = null, path: String? = null): String? =
        PlaceGuess.of(asset, name, address, path)?.name

    // What the real share of the lake gave: EXTRA_SUBJECT + the path of the redirect (docs/04 R43).
    private val lakePath = "Hoàn Kiếm Lake, Hoan Kiem, Ha Noi, Vietnam"

    @Test fun recordedShareOfTheLake() {
        assertEquals("Hồ Hoàn Kiếm", guess("Hoàn Kiếm Lake", path = lakePath))
        assertEquals("Hồ Hoàn Kiếm", guess("", path = lakePath))
        val p = PlaceGuess.of(asset, "Hoàn Kiếm Lake", null, lakePath)!!
        assertEquals(GeoPoint(21.02883, 105.85254), p.location)
    }

    @Test fun vietnameseWithAndWithoutDiacritics() {
        for (n in listOf("Hồ Hoàn Kiếm", "Ho Hoan Kiem", "HỒ HOÀN KIẾM", "ho hoan kiem")) assertEquals(n, "Hồ Hoàn Kiếm", guess(n))
        for (n in listOf("Cột cờ Lũng Cú", "Cot co Lung Cu")) assertEquals(n, "Cột cờ Lũng Cú", guess(n))
        for (n in listOf("Đèo Mã Pí Lèng", "Deo Ma Pi Leng", "Ma Pi Leng")) assertEquals(n, "Đèo Mã Pí Lèng", guess(n))
        assertEquals("Khu du lịch sinh thái Tràng An", guess("Tràng An"))
        assertEquals("Vịnh Hạ Long", guess("Vịnh Hạ Long"))
        assertEquals("Chợ Bến Thành", guess("Chợ Bến Thành"))
    }

    @Test fun englishNamesAndTypeWords() {
        assertEquals("Hồ Hoàn Kiếm", guess("Hoan Kiem Lake"))
        assertEquals("Cột cờ Lũng Cú", guess("Lung Cu Flag Tower"))
        assertEquals("Đèo Mã Pí Lèng", guess("Ma Pi Leng Pass"))
        assertEquals("Vịnh Hạ Long", guess("Ha Long Bay"))
        assertEquals("Phan Si Păng", guess("Mount Phan Si Pang"))
        assertEquals("Nhà thờ Đức Bà Sài Gòn", guess("Notre Dame Cathedral"))
    }

    @Test fun nameWithAddressTail() {
        // Google's path form: the name, then the ward / district / province.
        assertEquals("Hồ Hoàn Kiếm", guess("Hoàn Kiếm Lake, Hoan Kiem, Ha Noi, Vietnam"))
        assertEquals("Chợ Bến Thành", guess("Bến Thành Market", path = "Ben Thanh Market, Ben Thanh, Quan 1, Ho Chi Minh, Vietnam"))
        // A business has no name in the gazetteer: the ward its address names is the guess.
        assertEquals("Phường Hoàn Kiếm", guess("Highlands Coffee", address = "Phố Tràng Tiền, Hoàn Kiếm, Hà Nội"))
        assertEquals("Phường Hoàn Kiếm", guess("Highlands Coffee", address = "12 Phố Hàng Bạc, Phường Hàng Bạc, Quận Hoàn Kiếm, Hà Nội"))
        assertEquals("Phường Yên Hòa", guess("Trung Hòa Nhân Chính", path = "Trung Hòa Nhân Chính, Yen Hoa, Ha Noi, Vietnam"))
        assertEquals("Lũng Cú", guess("Cà phê Lũng Cú, Xã Lũng Cú, Tuyên Quang, Vietnam"))
    }

    @Test fun noMatchGivesNoGuess() {
        assertNull(guess("Quán ăn ABC"))
        assertNull(guess("Phở Thìn Lò Đúc"))
        assertNull(guess("Cà phê Lũng Cú"))        // a business: no address, nothing to go on
        assertNull(guess("Thôn 1"))                // hundreds of villages, no province named
        assertNull(guess(""))
        assertNull(guess("ab"))
        assertNull(guess("Xyzzy Plugh", address = "Quận Không Có, Tỉnh Nào Đó"))
        assertNull(guess("Highlands Coffee", address = "Hà Nội"))   // a province alone is too coarse
    }

    @Test fun provinceNameIsNotAGuessButACityIs() {
        assertEquals(PlaceKind.CITY, PlaceGuess.of(asset, "Hội An", null, null)!!.kind)
        assertNull(PlaceGuess.of(asset, "Tuyên Quang", null, null)?.takeIf { it.kind == PlaceKind.PROVINCE })
    }

    @Test fun theProvinceInTheAddressPicksAmongSameNames() {
        // "Đồng Văn": a town in Tuyên Quang and a ward in Ninh Bình; the address says which.
        val ninhBinh = PlaceGuess.of(asset, "Đồng Văn", "Ninh Bình", null)!!
        assertEquals("Ninh Bình", ninhBinh.region.name)
        val tuyenQuang = PlaceGuess.of(asset, "Đồng Văn", "Tuyên Quang", null)!!
        assertEquals("Tuyên Quang", tuyenQuang.region.name)
    }

    @Test fun apply_putsTheGuessOnAResultWithoutPosition() = runBlocking {
        val text = "https://maps.app.goo.gl/T7jLEWW3zrSJRsjSA"
        val final = "https://www.google.com/maps/place/Ho%C3%A0n+Ki%E1%BA%BFm+Lake,+Hoan+Kiem,+Ha+Noi,+Vietnam/data=!4m2!3m1!1s0x3135ab953357c995:0x1babf6bb4f9a20e"
        val p = SharedPlaces.parse(SharedPlaces.combine(text, "Hoàn Kiếm Lake")) as SharedPlaces.Parsed.Place
        val r = SharedPlaces.resolve(p, LinkFollower { final })
        assertEquals(lakePath, (r as Resolved.NoCoordinates).pathName)
        assertNull(r.guess)
        assertEquals(GeoPoint(21.02883, 105.85254), PlaceGuess.apply(asset, r).guess)
        // offline: the subject alone is enough, and the offline result keeps its kind
        val off = SharedPlaces.resolve(p, LinkFollower { throw IOException("offline") })
        val offGuess = PlaceGuess.apply(asset, off)
        assertEquals(true, offGuess is Resolved.Unreachable)
        assertEquals(GeoPoint(21.02883, 105.85254), offGuess.guess)
        // a result with a position is left alone (a dropped pin keeps its exact coordinates)
        val found = Resolved.Found("Hồ Hoàn Kiếm", null, GeoPoint(21.0, 105.8))
        assertEquals(found, PlaceGuess.apply(asset, found))
        assertNull(found.guess)
        assertNotNull(PlaceGuess.apply(asset, Resolved.NoCoordinates("Hồ Hoàn Kiếm", null)).guess)
    }
}
