package vn.lutrinh.app.ui.picker

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

/** Offline shortcuts (no geocoding service yet): well-known travel areas in Vietnam. */
val QUICK_PLACES = listOf(
    "Hà Giang" to GeoPoint(22.8233, 104.9836),
    "Đồng Văn" to GeoPoint(23.2786, 105.3621),
    "Mã Pí Lèng" to GeoPoint(23.2397, 105.4096),
    "Ninh Bình" to GeoPoint(20.2506, 105.9745),
    "Tràng An" to GeoPoint(20.2546, 105.8954),
    "Hà Nội" to GeoPoint(21.0285, 105.8542),
    "Sa Pa" to GeoPoint(22.3364, 103.8438),
    "Hội An" to GeoPoint(15.8801, 108.3380),
    "Đà Lạt" to GeoPoint(11.9404, 108.4583),
)

class LocationPickerViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val tripId: String = checkNotNull(handle["tripId"])
    private val stepId: String? = handle.get<String>("stepId")
    val initial: GeoPoint? = handle.get<String>("lat")?.toDoubleOrNull()?.let { lat ->
        handle.get<String>("lng")?.toDoubleOrNull()?.let { lng -> GeoPoint(lat, lng) }
    }
    val markers = c.steps.observeSteps(tripId)
        .map { contextMarkers(it, stepId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onBack: () -> Unit,
    onPicked: (GeoPoint) -> Unit,
    vm: LocationPickerViewModel = viewModel(factory = factory { c, h -> LocationPickerViewModel(c, h) }),
) {
    val markers by vm.markers.collectAsStateWithLifecycle()
    val start = vm.initial ?: NewStepDefaults.FALLBACK_CENTER
    var center by remember { mutableStateOf(start) }
    var moveTo by remember { mutableStateOf<GeoPoint?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PickerMap(
                initial = start,
                initialZoom = if (vm.initial != null) MapConfig.PICKER_ZOOM else 5.5,
                contextMarkers = markers,
                onCenterChanged = { center = it },
                moveTo = moveTo,
                modifier = Modifier.fillMaxSize(),
            )
            // Fixed centre pin: its tip marks the picked point.
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-22).dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                items(QUICK_PLACES) { (name, point) ->
                    AssistChip(
                        onClick = { moveTo = point },
                        label = { Text(name) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
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
}
