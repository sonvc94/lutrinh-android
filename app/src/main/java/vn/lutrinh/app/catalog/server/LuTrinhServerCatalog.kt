package vn.lutrinh.app.catalog.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import vn.lutrinh.app.catalog.PackageJson
import vn.lutrinh.app.catalog.PackageJson.objects
import vn.lutrinh.app.catalog.PackageJson.optIntOrNull
import vn.lutrinh.app.catalog.PackageJson.optStringOrNull
import vn.lutrinh.app.catalog.PackageJson.strings
import vn.lutrinh.app.domain.catalog.ByteSink
import vn.lutrinh.app.domain.catalog.CatalogError
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.DownloadTicket
import vn.lutrinh.app.domain.catalog.FetchedRoute
import vn.lutrinh.app.domain.catalog.LinkRef
import vn.lutrinh.app.domain.catalog.MediaRef
import vn.lutrinh.app.domain.catalog.RemoteStatus
import vn.lutrinh.app.domain.catalog.RouteCatalog
import vn.lutrinh.app.domain.catalog.RouteChange
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RoutePackage
import vn.lutrinh.app.domain.catalog.RouteStatus
import vn.lutrinh.app.domain.catalog.RouteSummary
import vn.lutrinh.app.domain.usecase.Polyline
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The Lữ Trình route server, public API v1 (docs/02 "Route server API"). Sends only the code (or
 * the codes + versions of downloaded routes), our User-Agent and the Accept type: no cookie, no
 * device id, nothing about the user's trips. The address comes from `Catalogs` (CatalogBoundaryTest).
 */
class LuTrinhServerCatalog(
    baseUrl: String,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient(),
) : RouteCatalog {
    override val id = "lutrinh-server"

    private val base = baseUrl.trimEnd('/') + "/api/v1"

    /** `GET /meta` goes along with the first request of the process (never on its own). */
    @Volatile private var metaChecked = false

    override suspend fun resolve(code: RouteCode): CatalogResult<RouteSummary> = io {
        checkMeta()?.let { return@io it }
        call(get("$base/codes/${code.value}")) { r ->
            CatalogResult.Ok(summary(code, JSONObject(r.body!!.string())))
        }
    }

    override suspend fun fetchRoute(ticket: DownloadTicket): CatalogResult<FetchedRoute> = io {
        call(get("$base/dl/${ticket.token}/route")) { r ->
            val bytes = r.body!!.bytes()
            val pkg = PackageJson.parse(bytes.toString(Charsets.UTF_8))
                ?: return@call CatalogResult.Refused(CatalogError.BAD_PACKAGE)
            CatalogResult.Ok(FetchedRoute(PackageJson.sha256(bytes), pkg))
        }
    }

    override suspend fun fetchMedia(ticket: DownloadTicket, ref: MediaRef, sink: ByteSink, from: Long): CatalogResult<Unit> = io {
        val request = get("$base/dl/${ticket.token}/media/${ref.sha256}").apply { if (from > 0) header("Range", "bytes=$from-") }
        call(request, okCodes = setOf(200, 206, 416)) { r ->
            if (r.code == 416) return@call CatalogResult.Ok(Unit) // nothing left past `from`: the hash decides
            val input = r.body!!.byteStream()
            // A server that ignores Range answers 200 with the whole file: skip what we have.
            var skip = if (r.code == 200) from else 0L
            val buf = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                if (skip >= n) { skip -= n; continue }
                val start = skip.toInt()
                skip = 0
                if (start == 0) sink.write(buf, n) else sink.write(buf.copyOfRange(start, n), n - start)
            }
            CatalogResult.Ok(Unit)
        }
    }

    override suspend fun checkUpdates(links: List<LinkRef>): CatalogResult<List<RouteStatus>> = io {
        require(links.size <= RouteCatalog.MAX_UPDATE_LINKS)
        val body = JSONArray(links.map { JSONObject().put("code", it.code.value).put("routeId", it.routeId).put("version", it.version) })
        val request = Request.Builder().url("$base/updates").post(body.toString().toRequestBody(JSON))
        call(request) { r ->
            val text = r.body!!.string().trim()
            val items = if (text.startsWith("[")) JSONArray(text) else JSONObject(text).getJSONArray("results")
            val byKey = items.objects().associateBy { it.optString("code") + "|" + it.optString("routeId") }
            CatalogResult.Ok(links.map { link -> status(link, byKey[link.code.value + "|" + link.routeId]) })
        }
    }

    private fun checkMeta(): CatalogResult<Nothing>? {
        if (metaChecked) return null
        return try {
            client.newCall(get("$base/meta").build()).execute().use { r ->
                if (!r.isSuccessful) return null // meta is advice: a missing one never blocks a lookup
                metaChecked = true
                val o = JSONObject(r.body!!.string())
                val versions = o.optJSONArray("formatVersions")
                if (versions != null && (0 until versions.length()).none { versions.optInt(it) == RoutePackage.FORMAT_VERSION }) {
                    return CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED)
                }
                if (o.optBoolean("maintenance", false)) return CatalogResult.Unavailable()
                null
            }
        } catch (e: IOException) {
            null
        } catch (e: org.json.JSONException) {
            null
        }
    }

    private fun get(url: String) = Request.Builder().url(url).get()

    /** Runs [request]; a status in [okCodes] goes to [onOk], an API error to its [CatalogResult]. */
    private inline fun <T> call(request: Request.Builder, okCodes: Set<Int> = setOf(200), onOk: (Response) -> CatalogResult<T>): CatalogResult<T> =
        try {
            client.newCall(request.header("User-Agent", userAgent).header("Accept", ACCEPT).build()).execute().use { r ->
                if (r.code in okCodes) {
                    try { onOk(r) } catch (e: org.json.JSONException) { CatalogResult.Refused(CatalogError.BAD_PACKAGE) }
                } else {
                    refusal(r)
                }
            }
        } catch (e: IOException) {
            CatalogResult.Unavailable()
        }

    private fun refusal(r: Response): CatalogResult<Nothing> {
        val retryHeader = r.header("Retry-After")?.trim()?.toIntOrNull()
        val err = runCatching { JSONObject(r.body!!.string()).getJSONObject("error") }.getOrNull()
        val retry = err?.optIntOrNull("retryAfterS") ?: retryHeader
        val code = CatalogError.fromCode(err?.optStringOrNull("code"))
        return when {
            code != null -> CatalogResult.Refused(code, retry)
            r.code == 400 -> CatalogResult.Refused(CatalogError.BAD_FORMAT)
            r.code == 401 -> CatalogResult.Refused(CatalogError.TOKEN_EXPIRED)
            r.code == 404 -> CatalogResult.Refused(CatalogError.CODE_NOT_FOUND)
            r.code == 406 -> CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED)
            r.code == 410 -> CatalogResult.Refused(CatalogError.ROUTE_UNPUBLISHED)
            r.code == 429 -> CatalogResult.Refused(CatalogError.RATE_LIMITED, retry)
            else -> CatalogResult.Unavailable(retry)
        }
    }

    private fun summary(code: RouteCode, o: JSONObject): RouteSummary {
        val region = o.optJSONObject("region")
        val version = o.getInt("version")
        val routeId = o.getString("routeId")
        return RouteSummary(
            code = code,
            routeId = routeId,
            version = version,
            formatVersion = o.optInt("formatVersion", RoutePackage.FORMAT_VERSION),
            title = o.getString("title"),
            description = o.optString("description", ""),
            days = o.getInt("days"),
            stops = o.getInt("stops"),
            distanceM = o.optDouble("distanceM", 0.0),
            photos = o.optInt("photos", 0),
            videos = o.optInt("videos", 0),
            bytes = o.optLong("bytes", 0),
            files = o.optInt("files", 0),
            bounds = region?.optJSONObject("bounds")?.let(PackageJson::bounds),
            readyMade = region?.optStringOrNull("readyMade"),
            overview = o.optStringOrNull("overview")?.let { runCatching { Polyline.decode6(it) }.getOrNull() }.orEmpty(),
            ticket = DownloadTicket(code, routeId, version, o.getString("downloadToken")),
        )
    }

    private fun status(link: LinkRef, o: JSONObject?): RouteStatus {
        val s = o?.optString("status") ?: return RouteStatus(link, RemoteStatus.ACTIVE)
        return when (s) {
            "newer" -> {
                val c = o.optJSONObject("changes") ?: JSONObject()
                RouteStatus(
                    link, RemoteStatus.ACTIVE,
                    RouteChange(
                        version = o.getInt("version"),
                        publishedAt = o.optStringOrNull("publishedAt"),
                        stopsAdded = c.optJSONArray("stopsAdded")?.strings().orEmpty(),
                        stopsRemoved = c.optJSONArray("stopsRemoved")?.strings().orEmpty(),
                        stopsEdited = c.optInt("stopsEdited", 0),
                        mediaAdded = c.optInt("mediaAdded", 0),
                        bytes = c.optLong("bytes", 0),
                        note = o.optStringOrNull("note"),
                    ),
                )
            }
            else -> RouteStatus(link, RemoteStatus.fromId(s))
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val ACCEPT = "application/vnd.lutrinh.route+json;v=1"
        private val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
