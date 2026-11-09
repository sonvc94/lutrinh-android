package vn.lutrinh.app

import android.app.Application
import vn.lutrinh.app.map.MapSetup

class LuTrinhApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapSetup.init(this)
        container = AppContainer(this)
        // Re-attach offline map areas; resumes a download the app was killed in (R15).
        container.offlineMaps.start()
    }
}
