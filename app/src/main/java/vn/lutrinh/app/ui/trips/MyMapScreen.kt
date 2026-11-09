package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.OverallStats
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.usecase.overallStatsOf
import vn.lutrinh.app.domain.usecase.published
import vn.lutrinh.app.domain.usecase.routeOf
import vn.lutrinh.app.domain.usecase.tripColorSlots
import vn.lutrinh.app.map.AllTripsMap
import vn.lutrinh.app.map.MapTrip
import vn.lutrinh.app.map.TrackLine
import vn.lutrinh.app.domain.usecase.TrackMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.ui.common.BasemapFallbackNotice
import vn.lutrinh.app.ui.common.MapStyleButton
import vn.lutrinh.app.ui.common.MyLocationButton
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.rememberMyLocation
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.hairline
import vn.lutrinh.app.ui.theme.numerals

/** Trip colours on "Bản đồ của tôi" (R14): the 8 data colours of docs/08, chàm first. */
private val TripPalette = Palette.TripColors

data class MyMapTrip(val summary: TripSummary, val color: Color, val steps: List<Step>, val distanceKm: Double) {
    val id: String get() = summary.trip.id
}

data class MyMap(
    val trips: List<MyMapTrip>,
    val stats: OverallStats,
    /** Every recorded GPS track (R16), one line per segment. */
    val tracks: List<TrackLine> = emptyList(),
)

class MyMapViewModel(c: AppContainer) : ViewModel() {
    private val trackLines = c.tracks.observeAllPoints().map { points ->
        points.groupBy { it.trackId }.flatMap { (id, pts) -> TrackMath.displayLines(pts).map { TrackLine(id, it) } }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<Loadable<MyMap>> =
        combine(c.trips.observeTripSummaries(), c.steps.observeAllSteps(), trackLines) { summaries, steps, tracks ->
            val byTrip = steps.published().groupBy { it.tripId } // R20: drafts are not on My map
            val slots = tripColorSlots(summaries.map { it.trip }, TripPalette.size)
            val trips = summaries.map { s ->
                val own = byTrip[s.trip.id].orEmpty()
                MyMapTrip(s, TripPalette[slots.getValue(s.trip.id)], own, Geo.routeLengthKm(routeOf(own)))
            }
            Loadable.Ready(MyMap(trips, overallStatsOf(summaries.map { it.trip }, steps), tracks)) as Loadable<MyMap>
        }
            .catch { emit(Loadable.Failed(it.message ?: it.javaClass.simpleName)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}

/**
 * "Bản đồ của tôi": all trips on one map, each in its own colour, with overall totals.
 * Tap a line/dot to select a trip (its card scrolls into view); tap a card to open the trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMapScreen(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    vm: MyMapViewModel = viewModel(factory = factory { c, _ -> MyMapViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var cardsDp by remember { mutableIntStateOf(0) }
    var statsDp by remember { mutableIntStateOf(0) }
    val data = (state as? Loadable.Ready)?.value
    val snackbar = remember { SnackbarHostState() }
    val me = rememberMyLocation(snackbar)

    LaunchedEffect(selected, data) {
        val idx = data?.trips?.indexOfFirst { it.id == selected } ?: -1
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s is Loadable.Failed) {
            MessageState(stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
            return@Scaffold
        }
        if (data == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Box(Modifier.padding(padding).fillMaxSize()) {
            AllTripsMap(
                trips = data.trips.map { t ->
                    MapTrip(t.id, t.color, t.steps.map { it.location }, legsOf(t.steps).map { it.mode })
                },
                selectedId = selected,
                onTripClick = { selected = it },
                modifier = Modifier.fillMaxSize(),
                fitExtraTopDp = statsDp,
                fitExtraBottomDp = cardsDp,
                tracks = data.tracks,
                myLocation = me.fix,
                myLocationSignal = me.signal,
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = hairline,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()
                    .onSizeChanged { statsDp = with(density) { it.height.toDp().value.toInt() } },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Total("${data.stats.tripCount}", stringResource(R.string.my_map_trips), Modifier.weight(1f))
                    Total("${data.stats.stepCount}", stringResource(R.string.stat_steps), Modifier.weight(1f))
                    Total(DisplayFormat.kmNumber(data.stats.distanceKm), stringResource(R.string.stat_km), Modifier.weight(1.3f))
                }
            }
            if (data.trips.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = hairline,
                ) { Text(stringResource(R.string.my_map_empty), Modifier.padding(16.dp)) }
            }
            BasemapFallbackNotice(snackbar)
            // "Kiểu bản đồ" (R32) above "Vị trí của tôi".
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = cardsDp.dp + 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapStyleButton()
                MyLocationButton(me)
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { cardsDp = with(density) { it.height.toDp().value.toInt() } }
                    .navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                items(data.trips, key = { it.id }) { t -> TripMapCard(t, t.id == selected) { onOpenTrip(t.id) } }
            }
        }
    }
}

@Composable
private fun Total(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.numerals(), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A trip on "Bản đồ của tôi": white card on a hairline; the trip's colour is a short line sample
 * beside the title, the same stroke as on the map (a legend key, not a coloured edge). Selected =
 * a 2 dp chàm border.
 */
@Composable
private fun TripMapCard(t: MyMapTrip, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(250.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else hairline,
    ) {
        Column(Modifier.height(76.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(16.dp).height(3.dp).background(t.color, MaterialTheme.shapes.small))
                Text(
                    t.summary.trip.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            val openEnded = stringResource(R.string.trip_open_ended, "%s")
            Text(
                dateRange(t.summary.trip.startDate, t.summary.trip.endDate) { openEnded.format(it) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.my_map_trip_line, t.summary.stepCount, DisplayFormat.kmNumber(t.distanceKm)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
