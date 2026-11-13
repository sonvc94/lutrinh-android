package vn.lutrinh.app.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.ui.steps.SharedGap
import vn.lutrinh.app.ui.steps.SharedPrefill

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
/** R43: what the stop form takes from a Google Maps share (the arguments of Routes.stepFromShare). */
class SharedPrefillTest {
    private fun handle(vararg kv: Pair<String, String>) = SavedStateHandle(kv.toMap())

    @Test fun positionKnownFillsNameAndLocation() {
        val p = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "Hồ Gươm", "sharedLat" to "21.03", "sharedLng" to "105.85"))!!
        assertEquals(SharedPrefill("Hồ Gươm", GeoPoint(21.03, 105.85), null), p)
    }

    @Test fun noPositionKeepsTheNameAndSaysWhy() {
        assertEquals(SharedGap.NO_COORDINATES, SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedGap" to "none"))!!.gap)
        val offline = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedGap" to "offline"))!!
        assertEquals(SharedGap.UNREACHABLE, offline.gap)
        assertNull(offline.location)
        assertEquals("A", offline.title)
    }

    @Test fun invalidPositionIsNoPosition() {
        val p = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedLat" to "95", "sharedLng" to "10"))!!
        assertNull(p.location)
        assertEquals(SharedGap.NO_COORDINATES, p.gap)
    }

    @Test fun notFromAShareIsNull() {
        assertNull(SharedPrefill.from(handle("sharedTitle" to "A")))
    }

    @Test fun longNameFitsTheTitleLimit() {
        val p = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "x".repeat(300)))!!
        assertEquals(Limits.STEP_TITLE_MAX, p.title.length)
    }

    @Test fun routesCarryTheSharedPlace() {
        val found = vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved.Found("Hồ Gươm & Tháp", null, GeoPoint(21.03, 105.85))
        assertEquals("trip/t1/stepEdit?shared=1&sharedTitle=H%E1%BB%93%20G%C6%B0%C6%A1m%20%26%20Th%C3%A1p&sharedLat=21.03&sharedLng=105.85", Routes.stepFromShare("t1", found))
        val offline = vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved.Unreachable("A", null)
        assertEquals("tripEdit?shared=1&sharedTitle=A&sharedGap=offline", Routes.tripEditFromShare(offline))
    }

    @Test fun guessFromTheNameRidesAlongOnlyWithoutAPosition() {
        val g = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedGap" to "none", "sharedGuessLat" to "21.02883", "sharedGuessLng" to "105.85254"))!!
        assertEquals(GeoPoint(21.02883, 105.85254), g.guess)
        assertNull(g.location)
        assertEquals(SharedGap.NO_COORDINATES, g.gap)
        // a dropped pin's exact position wins, and a bad guess is no guess
        val pin = SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedLat" to "21.0", "sharedLng" to "105.8", "sharedGuessLat" to "10", "sharedGuessLng" to "100"))!!
        assertNull(pin.guess)
        assertNull(SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedGuessLat" to "95", "sharedGuessLng" to "10"))!!.guess)
        assertNull(SharedPrefill.from(handle("shared" to "1", "sharedTitle" to "A", "sharedGap" to "none"))!!.guess)
    }

    @Test fun routesCarryTheGuess() {
        val r = vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved.NoCoordinates("A", null, guess = GeoPoint(21.02883, 105.85254))
        assertEquals("trip/t1/stepEdit?shared=1&sharedTitle=A&sharedGap=none&sharedGuessLat=21.02883&sharedGuessLng=105.85254", Routes.stepFromShare("t1", r))
        val off = vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved.Unreachable("A", null, GeoPoint(1.5, 2.5))
        assertEquals("tripEdit?shared=1&sharedTitle=A&sharedGap=offline&sharedGuessLat=1.5&sharedGuessLng=2.5", Routes.tripEditFromShare(off))
    }
}
