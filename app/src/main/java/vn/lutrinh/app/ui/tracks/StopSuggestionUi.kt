package vn.lutrinh.app.ui.tracks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopSuggestion
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.PlaceLabel
import vn.lutrinh.app.ui.common.displayDay
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.common.formatCoord

/**
 * "Gợi ý điểm dừng" (R17, docs/03-screens.md): places where a finished track stayed ≥ 20 min
 * and no step exists yet. Hidden when there is nothing to suggest.
 */
@Composable
fun StopSuggestionSection(
    suggestions: List<StopSuggestion>,
    /** The trip's steps: a suggestion is described as "gần <nearest step>, N km". */
    steps: List<Step> = emptyList(),
    /** R19: gallery photos per [StopSuggestion.key] ("N ảnh trong máy"); empty without gallery access. */
    galleryCounts: Map<String, Int> = emptyMap(),
    /** "Thêm điểm dừng": receives the suggestion and the default step title ("Điểm dừng 14:05"). */
    onAdd: (StopSuggestion, String) -> Unit,
    onDismiss: (StopSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Column(modifier) {
        Text(
            "${stringResource(R.string.stop_suggest_title)} (${suggestions.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.stop_suggest_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s -> SuggestionCard(s, galleryCounts[s.key], steps, onAdd, onDismiss) }
        }
    }
}

@Composable
private fun SuggestionCard(s: StopSuggestion, galleryCount: Int?, steps: List<Step>, onAdd: (StopSuggestion, String) -> Unit, onDismiss: (StopSuggestion) -> Unit) {
    val start = s.dwell.startAt.localDateTime()
    val end = s.dwell.endAt.localDateTime()
    val defaultTitle = DisplayFormat.defaultStepTitle(start)
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.PinDrop, null, tint = TrackRed, modifier = Modifier.padding(top = 2.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        "${start.toLocalDate().displayDay()} · ${start.displayTime()}–${end.displayTime()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        listOf(
                            stringResource(R.string.stop_suggest_duration, DisplayFormat.duration(s.dwell.durationMs)),
                            stringResource(R.string.stop_suggest_photos, s.photoCount).takeIf { s.photoCount > 0 },
                            galleryCount?.takeIf { it > 0 }?.let { stringResource(R.string.stop_suggest_gallery, it) },
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val near = PlaceLabel.nearest(s.dwell.center, steps)
                    Text(
                        if (near != null) stringResource(R.string.stop_suggest_near, near.title, DisplayFormat.trackDistance(near.km * 1000))
                        else formatCoord(s.dwell.center.latitude, s.dwell.center.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDismiss(s) }) { Text(stringResource(R.string.stop_suggest_dismiss)) }
                FilledTonalButton(onClick = { onAdd(s, defaultTitle) }) { Text(stringResource(R.string.stop_suggest_add)) }
            }
        }
    }
}
