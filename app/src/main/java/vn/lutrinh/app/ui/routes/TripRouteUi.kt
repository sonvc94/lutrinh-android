package vn.lutrinh.app.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.data.RouteDownloadRequest
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.LinkRef
import vn.lutrinh.app.domain.catalog.RemoteStatus
import vn.lutrinh.app.domain.catalog.RouteChange
import vn.lutrinh.app.domain.catalog.RouteLink
import vn.lutrinh.app.domain.catalog.RouteUpdates
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.ui.common.BadgeTone
import vn.lutrinh.app.ui.common.Eyebrow
import vn.lutrinh.app.ui.common.TopoBadge
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.display
import vn.lutrinh.app.ui.common.formatBytes
import vn.lutrinh.app.ui.theme.Space
import vn.lutrinh.app.ui.theme.TopoIcons
import java.time.Instant
import java.time.ZoneId

/**
 * A trip's link to its route (R40) on the trip page: the badge, the once-a-day update check
 * (online, shared routes only), the sheet (credits, newer version, the two choices) and the
 * unpublished/revoked banner. Owned by the trip's view model ([scope]).
 */
class TripRouteLink(private val c: AppContainer, private val tripId: String, private val scope: CoroutineScope) {
    val link: StateFlow<RouteLink?> = c.routes.observeLink(tripId).stateIn(scope, SharingStarted.Eagerly, null)

    /** The sheet is open; [change] = what the newer version changes (known from this session's check). */
    data class Sheet(val change: RouteChange?)

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()
    private var lastChange: RouteChange? = null

    init {
        scope.launch {
            val l = link.filterNotNull().first()
            check(l, openSheet = true)
        }
    }

    /** Asks the server when due (≤ 1 per day, online); opens the sheet once per new version. */
    private suspend fun check(l: RouteLink, openSheet: Boolean) {
        val now = c.clock.nowMillis()
        if (!RouteUpdates.due(l, now, c.network.online.value)) return
        val r = withContext(Dispatchers.IO) { c.routeCatalog.checkUpdates(listOf(LinkRef(l.code, l.routeId, l.version))) }
        val answer = (r as? CatalogResult.Ok)?.value?.firstOrNull() ?: return
        val updated = RouteUpdates.apply(l, answer, now)
        c.routes.saveLink(updated)
        lastChange = answer.newer ?: lastChange
        if (openSheet && RouteUpdates.showSheet(updated) && _sheet.value == null) _sheet.value = Sheet(lastChange)
    }

    /** Badge tap: the sheet (credits, version), and a check when one is due. */
    fun openSheet() {
        _sheet.value = Sheet(lastChange)
        link.value?.let { l -> scope.launch { check(l, openSheet = false); _sheet.value?.let { _sheet.value = Sheet(lastChange) } } }
    }

    fun closeSheet() { _sheet.value = null }

    /** "Để sau": the sheet stays shut for this version; the badge keeps its dot. */
    fun later() {
        val l = link.value ?: return
        _sheet.value = null
        scope.launch { c.routes.saveLink(l.copy(skippedVersion = l.latestVersion)) }
    }

    /** "Thay bản trong máy" (unedited copies only, D-4): download the new version into this trip's id. */
    fun replace(trip: Trip): Boolean {
        val l = link.value ?: return false
        if (!RouteUpdates.canReplace(l)) return false
        _sheet.value = null
        c.routeDownloads.start(RouteDownloadRequest(l.code, trip.startDate, offlineMap = false, replace = tripId, title = trip.title, bytes = lastChange?.bytes ?: 0))
        return true
    }

    fun closeNotice() {
        val l = link.value ?: return
        scope.launch { c.routes.saveLink(l.copy(noticeClosed = true)) }
    }
}

/** The Sand badge "Lộ trình HG4N-7KQE · v3" (· có bản mới while a newer version is known). */
@Composable
fun RouteBadge(link: RouteLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = stringResource(if (link.hasNewer) R.string.route_badge_new else R.string.route_badge, link.code.display, link.version)
    TopoBadge(text, BadgeTone.Neutral, modifier.clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button, onClick = onClick))
}

/** Page 05 screen 9: the route is no longer shared; the copy stays complete. Closable. */
@Composable
fun RouteNoticeBanner(link: RouteLink, onClose: () -> Unit, modifier: Modifier = Modifier) {
    if (link.remoteStatus == RemoteStatus.ACTIVE || link.noticeClosed) return
    TopoCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.XS)) {
                Text(stringResource(R.string.route_unpublished_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.route_unpublished_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(TopoIcons.Close, stringResource(R.string.action_close)) }
        }
    }
}

/** Badge sheet: newer version (page 05 screen 7) or the current one with its credits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheet(
    link: RouteLink,
    change: RouteChange?,
    onNewTrip: () -> Unit,
    onReplace: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.XL).padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            if (link.hasNewer) {
                val v = link.latestVersion ?: link.version
                val date = change?.publishedAt?.let { iso ->
                    runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
                }
                Eyebrow(
                    if (date != null) stringResource(R.string.route_update_eyebrow_date, v, date.display())
                    else stringResource(R.string.route_update_eyebrow, v),
                )
                Text(stringResource(R.string.route_update_title), style = MaterialTheme.typography.titleLarge)
                change?.let { ch ->
                    if (ch.stopsAdded.isNotEmpty()) Text(stringResource(R.string.route_update_added, ch.stopsAdded.size, ch.stopsAdded.joinToString(", ")))
                    if (ch.stopsRemoved.isNotEmpty()) Text(stringResource(R.string.route_update_removed, ch.stopsRemoved.size, ch.stopsRemoved.joinToString(", ")))
                    if (ch.stopsEdited > 0) Text(stringResource(R.string.route_update_edited, ch.stopsEdited))
                    if (ch.mediaAdded > 0) Text(stringResource(R.string.route_update_media, ch.mediaAdded, formatBytes(ch.bytes)))
                    ch.note?.takeIf { it.isNotBlank() }?.let {
                        Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(shape = MaterialTheme.shapes.small, onClick = onNewTrip, modifier = Modifier.fillMaxWidth().padding(top = Space.S)) {
                    Text(stringResource(R.string.route_update_new_trip))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    if (RouteUpdates.canReplace(link)) {
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onReplace) { Text(stringResource(R.string.route_update_replace)) }
                    }
                    TextButton(onClick = onLater) { Text(stringResource(R.string.route_update_later)) }
                }
                if (!RouteUpdates.canReplace(link)) {
                    Text(
                        stringResource(R.string.route_update_edited_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Eyebrow(stringResource(R.string.route_info_eyebrow, link.version))
                Text(link.badge, style = MaterialTheme.typography.titleLarge)
                if (link.shared) Text(stringResource(R.string.route_info_current), style = MaterialTheme.typography.bodyMedium)
            }
            if (link.credits.isNotBlank()) {
                Eyebrow(stringResource(R.string.route_info_credits), Modifier.padding(top = Space.S))
                Text(link.credits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
