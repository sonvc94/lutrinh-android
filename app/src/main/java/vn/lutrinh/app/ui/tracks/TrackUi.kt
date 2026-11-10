package vn.lutrinh.app.ui.tracks

import android.Manifest
import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.Lift
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import vn.lutrinh.app.ui.common.NoticeDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.delay
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.TrackFormat
import vn.lutrinh.app.tracking.TrackRecordingService
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.displayDateTime
import vn.lutrinh.app.ui.theme.Palette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Colour of recorded tracks in the UI, same as the map line: ink (docs/05, docs/08). */
val TrackInk: Color = Palette.Track

/** Wall-clock "now", ticking every second while [running]. */
@Composable
fun rememberNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun Long.localDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

private enum class TrackDialog { RATIONALE, DENIED, LOCATION_OFF }

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * "Ghi hành trình" control (R16, docs/03-screens.md): start (explains, then asks precise location
 * and, on Android 13+, notifications), then pause/resume and stop while this trip records.
 * [active] = the app's open recording, whatever trip it belongs to.
 */
@Composable
fun RecordTrackControl(
    tripId: String,
    active: Track?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Over a map: no explanatory text, a floating surface. */
    compact: Boolean = false,
    /** R16: the trip ended before today: no "Ghi hành trình", only [onNewTripAndRecord]. */
    ended: Boolean = false,
    onNewTripAndRecord: () -> Unit = {},
    /** Start the recording as soon as the control is shown (once): "Tạo chuyến mới và ghi". */
    autoStart: Boolean = false,
    /** R22: opens "Ghi hành trình ổn định"; called once, right after the very first recording starts. */
    onOpenBatteryGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val guideOnce = (context.applicationContext as LuTrinhApp).container.prefs.batteryGuideOnce
    var dialog by remember { mutableStateOf<TrackDialog?>(null) }

    fun startNow() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) dialog = TrackDialog.LOCATION_OFF
        else {
            TrackRecordingService.start(context, tripId)
            if (guideOnce.consume()) onOpenBatteryGuide()
        }
    }
    // Notifications are asked once after location; recording also works when they are refused.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNow() }
    fun askNotificationsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) askNotificationsThenStart() else dialog = TrackDialog.DENIED
    }
    val onStart = {
        if (context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) askNotificationsThenStart() else dialog = TrackDialog.RATIONALE
    }

    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoStart) {
        if (autoStart && !autoStarted && active == null && !ended) {
            autoStarted = true
            onStart()
        }
    }

    val mine = active?.takeIf { it.tripId == tripId }
    val now = rememberNow(mine?.status == TrackStatus.RECORDING)
    // Over the map ([compact]) the specimen's map chip (card paper, 12 dp, chip shadow); inside
    // the GPS card a flat block of the same paper.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (compact) Lift.Chip else 0.dp,
        modifier = modifier,
    ) {
        when {
            mine != null -> Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val recording = mine.status == TrackStatus.RECORDING
                Box(Modifier.size(10.dp).background(if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, CircleShape))
                Column(Modifier.padding(start = 10.dp).then(if (compact) Modifier else Modifier.weight(1f))) {
                    Text(
                        stringResource(if (recording) R.string.track_recording else R.string.track_paused),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${TrackFormat.duration(mine.durationMs(now))} · ${TrackFormat.distance(mine.distanceM)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (recording) {
                    FilledTonalIconButton(onClick = onPause, shape = MaterialTheme.shapes.small) { Icon(TopoIcons.Pause, stringResource(R.string.track_pause)) }
                } else {
                    FilledTonalIconButton(onClick = onResume, shape = MaterialTheme.shapes.small) { Icon(TopoIcons.Play, stringResource(R.string.track_resume)) }
                }
                FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onStop) {
                    Icon(TopoIcons.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_stop))
                }
            }
            active != null -> Text(
                stringResource(R.string.track_other_trip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ended -> Column(Modifier.padding(if (compact) 4.dp else 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!compact) {
                    Text(
                        stringResource(R.string.track_trip_ended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onNewTripAndRecord) {
                    Icon(TopoIcons.Record, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_new_trip_and_record))
                }
            }
            else -> Row(
                Modifier.padding(if (compact) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact) {
                    Text(
                        stringResource(R.string.track_section_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                }
                FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onStart) {
                    Icon(TopoIcons.Record, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_record))
                }
            }
        }
    }

    when (dialog) {
        TrackDialog.RATIONALE -> NoticeDialog(
            icon = TopoIcons.Route,
            title = stringResource(R.string.track_permission_title),
            body = stringResource(R.string.track_permission_body),
            confirmLabel = stringResource(R.string.track_permission_continue),
            onConfirm = {
                dialog = null
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onDismissRequest = { dialog = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
        TrackDialog.DENIED -> SettingsDialog(
            title = stringResource(R.string.track_permission_denied_title),
            body = stringResource(R.string.track_permission_denied_body),
            intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            onDismiss = { dialog = null },
        )
        TrackDialog.LOCATION_OFF -> SettingsDialog(
            title = stringResource(R.string.track_location_off_title),
            body = stringResource(R.string.track_location_off_body),
            intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsDialog(title: String, body: String, intent: Intent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    NoticeDialog(
        icon = TopoIcons.MyLocation,
        title = title,
        body = body,
        confirmLabel = stringResource(R.string.action_open_settings),
        onConfirm = {
            onDismiss()
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        onDismissRequest = onDismiss,
        dismissLabel = stringResource(R.string.action_close),
    )
}

/** "Hành trình đã ghi": one row per track (date, distance, duration, points), delete when ended. */
@Composable
fun TrackList(tracks: List<Track>, onDelete: (String) -> Unit, modifier: Modifier = Modifier) {
    if (tracks.isEmpty()) return
    var confirm by remember { mutableStateOf<Track?>(null) }
    val now = rememberNow(tracks.any { it.status == TrackStatus.RECORDING })
    Column(modifier) {
        Text(
            "${stringResource(R.string.track_section_title)} (${tracks.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        tracks.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TopoIcons.Route, null, tint = TrackInk)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(t.startedAt.localDateTime().displayDateTime(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(
                            TrackFormat.distance(t.distanceM),
                            DisplayFormat.duration(t.durationMs(now)),
                            stringResource(R.string.track_points, t.pointCount),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        t.status == TrackStatus.RECORDING -> Text(stringResource(R.string.track_recording), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        t.status == TrackStatus.PAUSED -> Text(stringResource(R.string.track_paused), style = MaterialTheme.typography.labelSmall)
                        t.interrupted -> Text(stringResource(R.string.track_interrupted), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (!t.isOpen) {
                    IconButton(onClick = { confirm = t }) { Icon(TopoIcons.Delete, stringResource(R.string.track_delete)) }
                }
            }
        }
    }
    confirm?.let { t ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.track_delete_confirm_title),
            body = stringResource(
                R.string.track_delete_confirm_body, t.startedAt.localDateTime().displayDateTime(), TrackFormat.distance(t.distanceM), t.pointCount,
            ),
            onConfirm = { confirm = null; onDelete(t.id) },
            onDismiss = { confirm = null },
        )
    }
}
