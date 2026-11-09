package vn.lutrinh.app.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.map.OfflineArea
import vn.lutrinh.app.map.OfflineAreaState
import vn.lutrinh.app.map.OfflineConfig
import vn.lutrinh.app.map.OfflinePhase
import vn.lutrinh.app.map.OfflineProblem
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatBytes

/** "Tải vùng của chuyến đi này": the trip's area, or why there is none. */
data class TripOffline(val title: String, val state: OfflineAreaState?, val tooBig: Boolean)

data class OfflineUi(
    val readyMade: List<OfflineAreaState>,
    /** Only when opened from a trip's map. */
    val trip: TripOffline?,
    /** Areas of other trips that are stored (or being downloaded). */
    val otherTrips: List<OfflineAreaState>,
    val online: Boolean,
    val storeBytes: Long,
    val freeBytes: Long,
)

class OfflineMapsViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val tripId: String? = handle["tripId"]

    private val tripArea: Flow<Pair<String, OfflineArea?>?> = if (tripId == null) {
        flowOf(null)
    } else {
        combine(c.trips.observeTrip(tripId), c.steps.observeSteps(tripId)) { trip, steps ->
            trip?.let { it.title to OfflineConfig.tripArea(tripId, it.title, steps.map { s -> s.location }) }
        }
    }

    val ui: StateFlow<OfflineUi?> = combine(c.offlineMaps.states, tripArea, c.network.online, c.offlineMaps.storeBytes) { states, trip, online, store ->
        val tripKey = tripId?.let { OfflineConfig.tripKey(it) }
        OfflineUi(
            readyMade = OfflineConfig.READY_MADE.map { states[it.key] ?: OfflineAreaState(it, OfflinePhase.NOT_DOWNLOADED) },
            trip = trip?.let { (title, area) ->
                val stored = tripKey?.let { states[it] }
                val state = stored ?: area?.let { OfflineAreaState(it, OfflinePhase.NOT_DOWNLOADED) }
                TripOffline(title, state, tooBig = stored == null && area != null && !OfflineConfig.withinLimit(area.bounds))
            },
            otherTrips = states.values.filter { OfflineConfig.tripIdOf(it.area.key) != null && it.area.key != tripKey },
            online = online,
            storeBytes = store,
            freeBytes = c.offlineMaps.freeBytes(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun download(area: OfflineArea) = c.offlineMaps.download(area)
    fun pause(key: String) = c.offlineMaps.pause(key)
    fun resume(key: String) = c.offlineMaps.resume(key)
    fun delete(key: String) = c.offlineMaps.delete(key)
}

/** "Bản đồ offline" (docs/03 §11, R15): ready-made regions, this trip's area, stored areas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    vm: OfflineMapsViewModel = viewModel(factory = factory { c, h -> OfflineMapsViewModel(c, h) }),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<OfflineAreaState?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        val u = ui ?: return@Scaffold
        val actions = AreaActions(
            download = vm::download,
            pause = vm::pause,
            resume = vm::resume,
            cancel = vm::delete,
            delete = { toDelete = it },
        )
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.offline_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!u.online) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.offline_offline_now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (u.trip != null) {
                item { SectionTitle(stringResource(R.string.offline_section_trip)) }
                item { TripAreaCard(u.trip, actions) }
            }
            item { SectionTitle(stringResource(R.string.offline_section_ready)) }
            u.readyMade.forEach { s -> item(key = s.area.key) { AreaCard(s.area.name, s.area.description, s, actions) } }
            if (u.otherTrips.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.offline_section_trips)) }
                u.otherTrips.forEach { s -> item(key = s.area.key) { AreaCard(s.area.name, "", s, actions) } }
            }
            item {
                Text(
                    stringResource(R.string.offline_store, formatBytes(u.storeBytes), formatBytes(u.freeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    toDelete?.let { s ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.offline_delete_title, s.area.name),
            body = stringResource(R.string.offline_delete_body, formatBytes(s.completedBytes)),
            onConfirm = { vm.delete(s.area.key); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

private class AreaActions(
    val download: (OfflineArea) -> Unit,
    val pause: (String) -> Unit,
    val resume: (String) -> Unit,
    val cancel: (String) -> Unit,
    val delete: (OfflineAreaState) -> Unit,
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TripAreaCard(trip: TripOffline, actions: AreaActions) {
    val s = trip.state
    when {
        s == null -> InfoCard(stringResource(R.string.offline_trip_area), stringResource(R.string.offline_trip_no_steps))
        trip.tooBig -> InfoCard(
            stringResource(R.string.offline_trip_area),
            stringResource(R.string.offline_trip_too_big, formatBytes(s.estimateBytes), formatBytes(OfflineConfig.MAX_AREA_BYTES)),
        )
        else -> AreaCard(
            stringResource(R.string.offline_trip_area),
            stringResource(R.string.offline_trip_area_body, trip.title, OfflineConfig.TRIP_MARGIN_KM.toInt()),
            s,
            actions,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AreaCard(title: String, description: String, s: OfflineAreaState, actions: AreaActions) {
    val key = s.area.key
    val percent = (s.progress * 100).toInt()
    val done = s.phase == OfflinePhase.COMPLETE
    Card(
        Modifier.fillMaxWidth(),
        colors = if (done) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val status = when (s.phase) {
                OfflinePhase.NOT_DOWNLOADED -> stringResource(R.string.offline_estimate, formatBytes(s.estimateBytes))
                OfflinePhase.DOWNLOADING -> if (s.problem == OfflineProblem.SERVER) {
                    stringResource(R.string.offline_error_server)
                } else {
                    stringResource(R.string.offline_progress, percent, formatBytes(s.completedBytes))
                }
                OfflinePhase.WAITING_NETWORK -> stringResource(R.string.offline_waiting_network, percent)
                OfflinePhase.PAUSED -> stringResource(R.string.offline_paused, percent, formatBytes(s.completedBytes))
                OfflinePhase.FAILED -> when (s.problem) {
                    OfflineProblem.NO_NETWORK -> stringResource(R.string.offline_error_no_network)
                    OfflineProblem.STORAGE_FULL -> stringResource(R.string.offline_error_storage, formatBytes(s.estimateBytes))
                    else -> stringResource(R.string.offline_error_other)
                }
                OfflinePhase.COMPLETE -> stringResource(R.string.offline_complete, formatBytes(s.completedBytes))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (s.phase == OfflinePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (s.phase in setOf(OfflinePhase.DOWNLOADING, OfflinePhase.WAITING_NETWORK, OfflinePhase.PAUSED)) {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (s.phase) {
                    OfflinePhase.NOT_DOWNLOADED -> FilledTonalButton(onClick = { actions.download(s.area) }) { Text(stringResource(R.string.offline_download)) }
                    OfflinePhase.DOWNLOADING, OfflinePhase.WAITING_NETWORK -> {
                        OutlinedButton(onClick = { actions.pause(key) }) { Text(stringResource(R.string.offline_pause)) }
                        TextButton(onClick = { actions.cancel(key) }) { Text(stringResource(R.string.offline_cancel)) }
                    }
                    OfflinePhase.PAUSED -> {
                        FilledTonalButton(onClick = { actions.resume(key) }) { Text(stringResource(R.string.offline_resume)) }
                        TextButton(onClick = { actions.cancel(key) }) { Text(stringResource(R.string.offline_cancel)) }
                    }
                    OfflinePhase.FAILED -> {
                        FilledTonalButton(onClick = { actions.download(s.area) }) { Text(stringResource(R.string.action_retry)) }
                        TextButton(onClick = { actions.cancel(key) }) { Text(stringResource(R.string.offline_cancel)) }
                    }
                    OfflinePhase.COMPLETE -> OutlinedButton(onClick = { actions.delete(s) }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
