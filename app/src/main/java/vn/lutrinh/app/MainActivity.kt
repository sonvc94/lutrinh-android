package vn.lutrinh.app

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.SharedPlaces
import vn.lutrinh.app.ui.AppNavigation
import vn.lutrinh.app.ui.theme.LuTrinhTheme

class MainActivity : ComponentActivity() {
    /**
     * Library strings (bottom-sheet "Đóng"/"Kéo", pickers) follow the app, not the device: the
     * activity's resources are Vietnamese whatever the device language (still the real Activity).
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocales(LocaleList(DisplayFormat.VI)) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The theme is light-only (Theme.kt), so the bars are light too: dark icons on the cream
        // bar even when the device is in dark mode. A dark palette will switch this with the theme.
        val light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process death) must not take the same share again.
        if (savedInstanceState == null) takeShare(intent, fresh = true)
        setContent { LuTrinhTheme { AppNavigation(incomingShare = incomingShare.value, onShareHandled = { incomingShare.value = null }) } }
    }

    /** The text of a Google Maps share waiting for the navigation to open it (R43). */
    private val incomingShare = mutableStateOf<String?>(null)

    /** The app is running (singleTask): another share arrives here instead of a second activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShare(intent, fresh = false)
    }

    /**
     * R43: a `text/plain` share. A Google Maps place goes to "Thêm vào chuyến đi"; any other text
     * gets a short message and the app steps back ([fresh] = it was started for this share only:
     * close it; otherwise send the task to the back so the user lands in the app they shared from).
     */
    private fun takeShare(intent: Intent, fresh: Boolean) {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(MAX_SHARE_CHARS)
        val joined = SharedPlaces.combine(text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val isMaps = SharedPlaces.parse(joined) !is SharedPlaces.Parsed.NotGoogleMaps
        Log.i("ShareIn", "text share: " + if (isMaps) "Google Maps place" else "not Google Maps (refused)")
        if (!isMaps) {
            Toast.makeText(this, R.string.share_in_only_maps, Toast.LENGTH_LONG).show()
            if (fresh) finish() else moveTaskToBack(true)
        } else {
            incomingShare.value = joined
        }
    }

    private companion object {
        const val MAX_SHARE_CHARS = 2_000
    }
}
