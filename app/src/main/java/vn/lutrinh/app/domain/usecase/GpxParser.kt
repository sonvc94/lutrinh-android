package vn.lutrinh.app.domain.usecase

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import vn.lutrinh.app.domain.model.GeoPoint
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/**
 * R25: reads GPX 1.0/1.1 with a streaming SAX parser (JDK + Android; iOS: `XMLParser`).
 * Keeps `<metadata><name|desc>` (GPX 1.0: top-level `<name>`/`<desc>`), `<wpt>` (time, name,
 * desc or else cmt), `<trk>/<trkseg>/<trkpt>` and `<rte>/<rtept>` (a route = one segment);
 * everything else (extensions, links, other namespaces) is ignored. Element names are matched
 * by local name, so files with or without the GPX namespace both read.
 */
object GpxParser {
    /** Throws [GpxException] for unreadable, non-XML, non-GPX files and points without lat/lon. */
    fun parse(input: InputStream): GpxDocument {
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
            // No DTDs / external entities (XXE). Android's parser has none of these features: ignore failures.
            for ((feature, on) in listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            )) runCatching { factory.setFeature(feature, on) }
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (e: Exception) {
            throw wrapped(e) ?: when (e) {
                is SAXParseException -> GpxException(GpxError.NotXml(e.lineNumber.takeIf { it > 0 }), e)
                is java.io.IOException -> GpxException(GpxError.Unreadable, e)
                else -> GpxException(GpxError.NotXml(handler.line()), e) // other SAX / parser configuration errors
            }
        }
        if (!handler.sawGpx) throw GpxException(GpxError.NotGpx)
        return handler.result()
    }

    /** A [GpxException] thrown by the handler, possibly wrapped by the parser. */
    private fun wrapped(e: Throwable): GpxException? {
        var c: Throwable? = e
        repeat(5) {
            when (c) {
                is GpxException -> return c as GpxException
                is org.xml.sax.SAXException -> c = (c as org.xml.sax.SAXException).exception ?: c!!.cause
                else -> c = c?.cause
            }
        }
        return null
    }

    private class Handler : DefaultHandler() {
        var sawGpx = false
        private var locator: Locator? = null
        private val path = ArrayList<String>()
        private val text = StringBuilder()

        private var name: String? = null
        private var desc: String? = null
        private val tracks = ArrayList<GpxTrack>()
        private val waypoints = ArrayList<GpxWaypoint>()

        // current element state
        private var trackName: String? = null
        private var segments: MutableList<List<GpxPoint>>? = null
        private var segment: MutableList<GpxPoint>? = null
        private var pointLoc: GeoPoint? = null
        private var pointTime: String? = null
        private var pointEle: String? = null
        private var pointName: String? = null
        private var pointDesc: String? = null
        private var pointCmt: String? = null

        fun line(): Int? = locator?.lineNumber?.takeIf { it > 0 }

        fun result() = GpxDocument(name, desc, tracks.toList(), waypoints.toList())

        override fun setDocumentLocator(locator: Locator) { this.locator = locator }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val el = local(localName, qName)
            if (path.isEmpty()) {
                if (el != "gpx") throw GpxException(GpxError.NotGpx)
                sawGpx = true
            }
            val parent = path.lastOrNull()
            path += el
            text.setLength(0)
            when {
                el == "trk" && parent == "gpx" -> { trackName = null; segments = ArrayList() }
                el == "rte" && parent == "gpx" -> { trackName = null; segments = ArrayList(); segment = ArrayList() }
                el == "trkseg" && parent == "trk" -> segment = ArrayList()
                (el == "trkpt" && parent == "trkseg") || (el == "rtept" && parent == "rte") || (el == "wpt" && parent == "gpx") -> {
                    pointLoc = location(attributes)
                    pointTime = null; pointEle = null; pointName = null; pointDesc = null; pointCmt = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val el = local(localName, qName)
            path.removeAt(path.size - 1)
            val parent = path.lastOrNull()
            val grand = path.getOrNull(path.size - 2)
            val value = text.toString().trim()
            text.setLength(0)
            when {
                // metadata (GPX 1.1) or top level (GPX 1.0)
                el == "name" && (parent == "metadata" && grand == "gpx" || parent == "gpx") -> name = value.ifEmpty { null }
                el == "desc" && (parent == "metadata" && grand == "gpx" || parent == "gpx") -> desc = value.ifEmpty { null }
                el == "name" && (parent == "trk" || parent == "rte") && grand == "gpx" -> trackName = value.ifEmpty { null }
                parent in POINT_ELEMENTS && el == "time" -> pointTime = value
                parent in POINT_ELEMENTS && el == "ele" -> pointEle = value
                parent in POINT_ELEMENTS && el == "name" -> pointName = value.ifEmpty { null }
                parent in POINT_ELEMENTS && el == "desc" -> pointDesc = value.ifEmpty { null }
                parent in POINT_ELEMENTS && el == "cmt" -> pointCmt = value.ifEmpty { null }
                (el == "trkpt" && parent == "trkseg") || (el == "rtept" && parent == "rte") -> {
                    segment?.add(GpxPoint(pointLoc!!, pointTime?.let(GpxFormat::parseTime), pointEle?.toDoubleOrNull()))
                }
                el == "wpt" && parent == "gpx" -> waypoints += GpxWaypoint(
                    pointLoc!!, pointTime?.let(GpxFormat::parseTime), pointName, pointDesc ?: pointCmt,
                )
                el == "trkseg" && parent == "trk" -> { segment?.let { segments?.add(it) }; segment = null }
                el == "trk" && parent == "gpx" -> { tracks += GpxTrack(trackName, segments.orEmpty().toList()); segments = null }
                el == "rte" && parent == "gpx" -> {
                    tracks += GpxTrack(trackName, listOf(segment.orEmpty().toList()))
                    segment = null; segments = null
                }
            }
        }

        private fun location(a: Attributes): GeoPoint {
            val lat = attr(a, "lat")?.trim()?.toDoubleOrNull()
            val lon = attr(a, "lon")?.trim()?.toDoubleOrNull()
            val p = if (lat != null && lon != null) GeoPoint(lat, lon) else null
            if (p == null || !p.isValid) throw GpxException(GpxError.BadPoint(line()))
            return p
        }

        private fun attr(a: Attributes, name: String): String? =
            a.getValue("", name) ?: a.getValue(name) ?: (0 until a.length).firstOrNull { local(a.getLocalName(it), a.getQName(it)) == name }?.let(a::getValue)

        private fun local(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':').orEmpty()
    }

    private val POINT_ELEMENTS = setOf("trkpt", "rtept", "wpt")
}
