package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.clickable
import vn.lutrinh.app.ui.theme.AtlasIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import vn.lutrinh.app.domain.routing.LegMeasure
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.map.TransportStyle

/**
 * The distance part of a leg chip (R36): "48,5 km · ~1 giờ 32 phút" along the road, "27,3 km chim
 * bay" for a straight leg (no stored route, routing off, or a train/plane/boat leg). A Xe máy leg
 * drawn with the car profile (no motorbike route) ends with "· tuyến ô tô".
 */
@Composable
fun legDistanceText(m: LegMeasure): String =
    if (m.byRoad) {
        val km = stringResource(R.string.leg_km, DisplayFormat.kmNumber(m.km))
        val road = m.durationS?.let { "$km · ${DisplayFormat.approxDuration(it)}" } ?: km
        if (m.carRoute) "$road · ${stringResource(R.string.leg_car_route)}" else road
    } else stringResource(R.string.leg_km_straight, DisplayFormat.kmNumber(m.km))

/** Compact chip on the timeline / map card: the leg's mode (or a prompt), its distance and (road) time. */
@Composable
fun LegChip(leg: Leg, measure: LegMeasure, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mode = leg.mode
    val style = mode?.let { TransportStyle.of(it) }
    val label = style?.let { stringResource(it.label) } ?: stringResource(R.string.leg_set_mode)
    AssistChip(
        onClick = onClick,
        label = { Text("$label · ${legDistanceText(measure)}", style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                style?.icon ?: AtlasIcons.Route,
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
fun LegModeSheet(leg: Leg, measure: LegMeasure, onPick: (TransportMode?) -> Unit, onDismiss: () -> Unit) {
    // Fully expanded: all 8 choices fit on a phone without dragging the sheet up first.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.leg_sheet_title, leg.from.title, leg.to.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                if (measure.byRoad && measure.durationS != null) {
                    stringResource(
                        if (measure.carRoute) R.string.leg_sheet_subtitle_road_car else R.string.leg_sheet_subtitle_road,
                        DisplayFormat.kmNumber(measure.km), DisplayFormat.approxDuration(measure.durationS),
                    )
                } else stringResource(R.string.leg_sheet_subtitle, DisplayFormat.kmNumber(leg.distanceKm)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            (TransportMode.entries + listOf<TransportMode?>(null)).forEach { mode ->
                val style = mode?.let { TransportStyle.of(it) }
                ListItem(
                    headlineContent = { Text(style?.let { stringResource(it.label) } ?: stringResource(R.string.transport_none)) },
                    leadingContent = {
                        Icon(style?.icon ?: AtlasIcons.Route, null, tint = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { if (mode == leg.mode) Icon(AtlasIcons.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onPick(mode) },
                )
            }
        }
    }
}
