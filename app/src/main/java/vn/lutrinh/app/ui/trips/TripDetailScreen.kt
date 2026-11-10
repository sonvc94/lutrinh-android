package vn.lutrinh.app.ui.trips

import vn.lutrinh.app.domain.model.Dwell
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.LegMeasure
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.ui.common.ReadoutCount
import vn.lutrinh.app.ui.common.KmReadout
import vn.lutrinh.app.ui.common.MapButton
import vn.lutrinh.app.ui.common.ChipFab
import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.Space
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import vn.lutrinh.app.domain.usecase.stepNumbers
import vn.lutrinh.app.ui.common.DraftBadge
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import vn.lutrinh.app.map.PreviewFrame
import vn.lutrinh.app.map.TrackLine
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.tracks.GpsCard
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.LocalMedia
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.ui.common.displayDay
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.transfer.rememberTransfer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import java.time.temporal.ChronoUnit
import vn.lutrinh.app.ui.theme.hairline
import vn.lutrinh.app.ui.theme.numerals
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.common.Eyebrow
import vn.lutrinh.app.ui.common.ScreenHeading
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.TopoPanel
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

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
    /** R36: stored leg routes by arrival step id (empty while routing is off). */
    val routes: Map<String, LegRoute> = emptyMap(),
) {
    /** legs[i] = i-th published step -> the next published one (R12; drafts have no legs, R20). */
    val legs: List<Leg> = legsOf(steps)
    val legModes: List<TransportMode?> get() = legs.map { it.mode }

    /** R20: number of each published step (drafts have none). */
    val numbers: Map<String, Int> = stepNumbers(steps)

    /** The leg that ends at [stepId], or null (first published step, or a draft). */
    fun legInto(stepId: String): Leg? = legs.firstOrNull { it.to.id == stepId }

    /** R36: the line drawn per leg (road route or straight/arc), same order as [legs]. */
    val legPaths: List<List<GeoPoint>> = legs.map { LegRoutes.path(it, LegRoutes.usable(it, routes)) }

    /** R36: km (road or straight) and estimated time of [leg]. */
    fun measure(leg: Leg): LegMeasure = LegRoutes.measure(leg, LegRoutes.usable(leg, routes))

    /** R36: ids of the routers whose road lines are drawn (the map credits them). */
    val routedBy: Set<String> = legs.mapNotNullTo(HashSet()) { LegRoutes.usable(it, routes)?.provider }
}

/** Stop suggestions as map markers (R17, docs/05-map.md "Stop suggestions"). */
fun TripDetail.stopMarkers(): List<StopMarker> = stopSuggestions.map { StopMarker(it.key, it.dwell.center) }

/** Pins in the given (timeline) order; published steps carry their number, drafts are hollow and unnumbered (R20). */
fun List<Step>.toMarkers(highlightId: String? = null): List<MapMarker> {
    val numbers = stepNumbers(this)
    return map { s -> MapMarker(s.id, numbers[s.id]?.toString().orEmpty(), s.location, s.id == highlightId, draft = s.isDraft) }
}

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

    /** R36: the routes this trip is drawn with (none while routing is off). */
    private val routes = combine(c.legRoutes.observeForTrip(tripId), c.routingSettings.enabled) { stored, _ ->
        c.routingSettings.shown(stored)
    }

    private val suggestionsAndRoutes = combine(stopSuggestions, routes) { s, r -> s to r }

    val state: StateFlow<Loadable<TripDetail>> =
        combine(
            c.trips.observeTrip(tripId), c.steps.observeSteps(tripId), c.tracks.observeTracks(tripId), trackLines, suggestionsAndRoutes,
        ) { trip, steps, tracks, lines, (suggestions, routes) ->
            if (trip == null) {
                Loadable.Missing
            } else {
                val ordered = steps.inTimelineOrder()
                Loadable.Ready(
                    TripDetail(
                        trip, ordered, statsOf(trip, ordered, tracks, routes), tracks, lines, suggestions,
                        TrackRules.canRecordOn(trip, c.clock.today()), routes,
                    ),
                )
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

    /** R21 "Đổi ảnh bìa": null = "Tự động". */
    fun setCover(photoId: String?) = viewModelScope.launch { c.setTripCover(tripId, photoId) }

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
    var pickCover by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val transfer = rememberTransfer(snackbar)
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
                    IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = onShare) { Icon(TopoIcons.Share, stringResource(R.string.share_trip)) }
                        IconButton(onClick = { menu = true }) { Icon(TopoIcons.More, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trip_edit_title)) },
                                leadingIcon = { Icon(TopoIcons.Edit, null) },
                                onClick = { menu = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trip_cover_change)) },
                                leadingIcon = { Icon(TopoIcons.Photo, null) },
                                onClick = { menu = false; pickCover = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gpx_export)) },
                                leadingIcon = { Icon(TopoIcons.Route, null) },
                                onClick = { menu = false; detail?.trip?.id?.let(transfer.exportGpx) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trip_delete_button)) },
                                leadingIcon = { Icon(TopoIcons.Delete, null) },
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
                ChipFab(fabLabel, TopoIcons.Add, onClick = { fabMenu = true }, expanded = fabExpanded)
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trip_add_step)) },
                        leadingIcon = { Icon(TopoIcons.AddStep, null) },
                        onClick = { fabMenu = false; onAddStep() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.from_photos_title)) },
                        leadingIcon = { Icon(TopoIcons.AddPhotos, null) },
                        onClick = { fabMenu = false; onAddFromPhotos() },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            Loadable.Missing -> MessageState(stringResource(R.string.not_found), "", Modifier.padding(padding))
            is Loadable.Failed -> MessageState(stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
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
        LegModeSheet(leg, detail.measure(leg), onPick = { vm.setLegMode(leg.to.id, it); editLegTo = null }, onDismiss = { editLegTo = null })
    }

    if (pickCover && detail != null) {
        CoverPickerSheet(detail.steps, detail.trip.coverPhotoId, onPick = { vm.setCover(it); pickCover = false }, onDismiss = { pickCover = false })
    }

    if (confirmDelete && detail != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.trip_delete_confirm_title),
            // Every step goes, drafts too (the readout counts published steps only, R20).
            body = stringResource(R.string.trip_delete_confirm_body, detail.trip.title, detail.steps.size, detail.steps.sumOf { it.photos.size }),
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
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Space.XXL * 3), // clears the FAB (56 + 16 + 24)
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(PreviewFrame.HEIGHT_DP.dp)) {
                TripMap(markers = d.steps.toMarkers(), legModes = d.legModes, legPaths = d.legPaths, routedBy = d.routedBy, interactive = false, fitPaddingDp = PreviewFrame.PADDING_DP,
                    // Credit at the bottom and the expand button at the top right must not hide a pin.
                    fitExtraBottomDp = PreviewFrame.EXTRA_BOTTOM_DP, fitExtraRightDp = PreviewFrame.EXTRA_RIGHT_DP,
                    modifier = Modifier.fillMaxSize(), tracks = d.trackLines,
                    stopSuggestions = d.stopMarkers(),
                )
                Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
                MapButton(TopoIcons.Expand, stringResource(R.string.trip_open_map), onOpenMap, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
        item { TripHeader(d) }
        item(key = "gps") { gpsCard() }
        if (d.steps.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(R.string.trip_no_steps_title),
                    body = stringResource(R.string.trip_no_steps_body),
                    actionLabel = stringResource(R.string.trip_add_step),
                    onAction = onAddStep,
                    modifier = Modifier.height(360.dp),
                )
            }
        } else {
            val lastNumber = d.numbers.values.maxOrNull() ?: 0
            itemsIndexed(d.steps, key = { _, s -> s.id }) { i, step ->
                val prevDay = d.steps.getOrNull(i - 1)?.arrivedAt?.toLocalDate()
                val day = step.arrivedAt.toLocalDate()
                d.legInto(step.id)?.let { leg -> LegRow(leg, d.measure(leg)) { onEditLeg(step.id) } }
                if (day != prevDay) {
                    val n = ChronoUnit.DAYS.between(d.trip.startDate, day) + 1
                    Eyebrow(
                        stringResource(R.string.day_label, n.toInt(), day.displayDay()),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                val number = d.numbers[step.id]
                StepRow(number, discRole(number, lastNumber), step, isLast = i == d.steps.lastIndex) { onOpenStep(step.id) }
            }
        }
    }
}

/**
 * The trip's header (docs/08 §5 "Trip header"), like the specimen's profile screen: the dates as
 * the eyebrow over the 28 bold title, the description, then the stats panel (km total, counts,
 * scale bar, road time) on card paper.
 */
@Composable
private fun TripHeader(d: TripDetail) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        val openEnded = stringResource(R.string.trip_open_ended, "%s")
        ScreenHeading(
            title = d.trip.title,
            eyebrow = dateRange(d.trip.startDate, d.trip.endDate) { openEnded.format(it) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (d.trip.description.isNotBlank()) {
            Text(d.trip.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp))
        }
        TopoPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            KmReadout(
                km = d.stats.distanceKm,
                kmUnit = stringResource(R.string.stat_km),
                kmNote = stringResource(
                    when (d.stats.distanceSource) {
                        DistanceSource.GPS -> R.string.stat_km_gps
                        DistanceSource.ROAD -> R.string.stat_km_road
                        DistanceSource.MIXED -> R.string.stat_km_mixed
                        DistanceSource.STRAIGHT_LINE -> R.string.stat_km_straight
                    },
                ),
                counts = buildList {
                    add(ReadoutCount(DisplayFormat.count(d.stats.stepCount.toLong()), stringResource(R.string.stat_steps)))
                    add(ReadoutCount(DisplayFormat.count(d.stats.photoCount.toLong()), stringResource(R.string.stat_photos)))
                    d.stats.days?.let { add(ReadoutCount(DisplayFormat.count(it.toLong()), stringResource(R.string.stat_days))) }
                },
            )
            // R36: the routers' travel-time estimate of the trip, partial when some legs have no road route.
            d.stats.durationS?.let { s ->
                val time = DisplayFormat.approxDuration(s)
                Text(
                    if (d.stats.legsWithoutTime > 0) stringResource(R.string.stat_time_partial, time, d.stats.legsWithoutTime)
                    else stringResource(R.string.stat_time, time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Between two timeline rows: the rail continues, with the leg's mode chip beside it. */
@Composable
private fun LegRow(leg: Leg, measure: LegMeasure, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).height(48.dp), Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        LegChip(leg, measure, onClick, Modifier.padding(start = 8.dp))
    }
}

/** Which pin a timeline disc repeats: the map's start (trail green), stop (waypoint blue) or end (forest). */
private enum class DiscRole { Start, Stop, End }

private fun discRole(number: Int?, lastNumber: Int): DiscRole = when {
    number == null || lastNumber < 2 -> DiscRole.Stop
    number == 1 -> DiscRole.Start
    number == lastNumber -> DiscRole.End
    else -> DiscRole.Stop
}

/**
 * Timeline row: on the rail, a numbered disc in its pin colour (white ring, like the map pins);
 * beside it a card (specimen `.card`) with title, time, note excerpt and thumbnails. A draft
 * (R20, [number] null) has a hollow disc, a "Nháp" badge and muted content.
 */
@Composable
private fun StepRow(number: Int?, role: DiscRole, step: Step, isLast: Boolean, onClick: () -> Unit) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).drawBehind {
            // The rail runs from under the disc to the row's bottom, whatever the card's height.
            if (!isLast) {
                val x = 16.dp.toPx()
                drawLine(rail, Offset(x, 30.dp.toPx()), Offset(x, size.height), 2.dp.toPx())
            }
        },
    ) {
        Box(Modifier.width(32.dp).padding(top = 12.dp), Alignment.TopCenter) {
            val ring = MaterialTheme.colorScheme.surfaceContainerLowest
            if (number == null) {
                Box(
                    Modifier.size(28.dp).background(ring, CircleShape).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    Alignment.Center,
                ) { Icon(TopoIcons.Note, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val fill = when (role) {
                    DiscRole.Start -> Palette.Trail
                    DiscRole.Stop -> Palette.Waypoint
                    DiscRole.End -> Palette.Forest
                }
                Box(
                    Modifier.size(28.dp).background(ring, CircleShape).padding(2.dp).background(fill, CircleShape),
                    Alignment.Center,
                ) { Text("$number", color = Palette.Card, style = MaterialTheme.typography.labelLarge.numerals()) }
            }
        }
        TopoCard(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp).weight(1f),
            onClick = onClick,
        ) {
            Column(Modifier.alpha(if (step.isDraft) 0.6f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (step.isDraft) DraftBadge(Modifier.padding(start = 8.dp))
                }
                Text(step.arrivedAt.displayTime(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (step.note.isNotBlank()) {
                    Text(step.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                if (step.photos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(56.dp)) {
                        step.photos.take(4).forEach { p ->
                            LocalMedia(p, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                        }
                        if (step.photos.size > 4) {
                            Box(
                                Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
                                Alignment.Center,
                            ) { Text("+${step.photos.size - 4}", style = MaterialTheme.typography.titleMedium.numerals()) }
                        }
                    }
                }
            }
        }
    }
}
