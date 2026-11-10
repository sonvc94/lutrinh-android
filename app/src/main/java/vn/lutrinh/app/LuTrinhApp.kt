package vn.lutrinh.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.launch
import vn.lutrinh.app.map.MapEngines
import vn.lutrinh.app.tracking.TrackNotifications

class LuTrinhApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapEngines.active.initialize(this)
        container = AppContainer(this)
        // Re-attach offline map areas; resumes a download the app was killed in (R15).
        container.offlineMaps.start()
        TrackNotifications.createChannel(this)
        // A fresh process has no recording: an open track was left by a process that died (R16).
        container.appScope.launch { container.recorder.recoverInterrupted() }
        // Road routes for legs (R36): fetched in the background while on, noticed and online.
        container.routeSync.start(container.appScope)
    }

    /** Coil for local files only; video files draw a still frame (R29 thumbnails). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
}
