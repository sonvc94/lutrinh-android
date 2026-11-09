package vn.lutrinh.app

import android.app.Application
import kotlinx.coroutines.launch
import vn.lutrinh.app.map.MapSetup
import vn.lutrinh.app.tracking.TrackNotifications

class LuTrinhApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapSetup.init(this)
        container = AppContainer(this)
        // Re-attach offline map areas; resumes a download the app was killed in (R15).
        container.offlineMaps.start()
        TrackNotifications.createChannel(this)
        // A fresh process has no recording: an open track was left by a process that died (R16).
        container.appScope.launch { container.recorder.recoverInterrupted() }
    }
}
