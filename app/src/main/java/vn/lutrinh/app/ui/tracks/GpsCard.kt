package vn.lutrinh.app.ui.tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopSuggestion
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.TrackFormat

/**
 * "Hành trình GPS" (docs/03-screens.md "Trip detail"): the record control, the recorded tracks
 * and the stop suggestions in one collapsible card, so the timeline stays the main content.
 * Collapsed, the header carries the counts (or the live recording).
 */
@Composable
fun GpsCard(
    tripId: String,
    tracks: List<Track>,
    suggestions: List<StopSuggestion>,
    steps: List<Step>,
    galleryCounts: Map<String, Int>,
    recording: Track?,
    /** R16: the trip ended before today. */
    ended: Boolean,
    autoStart: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNewTripAndRecord: () -> Unit,
    onDeleteTrack: (String) -> Unit,
    onAddFromStop: (StopSuggestion, String) -> Unit,
    onDismissStop: (StopSuggestion) -> Unit,
    /** Called when the card has just been opened by the user (the screen scrolls it to the top). */
    onOpened: () -> Unit = {},
    /** R22: "Ghi hành trình ổn định" (link under the record control, and once after the first recording starts). */
    onOpenBatteryGuide: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Opened by "Tạo chuyến mới và ghi" so the permission dialogs of the control exist.
    var expanded by rememberSaveable { mutableStateOf(autoStart) }
    val mine = recording?.takeIf { it.tripId == tripId }
    val now = rememberNow(mine?.status == TrackStatus.RECORDING)
    val summary = when {
        mine != null -> "${stringResource(if (mine.status == TrackStatus.RECORDING) R.string.track_recording else R.string.track_paused)} · " +
            "${TrackFormat.duration(mine.durationMs(now))} · ${TrackFormat.distance(mine.distanceM)}"
        tracks.isEmpty() && suggestions.isEmpty() -> stringResource(R.string.gps_card_empty)
        else -> listOfNotNull(
            stringResource(R.string.gps_card_tracks, tracks.size).takeIf { tracks.isNotEmpty() },
            stringResource(R.string.gps_card_suggestions, suggestions.size).takeIf { suggestions.isNotEmpty() },
        ).joinToString(" · ")
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded; if (expanded) onOpened() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Route, null, tint = TrackRed)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.gps_card_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mine != null) TrackRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    stringResource(if (expanded) R.string.gps_card_collapse else R.string.gps_card_expand),
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    RecordTrackControl(
                        tripId = tripId, active = recording,
                        onPause = onPause, onResume = onResume, onStop = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        ended = ended, onNewTripAndRecord = onNewTripAndRecord, autoStart = autoStart,
                        onOpenBatteryGuide = onOpenBatteryGuide,
                    )
                    TextButton(onClick = onOpenBatteryGuide) {
                        Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.battery_guide_link))
                    }
                    TrackList(tracks, onDelete = onDeleteTrack)
                    StopSuggestionSection(
                        suggestions, steps = steps, galleryCounts = galleryCounts,
                        onAdd = onAddFromStop, onDismiss = onDismissStop,
                    )
                }
            }
        }
    }
}
