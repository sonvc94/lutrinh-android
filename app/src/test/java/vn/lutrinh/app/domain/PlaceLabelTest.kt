package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.ExtendCase
import vn.lutrinh.app.domain.usecase.PlaceLabel
import vn.lutrinh.app.domain.usecase.TripDates
import vn.lutrinh.app.domain.usecase.TripExtension
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** F15 "gần <điểm dừng>, N km" and F12 extend-dialog cases (docs/03 "Display conventions", R17). */
class PlaceLabelTest {
    private fun step(id: String, title: String, lat: Double, lng: Double) =
        Step(id, "t", title, "", GeoPoint(lat, lng), LocalDateTime.of(2025, 10, 20, 9, 0), 0, 0)

    private val hagiang = step("a", "Hà Giang", 22.8233, 104.9836)
    private val dongvan = step("b", "Đồng Văn", 23.2786, 105.3621)

    @Test fun `nearest step and its distance`() {
        val p = GeoPoint(23.2397, 105.4096) // Mã Pí Lèng, ~6 km from Đồng Văn
        val n = PlaceLabel.nearest(p, listOf(hagiang, dongvan))!!
        assertEquals("Đồng Văn", n.title)
        assertEquals(6.0, n.km, 1.0)
        assertEquals("gần Đồng Văn, ${DisplayFormat.km(n.km)}", PlaceLabel.label(p, listOf(hagiang, dongvan)))
    }

    @Test fun `distance is dropped when the point is at the step`() {
        assertEquals("gần Hà Giang", PlaceLabel.label(GeoPoint(22.8234, 104.9836), listOf(hagiang, dongvan)))
    }

    @Test fun `the edited step is excluded and no other step gives no label`() {
        assertNull(PlaceLabel.label(hagiang.location, listOf(hagiang), excludeId = "a"))
        assertNull(PlaceLabel.label(hagiang.location, emptyList()))
        assertEquals("Đồng Văn", PlaceLabel.nearest(hagiang.location, listOf(hagiang, dongvan), excludeId = "a")!!.title)
    }

    private val d = { m: Int, day: Int -> LocalDate.of(2026, m, day) }

    @Test fun `extend dialog case is before-start for a closed trip, after-end, or open-ended`() {
        val closed = TripDates(d(10, 20), d(10, 23))
        assertEquals(ExtendCase.BEFORE_START, TripExtension.caseOf(closed, TripExtension.widen(closed.start, closed.end, d(10, 18))!!))
        assertEquals(ExtendCase.AFTER_END, TripExtension.caseOf(closed, TripExtension.widen(closed.start, closed.end, d(10, 25))!!))
        val open = TripDates(d(11, 9), null)
        assertEquals(ExtendCase.OPEN_ENDED, TripExtension.caseOf(open, TripExtension.widen(open.start, open.end, d(11, 5))!!))
    }

    @Test fun `each extend case has its own wording and none reads den tu`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val body = ExtendCase.entries.map { c ->
            val name = "extend_trip_body_" + c.name.lowercase()
            val m = Regex("""<string name="$name">(.*?)</string>""").find(xml)
            assertEquals("missing string $name", true, m != null)
            m!!.groupValues[1]
        }
        assertEquals(3, body.toSet().size)
        body.forEach { assertEquals(false, it.contains("đến từ")) }
    }
}
