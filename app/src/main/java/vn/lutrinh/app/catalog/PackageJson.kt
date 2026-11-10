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

/**
 * Reads `route.json` (format `lutrinh-route`, docs/02 "Route package") into a [RoutePackage]:
 * the structure only; the integrity rules are `PackageRules` (domain). Shared by every adapter.
 */
object PackageJson {
    /** The package, or null when the text is not a `lutrinh-route` document of a known shape. */
    fun parse(text: String): RoutePackage? = runCatching { read(JSONObject(text)) }.getOrNull()

    /** Lower-case hex sha256 of [bytes]. */
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun read(o: JSONObject): RoutePackage? {
        if (o.getString("format") != RoutePackage.FORMAT) return null
        val formatVersion = o.getInt("formatVersion")
        val r = o.getJSONObject("route")
        val region = r.optJSONObject("region")
        val route = RouteInfo(
            id = r.getString("id"),
            version = r.getInt("version"),
            publishedAt = r.optStringOrNull("publishedAt"),
            title = r.getString("title"),
            description = r.optString("description", ""),
            days = r.getInt("days"),
            cover = r.optStringOrNull("cover"),
            bounds = region?.optJSONObject("bounds")?.let(::bounds),
            readyMade = region?.optStringOrNull("readyMade"),
            credits = r.optJSONArray("credits")?.strings().orEmpty(),
        )
        val stops = o.getJSONArray("stops").objects().map { s ->
            RouteStop(
                id = s.getString("id"),
                day = s.getInt("day"),
                time = s.optStringOrNull("time"),
                title = s.getString("title"),
                note = s.optString("note", ""),
                lat = s.getDouble("lat"),
                lng = s.getDouble("lng"),
                arrival = s.optStringOrNull("arrival"),
                media = s.optJSONArray("media")?.strings().orEmpty(),
            )
        }
        val legs = o.optJSONArray("legs")?.objects().orEmpty().map { l ->
            RouteLeg(
                to = l.getString("to"),
                profile = l.getString("profile"),
                usedProfile = l.optStringOrNull("usedProfile"),
                source = l.getString("source"),
                status = l.getString("status"),
                distanceM = l.getDouble("distanceM"),
                durationS = l.getDouble("durationS"),
                path = l.optString("path", ""),
            )
        }
        val media = o.optJSONArray("media")?.objects().orEmpty().map { m ->
            val type = m.getString("type")
            require(type == "photo" || type == "video")
            RouteMedia(
                id = m.getString("id"),
                sha256 = m.getString("sha256"),
                video = type == "video",
                bytes = m.getLong("bytes"),
                w = m.optIntOrNull("w"),
                h = m.optIntOrNull("h"),
                durationMs = m.optLongOrNull("durationMs"),
                credit = m.getString("credit"),
            )
        }
        val size = o.getJSONObject("size")
        return RoutePackage(formatVersion, route, stops, legs, media, size.getLong("media"), size.getInt("files"))
    }

    fun bounds(b: JSONObject) = RouteBounds(b.getDouble("south"), b.getDouble("west"), b.getDouble("north"), b.getDouble("east"))

    fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
    fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
    fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
    fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
