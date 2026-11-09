package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.displayDateTime
import vn.lutrinh.app.ui.common.factory

/** Full-screen trip map: numbered pins + route, a card carousel synced with the selected pin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    onBack: () -> Unit,
    onOpenStep: (String) -> Unit,
    onOpenOffline: () -> Unit,
    /** "Tạo chuyến mới và ghi" (R16, ended trips). */
    onNewTripAndRecord: () -> Unit = {},
    vm: TripDetailViewModel = viewModel(factory = factory { c, h -> TripDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var editLegTo by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val detail = (state as? Loadable.Ready)?.value
    val density = LocalDensity.current
    var carouselDp by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected, detail) {
        val idx = detail?.steps?.indexOfFirst { it.id == selected } ?: -1
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.trip?.title ?: stringResource(R.string.trip_map_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onOpenOffline) { Icon(Icons.Outlined.DownloadForOffline, stringResource(R.string.offline_title)) }
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
                modifier = Modifier.fillMaxSize(),
                fitPaddingDp = 48,
                // Cards height + their bottom offset, so the fit keeps every pin above the cards.
                fitExtraBottomDp = carouselDp,
                // The leg whose mode sheet is open is highlighted and framed above the sheet.
                selectedLegTo = editLegTo,
                onMarkerClick = { selected = it },
                onLegClick = { editLegTo = it },
                tracks = detail.trackLines,
                stopSuggestions = detail.stopMarkers(),
            )
            // "Ghi hành trình" (R16) floats top-right; the offline indicator sits top-left.
            RecordTrackControl(
                tripId = detail.trip.id, active = recording,
                onPause = { vm.pauseRecording() }, onResume = { vm.resumeRecording() }, onStop = { vm.stopRecording() },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                compact = true,
                ended = !detail.canRecord, onNewTripAndRecord = onNewTripAndRecord,
            )
            if (detail.steps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    tonalElevation = 2.dp,
                ) { Text(stringResource(R.string.trip_map_empty), Modifier.padding(12.dp)) }
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Measured outside the insets/offset: the whole strip the cards take from the map.
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { carouselDp = with(density) { it.height.toDp().value.toInt() } }
                    .navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                itemsIndexed(detail.steps, key = { _, s -> s.id }) { i, step ->
                    val isSel = step.id == selected
                    Card(
                        modifier = Modifier.width(260.dp).clickable {
                            if (isSel) onOpenStep(step.id) else selected = step.id
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val cover = step.photos.firstOrNull()
                            if (cover != null) {
                                LocalPhoto(cover.fileName, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                            } else {
                                Box(Modifier.size(56.dp), Alignment.Center) { Icon(Icons.Outlined.Image, null) }
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(step.arrivedAt.displayDateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // The leg that ends here (R12); the first step has none.
                                if (i > 0) LegChip(detail.legs[i - 1], onClick = { editLegTo = step.id }, Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.legs?.firstOrNull { it.to.id == editLegTo }?.let { leg ->
        LegModeSheet(leg, onPick = { vm.setLegMode(leg.to.id, it); editLegTo = null }, onDismiss = { editLegTo = null })
    }
}
