package vn.lutrinh.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.TripImportRepository
import vn.lutrinh.app.domain.usecase.GpxDocument
import vn.lutrinh.app.domain.usecase.GpxError
import vn.lutrinh.app.domain.usecase.GpxException
import vn.lutrinh.app.domain.usecase.GpxFormat
import vn.lutrinh.app.domain.usecase.GpxImport
import vn.lutrinh.app.domain.usecase.GpxParser
import vn.lutrinh.app.domain.usecase.GpxWriter
import vn.lutrinh.app.domain.usecase.ImportGpx
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

/** R25: GPX writer, parser and import plan (docs/04-business-rules.md R25). */
class GpxTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = Instant.parse("2026-11-09T03:00:00Z")

    private fun ids(): IdGenerator { var n = 0; return IdGenerator { "id${++n}" } }

    private fun parse(s: String): GpxDocument = GpxParser.parse(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)))

    private fun fixture(): String = javaClass.getResource("/gpx/sample.gpx")!!.readText()

    private val trip = Trip("t1", "Vòng cung Hà Giang & <Đồng Văn>", "Đi \"xe máy\" 4 ngày", LocalDate.parse("2025-10-20"), LocalDate.parse("2025-10-23"), 0, 0)

    private fun step(id: String, title: String, at: String, draft: Boolean = false, note: String = "") =
        Step(id, "t1", title, note, GeoPoint(22.8233, 104.9836), LocalDateTime.parse(at), 0, 0, isDraft = draft)

    private val track = Track("k1", "t1", TrackStatus.ENDED, 1_760_922_000_000, 1_760_925_600_000, 3_600_000, null, 1, 1500.0, 4, false, 0, 0)
    private val points = listOf(
        TrackPoint("k1", 0, 1_760_922_000_000, GeoPoint(22.8233, 104.9836), altitudeM = 110.0),
        TrackPoint("k1", 0, 1_760_922_060_000, GeoPoint(22.8243, 104.9846)),
        TrackPoint("k1", 1, 1_760_925_000_000, GeoPoint(22.8300, 104.9900)),
        TrackPoint("k1", 1, 1_760_925_600_123, GeoPoint(22.8400, 104.9950)),
    )
    private val steps = listOf(
        step("s1", "TP Hà Giang", "2025-10-20T08:00", note = "Ăn sáng & <cà phê>"),
        step("s2", "Nháp", "2025-10-20T09:00", draft = true),
        step("s3", "Đèo Mã Pí Lèng", "2025-10-21T15:30"),
    )

    private fun written() = GpxWriter.write(trip, steps, listOf(track), points, zone, now)

    @Test fun writerOutputIsWellFormedGpx11() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(written().toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement
        assertEquals("gpx", root.localName)
        assertEquals(GpxFormat.NAMESPACE, root.namespaceURI)
        assertEquals("1.1", root.getAttribute("version"))
        assertEquals(4, doc.getElementsByTagNameNS(GpxFormat.NAMESPACE, "trkpt").length)
        assertEquals(2, doc.getElementsByTagNameNS(GpxFormat.NAMESPACE, "trkseg").length)
        // Drafts are left out (R20).
        assertEquals(2, doc.getElementsByTagNameNS(GpxFormat.NAMESPACE, "wpt").length)
    }

    @Test fun writerEscapesVietnameseAndXmlSpecials() {
        val gpx = written()
        assertTrue(gpx.contains("<name>Vòng cung Hà Giang &amp; &lt;Đồng Văn&gt;</name>"))
        assertTrue(gpx.contains("<desc>Đi &quot;xe máy&quot; 4 ngày</desc>"))
        assertTrue(gpx.contains("<desc>Ăn sáng &amp; &lt;cà phê&gt;</desc>"))
        assertFalse(gpx.contains("Nháp"))
        assertEquals("a&amp;b&lt;c&gt;d&quot;&apos;", GpxFormat.escape("a&b<c>d\"'"))
        assertEquals("ab", GpxFormat.escape("a\u0001b")) // XML 1.0 forbids control characters
    }

    @Test fun writerTimesAreUtcIso8601() {
        val gpx = written()
        // Step 08:00 in Vietnam (+07) = 01:00 UTC.
        assertTrue(gpx.contains("<time>2025-10-20T01:00:00Z</time>\n    <name>TP Hà Giang</name>"))
        assertTrue(gpx.contains("<time>2025-10-21T08:30:00Z</time>"))
        assertTrue(gpx.contains("<trkpt lat=\"22.8233\" lon=\"104.9836\"><ele>110.0</ele><time>2025-10-20T01:00:00Z</time></trkpt>"))
        assertTrue(gpx.contains("<time>2025-10-20T02:00:00.123Z</time>"))
        assertTrue(gpx.contains("<metadata>") && gpx.contains("<time>2026-11-09T03:00:00Z</time>"))
        assertEquals("105", GpxFormat.coord(105.0))
        assertEquals("-0.0000001", GpxFormat.coord(-0.0000001))
    }

    @Test fun parserReadsTracksRoutesAndWaypoints() {
        val doc = parse(fixture())
        assertEquals("Hà Giang & Cao Bằng", doc.name)
        assertEquals("Vòng cung \"đá\" <3 ngày>", doc.description)
        assertEquals(listOf("TP Hà Giang", null, "Đèo Mã Pí Lèng"), doc.waypoints.map { it.name })
        assertEquals("Chỉ có cmt", doc.waypoints[1].description)
        assertEquals(Instant.parse("2025-10-19T22:30:00Z"), doc.waypoints[1].time)
        assertNull(doc.waypoints[2].time)
        // File order: the route, then the track (its empty segment kept by the parser).
        assertEquals(listOf("Tuyến", "Ghi GPS"), doc.tracks.map { it.name })
        assertEquals(listOf(2), doc.tracks[0].segments.map { it.size })
        assertEquals(listOf(3, 0, 2), doc.tracks[1].segments.map { it.size })
        assertNull(doc.tracks[1].segments[0][2].time)
        assertEquals(110.5, doc.tracks[1].segments[0][0].elevation!!, 1e-9)
        assertEquals(7, doc.pointCount)
    }

    @Test fun parserAcceptsGpx10WithoutNamespace() {
        val doc = parse("""<gpx version="1.0"><name>Cũ</name><trk><trkseg><trkpt lat="21" lon="105"/></trkseg></trk></gpx>""")
        assertEquals("Cũ", doc.name)
        assertEquals(1, doc.pointCount)
    }

    private fun expectError(s: String): GpxError = try {
        parse(s); fail("no error"); error("unreachable")
    } catch (e: GpxException) {
        e.error
    }

    @Test fun malformedFilesAreErrors() {
        val truncated = fixture().let { it.substring(0, it.length / 2) }
        assertTrue(expectError(truncated) is GpxError.NotXml)
        assertTrue(expectError("not xml at all") is GpxError.NotXml)
        assertTrue(expectError("") is GpxError.NotXml)
        assertEquals(GpxError.NotGpx, expectError("<kml><Placemark/></kml>"))
        assertTrue(expectError("""<gpx><wpt lat="abc" lon="105"/></gpx>""") is GpxError.BadPoint)
        assertTrue(expectError("""<gpx><trk><trkseg><trkpt lat="95" lon="105"/></trkseg></trk></gpx>""") is GpxError.BadPoint)
        assertTrue(expectError("""<gpx><wpt lon="105"/></gpx>""") is GpxError.BadPoint)
    }

    private class RecordingStore : TripImportRepository {
        val stored = ArrayList<Trip>()
        var steps = 0
        var points = 0
        override suspend fun insertTrip(trip: Trip, steps: List<Step>, tracks: List<Pair<Track, List<TrackPoint>>>) {
            stored += trip; this.steps += steps.size; points += tracks.sumOf { it.second.size }
        }
    }

    private val clock = object : Clock {
        override fun nowMillis() = now.toEpochMilli()
        override fun today(): LocalDate = LocalDate.parse("2026-11-09")
    }

    @Test fun malformedOrEmptyFileCreatesNothing() = runTest {
        val store = RecordingStore()
        val import = ImportGpx(store, clock, ids())
        for (bad in listOf(fixture().take(400), "<gpx version=\"1.1\"></gpx>", "<gpx><trk><trkseg/></trk></gpx>")) {
            try {
                import(parse(bad), "x.gpx", zone) { null }
                fail("imported $bad")
            } catch (e: GpxException) {
                assertTrue(e.error is GpxError.NotXml || e.error == GpxError.Empty)
            }
        }
        assertTrue(store.stored.isEmpty())
        assertEquals(0, store.steps + store.points)
    }

    @Test fun importMakesANewTripFromTheFile() {
        val plan = GpxImport.plan(parse(fixture()), "ha-giang.gpx", zone, now.toEpochMilli(), clock.today(), ids()) { p ->
            if (p.latitude > 23.06 && p.latitude < 23.08) "Quản Bạ" else null
        }
        assertEquals("Hà Giang & Cao Bằng", plan.trip.title)
        assertEquals("Vòng cung \"đá\" <3 ngày>", plan.trip.description)
        assertEquals(LocalDate.parse("2025-10-20"), plan.trip.startDate)
        assertEquals(LocalDate.parse("2025-10-22"), plan.trip.endDate)
        // Name kept, else the R24 place name, else the default title.
        assertEquals(listOf("TP Hà Giang", "Quản Bạ", "Đèo Mã Pí Lèng"), plan.steps.map { it.title })
        assertEquals(LocalDateTime.parse("2025-10-20T08:00"), plan.steps[0].arrivedAt)
        assertEquals(LocalDateTime.parse("2025-10-20T05:30"), plan.steps[1].arrivedAt)
        assertEquals(LocalDateTime.parse("2025-10-20T00:00"), plan.steps[2].arrivedAt) // no time: start of the first day
        assertTrue(plan.steps.all { !it.isDraft && it.tripId == plan.trip.id })
        assertEquals(2, plan.tracks.size)
        val (trk, pts) = plan.tracks[1]
        assertEquals(5, pts.size)
        assertEquals(listOf(0, 0, 0, 1, 1), pts.map { it.segment }) // the empty segment is dropped
        assertEquals(pts[1].recordedAt, pts[2].recordedAt) // a point without a time takes the previous one's
        assertEquals(TrackStatus.ENDED, trk.status)
        assertEquals(1, trk.segment)
        assertEquals(5, trk.pointCount)
        assertEquals(10 * 60_000L + 20 * 60_000L - 500, trk.activeMs) // 01:00→01:10 + 09:00:00.500→09:20
        assertTrue(trk.distanceM > 0)
        assertEquals(7, plan.pointCount)
    }

    @Test fun titleFallsBackToTheFileName() {
        val doc = parse("""<gpx><wpt lat="21" lon="105"/></gpx>""")
        assertEquals("Chuyến Sapa", GpxImport.tripTitle(doc, "Chuyến Sapa.gpx"))
        assertEquals(GpxImport.DEFAULT_TITLE, GpxImport.tripTitle(doc, null))
        val plan = GpxImport.plan(doc, null, zone, now.toEpochMilli(), clock.today(), ids()) { null }
        assertEquals(LocalDate.parse("2026-11-09"), plan.trip.startDate) // no times at all: today
        assertEquals("Điểm dừng 00:00", plan.steps.single().title)
    }

    @Test fun exportThenImportKeepsPointsAndStepNames() = runTest {
        val gpx = written()
        val store = RecordingStore()
        val imported = ImportGpx(store, clock, ids())(parse(gpx), "x.gpx", zone) { "auto" }
        assertEquals(points.size, imported.pointCount)
        assertEquals(listOf("TP Hà Giang", "Đèo Mã Pí Lèng"), imported.steps.map { it.title })
        assertEquals(listOf("Ăn sáng & <cà phê>", ""), imported.steps.map { it.note })
        assertEquals(trip.title, imported.trip.title)
        assertEquals(trip.description, imported.trip.description)
        assertEquals(steps[0].arrivedAt, imported.steps[0].arrivedAt)
        val (t, pts) = imported.tracks.single()
        assertEquals(points.map { it.recordedAt }, pts.map { it.recordedAt })
        assertEquals(points.map { it.segment }, pts.map { it.segment })
        assertEquals(points.map { it.location }, pts.map { it.location })
        assertEquals(1, t.segment)
        assertEquals(1, store.stored.size)
        assertEquals(points.size, store.points)
    }
}
