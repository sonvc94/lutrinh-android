package vn.lutrinh.app.sharein

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Google Maps link (`maps.app.goo.gl/…` → `google.com/maps/place/…@lat,lng`) to read the position
 * out of the final address. Nothing but that URL is sent (no trip data, no position of the
 * user); the page behind it is never read. Every hop must be on the Google host allow-list
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

    companion object {
        const val MAX_HOPS = 6
        const val TIMEOUT_S = 6L
        const val CALL_TIMEOUT_S = 12L
        val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
    }
}
