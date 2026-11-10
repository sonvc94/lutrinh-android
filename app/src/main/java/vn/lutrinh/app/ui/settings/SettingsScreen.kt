package vn.lutrinh.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import vn.lutrinh.app.ui.common.NoticeDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.common.Eyebrow
import vn.lutrinh.app.ui.common.TopoCard
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button

/**
 * "Cài đặt": the routing switch (R36). On (default) = routed legs follow the road and their stop
 * positions go to the routing server; off = straight lines and no network call for routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as LuTrinhApp).container.routingSettings
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Eyebrow(
                stringResource(R.string.settings_section_map),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            // One card per setting (specimen `.card`); the row is the toggle.
            TopoCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = { settings.setEnabled(it) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.settings_routing), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (enabled) R.string.settings_routing_on else R.string.settings_routing_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The row is the toggle (one TalkBack node); the switch only shows the state.
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }
            Text(
                stringResource(R.string.settings_routing_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The one-time notice (R36), before the first stop position leaves the device: what is sent and
 * to whom. Any answer marks it seen; "Tắt tính đường" also turns routing off. Topo dialog: the
 * route mark in signal orange over the title, the accept as the forest button.
 */
@Composable
fun RoutingNoticeDialog(onAccept: () -> Unit, onTurnOff: () -> Unit) {
    NoticeDialog(
        icon = TopoIcons.Route,
        title = stringResource(R.string.routing_notice_title),
        body = stringResource(R.string.routing_notice_body),
        confirmLabel = stringResource(R.string.routing_notice_ok),
        onConfirm = onAccept,
        onDismissRequest = onAccept,
        dismissLabel = stringResource(R.string.routing_notice_off),
        onDismissAction = onTurnOff,
    )
}
