package vn.lutrinh.app.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.lutrinh.app.BuildConfig
import vn.lutrinh.app.domain.routing.RoutingProviders
import vn.lutrinh.app.routing.osrm.OsrmRouter
import vn.lutrinh.app.routing.valhalla.ValhallaRouter
import java.util.concurrent.TimeUnit

/**
 * The routers of the app (R36, docs/05-map.md "Routing"): the registry and the ONLY place their
 * server addresses live. FOSSGIS asks apps not to hard-code its URLs in many places; with no server
 * of ours, a move to another server (or a keyed provider) is a change here plus, for a new API,
 * one adapter package. Nothing outside `routing/` names a router (RoutingBoundaryTest).
 */
object Routers {
    /** Valhalla on FOSSGIS (OSM data, keyless, max 1 request/s): every routed profile. */
    const val VALHALLA_URL = "https://valhalla1.openstreetmap.de"

    /** OSRM car on FOSSGIS: fallback for car legs only (OSRM has no motorbike profile). */
    const val OSRM_CAR_URL = "https://routing.openstreetmap.de/routed-car"

    /** Identifies the app to the routing servers (their usage policy requires a real User-Agent). */
    val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    private val http: RouterHttp by lazy { RouterHttp(USER_AGENT) }

    /** Valhalla first; OSRM only when Valhalla is unavailable for a car leg. */
    val registry: RoutingProviders by lazy {
        RoutingProviders(ValhallaRouter(VALHALLA_URL, http), listOf(OsrmRouter(OSRM_CAR_URL, http)))
    }
}

/** One HTTP answer: status code and body text. */
class HttpAnswer(val code: Int, val body: String)

/** The HTTP client the adapters share: our User-Agent, short timeouts, calls off the main thread. */
class RouterHttp(private val userAgent: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String): HttpAnswer = call(Request.Builder().url(url).get())

    suspend fun postJson(url: String, json: String): HttpAnswer =
        call(Request.Builder().url(url).post(json.toRequestBody(JSON)))

    private suspend fun call(builder: Request.Builder): HttpAnswer = withContext(Dispatchers.IO) {
        client.newCall(builder.header("User-Agent", userAgent).build()).execute().use { r ->
            HttpAnswer(r.code, r.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
