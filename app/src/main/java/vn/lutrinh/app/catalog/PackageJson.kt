package vn.lutrinh.app.catalog

import org.json.JSONArray
import org.json.JSONObject
import vn.lutrinh.app.domain.catalog.RouteBounds
import vn.lutrinh.app.domain.catalog.RouteInfo
import vn.lutrinh.app.domain.catalog.RouteLeg
import vn.lutrinh.app.domain.catalog.RouteMedia
import vn.lutrinh.app.domain.catalog.RoutePackage
import vn.lutrinh.app.domain.catalog.RouteStop
import java.security.MessageDigest

/** What reading a `route.json` gave. */
sealed interface PackageRead {
    data class Ok(val pkg: RoutePackage) : PackageRead
    /** `formatVersion` newer than this app reads (checked before the schema). */
    data class Unsupported(val formatVersion: Int) : PackageRead
    /** Not a document of `route.schema.json` ([where] = JSON pointer of the first problem). */
    data class Invalid(val where: String) : PackageRead
}

/**
 * Reads `route.json` (format `lutrinh-route`, docs/02 "Route package") into a [RoutePackage],
 * enforcing the shared `spec/route.schema.json` (required keys, no extra keys, types, enums,
 * patterns, lengths); the cross-reference rules are `PackageRules` (domain). Shared by every adapter.
 */
object PackageJson {
    /** The package, or null when the text is not a valid `lutrinh-route` document this app reads. */
    fun parse(text: String): RoutePackage? = (read(text) as? PackageRead.Ok)?.pkg

    fun read(text: String): PackageRead {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return PackageRead.Invalid("")
        val fv = o.opt("formatVersion")
        if (fv is Number && fv.toDouble() == Math.floor(fv.toDouble()) && fv.toDouble() > RoutePackage.FORMAT_VERSION) {
            return PackageRead.Unsupported(fv.toInt())
        }
        return try {
            PackageRead.Ok(Reader().pkg(o))
        } catch (e: SchemaError) {
            PackageRead.Invalid(e.where)
        }
    }

    /** Lower-case hex sha256 of [bytes]. */
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    fun bounds(b: JSONObject) = RouteBounds(b.getDouble("south"), b.getDouble("west"), b.getDouble("north"), b.getDouble("east"))

    fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
    fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
    fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
    fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private class SchemaError(val where: String) : Exception(where)

    private val ID = Regex("[a-z][a-z0-9_]{0,39}")
    private val ROUTE_ID = Regex("rt_[a-z0-9]{8,32}")
    private val PUBLISHED = Regex("[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z")
    private val TRIMMED = Regex("(?U)(?:|\\S(?:[\\s\\S]*\\S)?)")
    private val TIME = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val REGION_KEY = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val SOURCE = Regex("[a-z][a-z0-9_-]{0,31}")
    private val PATH = Regex("[?-~]*")
    private val PROFILES = setOf("motorbike", "car", "bus", "walk")
    private val ARRIVALS = setOf("walk", "motorbike", "car", "bus", "train", "plane", "boat")
    private val STATUSES = setOf("ok", "no_route", "too_far")

    /** One pass over the document; throws [SchemaError] at the first value the schema refuses. */
    private class Reader {
        fun pkg(o: JSONObject): RoutePackage {
            keys(o, "", "format", "formatVersion", "route", "stops", "legs", "media", "size")
            if (o.get("format") != RoutePackage.FORMAT) fail("/format")
            val formatVersion = int(o, "formatVersion", "", 1, 1).toInt()
            val route = route(obj(o, "route", ""), "/route")
            val stops = array(o, "stops", "", 2, 200).mapIndexed { i, v -> stop(v as? JSONObject ?: fail("/stops/$i"), "/stops/$i") }
            val legs = array(o, "legs", "", 0, 199).mapIndexed { i, v -> leg(v as? JSONObject ?: fail("/legs/$i"), "/legs/$i") }
            val media = array(o, "media", "", 0, Int.MAX_VALUE).mapIndexed { i, v -> media(v as? JSONObject ?: fail("/media/$i"), "/media/$i") }
            val size = obj(o, "size", "")
            keys(size, "/size", "media", "files")
            return RoutePackage(
                formatVersion, route, stops, legs, media,
                int(size, "media", "/size", 0, Long.MAX_VALUE), int(size, "files", "/size", 0, Int.MAX_VALUE.toLong()).toInt(),
            )
        }

        fun route(r: JSONObject, at: String): RouteInfo {
            keys(r, at, "id", "version", "publishedAt", "title", "description", "days", "cover", "region", "credits")
            val region = obj(r, "region", at)
            keys(region, "$at/region", "bounds", "readyMade")
            val b = obj(region, "bounds", "$at/region")
            keys(b, "$at/region/bounds", "south", "west", "north", "east")
            val bat = "$at/region/bounds"
            return RouteInfo(
                id = string(r, "id", at, ROUTE_ID),
                version = int(r, "version", at, 1, Int.MAX_VALUE.toLong()).toInt(),
                publishedAt = string(r, "publishedAt", at, PUBLISHED),
                title = text(r, "title", at, 1, 60),
                description = text(r, "description", at, 0, 1000),
                days = int(r, "days", at, 1, 30).toInt(),
                cover = nullable(r, "cover") { string(r, "cover", at, ID) },
                bounds = RouteBounds(
                    number(b, "south", bat, -90.0, 90.0), number(b, "west", bat, -180.0, 180.0),
                    number(b, "north", bat, -90.0, 90.0), number(b, "east", bat, -180.0, 180.0),
                ),
                readyMade = nullable(region, "readyMade") { string(region, "readyMade", "$at/region", REGION_KEY) },
                credits = array(r, "credits", at, 0, Int.MAX_VALUE).mapIndexed { i, v -> trimmed(v, "$at/credits/$i", 1, Int.MAX_VALUE) },
            )
        }

        fun stop(s: JSONObject, at: String): RouteStop {
            keys(s, at, "id", "day", "time", "title", "note", "lat", "lng", "arrival", "media")
            val media = array(s, "media", at, 0, 30).mapIndexed { i, v -> (v as? String)?.takeIf(ID::matches) ?: fail("$at/media/$i") }
            if (media.toSet().size != media.size) fail("$at/media")
            return RouteStop(
                id = string(s, "id", at, ID),
                day = int(s, "day", at, 1, 30).toInt(),
                time = nullable(s, "time") { string(s, "time", at, TIME) },
                title = text(s, "title", at, 1, 80),
                note = text(s, "note", at, 0, 2000),
                lat = number(s, "lat", at, -90.0, 90.0),
                lng = number(s, "lng", at, -180.0, 180.0),
                arrival = nullable(s, "arrival") { oneOf(s, "arrival", at, ARRIVALS) },
                media = media,
            )
        }

        fun leg(l: JSONObject, at: String) = RouteLeg(
            to = keys(l, at, "to", "profile", "usedProfile", "source", "status", "distanceM", "durationS", "path")
                .let { string(l, "to", at, ID) },
            profile = oneOf(l, "profile", at, PROFILES),
            usedProfile = nullable(l, "usedProfile") { oneOf(l, "usedProfile", at, PROFILES) },
            source = string(l, "source", at, SOURCE),
            status = oneOf(l, "status", at, STATUSES),
            distanceM = number(l, "distanceM", at, 0.0, Double.MAX_VALUE),
            durationS = number(l, "durationS", at, 0.0, Double.MAX_VALUE),
            path = string(l, "path", at, PATH),
        )

        fun media(m: JSONObject, at: String): RouteMedia {
            keys(m, at, "id", "sha256", "type", "bytes", "w", "h", "durationMs", "credit")
            val video = oneOf(m, "type", at, setOf("photo", "video")) == "video"
            val duration = nullable(m, "durationMs") { int(m, "durationMs", at, 1, 60_000) }
            if (video != (duration != null)) fail("$at/durationMs")
            return RouteMedia(
                id = string(m, "id", at, ID),
                sha256 = string(m, "sha256", at, SHA256),
                video = video,
                bytes = int(m, "bytes", at, 1, Long.MAX_VALUE),
                w = int(m, "w", at, 1, 2560).toInt(),
                h = int(m, "h", at, 1, 2560).toInt(),
                durationMs = duration,
                credit = text(m, "credit", at, 1, Int.MAX_VALUE),
            )
        }

        /** Exactly these keys (all required, `additionalProperties: false`). */
        fun keys(o: JSONObject, at: String, vararg names: String) {
            names.forEach { if (!o.has(it)) fail("$at/$it") }
            o.keys().forEach { if (it !in names) fail("$at/$it") }
        }

        fun fail(at: String): Nothing = throw SchemaError(at)

        fun obj(o: JSONObject, name: String, at: String): JSONObject = o.opt(name) as? JSONObject ?: fail("$at/$name")

        fun array(o: JSONObject, name: String, at: String, min: Int, max: Int): List<Any?> {
            val a = o.opt(name) as? JSONArray ?: fail("$at/$name")
            if (a.length() !in min..max) fail("$at/$name")
            return (0 until a.length()).map { a.opt(it) }
        }

        fun <T> nullable(o: JSONObject, name: String, read: () -> T): T? = if (o.opt(name) == JSONObject.NULL) null else read()

        fun string(o: JSONObject, name: String, at: String, pattern: Regex): String =
            (o.opt(name) as? String)?.takeIf(pattern::matches) ?: fail("$at/$name")

        fun oneOf(o: JSONObject, name: String, at: String, allowed: Set<String>): String =
            (o.opt(name) as? String)?.takeIf { it in allowed } ?: fail("$at/$name")

        fun text(o: JSONObject, name: String, at: String, min: Int, max: Int): String = trimmed(o.opt(name), "$at/$name", min, max)

        /** A trimmed string of [min]..[max] characters (code points, as JSON Schema counts them). */
        fun trimmed(v: Any?, at: String, min: Int, max: Int): String {
            val s = v as? String ?: fail(at)
            if (!TRIMMED.matches(s) || s.codePointCount(0, s.length) !in min..max) fail(at)
            return s
        }

        fun number(o: JSONObject, name: String, at: String, min: Double, max: Double): Double {
            val d = (o.opt(name) as? Number)?.toDouble() ?: fail("$at/$name")
            if (d.isNaN() || d < min || d > max) fail("$at/$name")
            return d
        }

        fun int(o: JSONObject, name: String, at: String, min: Long, max: Long): Long {
            val n = o.opt(name) as? Number ?: fail("$at/$name")
            val d = n.toDouble()
            if (d != Math.floor(d) || d.isInfinite()) fail("$at/$name")
            val v = n.toLong()
            if (v < min || v > max) fail("$at/$name")
            return v
        }
    }
}
