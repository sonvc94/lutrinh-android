package vn.lutrinh.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.TripSummary
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.SharedPlaces
import vn.lutrinh.app.domain.usecase.SharedPlaces.Resolved
import vn.lutrinh.app.ui.common.Eyebrow
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatCoord
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.TopoIcons

/**
 * "Thêm vào chuyến đi" (R43): a place shared from Google Maps. Parses the text, resolves the
 * position (follows the short link, with a timeout), then lists the user's trips to put the stop
 * into. Nothing is saved here: the choice opens the stop form prefilled.
 */
class SharePlaceViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val place = SharedPlaces.parse(handle.get<String>("text").orEmpty()) as? SharedPlaces.Parsed.Place

    /** Null while the link is being followed. */
    private val _resolved = MutableStateFlow<Resolved?>(null)
    val resolved: StateFlow<Resolved?> = _resolved.asStateFlow()

    /** The text was not a Google Maps place (cannot happen from MainActivity, which checks first). */
    val valid: Boolean = place != null

    val trips: StateFlow<List<TripSummary>?> = c.trips.observeTripSummaries()
        .map<List<TripSummary>, List<TripSummary>?> { it }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (place != null) viewModelScope.launch {
            val r = SharedPlaces.resolve(place, c.linkFollower)
            android.util.Log.i("ShareIn", "resolved: " + r.javaClass.simpleName + (if (r is Resolved.Found) " (from " + (if (place.point != null) "the text" else "the short link") + ")" else ""))
            _resolved.value = r
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePlaceScreen(
    onBack: () -> Unit,
    /** The chosen trip, or null = "Chuyến đi mới"; with what the share resolved to. */
    onPick: (tripId: String?, Resolved) -> Unit,
    vm: SharePlaceViewModel = viewModel(factory = factory { c, h -> SharePlaceViewModel(c, h) }),
) {
    val resolved by vm.resolved.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_in_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PlaceCard(resolved) }
            item { Eyebrow(stringResource(R.string.share_in_pick), Modifier.padding(start = 8.dp, top = 8.dp)) }
            val ready = resolved
            items(trips.orEmpty(), key = { it.trip.id }) { s ->
                TripRow(s, enabled = ready != null) { ready?.let { r -> onPick(s.trip.id, r) } }
            }
            item {
                NewTripRow(enabled = ready != null && trips != null) { ready?.let { r -> onPick(null, r) } }
            }
        }
    }
}

@Composable
private fun PlaceCard(resolved: Resolved?) {
    TopoCard(Modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.share_in_from_maps))
        if (resolved == null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.share_in_resolving), style = MaterialTheme.typography.bodyMedium)
            }
            return@TopoCard
        }
        Text(
            resolved.name.ifEmpty { stringResource(R.string.share_in_no_name) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        resolved.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TopoIcons.Place, null, tint = Palette.Contour, modifier = Modifier.size(18.dp))
            val (text, color) = when (resolved) {
                is Resolved.Found -> formatCoord(resolved.point.latitude, resolved.point.longitude) to MaterialTheme.colorScheme.onSurfaceVariant
                is Resolved.NoCoordinates -> stringResource(R.string.share_in_gap_none) to MaterialTheme.colorScheme.onSurface
                is Resolved.Unreachable -> stringResource(R.string.share_in_gap_offline) to MaterialTheme.colorScheme.onSurface
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun TripRow(s: TripSummary, enabled: Boolean, onClick: () -> Unit) {
    TopoCard(Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Text(s.trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val openEnded = stringResource(R.string.trip_open_ended, "%s")
        Text(
            dateRange(s.trip.startDate, s.trip.endDate) { openEnded.format(it) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.trip_counts, s.stepCount, DisplayFormat.tripMediaCount(s.photoCount, s.videoCount)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewTripRow(enabled: Boolean, onClick: () -> Unit) {
    TopoCard(Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(TopoIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.share_in_new_trip), style = MaterialTheme.typography.titleMedium)
        }
    }
}
