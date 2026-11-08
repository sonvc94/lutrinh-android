package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.map.TransportStyle
import java.util.Locale

/** Straight-line distance of a leg, 1 decimal under 10 km. */
fun Leg.kmText(): String = String.format(Locale.US, if (distanceKm < 10) "%.1f" else "%.0f", distanceKm)

/** Compact chip on the timeline / map card: the leg's mode (or a prompt) and its distance. */
@Composable
fun LegChip(leg: Leg, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mode = leg.mode
    val style = mode?.let { TransportStyle.of(it) }
    val label = style?.let { stringResource(it.label) } ?: stringResource(R.string.leg_set_mode)
    AssistChip(
        onClick = onClick,
        label = { Text("$label · ${stringResource(R.string.leg_km, leg.kmText())}", style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                style?.icon ?: Icons.Outlined.Route,
                null,
                Modifier.size(AssistChipDefaults.IconSize),
                tint = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    )
}

/** Bottom sheet to pick the transport of one leg (R12); "Chưa chọn" clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegModeSheet(leg: Leg, onPick: (TransportMode?) -> Unit, onDismiss: () -> Unit) {
    // Fully expanded: all 8 choices fit on a phone without dragging the sheet up first.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.leg_sheet_title, leg.from.title, leg.to.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.leg_sheet_subtitle, leg.kmText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            (TransportMode.entries + listOf<TransportMode?>(null)).forEach { mode ->
                val style = mode?.let { TransportStyle.of(it) }
                ListItem(
                    headlineContent = { Text(style?.let { stringResource(it.label) } ?: stringResource(R.string.transport_none)) },
                    leadingContent = {
                        Icon(style?.icon ?: Icons.Outlined.Route, null, tint = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { if (mode == leg.mode) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onPick(mode) },
                )
            }
        }
    }
}
