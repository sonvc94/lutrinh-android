package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.StopNavigation
import java.util.Locale

/** R42: the links that hand a stop to a maps app. */
class StopNavigationTest {
    @Test fun geoUriHasSixDecimalsAndTheEncodedName() {
        assertEquals(
            "geo:22.900000,105.000000?q=22.900000,105.000000(%C4%90%E1%BB%93ng%20V%C4%83n)",
            StopNavigation.geoUri(GeoPoint(22.9, 105.0), "Đồng Văn"),
        )
        assertEquals(
            "geo:-8.500000,105.123457?q=-8.500000,105.123457(A%20%26%20B%20%3Fx%3D1%20%23)",
            StopNavigation.geoUri(GeoPoint(-8.5, 105.123456789), "A & B ?x=1 #"),
        )
    }

    @Test fun parenthesesInTheNameDoNotEndTheLabel() {
        assertEquals("geo:1.000000,2.000000?q=1.000000,2.000000(Ch%E1%BB%A3%20%20c%C5%A9)", StopNavigation.geoUri(GeoPoint(1.0, 2.0), "Chợ (cũ)"))
    }

    @Test fun blankNameLeavesAPinWithoutLabel() {
        assertEquals("geo:1.000000,2.000000?q=1.000000,2.000000", StopNavigation.geoUri(GeoPoint(1.0, 2.0), "  "))
    }

    @Test fun mapsUrlIsGoogleDirectionsWithTheDestination() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=20.254600,105.895400", StopNavigation.mapsUrl(GeoPoint(20.2546, 105.8954)))
    }

    @Test fun decimalPointIgnoresTheDeviceLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale("vi", "VN"))
            assertEquals("21.000000,105.500000", StopNavigation.coords(GeoPoint(21.0, 105.5)))
            Locale.setDefault(Locale.GERMANY)
            assertEquals("21.000000,105.500000", StopNavigation.coords(GeoPoint(21.0, 105.5)))
        } finally {
            Locale.setDefault(old)
        }
    }
}
