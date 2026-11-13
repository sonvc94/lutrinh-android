package vn.lutrinh.app.ui.trips

import vn.lutrinh.app.domain.model.Dwell
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.LegMeasure
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.domain.routing.NearMe
import vn.lutrinh.app.domain.routing.NearMeFinder
import vn.lutrinh.app.domain.routing.NearMeState
import vn.lutrinh.app.domain.routing.NearMeSession
import vn.lutrinh.app.domain.routing.Origin
import vn.lutrinh.app.ui.picker.DeviceLocation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.CancellationSignal
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import vn.lutrinh.app.domain.usecase.StopMove
import vn.lutrinh.app.ui.common.MessageDialog
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import vn.lutrinh.app.domain.catalog.RouteLink
import vn.lutrinh.app.ui.routes.RouteBadge
import vn.lutrinh.app.ui.routes.RouteNoticeBanner
import vn.lutrinh.app.ui.routes.RouteSheet
import vn.lutrinh.app.ui.routes.TripRouteLink
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

/** LazyColumn item indexes of [TripDetailContent]: map, header, GPS card, the view switch (R45), then the timeline. */
private const val ITEM_HEADER = 1
private const val ITEM_GPS = 2

/** LazyColumn index of the first step's item (after the map, header, GPS card and view switch). */
private const val FIRST_STEP_ITEM = 4

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

/** R44: what the screen says after a drop. */
sealed interface MoveEvent {
    data class Moved(val stepId: String, val title: String, val arrival: java.time.LocalDateTime) : MoveEvent
    /** No whole minute fits there: nothing moved, the time edit opens after the message. */
    data class NoRoom(val stepId: String) : MoveEvent
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

    /** R36: the routes this trip is drawn with (none while routing is off; D-13: a downloaded route keeps its lines). */
    private val routes = combine(c.legRoutes.observeForTrip(tripId), c.routingSettings.enabled, c.routes.observeLink(tripId)) { stored, _, link ->
        c.routingSettings.shown(stored) { link != null }
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

    /** R40: the route this trip was downloaded from (badge, update sheet, banner); null link = an own trip. */
    val route = TripRouteLink(c, tripId, viewModelScope)

    /** R44: a stop dropped at a place that needs the user's pick of a leg first (null = none pending). */
    val pendingMove = MutableStateFlow<StopMove.Plan.Move?>(null)
    private val moveEventChannel = Channel<MoveEvent>(Channel.BUFFERED)
    val moveEvents = moveEventChannel.receiveAsFlow()

    /** R44: [stepId] dropped with [slot] other stops before it. */
    fun dropStop(stepId: String, slot: Int) = viewModelScope.launch {
        when (val p = c.moveStop.plan(tripId, stepId, slot)) {
            StopMove.Plan.Unchanged -> {}
            is StopMove.Plan.NoRoom -> moveEventChannel.send(MoveEvent.NoRoom(stepId))
            is StopMove.Plan.Move -> if (p.legs.needsChoice) pendingMove.value = p else commitMove(p, emptyList())
        }
    }

    fun commitMove(move: StopMove.Plan.Move, choices: List<Int?>) = viewModelScope.launch {
        pendingMove.value = null
        if (c.moveStop(move, choices)) moveEventChannel.send(MoveEvent.Moved(move.step.id, move.step.title, move.arrival))
    }

    fun cancelMove() { pendingMove.value = null }

    // R45 "Gần tôi": the stops by road distance from the user. The screen finds the position (permission,
    // GPS) and answers [locateRequests] with [onOrigin]; the result stays in memory while the stops are unchanged.
    private val locateChannel = Channel<Unit>(Channel.CONFLATED)
    val locateRequests = locateChannel.receiveAsFlow()
    private val near = NearMeSession(
        viewModelScope, NearMeFinder(c.routers, c.clock, log = { android.util.Log.i("NearMe", it) }),
        // The user's position and the stops go to the routing server: only with routing on and its notice accepted.
        consent = { c.routingSettings.enabled.value && c.routingSettings.noticeSeen.value },
        online = { c.network.online.value },
        locate = { locateChannel.trySend(Unit) },
    )
    val nearMe: StateFlow<NearMeState> = near.state

    /** The user opened "Gần tôi" ([force] = "Thử lại" / "Làm mới": a new position, a new calculation). */
    fun openNearMe(detail: TripDetail, force: Boolean = false) = near.open(NearMe.rowsOf(detail.steps, detail.numbers), force)

    /** "Đồng ý và tính đường": routing on, the notice counted as shown (the view showed the same words). */
    fun acceptNearMeConsent(detail: TripDetail) {
        c.routingSettings.setEnabled(true)
        c.routingSettings.markNoticeSeen()
        openNearMe(detail, force = true)
    }

    /** The screen's answer to a [locateRequests] entry. */
    fun onOrigin(origin: Origin) = near.onOrigin(origin)

    fun setLegMode(toStepId: String, mode: TransportMode?) = viewModelScope.launch { c.setLegTransport(toStepId, mode) }
    fun setLegNote(toStepId: String, note: String) = viewModelScope.launch { c.setLegNote(toStepId, note) }

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
    /** R44: the step's edit form (the time edit after a drop, or a drop with no room). */
    onEditStep: (String) -> Unit = {},
    /** "Tạo chuyến mới và ghi" (R16, ended trips): the new-trip form, then recording on the new trip. */
    onNewTripAndRecord: () -> Unit = {},
    onOpenBatteryGuide: () -> Unit = {},
    /** Opened by "Tạo chuyến mới và ghi": start recording on arrival. */
    autoRecord: Boolean = false,
    /** R40: opened right after a route download ("map" = an offline map is coming, "nomap" = offer one). */
    fromRoute: String? = null,
    onOpenOffline: () -> Unit = {},
    /** R40 "Tải thành chuyến đi mới" / "Thay bản trong máy": the code screen (with the code), or its download. */
    onEnterCode: (String) -> Unit = {},
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
    val routeLink by vm.route.link.collectAsStateWithLifecycle()
    val routeSheet by vm.route.sheet.collectAsStateWithLifecycle()
    val pendingMove by vm.pendingMove.collectAsStateWithLifecycle()
    var noRoomStepId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var stopView by rememberSaveable { mutableStateOf(StopView.Order) }
    val nearState by vm.nearMe.collectAsStateWithLifecycle()
    var locateSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(Unit) { onDispose { locateSignal?.cancel() } }
    fun fix() {
        if (!DeviceLocation.isEnabled(context)) { vm.onOrigin(Origin.LocationOff); return }
        locateSignal = DeviceLocation.request(context) { p -> vm.onOrigin(if (p != null) Origin.Fix(p) else Origin.NoFix) }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (DeviceLocation.hasPermission(context)) fix() else vm.onOrigin(Origin.NoPermission)
    }
    LaunchedEffect(Unit) {
        vm.locateRequests.collect { if (DeviceLocation.hasPermission(context)) fix() else locationPermission.launch(DeviceLocation.PERMISSIONS) }
    }
    val movedEdit = stringResource(R.string.stop_moved_edit)
    LaunchedEffect(Unit) {
        vm.moveEvents.collect { e ->
            when (e) {
                is MoveEvent.NoRoom -> noRoomStepId = e.stepId
                is MoveEvent.Moved -> {
                    snackbar.currentSnackbarData?.dismiss()
                    launch {
                        val r = snackbar.showSnackbar(
                            context.getString(R.string.stop_moved, e.title, e.arrival.displayTime()), actionLabel = movedEdit, duration = SnackbarDuration.Long,
                        )
                        if (r == SnackbarResult.ActionPerformed) onEditStep(e.stepId)
                    }
                }
            }
        }
    }
    var routeAddedShown by rememberSaveable { mutableStateOf(false) }
    val addedMsg = stringResource(R.string.route_added)
    val addedMap = stringResource(R.string.route_added_map)
    LaunchedEffect(fromRoute) {
        if (fromRoute == null || routeAddedShown) return@LaunchedEffect
        routeAddedShown = true
        val r = snackbar.showSnackbar(addedMsg, actionLabel = if (fromRoute == "nomap") addedMap else null, duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) onOpenOffline()
    }
    val detail = (state as? Loadable.Ready)?.value
    // R45: opening "Gần tôi" (or a change of stops while it is open) starts the calculation; the same stops do not redo it.
    LaunchedEffect(stopView, detail?.steps) { if (stopView == StopView.Near && detail != null) vm.openNearMe(detail) }
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
                onDropStop = vm::dropStop,
                stopView = stopView,
                onStopView = { stopView = it },
                nearState = nearState,
                onNearRetry = { vm.openNearMe(s.value, force = true) },
                onNearConsent = { vm.acceptNearMeConsent(s.value) },
                routeLink = routeLink, onRouteBadge = vm.route::openSheet, onCloseRouteNotice = vm.route::closeNotice,
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
        LegModeSheet(
            leg, detail.measure(leg),
            onPick = { vm.setLegMode(leg.to.id, it); editLegTo = null },
            onSaveNote = { vm.setLegNote(leg.to.id, it); editLegTo = null },
            onDismiss = { editLegTo = null },
        )
    }

    pendingMove?.let { move ->
        LegChoiceSheet(
            move.legs, cancelLabel = stringResource(R.string.leg_choice_cancel_move),
            onDone = { vm.commitMove(move, it) }, onCancel = vm::cancelMove,
        )
    }
    noRoomStepId?.let { id ->
        MessageDialog(
            title = stringResource(R.string.stop_move_no_room_title),
            body = stringResource(R.string.stop_move_no_room_body, detail?.steps?.firstOrNull { it.id == id }?.title.orEmpty()),
            closeLabel = stringResource(R.string.stop_move_no_room_edit),
            onClose = { noRoomStepId = null; onEditStep(id) },
        )
    }

    if (pickCover && detail != null) {
        CoverPickerSheet(detail.steps, detail.trip.coverPhotoId, onPick = { vm.setCover(it); pickCover = false }, onDismiss = { pickCover = false })
    }

    routeSheet?.let { sheet ->
        routeLink?.let { link ->
            RouteSheet(
                link, sheet.change,
                onNewTrip = { vm.route.closeSheet(); onEnterCode(link.code.value) },
                onReplace = { detail?.let { if (vm.route.replace(it.trip)) onEnterCode(link.code.value) } },
                onLater = vm.route::later,
                onDismiss = vm.route::closeSheet,
            )
        }
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
    onDropStop: (stepId: String, slot: Int) -> Unit,
    stopView: StopView,
    onStopView: (StopView) -> Unit,
    nearState: NearMeState,
    onNearRetry: () -> Unit,
    onNearConsent: () -> Unit,
    routeLink: RouteLink?,
    onRouteBadge: () -> Unit,
    onCloseRouteNotice: () -> Unit,
    gpsCard: @Composable () -> Unit,
) {
    val drag = rememberStopDrag(listState, d.steps, onDropStop)
    val moveUp = stringResource(R.string.stop_move_up)
    val moveDown = stringResource(R.string.stop_move_down)
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
        item { TripHeader(d, routeLink, onRouteBadge, onCloseRouteNotice) }
        item(key = "gps") { gpsCard() }
        // R45: the same stops in order (the timeline, drag to reorder) or by road distance from the user.
        item(key = "view") {
            if (d.numbers.isNotEmpty()) StopViewSwitch(stopView, onStopView, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (stopView == StopView.Near && d.numbers.isNotEmpty()) {
            nearMeItems(nearState, onOpenStep, onNearRetry, onNearConsent)
        } else if (d.steps.isEmpty()) {
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
                if (drag.lineBefore == step.id) DropLine()
                d.legInto(step.id)?.let { leg -> LegRow(leg, d.measure(leg)) { onEditLeg(step.id) } }
                if (day != prevDay) {
                    val n = ChronoUnit.DAYS.between(d.trip.startDate, day) + 1
                    Eyebrow(
                        stringResource(R.string.day_label, n.toInt(), day.displayDay()),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                val number = d.numbers[step.id]
                val isDragged = drag.id == step.id
                val actions = buildList {
                    if (i > 0) add(CustomAccessibilityAction(moveUp) { onDropStop(step.id, i - 1); true })
                    if (i < d.steps.lastIndex) add(CustomAccessibilityAction(moveDown) { onDropStop(step.id, i + 1); true })
                }
                StepRow(
                    number, discRole(number, lastNumber), step, isLast = i == d.steps.lastIndex,
                    modifier = Modifier
                        .semantics { customActions = actions }
                        .then(
                            if (isDragged) Modifier.zIndex(1f).graphicsLayer { translationY = drag.offsetY; scaleX = 1.03f; scaleY = 1.03f; alpha = 0.95f }
                            else Modifier,
                        )
                        .pointerInput(step.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag.start(step.id) },
                                onDragEnd = { drag.end() },
                                onDragCancel = { drag.cancel() },
                                onDrag = { change, amount -> change.consume(); drag.move(amount.y, 32.dp.toPx()) },
                            )
                        },
                ) { onOpenStep(step.id) }
                if (drag.lineAfterLast == step.id) DropLine()
            }
        }
    }
}

/**
 * R44: the long-press drag of a stop in the timeline. While it is held the stop follows the finger
 * and a line shows where it will go (between the two stops whose middles are above/below it); the
 * list scrolls when the finger is near an edge. Nothing is stored until release: [onDrop] gets the
 * stop and the number of OTHER stops that come before the drop place.
 */
@Stable
private class StopDrag(
    private val list: LazyListState,
    private val steps: () -> List<Step>,
    private val onDrop: (String, Int) -> Unit,
) {
    var id by mutableStateOf<String?>(null); private set
    var offsetY by mutableFloatStateOf(0f); private set
    private var slot by mutableIntStateOf(-1)

    /** The step whose item the drop line is drawn above, or the last step (line below it). */
    val lineBefore: String?
        get() = id?.let { me ->
            val others = steps().filter { it.id != me }
            if (slot < 0 || slot == currentSlot(me)) null else others.getOrNull(slot)?.id
        }
    val lineAfterLast: String?
        get() = id?.let { me ->
            val others = steps().filter { it.id != me }
            if (slot < 0 || slot == currentSlot(me) || slot < others.size) null else others.lastOrNull()?.id
        }

    private fun currentSlot(me: String) = steps().indexOfFirst { it.id == me }

    fun start(stepId: String) { id = stepId; offsetY = 0f; slot = currentSlot(stepId) }

    fun move(dy: Float, edgePx: Float) {
        val me = id ?: return
        val layout = list.layoutInfo
        val mine = layout.visibleItemsInfo.firstOrNull { it.key == me } ?: return
        offsetY += dy
        // Near an edge the list follows the finger; the stop keeps its place under it.
        val top = mine.offset + offsetY
        val scroll = when {
            dy > 0 && top + mine.size > layout.viewportEndOffset - edgePx -> dy
            dy < 0 && top < layout.viewportStartOffset + edgePx -> dy
            else -> 0f
        }
        if (scroll != 0f) offsetY += list.dispatchRawDelta(scroll)
        slot = slotAt(me)
    }

    /** Other stops whose item middle is above the dragged stop's middle (off-screen ones by their index). */
    private fun slotAt(me: String): Int {
        val layout = list.layoutInfo
        val visible = layout.visibleItemsInfo
        val mine = visible.firstOrNull { it.key == me } ?: return slot
        val centre = mine.offset + mine.size / 2f + offsetY
        val byKey: Map<Any, LazyListItemInfo> = visible.associateBy { it.key }
        val firstVisible = visible.firstOrNull()?.index ?: 0
        return steps().withIndex().count { (i, s) ->
            s.id != me && (byKey[s.id]?.let { it.offset + it.size / 2f < centre } ?: (FIRST_STEP_ITEM + i < firstVisible))
        }
    }

    fun end() {
        val me = id
        val target = slot
        cancel()
        if (me != null && target >= 0) onDrop(me, target)
    }

    fun cancel() { id = null; offsetY = 0f; slot = -1 }
}

@Composable
private fun rememberStopDrag(list: LazyListState, steps: List<Step>, onDrop: (String, Int) -> Unit): StopDrag {
    val latestSteps by rememberUpdatedState(steps)
    val latestDrop by rememberUpdatedState(onDrop)
    return remember(list) { StopDrag(list, { latestSteps }, { id, slot -> latestDrop(id, slot) }) }
}

/** The line that shows where a dragged stop will land (the route's Signal orange, 4 dp). */
@Composable
private fun DropLine() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).background(Palette.Signal, CircleShape))
}

/**
 * The trip's header (docs/08 §5 "Trip header"), like the specimen's profile screen: the dates as
 * the eyebrow over the 28 bold title, the description, then the stats panel (km total, counts,
 * scale bar, road time) on card paper.
 */
@Composable
private fun TripHeader(d: TripDetail, routeLink: RouteLink?, onRouteBadge: () -> Unit, onCloseRouteNotice: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        val openEnded = stringResource(R.string.trip_open_ended, "%s")
        ScreenHeading(
            title = d.trip.title,
            eyebrow = dateRange(d.trip.startDate, d.trip.endDate) { openEnded.format(it) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        // R40: the only thing that sets a downloaded trip apart (page 05 screen 6); tap = credits + update check.
        routeLink?.let { RouteBadge(it, onRouteBadge, Modifier.padding(start = 8.dp, top = 8.dp)) }
        routeLink?.let { RouteNoticeBanner(it, onCloseRouteNotice, Modifier.padding(top = 12.dp)) }
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
                    add(ReadoutCount(DisplayFormat.count((d.stats.photoCount - d.stats.videoCount).toLong()), stringResource(R.string.stat_photos)))
                    if (d.stats.videoCount > 0) add(ReadoutCount(DisplayFormat.count(d.stats.videoCount.toLong()), "video"))
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

/**
 * Between two timeline rows: the rail continues, with the leg's mode chip beside it and, under
 * the chip, the leg's note (R41: two lines, "Xem thêm" opens the rest).
 */
@Composable
private fun LegRow(leg: Leg, measure: LegMeasure, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).fillMaxHeight().heightIn(min = 48.dp), Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            LegChip(leg, measure, onClick)
            leg.note?.let { LegNote(it, Modifier.padding(bottom = 8.dp)) }
        }
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
private fun StepRow(number: Int?, role: DiscRole, step: Step, isLast: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).drawBehind {
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
                            LocalMedia(p, Modifier.size(56.dp).clip(MaterialTheme.shapes.small), compactMark = true)
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
