package vn.lutrinh.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.LinkFollower
import vn.lutrinh.app.domain.usecase.SharedPlaces
import vn.lutrinh.app.domain.usecase.SharedPlaces.Parsed
import vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved
import vn.lutrinh.app.sharein.GoogleMapsLinks
import java.io.IOException

/** R43: share texts of Google Maps (captured on an AVD with Google Maps 2026-11-13) and the position they resolve to. */
class SharedPlacesTest {
    // What the Android app sent for the place "Hoàn Kiếm Lake": EXTRA_TEXT = the link, EXTRA_SUBJECT = the name.
    private val poiText = "https://maps.app.goo.gl/T7jLEWW3zrSJRsjSA"
    private val poiSubject = "Hoàn Kiếm Lake"

    // Where that short link ends (real 302 `Location`, tracking parameters cut): a place by name, no coordinates.
    private val poiFinal = "https://www.google.com/maps/place/Ho%C3%A0n+Ki%E1%BA%BFm+Lake,+Hoan+Kiem,+Ha+Noi,+Vietnam/" +
        "data=!4m2!3m1!1s0x3135ab953357c995:0x1babf6bb4f9a20e?utm_source=mstt_1&entry=gps&coh=192189"

    // A dropped pin ("Trung Hòa Nhân Chính"): the same shape, but the final URL carries !3d…!4d….
    private val pinText = "https://maps.app.goo.gl/k5oZwmBQrqBrrcUj9"
    private val pinFinal = "https://www.google.com/maps/place/Trung+H%C3%B2a+Nh%C3%A2n+Ch%C3%ADnh,+Yen+Hoa,+Ha+Noi,+Vietnam/" +
        "data=!4m6!3m5!1s0x3135aca388e3db1d:0xebf549b14fb286f!7e2!8m2!3d21.0044695!4d105.7999624?utm_source=mstt_1&entry=gps"

    private fun follower(vararg answers: Pair<String, String?>) = LinkFollower { url ->
        answers.toMap().getOrElse(url) { throw IOException("no answer for $url") }
    }

    private fun place(text: String, subject: String? = null) =
        SharedPlaces.parse(SharedPlaces.combine(text, subject)) as Parsed.Place

    @Test fun realShareIsNameFromSubjectPlusShortLink() {
        val p = place(poiText, poiSubject)
        assertEquals("Hoàn Kiếm Lake", p.name)
        assertEquals(poiText, p.link)
        assertNull(p.address)
        assertNull(p.point)
    }

    @Test fun shortLinkWithoutCoordinatesFallsBackToNameOnly() = runBlocking {
        val r = SharedPlaces.resolve(place(poiText, poiSubject), follower(poiText to poiFinal))
        assertEquals(Resolved.NoCoordinates("Hoàn Kiếm Lake", null, pathName = "Hoàn Kiếm Lake, Hoan Kiem, Ha Noi, Vietnam"), r)
    }

    @Test fun shortLinkOfADroppedPinResolvesToItsCoordinates() = runBlocking {
        val r = SharedPlaces.resolve(place(pinText, "Trung Hòa Nhân Chính"), follower(pinText to pinFinal))
        assertEquals(Resolved.Found("Trung Hòa Nhân Chính", null, GeoPoint(21.0044695, 105.7999624)), r)
    }

    @Test fun bareLinkTakesTheNameFromTheResolvedUrl() = runBlocking {
        val r = SharedPlaces.resolve(place(pinText), follower(pinText to pinFinal))
        assertEquals("Trung Hòa Nhân Chính, Yen Hoa, Ha Noi, Vietnam", r.name)
        assertTrue(r is Resolved.Found)
    }

    @Test fun offlineShortLinkIsUnreachableAndKeepsTheName() = runBlocking {
        val r = SharedPlaces.resolve(place(poiText, poiSubject), LinkFollower { throw IOException("Unable to resolve host") })
        assertEquals(Resolved.Unreachable("Hoàn Kiếm Lake", null), r)
    }

    @Test fun aRedirectOffGoogleIsNoCoordinates() = runBlocking {
        val r = SharedPlaces.resolve(place(poiText, poiSubject), LinkFollower { null })
        assertEquals(Resolved.NoCoordinates("Hoàn Kiếm Lake", null), r)
    }

    @Test fun fullUrlInTheTextNeedsNoNetwork() = runBlocking {
        val text = "Hồ Gươm\nPhố Đinh Tiên Hoàng, Hà Nội\nhttps://www.google.com/maps/place/H%E1%BB%93+G%C6%B0%C6%A1m/@21.0287747,105.8522264,17z/data=!3m1!4b1"
        val p = place(text)
        assertEquals("Hồ Gươm", p.name)
        assertEquals("Phố Đinh Tiên Hoàng, Hà Nội", p.address)
        // Must not call the follower: it throws.
        val r = SharedPlaces.resolve(p, LinkFollower { error("network used") })
        assertEquals(Resolved.Found("Hồ Gươm", "Phố Đinh Tiên Hoàng, Hà Nội", GeoPoint(21.0287747, 105.8522264)), r)
    }

    @Test fun coordinateFormatsInUrls() {
        val want = GeoPoint(21.0287747, 105.8522264)
        fun at(u: String) = SharedPlaces.coordinatesIn(u)
        assertEquals(want, at("https://www.google.com/maps/@21.0287747,105.8522264,15z"))
        assertEquals(want, at("https://www.google.com/maps/place/X/data=!3d21.0287747!4d105.8522264"))
        assertEquals(want, at("https://www.google.com/maps?q=21.0287747,105.8522264"))
        assertEquals(want, at("https://www.google.com/maps?q=21.0287747%2C105.8522264"))
        assertEquals(want, at("https://maps.google.com/?ll=21.0287747,105.8522264&z=15"))
        assertEquals(want, at("https://www.google.com/maps/search/?api=1&query=21.0287747,105.8522264"))
        assertEquals(want, at("https://www.google.com/maps/place/21.0287747,105.8522264"))
        assertEquals(GeoPoint(-33.8567844, 151.2152967), at("https://www.google.com/maps/@-33.8567844,151.2152967,17z"))
    }

    @Test fun thePinBeatsTheMapCentre() {
        val u = "https://www.google.com/maps/place/X/@21.1,105.9,17z/data=!4m6!3m5!8m2!3d21.0044695!4d105.7999624"
        assertEquals(GeoPoint(21.0044695, 105.7999624), SharedPlaces.coordinatesIn(u))
    }

    @Test fun consentPageHoldsTheRealUrlInContinue() {
        val u = "https://consent.google.com/m?continue=https://www.google.com/maps/place/X/@21.02,105.85,17z&gl=DE"
        assertEquals(GeoPoint(21.02, 105.85), SharedPlaces.coordinatesIn(u))
    }

    @Test fun urlsWithoutUsableCoordinates() {
        assertNull(SharedPlaces.coordinatesIn(poiFinal))
        assertNull(SharedPlaces.coordinatesIn("https://www.google.com/maps/place/Hanoi"))
        assertNull(SharedPlaces.coordinatesIn("https://www.google.com/maps?q=0,0"))
        assertNull(SharedPlaces.coordinatesIn("https://www.google.com/maps/@91.0,105.0,15z"))
        assertNull(SharedPlaces.coordinatesIn("https://www.google.com/maps?q=Ho+Guom"))
    }

    @Test fun nameInAPlaceUrl() {
        assertEquals("Hoàn Kiếm Lake, Hoan Kiem, Ha Noi, Vietnam", SharedPlaces.nameIn(poiFinal))
        assertNull(SharedPlaces.nameIn("https://www.google.com/maps/place/21.0287747,105.8522264"))
        assertNull(SharedPlaces.nameIn("https://www.google.com/maps?q=21.0,105.8"))
    }

    @Test fun textThatIsNotAGoogleMapsPlace() {
        for (t in listOf(
            "", "Hello", "Xem https://example.com/maps/place/x", "https://www.facebook.com/",
            "https://maps.app.goo.gl.evil.example/abc", "https://evilgoogle.com/maps/place/x",
            "https://www.google.com/search?q=hanoi", "https://goo.gl/something", "http://www.google.com.evil.example/maps",
        )) {
            assertEquals(t, Parsed.NotGoogleMaps, SharedPlaces.parse(t))
        }
    }

    @Test fun otherGoogleMapsLinkShapesAreAccepted() {
        for (u in listOf(
            "https://maps.app.goo.gl/abc", "https://goo.gl/maps/abc", "https://maps.google.com/?q=21.0,105.8",
            "https://www.google.com/maps/place/Hanoi", "https://google.com/maps?q=21.0,105.8",
        )) {
            assertTrue(u, SharedPlaces.parse("Name\n$u") is Parsed.Place)
        }
    }

    @Test fun multiLineShareKeepsNameAddressAndTrimsLinkPunctuation() {
        val p = place("Quán Cà Phê\n12 Phố Huế\nHai Bà Trưng, Hà Nội\n(https://maps.app.goo.gl/abc).")
        assertEquals("Quán Cà Phê", p.name)
        assertEquals("12 Phố Huế, Hai Bà Trưng, Hà Nội", p.address)
        assertEquals("https://maps.app.goo.gl/abc", p.link)
    }

    @Test fun subjectIsUsedOnlyWhenTheTextHasNoName() {
        assertEquals("A\nhttps://maps.app.goo.gl/x", SharedPlaces.combine("https://maps.app.goo.gl/x", "A"))
        assertEquals("B\nhttps://maps.app.goo.gl/x", SharedPlaces.combine("B\nhttps://maps.app.goo.gl/x", "A"))
        assertEquals("https://maps.app.goo.gl/x", SharedPlaces.combine("https://maps.app.goo.gl/x", null))
    }

    @Test fun linkFollowerNeverLeavesTheGoogleAllowList() = runBlocking {
        val links = GoogleMapsLinks()
        // No request is made for these: refused before the first hop.
        assertNull(links.finalUrl("https://evil.example/maps"))
        assertNull(links.finalUrl("http://maps.app.goo.gl/abc"))
        assertNull(links.finalUrl("https://maps.app.goo.gl.evil.example/abc"))
        assertNull(links.finalUrl("file:///etc/passwd"))
    }
}
