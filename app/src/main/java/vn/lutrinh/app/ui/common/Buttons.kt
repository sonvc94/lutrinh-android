package vn.lutrinh.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.ui.theme.hairline

/*
 * Flat buttons (docs/08 §5 "Buttons"): no shadows, no pills. The only places in ui/ that use the
 * Material FAB composables; `DesignGuardTest.noShadowedFabs` keeps it that way.
 */

/**
 * The screen's one main action (trip list "Chuyến đi mới", trip detail "Thêm"): a filled chàm
 * block with 4 dp corners and no shadow; it still collapses to its icon while [expanded] is false.
 * Labelled with [label] as a content description (M3 1.3 gives the extended FAB no text, AGENTS.md).
 */
@Composable
fun FlatFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = true) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(icon, null) },
        text = { Text(label) },
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.semantics { contentDescription = label },
    )
}

/**
 * A map control ("Kiểu bản đồ", "Vị trí của tôi", "Xem toàn bộ hành trình", "Xem bản đồ"): a
 * 44 dp square of raised paper with a hairline and 4 dp corners, laid on the map like a legend box.
 */
@Composable
fun MapButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = hairline,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(22.dp)) }
    }
}
