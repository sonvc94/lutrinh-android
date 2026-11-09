package vn.lutrinh.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.lutrinh.app.domain.usecase.DisplayFormat
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
        setContent { LuTrinhTheme { AppNavigation() } }
    }
}
