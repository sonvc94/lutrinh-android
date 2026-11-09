package vn.lutrinh.app.ui.common

import android.content.Context
import vn.lutrinh.app.ui.theme.AtlasIcons
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lutrinh.app.R
import vn.lutrinh.app.map.MyLocationFix
import vn.lutrinh.app.ui.picker.DeviceLocation

/**
 * State of the "Vị trí của tôi" map button (R22): the last [fix] (drawn as a blue dot) and a
 * [signal] that changes with every new fix so the map flies to it. One fix per tap, nothing
 * tracked while the screen is open.
 */
class MyLocationState internal constructor(
    private val context: Context,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    var fix by mutableStateOf<MyLocationFix?>(null)
        private set
    var signal by mutableIntStateOf(0)
        private set
    internal var locating by mutableStateOf(false)
    internal var askPermission by mutableStateOf(false)
    private var request: CancellationSignal? = null
    internal lateinit var launchPermission: () -> Unit

    internal fun cancel() { request?.cancel() }

    /** The button: permission rationale first when needed, then one fix. */
    fun onClick() {
        if (locating) return
        if (DeviceLocation.hasPermission(context)) locate() else askPermission = true
    }

    internal fun onPermissionResult() {
        if (DeviceLocation.hasPermission(context)) locate()
        else say(R.string.my_location_denied, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    private fun locate() {
        if (!DeviceLocation.isEnabled(context)) {
            say(R.string.my_location_off, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        locating = true
        request = DeviceLocation.requestFix(context) { f ->
            locating = false
            if (f != null) { fix = f; signal++ } else say(R.string.picker_location_failed, null)
        }
    }

    private fun say(message: Int, settings: Intent?) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                context.getString(message),
                actionLabel = settings?.let { context.getString(R.string.action_open_settings) },
            )
            if (result == SnackbarResult.ActionPerformed && settings != null) {
                runCatching { context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

@Composable
fun rememberMyLocation(snackbar: SnackbarHostState): MyLocationState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { MyLocationState(context, snackbar, scope) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.onPermissionResult() }
    state.launchPermission = { launcher.launch(DeviceLocation.PERMISSIONS) }
    DisposableEffect(state) { onDispose { state.cancel() } }
    return state
}

/** Square map button ([MapButton], same look as "Xem toàn bộ hành trình") + the one-time permission explanation. */
@Composable
fun MyLocationButton(state: MyLocationState, modifier: Modifier = Modifier) {
    MapButton(AtlasIcons.MyLocation, stringResource(R.string.my_location), state::onClick, modifier)
    if (state.askPermission) {
        AlertDialog(
            onDismissRequest = { state.askPermission = false },
            title = { Text(stringResource(R.string.picker_permission_title)) },
            text = { Text(stringResource(R.string.my_location_permission_body)) },
            confirmButton = {
                TextButton(onClick = { state.askPermission = false; state.launchPermission() }) {
                    Text(stringResource(R.string.picker_permission_continue))
                }
            },
            dismissButton = { TextButton(onClick = { state.askPermission = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
