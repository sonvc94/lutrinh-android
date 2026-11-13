package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import java.net.URI
import java.net.URLDecoder

/**
 * "Chia sẻ địa điểm từ Google Maps" (R43): reads the text Google Maps hands to the share sheet.
 * Pure Kotlin, mirrored in Swift (docs/07-ios-port.md); the only network step (following the
 * short link) is the [LinkFollower] the caller supplies.
 *
 * Formats seen from the Android app (docs/04 R43 "Observed share texts"): the place name, the
 * address and a short `https://maps.app.goo.gl/…` link, one per line; EXTRA_SUBJECT may repeat
 * the name.
 */
object SharedPlaces {
    /** What a share text is. */
    sealed interface Parsed {
        /** No Google Maps link in it: not ours. */
        data object NotGoogleMaps : Parsed

        /**
         * A Google Maps link. [name] may be empty (a bare link: the final URL may still name the
         * place); [point] is set when the link itself holds the coordinates.
         */
        data class Place(val name: String, val address: String?, val link: String, val point: GeoPoint?) : Parsed
    }

    /** Hosts a share link (and each redirect hop) may have: Google Maps and its short links only. */
    fun isAllowedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "goo.gl" || h == "maps.app.goo.gl" || h == "google.com" || h.endsWith(".google.com")
    }

    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** True when [url] is a Google Maps place or short link (a host on the allow-list and a maps path). */
    fun isMapsUrl(url: String): Boolean {
        val uri = uriOf(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (!isAllowedHost(host)) return false
        val path = uri.rawPath.orEmpty()
        return when {
            host == "maps.app.goo.gl" -> path.length > 1
            host == "goo.gl" -> path.startsWith("/maps")
            host == "maps.google.com" -> true
            host == "consent.google.com" -> false
            else -> path.startsWith("/maps") || (path.isEmpty() || path == "/") && uri.rawQuery?.contains("q=") == true
        }
    }

    /** The text to parse: EXTRA_TEXT, with EXTRA_SUBJECT in front when the text has no line besides links. */
    fun combine(text: String, subject: String?): String {
        val s = subject?.trim().orEmpty()
        val hasName = text.lines().any { it.isNotBlank() && !URL.containsMatchIn(it) }
        return if (s.isEmpty() || hasName || URL.containsMatchIn(s)) text else s + "\n" + text
    }

    /** Name = first line that is not a link, address = the lines after it, link = the first Google Maps link. */
    fun parse(text: String): Parsed {
        val link = URL.findAll(text).map { it.value.trimEnd('.', ',', ')', ';') }.firstOrNull { isMapsUrl(it) }
            ?: return Parsed.NotGoogleMaps
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !URL.containsMatchIn(it) }
        val name = lines.firstOrNull().orEmpty().take(MAX_NAME)
        val address = lines.drop(1).joinToString(", ").takeIf { it.isNotEmpty() }?.take(MAX_ADDRESS)
        return Parsed.Place(name, address, link, coordinatesIn(link))
    }

    /**
     * The place's position in a Google Maps URL, best source first: `!3d<lat>!4d<lng>` (the pin
     * itself), `@lat,lng` (the map centre), `q` / `query` / `ll` / `destination` / `center` =
     * `lat,lng`, a `/place/lat,lng` path. A consent page (`consent.google.com`) carries the real
     * URL in `continue`. Null when none is there (a place found by name only).
     */
    fun coordinatesIn(url: String): GeoPoint? {
        val uri = uriOf(url)
        if (uri != null && uri.host.equals("consent.google.com", ignoreCase = true)) {
            queryParam(uri, "continue")?.let { return coordinatesIn(it) }
        }
        val decoded = decode(url)
        PIN.find(decoded)?.let { m -> point(m.groupValues[1], m.groupValues[2])?.let { return it } }
        AT.find(decoded)?.let { m -> point(m.groupValues[1], m.groupValues[2])?.let { return it } }
        if (uri != null) {
            for (key in COORD_PARAMS) {
                val v = queryParam(uri, key) ?: continue
                PAIR.matchEntire(v.trim())?.let { m -> point(m.groupValues[1], m.groupValues[2])?.let { return it } }
            }
            val last = decode(uri.rawPath.orEmpty()).substringAfterLast('/')
            PAIR.matchEntire(last)?.let { m -> point(m.groupValues[1], m.groupValues[2])?.let { return it } }
        }
        return null
    }

    /** The place name inside a resolved `…/maps/place/<name>/@…` URL, or null. */
    fun nameIn(url: String): String? {
        val uri = uriOf(url) ?: return null
        val path = uri.rawPath.orEmpty()
        val i = path.indexOf("/place/")
        if (i < 0) return null
        val seg = path.substring(i + "/place/".length).substringBefore('/')
        val name = decode(seg.replace('+', ' ')).trim()
        return name.takeIf { it.isNotEmpty() && PAIR.matchEntire(it) == null }?.take(MAX_NAME)
    }

    /** What the share resolves to once the link has been followed. */
    sealed interface Resolved {
        val name: String
        val address: String?

        /** R43: where the offline gazetteer puts the place by its name ([PlaceGuess]); null = no guess. Only a position-less result has one. */
        val guess: GeoPoint? get() = null

        /** Position known: [point]. */
        data class Found(override val name: String, override val address: String?, val point: GeoPoint) : Resolved

        /** The link gave no position (unknown format, a place by name only, a redirect off Google). */
        data class NoCoordinates(
            override val name: String,
            override val address: String?,
            /** The name in the final URL's `/place/<name>/` path ("Hoàn Kiếm Lake, Hoan Kiem, Ha Noi, Vietnam"), for the guess. */
            val pathName: String? = null,
            override val guess: GeoPoint? = null,
        ) : Resolved

        /** The link could not be followed (offline, timeout, server error). */
        data class Unreachable(override val name: String, override val address: String?, override val guess: GeoPoint? = null) : Resolved
    }

    /**
     * Position of a parsed share: from the text's own link when it holds one, otherwise by
     * following the short link with [follower]. Never throws: every failure is a [Resolved] value.
     */
    suspend fun resolve(place: Parsed.Place, follower: LinkFollower): Resolved {
        place.point?.let { return Resolved.Found(place.name, place.address, it) }
        val finalUrl = try {
            follower.finalUrl(place.link)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Resolved.Unreachable(place.name, place.address)
        } ?: return Resolved.NoCoordinates(place.name, place.address)
        val name = place.name.ifEmpty { nameIn(finalUrl).orEmpty() }
        val point = coordinatesIn(finalUrl)
        return if (point != null) Resolved.Found(name, place.address, point)
        else Resolved.NoCoordinates(name, place.address, pathName = nameIn(finalUrl))
    }

    const val MAX_NAME = 120
    const val MAX_ADDRESS = 300

    private val PIN = Regex("""!3d(-?\d{1,2}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")
    private val AT = Regex("""@(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")
    private val PAIR = Regex("""(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")
    private val COORD_PARAMS = listOf("q", "query", "ll", "destination", "center", "daddr")

    private fun point(lat: String, lng: String): GeoPoint? {
        val p = GeoPoint(lat.toDoubleOrNull() ?: return null, lng.toDoubleOrNull() ?: return null)
        // 0,0 is the "no position" some links carry (geo:0,0?q=name).
        return p.takeIf { it.isValid && !(it.latitude == 0.0 && it.longitude == 0.0) }
    }

    private fun uriOf(url: String): URI? = try { URI(url.trim()) } catch (e: Exception) { null }

    private fun decode(s: String): String = try { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { s }

    private fun queryParam(uri: URI, key: String): String? =
        uri.rawQuery?.split('&')?.firstNotNullOfOrNull { kv ->
            val k = kv.substringBefore('=')
            if (k == key) try { URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") } catch (e: Exception) { null } else null
        }
}

/**
 * Follows a link's redirects and returns the URL it ends at, or null when it ends somewhere that
 * is not Google Maps. Throws on network failure. The one implementation is
 * `sharein/GoogleMapsLinks.kt`; tests use a fake.
 */
fun interface LinkFollower {
    suspend fun finalUrl(url: String): String?
}
