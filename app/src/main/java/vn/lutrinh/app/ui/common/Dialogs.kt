package vn.lutrinh.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.ui.theme.Palette

/*
 * The three dialog types (docs/08 §5 "Dialogs", rule R38). Every AlertDialog in the app goes
 * through one of these; the title is always titleLarge (22 / 700), the body bodyMedium.
 *  - [ConfirmDialog]: a question with two text buttons, no icon; a destructive confirm is in Error.
 *  - [NoticeDialog]: explains or asks for a permission; Signal icon 24 dp, Forest filled main button.
 *  - [MessageDialog] / [BusyDialog]: an error (title optional, one "close" button) or a wait (no button).
 */

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/** [onDismissRequest] is back / outside tap; [dismissLabel] + [onDismissAction] the optional text button. */
@Composable
fun NoticeDialog(
    icon: ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissLabel: String? = null,
    onDismissAction: () -> Unit = onDismissRequest,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(icon, null, tint = Palette.Signal) },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(shape = MaterialTheme.shapes.small, onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = dismissLabel?.let { label -> { TextButton(onClick = onDismissAction) { Text(label) } } },
    )
}

@Composable
fun MessageDialog(title: String?, body: String, closeLabel: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = title?.let { { Text(it, style = MaterialTheme.typography.titleLarge) } },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onClose) { Text(closeLabel) } },
    )
}

@Composable
fun BusyDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}
