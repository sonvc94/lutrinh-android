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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.domain.usecase.TrackMath
import vn.lutrinh.app.domain.usecase.TripStats
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.statsOf
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TrackLine
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.tracks.RecordTrackControl
import vn.lutrinh.app.ui.tracks.TrackList
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

data class TripDetail(
    val trip: Trip,
    val steps: List<Step>,
    val stats: TripStats,
    /** Recorded GPS tracks, newest first (R16). */
    val tracks: List<Track> = emptyList(),
    /** The tracks as drawable lines (one per segment, simplified). */
    val trackLines: List<TrackLine> = emptyList(),
) {
    /** legs[i] = steps[i] -> steps[i+1] (R12). */
    val legs: List<Leg> = legsOf(steps)
    val legModes: List<TransportMode?> get() = legs.map { it.mode }
}

fun List<Step>.toMarkers(highlightId: String? = null): List<MapMarker> =
    mapIndexed { i, s -> MapMarker(s.id, "${i + 1}", s.location, s.id == highlightId) }

class TripDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val deleted = MutableStateFlow(false)

    /** The app's open recording (any trip), for the "Ghi hành trình" control. */
    val recording: StateFlow<Track?> = c.recorder.active

    private val trackLines = combine(c.tracks.observePoints(tripId), c.recorder.active.map { it?.id }.distinctUntilChanged()) { points, liveId ->
        points.groupBy { it.trackId }.flatMap { (id, pts) -> TrackMath.displayLines(pts).map { TrackLine(id, it, live = id == liveId) } }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<Loadable<TripDetail>> =
        combine(c.trips.observeTrip(tripId), c.steps.observeSteps(tripId), c.tracks.observeTracks(tripId), trackLines) { trip, steps, tracks, lines ->
            if (trip == null) {
                Loadable.Missing
            } else {
                val ordered = steps.inTimelineOrder()
                Loadable.Ready(TripDetail(trip, ordered, statsOf(trip, ordered), tracks, lines))
            }
        }
            .catch { emit(Loadable.Failed(it.message ?: it.javaClass.simpleName)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun setLegMode(toStepId: String, mode: TransportMode?) = viewModelScope.launch { c.setLegTransport(toStepId, mode) }

    // Recording commands run in the app scope: leaving the screen must not cancel a stop.
    fun pauseRecording() = c.appScope.launch { c.recorder.pause() }
    fun resumeRecording() = c.appScope.launch { c.recorder.resume() }
    fun stopRecording() = c.appScope.launch { c.recorder.stop() }
    fun deleteTrack(trackId: String) = viewModelScope.launch { c.deleteTrack(trackId) }

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
    onAddFromPhotos: () -> Unit,
    onOpenStep: (String) -> Unit,
    vm: TripDetailViewModel = viewModel(factory = factory { c, h -> TripDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editLegTo by remember { mutableStateOf<String?>(null) }
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
            val fabLabel = stringResource(R.string.trip_add_step)
            if (detail != null) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = onAddFromPhotos,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.from_photos_title)) }
                ExtendedFloatingActionButton(
                    onClick = onAddStep,
                    icon = { Icon(Icons.Filled.AddLocationAlt, null) },
                    text = { Text(fabLabel) },
                    // M3 1.3 exposes no text for the extended FAB; label it for TalkBack and UI tests.
                    modifier = Modifier.semantics { contentDescription = fabLabel },
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            Loadable.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
            is Loadable.Failed -> MessageState(Icons.Outlined.ErrorOutline, stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
            is Loadable.Ready -> TripDetailContent(
                s.value, padding, onOpenMap, onAddStep, onOpenStep, onEditLeg = { editLegTo = it },
                tracksSection = {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        RecordTrackControl(
                            tripId = s.value.trip.id, active = recording,
                            onPause = { vm.pauseRecording() }, onResume = { vm.resumeRecording() }, onStop = { vm.stopRecording() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TrackList(s.value.tracks, onDelete = { vm.deleteTrack(it) })
                    }
                },
            )
        }
    }

    detail?.legs?.firstOrNull { it.to.id == editLegTo }?.let { leg ->
        LegModeSheet(leg, onPick = { vm.setLegMode(leg.to.id, it); editLegTo = null }, onDismiss = { editLegTo = null })
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
    onEditLeg: (toStepId: String) -> Unit,
    tracksSection: @Composable () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 104.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                TripMap(markers = d.steps.toMarkers(), legModes = d.legModes, interactive = false, fitPaddingDp = 40, modifier = Modifier.fillMaxSize(), tracks = d.trackLines)
                Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
                SmallFloatingActionButton(
                    onClick = onOpenMap,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { Icon(Icons.Outlined.OpenInFull, stringResource(R.string.trip_open_map)) }
            }
        }
        item { TripHeader(d) }
        item(key = "tracks") { tracksSection() }
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
                if (i > 0) LegRow(d.legs[i - 1]) { onEditLeg(step.id) }
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

/** Between two timeline rows: the connector line continues, with the leg's mode chip beside it. */
@Composable
private fun LegRow(leg: Leg, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).height(44.dp), Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        LegChip(leg, onClick, Modifier.padding(start = 12.dp))
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
