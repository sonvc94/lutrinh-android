package vn.lutrinh.app.ui.picker

import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.map.PickerMove
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import vn.lutrinh.app.map.MapConfig
import vn.lutrinh.app.map.PickerMap
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatCoord
import vn.lutrinh.app.ui.steps.contextMarkers

/** A shortcut chip: a step of this trip (D4: no gazetteer, no online search). */
data class PlaceChip(val label: String, val point: GeoPoint)

class LocationPickerViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val tripId: String = checkNotNull(handle["tripId"])
    private val stepId: String? = handle.get<String>("stepId")
    val initial: GeoPoint? = handle.get<String>("lat")?.toDoubleOrNull()?.let { lat ->
        handle.get<String>("lng")?.toDoubleOrNull()?.let { lng -> GeoPoint(lat, lng) }
    }
    private val steps = c.steps.observeSteps(tripId)
    val markers = steps
        .map { contextMarkers(it, stepId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The trip's other steps in timeline order, named as in the timeline. */
    val chips = steps
        .map { stepChips(it, stepId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Chips for the picker: every step of the trip except the one being edited, in timeline order. */
fun stepChips(steps: List<Step>, excludeId: String?): List<PlaceChip> =
    steps.inTimelineOrder().filter { it.id != excludeId }.map { PlaceChip(it.title, it.location) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onBack: () -> Unit,
    onPicked: (GeoPoint) -> Unit,
    vm: LocationPickerViewModel = viewModel(factory = factory { c, h -> LocationPickerViewModel(c, h) }),
) {
    val markers by vm.markers.collectAsStateWithLifecycle()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val start = vm.initial ?: NewStepDefaults.FALLBACK_CENTER
    var center by remember { mutableStateOf(start) }
    var moveTo by remember { mutableStateOf<PickerMove?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var askPermission by remember { mutableStateOf(false) }
    var signal by remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(Unit) { onDispose { signal?.cancel() } }

    fun say(res: Int) = scope.launch { snackbar.showSnackbar(context.getString(res)) }
    // "Vị trí của tôi": one fix, then the camera flies there; the user still confirms with the button.
    fun locate() {
        if (!DeviceLocation.isEnabled(context)) { say(R.string.picker_location_off); return }
        locating = true
        signal = DeviceLocation.request(context) { p ->
            locating = false
            if (p != null) moveTo = PickerMove(p) else say(R.string.picker_location_failed)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (DeviceLocation.hasPermission(context)) locate() else say(R.string.picker_location_denied)
    }
    val onMyLocation = { if (DeviceLocation.hasPermission(context)) locate() else askPermission = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PickerMap(
                initial = start,
                initialZoom = if (vm.initial != null) MapConfig.PICKER_ZOOM else 5.5,
                contextMarkers = markers,
                onCenterChanged = { center = it },
                moveTo = moveTo,
                modifier = Modifier.fillMaxSize(),
                indicatorTop = 56.dp, // below the quick-place chips
            )
            // Fixed centre pin: its tip marks the picked point.
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-22).dp),
            )
            val chipColors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                item {
                    AssistChip(
                        onClick = onMyLocation,
                        enabled = !locating,
                        leadingIcon = { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(if (locating) R.string.picker_locating else R.string.picker_my_location)) },
                        colors = chipColors,
                    )
                }
                items(chips) { chip ->
                    AssistChip(
                        onClick = { moveTo = PickerMove(chip.point) },
                        label = { Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                        colors = chipColors,
                    )
                }
            }
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp).padding(bottom = 16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.picker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCoord(center.latitude, center.longitude), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onPicked(center) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.picker_confirm))
                    }
                }
            }
        }
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.picker_permission_title)) },
            text = { Text(stringResource(R.string.picker_permission_body)) },
            confirmButton = {
                TextButton(onClick = { askPermission = false; permissionLauncher.launch(DeviceLocation.PERMISSIONS) }) {
                    Text(stringResource(R.string.picker_permission_continue))
                }
            },
            dismissButton = { TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
