package vn.lutrinh.app.ui.trips

import vn.lutrinh.app.domain.model.Dwell
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.StopSuggestion
import vn.lutrinh.app.data.GalleryAccess
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.StopPhotos
import vn.lutrinh.app.domain.usecase.StopSuggestions
import vn.lutrinh.app.map.StopMarker
import java.time.ZoneId
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
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
import vn.lutrinh.app.domain.usecase.DistanceSource
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.domain.usecase.TrackMath
import vn.lutrinh.app.domain.usecase.TrackRules
import vn.lutrinh.app.domain.usecase.TripStats
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.statsOf
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TrackLine
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.tracks.GpsCard
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

/** LazyColumn item indexes of [TripDetailContent]: map, header, GPS card, then the timeline. */
private const val ITEM_HEADER = 1
private const val ITEM_GPS = 2

data class TripDetail(
    val trip: Trip,
    val steps: List<Step>,
    val stats: TripStats,
    /** Recorded GPS tracks, newest first (R16). */
    val tracks: List<Track> = emptyList(),
    /** The tracks as drawable lines (one per segment, simplified). */
    val trackLines: List<TrackLine> = emptyList(),
    /** Places a finished track stayed without a step yet (R17), oldest first. */
    val stopSuggestions: List<StopSuggestion> = emptyList(),
    /** R16: false for a trip that ended before today (no "Ghi hành trình", only "Tạo chuyến mới và ghi"). */
    val canRecord: Boolean = true,
) {
    /** legs[i] = steps[i] -> steps[i+1] (R12). */
    val legs: List<Leg> = legsOf(steps)
    val legModes: List<TransportMode?> get() = legs.map { it.mode }
}

/** Stop suggestions as map markers (R17, docs/05-map.md "Stop suggestions"). */
fun TripDetail.stopMarkers(): List<StopMarker> = stopSuggestions.map { StopMarker(it.key, it.dwell.center) }

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

    /**
     * R17: recomputed from the stored points whenever a track ends or is deleted, a step changes
     * or the user answers a suggestion (all are observed tables).
     */
    private val stopSuggestions = combine(
        c.tracks.observeTracks(tripId), c.tracks.observePoints(tripId), c.steps.observeSteps(tripId), c.stopDecisions.observeDecisions(tripId),
    ) { tracks, points, steps, decisions ->
        StopSuggestions.build(tracks, points, steps, decisions, ZoneId.systemDefault())
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    val state: StateFlow<Loadable<TripDetail>> =
        combine(
            c.trips.observeTrip(tripId), c.steps.observeSteps(tripId), c.tracks.observeTracks(tripId), trackLines, stopSuggestions,
        ) { trip, steps, tracks, lines, suggestions ->
            if (trip == null) {
                Loadable.Missing
            } else {
                val ordered = steps.inTimelineOrder()
                Loadable.Ready(TripDetail(trip, ordered, statsOf(trip, ordered, tracks), tracks, lines, suggestions, TrackRules.canRecordOn(trip, c.clock.today())))
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

    private val galleryTick = MutableStateFlow(0)

    /** Re-reads gallery access and photos (R19); called on every resume of the trip screen. */
    fun refreshGallery() = galleryTick.update { it + 1 }

    /**
     * R19: per suggestion key, how many gallery photos the step editor would offer ("N ảnh trong
     * máy"); empty without gallery access.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val galleryCounts: StateFlow<Map<String, Int>> =
        combine(stopSuggestions, c.steps.observeSteps(tripId), galleryTick) { suggestions, steps, _ -> suggestions to steps }
            .mapLatest { (suggestions, steps) ->
                if (suggestions.isEmpty() || c.gallery.access() == GalleryAccess.NONE) emptyMap()
                else {
                    val zone = ZoneId.systemDefault()
                    val tripPhotos = steps.flatMap { it.photos }
                    suggestions.associate { sg ->
                        val w = StopPhotos.window(sg.dwell.startAt, sg.dwell.endAt, zone)
                        sg.key to StopPhotos.offer(c.gallery.candidates(w, zone), w, tripPhotos).size
                    }
                }
            }
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** "Bỏ qua" (R17): hidden for good, also after a restart. */
    fun dismissStop(s: StopSuggestion) = viewModelScope.launch {
        val d = s.dwell
        c.stopDecisions.saveDecision(StopDecision(d.trackId, d.startAt, d.endAt, StopDecisionKind.DISMISSED, null, c.clock.nowMillis()))
    }

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
    /** "Chia sẻ chuyến đi" (R18). */
    onShare: () -> Unit,
    onAddStep: () -> Unit,
    /** "Thêm điểm dừng" on a stop suggestion (R17): the step editor prefilled from [Dwell]. */
    onAddFromStop: (Dwell, String) -> Unit,
    onAddFromPhotos: () -> Unit,
    onOpenStep: (String) -> Unit,
    /** "Tạo chuyến mới và ghi" (R16, ended trips): the new-trip form, then recording on the new trip. */
    onNewTripAndRecord: () -> Unit = {},
    onOpenBatteryGuide: () -> Unit = {},
    /** Opened by "Tạo chuyến mới và ghi": start recording on arrival. */
    autoRecord: Boolean = false,
    vm: TripDetailViewModel = viewModel(factory = factory { c, h -> TripDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val galleryCounts by vm.galleryCounts.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshGallery()
        onPauseOrDispose { }
    }
    var menu by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editLegTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val detail = (state as? Loadable.Ready)?.value
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Collapsing top bar: the title is in the header until that scrolls away, then it moves up.
    val titleOffsetPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val showBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > ITEM_HEADER ||
                (listState.firstVisibleItemIndex == ITEM_HEADER && listState.firstVisibleItemScrollOffset > titleOffsetPx)
        }
    }
    // The FAB shrinks to its icon as soon as the map has scrolled away.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(showBarTitle && detail != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(detail?.trip?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.share_trip)) }
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
            // One FAB (it used to be two stacked ones over the cards' own buttons): it collapses
            // on scroll and opens a menu with the two ways to add a step.
            val fabLabel = stringResource(R.string.trip_fab_add)
            if (detail != null) Box {
                ExtendedFloatingActionButton(
                    onClick = { fabMenu = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(fabLabel) },
                    // M3 1.3 exposes no text for the extended FAB; label it for TalkBack and UI tests.
                    modifier = Modifier.semantics { contentDescription = fabLabel },
                )
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trip_add_step)) },
                        leadingIcon = { Icon(Icons.Filled.AddLocationAlt, null) },
                        onClick = { fabMenu = false; onAddStep() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.from_photos_title)) },
                        leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                        onClick = { fabMenu = false; onAddFromPhotos() },
                    )
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            Loadable.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
            is Loadable.Failed -> MessageState(Icons.Outlined.ErrorOutline, stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
            is Loadable.Ready -> TripDetailContent(
                s.value, padding, listState, onOpenMap, onAddStep, onOpenStep, onEditLeg = { editLegTo = it },
                gpsCard = {
                    GpsCard(
                        tripId = s.value.trip.id, tracks = s.value.tracks, suggestions = s.value.stopSuggestions,
                        steps = s.value.steps, galleryCounts = galleryCounts, recording = recording,
                        ended = !s.value.canRecord, autoStart = autoRecord,
                        onPause = { vm.pauseRecording() }, onResume = { vm.resumeRecording() }, onStop = { vm.stopRecording() },
                        onNewTripAndRecord = onNewTripAndRecord,
                        onOpenBatteryGuide = onOpenBatteryGuide,
                        onDeleteTrack = { vm.deleteTrack(it) },
                        onAddFromStop = { sg, title -> onAddFromStop(sg.dwell, title) },
                        onDismissStop = { vm.dismissStop(it) },
                        // Scroll the opened card to the top so its buttons are not under the FAB.
                        onOpened = { scope.launch { listState.animateScrollToItem(ITEM_GPS) } },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
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
    listState: LazyListState,
    onOpenMap: () -> Unit,
    onAddStep: () -> Unit,
    onOpenStep: (String) -> Unit,
    onEditLeg: (toStepId: String) -> Unit,
    gpsCard: @Composable () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 104.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                TripMap(markers = d.steps.toMarkers(), legModes = d.legModes, interactive = false, fitPaddingDp = 40, modifier = Modifier.fillMaxSize(), tracks = d.trackLines,
                    stopSuggestions = d.stopMarkers(),
                )
                Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
                SmallFloatingActionButton(
                    onClick = onOpenMap,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { Icon(Icons.Outlined.OpenInFull, stringResource(R.string.trip_open_map)) }
            }
        }
        item { TripHeader(d) }
        item(key = "gps") { gpsCard() }
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
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tile = Modifier.fillMaxHeight()
            Stat("${d.stats.stepCount}", stringResource(R.string.stat_steps), tile.weight(1f))
            Stat("${d.stats.photoCount}", stringResource(R.string.stat_photos), tile.weight(1f))
            Stat(
                DisplayFormat.kmNumber(d.stats.distanceKm), stringResource(R.string.stat_km), tile.weight(1.4f),
                note = stringResource(if (d.stats.distanceSource == DistanceSource.GPS) R.string.stat_km_gps else R.string.stat_km_straight),
            )
            d.stats.days?.let { Stat("$it", stringResource(R.string.stat_days), tile.weight(1f)) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, note: String? = null) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            val on = MaterialTheme.colorScheme.onPrimaryContainer
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = on)
            // Labels wrap (1.3× font) instead of ending in "…"; a note sits on its own second line.
            Text(label, style = MaterialTheme.typography.labelSmall, color = on)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.75f))
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
