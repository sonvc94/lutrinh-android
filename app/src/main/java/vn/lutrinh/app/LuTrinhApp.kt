package vn.lutrinh.app

import android.app.Application
import vn.lutrinh.app.map.MapSetup

class LuTrinhApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapSetup.init(this)
    }
}
