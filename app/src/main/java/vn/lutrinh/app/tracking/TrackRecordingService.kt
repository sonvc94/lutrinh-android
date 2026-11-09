package vn.lutrinh.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.MainActivity
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.LocationFix
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.TrackFormat
import vn.lutrinh.app.domain.usecase.TrackRules
import vn.lutrinh.app.domain.usecase.TrackRecorder

/**
 * Foreground service (type location) that keeps a recording alive while the screen is off or
 * the user is elsewhere (R16, docs/04). It only moves data: GPS fixes → [TrackRecorder.onFix],
 * and [TrackRecorder.active] → notification + GPS on/off. Started from the UI while the app is
 * visible, so the "while in use" location permission is enough (no background location).
 *
 * Location source: the platform [LocationManager] GPS provider (no Play services dependency;
 * the emulator's `geo fix` feeds it directly). Not sticky: if the process dies, the system does
 * not restart it and [TrackRecorder.recoverInterrupted] ends the track on the next start.
 */
class TrackRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: TrackRecorder
    private lateinit var locationManager: LocationManager
    private var listening = false
    private var foreground = false
    private var tripTitle: String = ""

    private val listener = LocationListenerCompat { location -> onLocation(location) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val c = (application as LuTrinhApp).container
        recorder = c.recorder
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        scope.launch {
            recorder.active.collect { track -> render(track) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always go foreground first: startForegroundService() requires it within seconds.
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                scope.launch {
                    val result = if (tripId != null) recorder.start(tripId) else TrackRecorder.StartResult.TripNotFound
                    if (result is TrackRecorder.StartResult.TripNotFound && recorder.active.value == null) stopSelfNow()
                }
            }
            ACTION_PAUSE -> command { recorder.pause() }
            ACTION_RESUME -> command { recorder.resume() }
            ACTION_STOP -> command { recorder.stop() }
            else -> if (recorder.active.value == null) stopSelfNow()
        }
        return START_NOT_STICKY
    }

    /** A notification action; with nothing recording (e.g. a stale notification) the service ends. */
    private fun command(block: suspend () -> Unit) = scope.launch {
        block()
        if (recorder.active.value == null) stopSelfNow()
    }

    override fun onDestroy() {
        stopLocation()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        if (foreground) return true
        return try {
            ServiceCompat.startForeground(
                this, TrackNotifications.ID, TrackNotifications.build(this, recorder.active.value, tripTitle),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            foreground = true
            true
        } catch (e: Exception) {
            // No location permission (revoked meanwhile) or not allowed from the background.
            Log.w(TAG, "cannot start the recording in the foreground", e)
            false
        }
    }

    private suspend fun render(track: Track?) {
        if (track == null) {
            if (foreground) stopSelfNow()
            return
        }
        if (tripTitle.isEmpty()) {
            tripTitle = (application as LuTrinhApp).container.trips.getTrip(track.tripId)?.title.orEmpty()
        }
        if (track.status == TrackStatus.RECORDING) startLocation() else stopLocation()
        if (foreground && TrackNotifications.canPost(this)) {
            getSystemService(NotificationManager::class.java)
                .notify(TrackNotifications.ID, TrackNotifications.build(this, track, tripTitle))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "fine location permission missing; recording gets no fixes")
            return
        }
        val request = LocationRequestCompat.Builder(TrackRules.SAMPLE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(TrackRules.MIN_DISTANCE_M.toFloat())
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        LocationManagerCompat.requestLocationUpdates(
            locationManager, LocationManager.GPS_PROVIDER, request, ContextCompat.getMainExecutor(this), listener,
        )
        listening = true
    }

    private fun stopLocation() {
        if (!listening) return
        LocationManagerCompat.removeUpdates(locationManager, listener)
        listening = false
    }

    private fun onLocation(location: Location) {
        // Fix time on the wall clock, from the monotonic age of the fix (a GPS clock can be off).
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        val fix = LocationFix(
            location = GeoPoint(location.latitude, location.longitude),
            time = System.currentTimeMillis() - ageMs.coerceAtLeast(0),
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            altitudeM = if (location.hasAltitude()) location.altitude else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
        )
        scope.launch {
            val verdict = recorder.onFix(fix)
            Log.d(TAG, "fix ${fix.location.latitude},${fix.location.longitude} acc=${fix.accuracyM} -> $verdict")
        }
    }

    private fun stopSelfNow() {
        stopLocation()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    companion object {
        private const val TAG = "TrackRecording"
        const val ACTION_START = "vn.lutrinh.app.track.START"
        const val ACTION_PAUSE = "vn.lutrinh.app.track.PAUSE"
        const val ACTION_RESUME = "vn.lutrinh.app.track.RESUME"
        const val ACTION_STOP = "vn.lutrinh.app.track.STOP"
        const val EXTRA_TRIP_ID = "tripId"

        /** Starts recording [tripId]; call only while the app is visible and fine location is granted. */
        fun start(context: Context, tripId: String) {
            val intent = Intent(context, TrackRecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_TRIP_ID, tripId)
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun intent(context: Context, action: String): Intent =
            Intent(context, TrackRecordingService::class.java).setAction(action)
    }
}

/** The recording notification: elapsed time (chronometer), distance, pause/resume and stop. */
object TrackNotifications {
    const val ID = 4201
    const val CHANNEL = "track-recording"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.track_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.track_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun build(context: Context, track: Track?, tripTitle: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(code: Int, action: String) = PendingIntent.getService(
            context, code, TrackRecordingService.intent(context, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val now = System.currentTimeMillis()
        val recording = track?.status == TrackStatus.RECORDING
        val distance = TrackFormat.distance(track?.distanceM ?: 0.0)
        val title = context.getString(if (recording || track == null) R.string.track_notif_recording else R.string.track_notif_paused)
        val text = if (track == null || recording) {
            listOf(distance, tripTitle).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            listOf(TrackFormat.duration(track.durationMs(now)), distance, tripTitle).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_track)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (track != null && recording) {
            // The system chronometer ticks the elapsed recording time (pauses excluded).
            b.setUsesChronometer(true).setShowWhen(true).setWhen(now - track.durationMs(now))
            b.addAction(0, context.getString(R.string.track_pause), action(1, TrackRecordingService.ACTION_PAUSE))
        } else {
            b.setShowWhen(false)
            if (track != null) b.addAction(0, context.getString(R.string.track_resume), action(2, TrackRecordingService.ACTION_RESUME))
        }
        b.addAction(0, context.getString(R.string.track_stop), action(3, TrackRecordingService.ACTION_STOP))
        return b.build()
    }
}
