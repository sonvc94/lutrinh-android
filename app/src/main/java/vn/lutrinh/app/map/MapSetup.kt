package vn.lutrinh.app.map

import android.content.Context
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

object MapSetup {
    /**
     * Initialises MapLibre once per process. The only network traffic of the app is the
     * tile download done here by MapLibre; we attach our own User-Agent to it.
     */
    fun init(context: Context) {
        MapLibre.getInstance(context)
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", MapConfig.USER_AGENT).build(),
                )
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }
}
