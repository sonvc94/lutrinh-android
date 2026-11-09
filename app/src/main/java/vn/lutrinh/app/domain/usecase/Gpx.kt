package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.StepRepository
import vn.lutrinh.app.domain.repository.TrackRepository
import vn.lutrinh.app.domain.repository.TripImportRepository
import vn.lutrinh.app.domain.repository.TripRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * R25: GPX 1.1 export and import of one trip (docs/04-business-rules.md R25, format in
 * docs/02-data-model.md "GPX"). Pure Kotlin, mirrored 1:1 in Swift (docs/07-ios-port.md).
 */

/** A point of a GPX track or route; [time] is null when the file has none. */
data class GpxPoint(val location: GeoPoint, val time: Instant? = null, val elevation: Double? = null)

/** A `<wpt>`; [name]/[description] are trimmed, null when absent or blank. */
data class GpxWaypoint(
    val location: GeoPoint,
    val time: Instant? = null,
    val name: String? = null,
    val description: String? = null,
)

/** A `<trk>` (one list per `<trkseg>`) or a `<rte>` (one segment). */
data class GpxTrack(val name: String?, val segments: List<List<GpxPoint>>)

/** What a GPX file holds, in file order. */
data class GpxDocument(
    val name: String? = null,
    val description: String? = null,
    val tracks: List<GpxTrack> = emptyList(),
    val waypoints: List<GpxWaypoint> = emptyList(),
) {
    val pointCount: Int get() = tracks.sumOf { t -> t.segments.sumOf { it.size } }
    val isEmpty: Boolean get() = pointCount == 0 && waypoints.isEmpty()
}

/** Why a file cannot be imported (R25). Nothing is created for any of these. */
sealed interface GpxError {
    /** Not well-formed XML (truncated, binary, …); [line] = where the parser stopped, if known. */
    data class NotXml(val line: Int?) : GpxError

    /** Well-formed XML whose root is not `<gpx>`. */
    data object NotGpx : GpxError

    /** A point without a valid `lat`/`lon` (both are required by GPX). */
    data class BadPoint(val line: Int?) : GpxError

    /** A GPX file without any track point, route point or waypoint. */
    data object Empty : GpxError

    /** The file could not be read at all. */
    data object Unreadable : GpxError
}

class GpxException(val error: GpxError, cause: Throwable? = null) : Exception(error.toString(), cause)

object GpxFormat {
    const val NAMESPACE = "http://www.topografix.com/GPX/1/1"
    const val CREATOR = "Lữ Trình"
    const val MIME = "application/gpx+xml"
    const val EXTENSION = "gpx"

    /** XML text/attribute escaping; control characters XML 1.0 forbids are dropped. */
    fun escape(s: String): String {
        val b = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                '&' -> b.append("&amp;")
                '<' -> b.append("&lt;")
                '>' -> b.append("&gt;")
                '"' -> b.append("&quot;")
                '\'' -> b.append("&apos;")
                '\t', '\n', '\r' -> b.append(ch)
                else -> if (ch >= ' ' && ch != '￾' && ch != '￿') b.append(ch)
            }
        }
        return b.toString()
    }

    /** UTC ISO-8601 (`2025-10-20T01:30:00Z`); fractions only when not whole seconds. */
    fun time(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    /** Decimal degrees, at most 7 decimals (~1 cm), never in exponent notation. */
    fun coord(v: Double): String = String.format(Locale.ROOT, "%.7f", v).trimEnd('0').trimEnd('.')

    fun elevation(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

    /** Parses an xsd:dateTime; without an offset it is read as UTC (GPX 1.1 says times are UTC). Null when unparseable. */
    fun parseTime(s: String): Instant? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return runCatching { java.time.OffsetDateTime.parse(t).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
    }

    /** File name for an exported trip: its title with unsafe characters replaced, + ".gpx". */
    fun fileName(trip: Trip): String {
        val base = trip.title.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(60).ifBlank { "chuyen-di" }
        return "$base.$EXTENSION"
    }
}

/** R25 export: one trip → one GPX 1.1 document. */
object GpxWriter {
    /**
     * [steps] are the trip's steps (drafts are left out here, R20); [points] are the points of
     * [tracks] (any order of tracks; segment then time order inside a track). Step times are
     * local wall-clock times read in [zone] and written in UTC.
     */
    fun write(trip: Trip, steps: List<Step>, tracks: List<Track>, points: List<TrackPoint>, zone: ZoneId, now: Instant): String {
        val b = StringBuilder(256 + points.size * 110)
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        b.append("<gpx version=\"1.1\" creator=\"").append(GpxFormat.escape(GpxFormat.CREATOR)).append("\" ")
        b.append("xmlns=\"").append(GpxFormat.NAMESPACE).append("\" ")
        b.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        b.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        b.append("  <metadata>\n")
        b.append("    <name>").append(GpxFormat.escape(trip.title)).append("</name>\n")
        if (trip.description.isNotBlank()) b.append("    <desc>").append(GpxFormat.escape(trip.description)).append("</desc>\n")
        b.append("    <time>").append(GpxFormat.time(now.truncatedTo(ChronoUnit.SECONDS))).append("</time>\n")
        b.append("  </metadata>\n")
        // GPX 1.1 order: wpt*, rte*, trk*; inside wpt: ele, time, …, name, cmt, desc.
        for (s in steps.filterNot { it.isDraft }.inTimelineOrder()) {
            b.append("  <wpt lat=\"").append(GpxFormat.coord(s.location.latitude))
                .append("\" lon=\"").append(GpxFormat.coord(s.location.longitude)).append("\">\n")
            b.append("    <time>").append(GpxFormat.time(s.arrivedAt.atZone(zone).toInstant())).append("</time>\n")
            b.append("    <name>").append(GpxFormat.escape(s.title)).append("</name>\n")
            if (s.note.isNotBlank()) b.append("    <desc>").append(GpxFormat.escape(s.note)).append("</desc>\n")
            b.append("  </wpt>\n")
        }
        val byTrack = points.groupBy { it.trackId }
        for (t in tracks.sortedWith(compareBy<Track> { it.startedAt }.thenBy { it.id })) {
            val pts = byTrack[t.id].orEmpty()
            if (pts.isEmpty()) continue
            b.append("  <trk>\n")
            b.append("    <name>").append(GpxFormat.escape(trip.title)).append("</name>\n")
            for ((_, seg) in pts.groupBy { it.segment }.toSortedMap()) {
                b.append("    <trkseg>\n")
                for (p in seg.sortedBy { it.recordedAt }) {
                    b.append("      <trkpt lat=\"").append(GpxFormat.coord(p.location.latitude))
                        .append("\" lon=\"").append(GpxFormat.coord(p.location.longitude)).append("\">")
                    p.altitudeM?.let { b.append("<ele>").append(GpxFormat.elevation(it)).append("</ele>") }
                    b.append("<time>").append(GpxFormat.time(Instant.ofEpochMilli(p.recordedAt))).append("</time></trkpt>\n")
                }
                b.append("    </trkseg>\n")
            }
            b.append("  </trk>\n")
        }
        b.append("</gpx>\n")
        return b.toString()
    }
}

/** A trip built from a GPX file, ready to be stored in one transaction (R25). */
data class ImportedTrip(val trip: Trip, val steps: List<Step>, val tracks: List<Pair<Track, List<TrackPoint>>>) {
    val pointCount: Int get() = tracks.sumOf { it.second.size }
}

/** R25 import: a parsed GPX file → a NEW trip (never merged into an existing one). */
object GpxImport {
    /**
     * [fileName] is the picked file's display name (title fallback); [placeName] gives the R24
     * auto name of a point (null = none, then the default "Điểm dừng HH:mm"). Times are shown
     * as local wall-clock times in [zone]. Throws [GpxException] ([GpxError.Empty]) when the
     * document has nothing to import.
     */
    fun plan(
        doc: GpxDocument,
        fileName: String?,
        zone: ZoneId,
        nowMillis: Long,
        today: LocalDate,
        ids: IdGenerator,
        placeName: (GeoPoint) -> String?,
    ): ImportedTrip {
        if (doc.isEmpty) throw GpxException(GpxError.Empty)
        val times = doc.tracks.flatMap { t -> t.segments.flatMap { s -> s.mapNotNull { it.time } } } + doc.waypoints.mapNotNull { it.time }
        val first = times.minOrNull()
        val last = times.maxOrNull()
        val start = first?.atZone(zone)?.toLocalDate() ?: today
        val end = last?.atZone(zone)?.toLocalDate() ?: start
        val tripId = ids.newId()
        val trip = Trip(
            id = tripId,
            title = tripTitle(doc, fileName),
            description = (doc.description ?: "").take(Limits.TRIP_DESCRIPTION_MAX),
            startDate = start,
            endDate = end,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
        // A waypoint without a time sits at the start of the trip's first day; createdAt keeps file order.
        val steps = doc.waypoints.mapIndexed { i, w ->
            val at = (w.time?.atZone(zone)?.toLocalDateTime() ?: start.atStartOfDay()).truncatedTo(ChronoUnit.MINUTES)
            val title = w.name?.take(Limits.STEP_TITLE_MAX) ?: placeName(w.location) ?: DisplayFormat.defaultStepTitle(at)
            Step(
                id = ids.newId(), tripId = tripId, title = title,
                note = (w.description ?: "").take(Limits.STEP_NOTE_MAX),
                location = w.location, arrivedAt = at, createdAt = nowMillis + i, updatedAt = nowMillis + i,
            )
        }
        val fallbackStart = first?.toEpochMilli() ?: start.atStartOfDay(zone).toInstant().toEpochMilli()
        val tracks = doc.tracks.mapNotNull { t -> track(t, tripId, ids.newId(), fallbackStart, nowMillis) }
        return ImportedTrip(trip, steps, tracks)
    }

    /** Metadata name, else the first track's name, else the file name without extension. */
    fun tripTitle(doc: GpxDocument, fileName: String?): String {
        val fromFile = fileName?.substringAfterLast('/')?.let { n ->
            if (n.contains('.')) n.substringBeforeLast('.') else n
        }?.trim()
        val title = listOf(doc.name, doc.tracks.firstNotNullOfOrNull { it.name }, fromFile)
            .firstOrNull { !it.isNullOrBlank() }?.trim() ?: DEFAULT_TITLE
        return title.take(Limits.TRIP_TITLE_MAX)
    }

    /**
     * One GPX track/route → one ended track. Empty segments are dropped; a point without a time
     * takes the previous point's time (the first ones: the first time in the file), so stored
     * order stays file order.
     */
    private fun track(t: GpxTrack, tripId: String, trackId: String, fallbackStart: Long, now: Long): Pair<Track, List<TrackPoint>>? {
        val segments = t.segments.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val firstKnown = segments.firstNotNullOfOrNull { s -> s.firstNotNullOfOrNull { it.time } }?.toEpochMilli() ?: fallbackStart
        var last = firstKnown
        val points = ArrayList<TrackPoint>()
        var active = 0L
        segments.forEachIndexed { segIndex, seg ->
            val segStart = points.size
            for (p in seg) {
                val at = p.time?.toEpochMilli() ?: last
                last = at
                points += TrackPoint(trackId, segIndex, at, p.location, altitudeM = p.elevation)
            }
            active += (points.last().recordedAt - points[segStart].recordedAt).coerceAtLeast(0)
        }
        val track = Track(
            id = trackId, tripId = tripId, status = TrackStatus.ENDED,
            startedAt = points.minOf { it.recordedAt }, endedAt = points.maxOf { it.recordedAt },
            activeMs = active, resumedAt = null, segment = segments.size - 1,
            distanceM = TrackMath.lengthM(points), pointCount = points.size, interrupted = false,
            createdAt = now, updatedAt = now,
        )
        return track to points
    }

    const val DEFAULT_TITLE = "Chuyến đi từ GPX"
}

/** R25: the GPX text of a trip (null when the trip does not exist). */
class ExportGpx(
    private val trips: TripRepository,
    private val steps: StepRepository,
    private val tracks: TrackRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: String, zone: ZoneId): Pair<Trip, String>? {
        val trip = trips.getTrip(tripId) ?: return null
        val s = steps.getSteps(tripId)
        val t = tracks.observeTracks(tripId).first()
        val p = tracks.observePoints(tripId).first()
        return trip to GpxWriter.write(trip, s, t, p, zone, Instant.ofEpochMilli(clock.nowMillis()))
    }
}

/** R25: stores a parsed GPX file as a NEW trip in one transaction. */
class ImportGpx(
    private val store: TripImportRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(doc: GpxDocument, fileName: String?, zone: ZoneId, placeName: (GeoPoint) -> String?): ImportedTrip {
        val plan = GpxImport.plan(doc, fileName, zone, clock.nowMillis(), clock.today(), ids, placeName)
        store.insertTrip(plan.trip, plan.steps, plan.tracks)
        return plan
    }
}
