package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.TripStats
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.statsOf
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.ui.common.displayDay
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.common.factory
import java.time.temporal.ChronoUnit
import java.util.Locale

data class TripDetail(val trip: Trip, val steps: List<Step>, val stats: TripStats)

fun List<Step>.toMarkers(highlightId: String? = null): List<MapMarker> =
    mapIndexed { i, s -> MapMarker(s.id, "${i + 1}", s.location, s.id == highlightId) }

class TripDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val deleted = MutableStateFlow(false)

    val state: StateFlow<Loadable<TripDetail>> =
        combine(c.trips.observeTrip(tripId), c.steps.observeSteps(tripId)) { trip, steps ->
            if (trip == null) {
                Loadable.Missing
            } else {
                val ordered = steps.inTimelineOrder()
                Loadable.Ready(TripDetail(trip, ordered, statsOf(trip, ordered)))
            }
        }
            .catch { emit(Loadable.Failed(it.message ?: it.javaClass.simpleName)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun delete() = viewModelScope.launch {
        c.deleteTrip(tripId)
        deleted.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenMap: () -> Unit,
    onAddStep: () -> Unit,
    onOpenStep: (String) -> Unit,
    vm: TripDetailViewModel = viewModel(factory = factory { c, h -> TripDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val detail = (state as? Loadable.Ready)?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.trip?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = onOpenMap) { Icon(Icons.Outlined.Map, stringResource(R.string.trip_open_map)) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trip_edit_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trip_delete_button)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (detail != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddStep,
                    icon = { Icon(Icons.Filled.AddLocationAlt, null) },
                    text = { Text(stringResource(R.string.trip_add_step)) },
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            Loadable.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
            is Loadable.Failed -> MessageState(Icons.Outlined.ErrorOutline, stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
            is Loadable.Ready -> TripDetailContent(s.value, padding, onOpenMap, onAddStep, onOpenStep)
        }
    }

    if (confirmDelete && detail != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.trip_delete_confirm_title),
            body = stringResource(R.string.trip_delete_confirm_body, detail.trip.title, detail.stats.stepCount, detail.stats.photoCount),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TripDetailContent(
    d: TripDetail,
    padding: PaddingValues,
    onOpenMap: () -> Unit,
    onAddStep: () -> Unit,
    onOpenStep: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 104.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                TripMap(markers = d.steps.toMarkers(), interactive = false, fitPaddingDp = 40, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
                SmallFloatingActionButton(
                    onClick = onOpenMap,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { Icon(Icons.Outlined.OpenInFull, stringResource(R.string.trip_open_map)) }
            }
        }
        item { TripHeader(d) }
        if (d.steps.isEmpty()) {
            item {
                MessageState(
                    icon = Icons.Outlined.Route,
                    title = stringResource(R.string.trip_no_steps_title),
                    body = stringResource(R.string.trip_no_steps_body),
                    actionLabel = stringResource(R.string.trip_add_step),
                    onAction = onAddStep,
                    modifier = Modifier.height(360.dp),
                )
            }
        } else {
            itemsIndexed(d.steps, key = { _, s -> s.id }) { i, step ->
                val prevDay = d.steps.getOrNull(i - 1)?.arrivedAt?.toLocalDate()
                val day = step.arrivedAt.toLocalDate()
                if (day != prevDay) {
                    val n = ChronoUnit.DAYS.between(d.trip.startDate, day) + 1
                    Text(
                        stringResource(R.string.day_label, n.toInt(), day.displayDay()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                StepRow(i + 1, step, isLast = i == d.steps.lastIndex) { onOpenStep(step.id) }
            }
        }
    }
}

@Composable
private fun TripHeader(d: TripDetail) {
    Column(Modifier.padding(16.dp)) {
        Text(d.trip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        val openEnded = stringResource(R.string.trip_open_ended, "%s")
        Text(
            dateRange(d.trip.startDate, d.trip.endDate) { openEnded.format(it) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (d.trip.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(d.trip.description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${d.stats.stepCount}", stringResource(R.string.stat_steps), Modifier.weight(1f))
            Stat("${d.stats.photoCount}", stringResource(R.string.stat_photos), Modifier.weight(1f))
            Stat(String.format(Locale.US, "%.0f", d.stats.distanceKm), stringResource(R.string.stat_km), Modifier.weight(1.4f))
            d.stats.days?.let { Stat("$it", stringResource(R.string.stat_days), Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Timeline row: numbered dot + vertical line, title, time, note excerpt, thumbnails. */
@Composable
private fun StepRow(number: Int, step: Step, isLast: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
            Box(
                Modifier.padding(top = 10.dp).size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                Alignment.Center,
            ) { Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
            if (!isLast) {
                Box(Modifier.width(2.dp).height(if (step.photos.isEmpty()) 44.dp else 108.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 12.dp).weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(step.arrivedAt.displayTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.note.isNotBlank()) {
                Text(step.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (step.photos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(64.dp)) {
                    step.photos.take(4).forEach { p ->
                        LocalPhoto(p.fileName, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    if (step.photos.size > 4) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).fillMaxHeight(),
                            Alignment.Center,
                        ) { Text("+${step.photos.size - 4}") }
                    }
                }
            }
        }
    }
}
