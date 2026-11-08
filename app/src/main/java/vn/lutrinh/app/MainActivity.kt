package vn.lutrinh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.lutrinh.app.ui.AppNavigation
import vn.lutrinh.app.ui.theme.LuTrinhTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LuTrinhTheme { AppNavigation() } }
    }
}
