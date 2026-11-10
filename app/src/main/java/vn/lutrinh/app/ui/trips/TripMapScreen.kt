package vn.lutrinh.app.ui.trips

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import vn.lutrinh.app.domain.usecase.TripDays
import vn.lutrinh.app.ui.common.stepSharedBounds
import vn.lutrinh.app.ui.theme.Motion
import vn.lutrinh.app.ui.theme.numerals
import vn.lutrinh.app.ui.common.MapButton
import vn.lutrinh.app.ui.theme.TopoIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.abs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import vn.lutrinh.app.ui.theme.Lift
import vn.lutrinh.app.ui.common.TopoPanel
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lutrinh.app.R
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.tracks.RecordTrackControl
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.DraftBadge
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.displayDateTime
import vn.lutrinh.app.ui.common.BasemapFallbackNotice
import vn.lutrinh.app.ui.common.MapStyleButton
import vn.lutrinh.app.ui.common.MyLocationButton
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.rememberMyLocation

private const val CARD_WIDTH_DP = 260

/** Full-screen trip map: numbered pins + route, a card carousel synced with the selected pin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    onBack: () -> Unit,
    onOpenStep: (String) -> Unit,
    onOpenOffline: () -> Unit,
    /** "Tạo chuyến mới và ghi" (R16, ended trips). */
    onNewTripAndRecord: () -> Unit = {},
    onOpenBatteryGuide: () -> Unit = {},
    vm: TripDetailViewModel = viewModel(factory = factory { c, h -> TripDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    // R37: the card/pin selected at once, and the step the camera moves to once the cards stop.
    // Saved, so coming back from the step detail lands on the same card and camera (reverse expand).
    var focus by rememberSaveable(stateSaver = CarouselFocus.Saver) { mutableStateOf(CarouselFocus()) }
    val selected = focus.selected
    var programmaticScroll by remember { mutableStateOf(false) }
    var editLegTo by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val detail = (state as? Loadable.Ready)?.value
    val density = LocalDensity.current
    var carouselDp by remember { mutableIntStateOf(0) }

    var fitAll by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val me = rememberMyLocation(snackbar)

    // A pin or card tap selects a step: its card is centred, then (R37) the camera follows.
    LaunchedEffect(selected, detail) {
        val idx = detail?.steps?.indexOfFirst { it.id == selected } ?: -1
        if (idx >= 0) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(idx)
                focus = focus.scrolledTo()
            } finally {
                programmaticScroll = false
            }
        }
    }
    // F5/R37: when the user's swipe settles (the fling snaps to a card), the centred card becomes
    // the selected step and the camera moves; our own scrolls above are not swipes.
    LaunchedEffect(listState) {
        var swiped = false
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (moving) swiped = true
            else if (swiped) {
                swiped = false
                if (!programmaticScroll) {
                    val info = listState.layoutInfo
                    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val centred = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.key as? String
                    focus = focus.settled(centred)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(detail?.trip?.title ?: stringResource(R.string.trip_map_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onOpenOffline) { Icon(TopoIcons.Download, stringResource(R.string.offline_title)) }
                },
            )
        },
    ) { padding ->
        if (detail == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Box(Modifier.padding(padding).fillMaxSize()) {
            TripMap(
                markers = detail.steps.toMarkers(selected),
                legModes = detail.legModes,
                legPaths = detail.legPaths,
                routedBy = detail.routedBy,
                modifier = Modifier.fillMaxSize(),
                fitPaddingDp = 48,
                // Cards height + their bottom offset, so the fit keeps every pin above the cards.
                fitExtraBottomDp = carouselDp,
                // The leg whose mode sheet is open is highlighted and framed above the sheet.
                selectedLegTo = editLegTo,
                onMarkerClick = { focus = focus.pick(it) },
                onLegClick = { editLegTo = it },
                tracks = detail.trackLines,
                stopSuggestions = detail.stopMarkers(),
                fitAllSignal = fitAll,
                myLocation = me.fix,
                myLocationSignal = me.signal,
                focusStepId = focus.camera,
                showTravelledLeg = true,
            )
            BasemapFallbackNotice(snackbar)
            // "Ghi hành trình" (R16) floats top-right; the offline indicator sits top-left.
            RecordTrackControl(
                tripId = detail.trip.id, active = recording,
                onPause = { vm.pauseRecording() }, onResume = { vm.resumeRecording() }, onStop = { vm.stopRecording() },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                compact = true,
                ended = !detail.canRecord, onNewTripAndRecord = onNewTripAndRecord,
                onOpenBatteryGuide = onOpenBatteryGuide,
            )
            // R37: "Ngày N/M" of the step the camera shows; the number flips when the day changes.
            val shown = detail.steps.firstOrNull { it.id == focus.camera }
            AnimatedVisibility(
                visible = shown != null,
                enter = fadeIn(tween(Motion.DAY_FLIP_MS)),
                exit = fadeOut(tween(Motion.DAY_FLIP_MS)),
                // Bottom left, just above the cards (the top holds the recording control and the offline note).
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = carouselDp.dp + 12.dp),
            ) {
                val days = remember(detail) { detail.steps.map { it.arrivedAt.toLocalDate() } }
                var last by remember { mutableIntStateOf(1) }
                if (shown != null) last = TripDays.dayOf(detail.trip.startDate, shown.arrivedAt.toLocalDate())
                DayCounter(last, TripDays.total(detail.trip.startDate, detail.trip.endDate, days))
            }
            if (detail.steps.isEmpty()) {
                TopoPanel(Modifier.align(Alignment.TopCenter).padding(16.dp), floating = true) {
                    Text(stringResource(R.string.trip_map_empty))
                }
            }
            // "Vị trí của tôi" above "Xem toàn bộ hành trình" (clears the selection, flies back to the whole route).
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = carouselDp.dp + 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "Kiểu bản đồ" (R32): "Bản đồ" / "Vệ tinh".
                MapStyleButton()
                MyLocationButton(me)
                if (detail.steps.size > 1 || selected != null) {
                    MapButton(TopoIcons.Fit, stringResource(R.string.trip_map_fit), onClick = { focus = focus.pick(null); fitAll++ })
                }
            }
            // One card is centred and snapped to; the padding lets the first and last one reach the centre.
            val sidePadding = ((LocalConfiguration.current.screenWidthDp - CARD_WIDTH_DP) / 2).coerceAtLeast(16).dp
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Measured outside the insets/offset: the whole strip the cards take from the map.
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { carouselDp = with(density) { it.height.toDp().value.toInt() } }
                    .navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                itemsIndexed(detail.steps, key = { _, s -> s.id }) { i, step ->
                    val isSel = step.id == selected
                    // A card over the map (docs/08 §5 "Map cards"): card paper, 18 dp, the chip shadow;
                    // the selected one has a 2 dp forest edge.
                    Surface(
                        onClick = { if (isSel) onOpenStep(step.id) else focus = focus.pick(step.id) },
                        // R37: opening the card grows it into the step detail (and Back shrinks it).
                        modifier = Modifier.width(CARD_WIDTH_DP.dp).padding(bottom = 6.dp)
                            .stepSharedBounds(step.id, MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = Lift.Chip,
                        border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val cover = step.photos.firstOrNull()
                                if (cover != null) {
                                    LocalPhoto(cover.fileName, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                                } else {
                                    Box(
                                        Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
                                        Alignment.Center,
                                    ) { Icon(TopoIcons.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Column(Modifier.padding(start = 12.dp)) {
                                    // R20: a draft has no number and says "Nháp".
                                    val n = detail.numbers[step.id]
                                    Text(if (n != null) "$n. ${step.title}" else step.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(step.arrivedAt.displayDateTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Same row height on every card (F23): the leg that ends here (R12), or "Điểm xuất phát" on the first.
                            Row(Modifier.fillMaxWidth().height(32.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                val leg = detail.legInto(step.id)
                                if (leg != null) LegChip(leg, detail.measure(leg), onClick = { editLegTo = step.id }, Modifier.height(32.dp))
                                else if (step.isDraft) DraftBadge()
                                else Text(stringResource(R.string.trip_map_start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                // F5: the selected card says how to open it.
                                if (isSel) {
                                    Text(
                                        stringResource(R.string.trip_map_details) + " ›",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.legs?.firstOrNull { it.to.id == editLegTo }?.let { leg ->
        LegModeSheet(leg, detail.measure(leg), onPick = { vm.setLegMode(leg.to.id, it); editLegTo = null }, onDismiss = { editLegTo = null })
    }
}

/** R37: "Ngày N/M" over the trip map; N slides up to a later day and down to an earlier one. */
@Composable
private fun DayCounter(day: Int, total: Int) {
    val description = stringResource(R.string.trip_map_day_cd, day, total)
    Surface(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = Lift.Chip,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.trip_map_day), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            AnimatedContent(
                targetState = day,
                transitionSpec = {
                    val up = targetState > initialState
                    val slide = tween<IntOffset>(Motion.DAY_FLIP_MS, easing = Motion.EaseInOut)
                    val fade = tween<Float>(Motion.DAY_FLIP_MS, easing = Motion.EaseInOut)
                    (slideInVertically(slide) { h -> if (up) h else -h } + fadeIn(fade)) togetherWith
                        (slideOutVertically(slide) { h -> if (up) -h else h } + fadeOut(fade)) using SizeTransform(clip = true)
                },
                label = "day",
            ) { n -> Text("$n", style = MaterialTheme.typography.labelLarge.numerals()) }
            Text("/$total", style = MaterialTheme.typography.labelLarge.numerals(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
