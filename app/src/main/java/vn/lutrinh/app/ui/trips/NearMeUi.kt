package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.routing.NearMeState
import vn.lutrinh.app.domain.routing.NearRow
import vn.lutrinh.app.domain.routing.StopRoad
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.numerals

/** The two views of a trip's stops (R45): the timeline in order, or by road distance from the user. */
enum class StopView { Order, Near }

/** "Theo thứ tự" | "Gần tôi": two segments, the chosen one a Forest chip, the other a Sand one. */
@Composable
fun StopViewSwitch(selected: StopView, onSelect: (StopView) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Segment(stringResource(R.string.near_view_order), selected == StopView.Order, { onSelect(StopView.Order) }, Modifier.weight(1f))
        Segment(stringResource(R.string.near_view_near), selected == StopView.Near, { onSelect(StopView.Near) }, Modifier.weight(1f))
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).selectable(selected = on, role = Role.Tab, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (on) MaterialTheme.colorScheme.inverseSurface else Palette.Sand,
        contentColor = if (on) MaterialTheme.colorScheme.inverseOnSurface else Palette.Ink,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * The "Gần tôi" body (R45): the list nearest → farthest with km and ~time, or one message with its
 * action (consent, permission, location off, no fix, offline), always with a way to try again.
 */
internal fun LazyListScope.nearMeItems(
    state: NearMeState,
    onOpenStep: (String) -> Unit,
    onRetry: () -> Unit,
    onConsent: () -> Unit,
) {
    when (state) {
        NearMeState.Idle, NearMeState.Locating -> item(key = "near-wait") {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.near_locating), style = MaterialTheme.typography.bodyMedium)
            }
        }
        NearMeState.NeedsConsent -> message("near-consent", R.string.near_consent_title, R.string.near_consent_body, R.string.near_consent_action, onConsent)
        NearMeState.NoPermission -> message("near-perm", R.string.near_no_permission_title, R.string.near_no_permission_body, R.string.near_retry, onRetry)
        NearMeState.LocationOff -> message("near-off", R.string.near_location_off_title, R.string.near_location_off_body, R.string.near_retry, onRetry)
        NearMeState.NoFix -> message("near-nofix", R.string.near_no_fix_title, R.string.near_no_fix_body, R.string.near_retry, onRetry)
        NearMeState.Offline -> message("near-offline", R.string.near_offline_title, R.string.near_offline_body, R.string.near_retry, onRetry)
        NearMeState.NoStops -> {}
        is NearMeState.Rows -> {
            item(key = "near-head") {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.pending > 0) stringResource(R.string.near_computing, state.pending) else stringResource(R.string.near_caption),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.near_refresh)) }
                }
            }
            items(state.rows.size, key = { "near-" + state.rows[it].step.id }) { i ->
                NearRowCard(state.rows[i]) { onOpenStep(state.rows[i].step.id) }
            }
        }
    }
}

private fun LazyListScope.message(key: String, title: Int, body: Int, action: Int, onAction: () -> Unit) {
    item(key = key) {
        MessageState(
            title = stringResource(title), body = stringResource(body), actionLabel = stringResource(action), onAction = onAction,
            modifier = Modifier.height(if (body == R.string.near_consent_body) 440.dp else 320.dp),
        )
    }
}

/** One stop of the "Gần tôi" list: its number in the timeline, title and the road km · ~time (or why there is none). */
@Composable
private fun NearRowCard(row: NearRow, onClick: () -> Unit) {
    TopoCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).background(Palette.Waypoint, CircleShape), Alignment.Center) {
                Text("${row.number}", color = Palette.Card, style = MaterialTheme.typography.labelLarge.numerals())
            }
            Column(Modifier.weight(1f)) {
                Text(row.step.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(roadText(row.road), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun roadText(road: StopRoad): String = when (road) {
    StopRoad.Pending -> stringResource(R.string.near_pending)
    is StopRoad.Found -> {
        val km = stringResource(R.string.leg_km, DisplayFormat.kmNumber(road.distance.distanceM / 1000))
        val text = "$km · ${DisplayFormat.approxDuration(road.distance.durationS)}"
        if (road.byCar) "$text · ${stringResource(R.string.leg_car_route)}" else text
    }
    is StopRoad.Unroutable -> stringResource(if (road.noRoute) R.string.near_unroutable_none else R.string.near_unroutable_failed)
}
