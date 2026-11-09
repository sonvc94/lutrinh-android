package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.ExperimentalFoundationApi
import vn.lutrinh.app.ui.common.FlatFab
import vn.lutrinh.app.ui.theme.AtlasIcons
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.BuildConfig
import vn.lutrinh.app.R
import vn.lutrinh.app.data.SampleData
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.usecase.routeOf
import vn.lutrinh.app.ui.common.RouteCover
import vn.lutrinh.app.ui.theme.hairline
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.transfer.rememberTransfer

class TripListViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<Loadable<List<TripSummary>>> = c.trips.observeTripSummaries()
        .map<List<TripSummary>, Loadable<List<TripSummary>>> { Loadable.Ready(it) }
        .catch { emit(Loadable.Failed(it.message ?: it.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Each trip's published steps in timeline order: the cover of a trip without photos (docs/08). */
    val routes: StateFlow<Map<String, List<GeoPoint>>> = c.steps.observeAllSteps()
        .map { steps -> steps.groupBy { it.tripId }.mapValues { (_, own) -> routeOf(own) } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun delete(id: String) = viewModelScope.launch { c.deleteTrip(id) }

    suspend fun seed() = SampleData.seed(c)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    /** "Chia sẻ chuyến đi" (R18) from a card's menu. */
    onShare: (String) -> Unit,
    onOpenMyMap: () -> Unit,
    onOpenOffline: () -> Unit,
    vm: TripListViewModel = viewModel(factory = factory { c, _ -> TripListViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TripSummary?>(null) }
    val transfer = rememberTransfer(snackbar, onOpenTrip = onOpen)
    val seededMsg = stringResource(R.string.trips_seeded)
    val seed: () -> Unit = {
        scope.launch {
            vm.seed()
            snackbar.showSnackbar(seededMsg)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.trips_title), fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMyMap) { Icon(AtlasIcons.Atlas, stringResource(R.string.my_map_title)) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AtlasIcons.More, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.offline_title)) },
                            leadingIcon = { Icon(AtlasIcons.Download, null) },
                            onClick = { menuOpen = false; onOpenOffline() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.gpx_import)) },
                            leadingIcon = { Icon(AtlasIcons.FileOpen, null) },
                            onClick = { menuOpen = false; transfer.importGpx() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_title)) },
                            leadingIcon = { Icon(AtlasIcons.Upload, null) },
                            onClick = { menuOpen = false; transfer.backup() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_restore)) },
                            leadingIcon = { Icon(AtlasIcons.Restore, null) },
                            onClick = { menuOpen = false; transfer.restore() },
                        )
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trips_seed)) },
                                onClick = { menuOpen = false; seed() },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            val fabLabel = stringResource(R.string.trips_new)
            FlatFab(fabLabel, AtlasIcons.Add, onClick = onCreate)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            is Loadable.Failed -> MessageState(
                stringResource(R.string.state_error, s.message), "",
                Modifier.padding(padding),
            )
            Loadable.Missing -> Unit
            is Loadable.Ready -> if (s.value.isEmpty()) {
                MessageState(
                    title = stringResource(R.string.trips_empty_title),
                    body = stringResource(R.string.trips_empty_body),
                    modifier = Modifier.padding(padding),
                    actionLabel = if (BuildConfig.DEBUG) stringResource(R.string.trips_seed) else stringResource(R.string.trips_new),
                    onAction = if (BuildConfig.DEBUG) seed else onCreate,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.value, key = { it.trip.id }) { summary ->
                        TripCard(summary, routes[summary.trip.id].orEmpty(), onOpen = { onOpen(summary.trip.id) }, onEdit = { onEdit(summary.trip.id) }, onShare = { onShare(summary.trip.id) }, onExportGpx = { transfer.exportGpx(summary.trip.id) }, onDelete = { toDelete = summary })
                    }
                }
            }
        }
    }

    toDelete?.let { t ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.trip_delete_confirm_title),
            // Drafts go too (R20): the card counts published steps only.
            body = stringResource(R.string.trip_delete_confirm_body, t.trip.title, t.stepCount + t.draftCount, t.photoCount + t.draftPhotoCount),
            onConfirm = { vm.delete(t.trip.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripCard(summary: TripSummary, route: List<GeoPoint>, onOpen: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onExportGpx: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = hairline,
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            val cover = summary.coverPhotoFile
            if (cover != null) {
                LocalPhoto(cover, Modifier.fillMaxSize())
            } else {
                RouteCover(route, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val openEnded = stringResource(R.string.trip_open_ended, "%s")
                Text(
                    dateRange(summary.trip.startDate, summary.trip.endDate) { openEnded.format(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val counts = stringResource(R.string.trip_counts, summary.stepCount, summary.photoCount)
                val drafts = if (summary.draftCount > 0) " · " + stringResource(R.string.trip_drafts, summary.draftCount) else ""
                Text(
                    counts + drafts,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(AtlasIcons.More, stringResource(R.string.trip_options, summary.trip.title))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(AtlasIcons.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_trip)) },
                        leadingIcon = { Icon(AtlasIcons.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gpx_export)) },
                        leadingIcon = { Icon(AtlasIcons.Route, null) },
                        onClick = { menu = false; onExportGpx() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(AtlasIcons.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
