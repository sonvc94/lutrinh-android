package vn.lutrinh.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint

/**
 * Offline indicator drawn over every map (R15). Nothing while online. Offline, a small chip
 * "Ngoại tuyến · <area>" when a downloaded area contains the camera centre; otherwise a clear
 * "Không có mạng — vùng này chưa tải bản đồ offline" note, as the basemap is then empty.
 */
@Composable
internal fun OfflineIndicator(center: GeoPoint?, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? LuTrinhApp ?: return
    val online by app.container.network.online.collectAsStateWithLifecycle()
    if (online) return
    val states by app.container.offlineMaps.states.collectAsStateWithLifecycle()
    val downloaded = states.values.filter { it.phase == OfflinePhase.COMPLETE }.map { it.area }
    val area = center?.let { OfflineConfig.covering(downloaded, it) }
    if (area != null) {
        Surface(
            modifier = modifier.padding(8.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.OfflinePin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.offline_indicator, area.name), style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        Surface(
            modifier = modifier.padding(8.dp).widthIn(max = 320.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 2.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(20.dp))
                Column {
                    Text(stringResource(R.string.offline_no_network), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.offline_not_downloaded), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
