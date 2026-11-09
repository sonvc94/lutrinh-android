package vn.lutrinh.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R

/*
 * Basemap choice (R32-R33, docs/03 "Kiểu bản đồ"): a layers button on the full maps (trip map,
 * "Bản đồ của tôi", location picker) opens a sheet listing the registered basemap providers; the
 * choice is kept on the device and applies to every map.
 */

/** The layers button and its sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleButton(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? LuTrinhApp ?: return
    val settings = app.container.basemaps
    val chosen by settings.chosen.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.map_style)
    SmallFloatingActionButton(
        onClick = { open = true },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { Icon(Icons.Outlined.Layers, contentDescription = label) }
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            settings.registry.providers.forEach { provider ->
                val selected = provider.id == chosen.id
                ListItem(
                    headlineContent = { Text(provider.displayName) },
                    supportingContent = {
                        Text(stringResource(if (provider.offlineAllowed) R.string.map_style_offline_ok else R.string.map_style_online_only))
                    },
                    leadingContent = {
                        Icon(if (provider.style.imagery != null) Icons.Outlined.SatelliteAlt else Icons.Outlined.Map, contentDescription = null)
                    },
                    trailingContent = { RadioButton(selected = selected, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .selectable(selected = selected) {
                            settings.choose(provider.id)
                            open = false
                        },
                )
            }
        }
    }
}

/**
 * R33: while the chosen basemap needs a network that is not there, the maps show the offline
 * one and this says why, once each time it happens.
 */
@Composable
fun BasemapFallbackNotice(snackbar: SnackbarHostState) {
    val app = LocalContext.current.applicationContext as? LuTrinhApp ?: return
    val chosen by app.container.basemaps.chosen.collectAsStateWithLifecycle()
    val online by app.container.network.online.collectAsStateWithLifecycle()
    val fallback = app.container.basemaps.registry.shown(chosen, online) != chosen
    val message = stringResource(R.string.map_style_needs_network)
    LaunchedEffect(fallback) { if (fallback) snackbar.showSnackbar(message) }
}
