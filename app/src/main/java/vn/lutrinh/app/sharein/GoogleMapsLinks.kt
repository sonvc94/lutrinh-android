package vn.lutrinh.app.sharein

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.lutrinh.app.BuildConfig
import vn.lutrinh.app.domain.usecase.LinkFollower
import vn.lutrinh.app.domain.usecase.SharedPlaces
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The ONLY network call of "Chia sẻ từ Google Maps" (R43): follows the redirects of a shared
 * Google Maps link (`maps.app.goo.gl/…` → `google.com/maps/place/…`) to read the position out of
 * the final address; when that has none but a place id, one more GET to Google's own
 * `maps/preview/place` with only that id ([placeReply]). Nothing else is sent (no trip data, no
 * position of the user); the redirect pages are never read. Every hop must be on the Google host allow-list
 * ([SharedPlaces.isAllowedHost], https only), at most [MAX_HOPS] hops, short timeouts.
 */
class GoogleMapsLinks(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .build(),
) : LinkFollower {
    override suspend fun finalUrl(url: String): String? = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_HOPS) {
            val http = current.toHttpUrlOrNull()
            if (http == null || !http.isHttps || !SharedPlaces.isAllowedHost(http.host)) return@withContext null
            // A page that already names the position needs no more hops.
            if (SharedPlaces.coordinatesIn(current) != null && it > 0) return@withContext current
            val request = Request.Builder().url(http).get().header("User-Agent", USER_AGENT).build()
            val next = client.newCall(request).execute().use { r ->
                when {
                    r.isRedirect -> r.header("Location")?.let { loc -> http.resolve(loc)?.toString() }
                        ?: throw IOException("redirect without Location")
                    r.isSuccessful -> null
                    else -> throw IOException("HTTP ${r.code}")
                }
            } ?: return@withContext current
            current = next
        }
        // Too many hops: what we have is the answer, if it has a position.
        current
    }

    /**
     * `GET https://www.google.com/maps/preview/place?hl=vi&pb=!1m1!1s<fid>`: no cookies, no key; the
     * body is read up to [MAX_REPLY_BYTES]. One retry on a 5xx. Null for any other answer.
     */
    override suspend fun placeReply(fid: String): String? = withContext(Dispatchers.IO) {
        if (!FID_SHAPE.matches(fid)) return@withContext null
        val url = HttpUrl.Builder().scheme("https").host(PLACE_HOST).addPathSegments("maps/preview/place")
            .addQueryParameter("hl", "vi").addEncodedQueryParameter("pb", "!1m1!1s$fid").build()
        repeat(2) { attempt ->
            val request = Request.Builder().url(url).get().header("User-Agent", USER_AGENT).build()
            val retry = client.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> return@withContext r.body?.source()?.let { src ->
                        src.request(MAX_REPLY_BYTES)
                        src.buffer.snapshot().utf8().take(MAX_REPLY_BYTES.toInt())
                    }
                    r.code in 500..599 && attempt == 0 -> true
                    else -> return@withContext null
                }
            }
            if (!retry) return@withContext null
        }
        null
    }

    companion object {
        private const val PLACE_HOST = "www.google.com"
        private const val MAX_REPLY_BYTES = 64L * 1024
        private val FID_SHAPE = Regex("""0x[0-9a-f]+:0x[0-9a-f]+""")

        const val MAX_HOPS = 6
        const val TIMEOUT_S = 6L
        const val CALL_TIMEOUT_S = 12L
        val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
    }
}
