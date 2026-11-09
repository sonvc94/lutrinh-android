package vn.lutrinh.app.ui.steps

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.lutrinh.app.R
import vn.lutrinh.app.data.GalleryAccess
import vn.lutrinh.app.domain.usecase.GalleryPhoto
import vn.lutrinh.app.ui.common.displayTime

private const val COLUMNS = 3

/**
 * "Ảnh chụp trong lúc dừng" (R19, docs/03-screens.md §5): gallery photos taken during the stop,
 * all ticked at first. The ticked ones are shown in the step form's single "Ảnh (N)" grid (badge
 * "Từ thư viện") and copied into the step on "Lưu"; this card only asks for access, lists the
 * unticked ones to add back, and offers "Chọn thêm ảnh". Denied leaves a note (the "Thêm ảnh"
 * Photo Picker button stays in the grid's header).
 */
@Composable
fun StopPhotosSection(
    s: StopPhotosState,
    onRequestAccess: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val range = "${s.window.from.displayTime()}–${s.window.to.displayTime()}"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stop_photos_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.stop_photos_window, range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                s.access == GalleryAccess.NONE && !s.asked -> {
                    Text(stringResource(R.string.stop_photos_explain), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onRequestAccess) { Text(stringResource(R.string.stop_photos_allow)) }
                }
                s.access == GalleryAccess.NONE -> {
                    Text(
                        stringResource(R.string.stop_photos_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.stop_photos_allow)) }
                }
                s.loading && !s.loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    val photos = s.selection.photos
                    if (photos.isEmpty()) {
                        Text(
                            stringResource(R.string.stop_photos_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // F3: ticked photos live in the "Ảnh (N)" grid above; only the unticked show here.
                        val ticked = s.selection.selected.size
                        Text(
                            stringResource(R.string.stop_photos_added, ticked, photos.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val unticked = photos.filterNot { s.selection.isSelected(it.id) }
                        if (unticked.isNotEmpty()) {
                            Text(stringResource(R.string.stop_photos_unticked), style = MaterialTheme.typography.bodySmall)
                            unticked.chunked(COLUMNS).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { p ->
                                        GalleryTile(p, false, { onToggle(p.id) }, Modifier.weight(1f))
                                    }
                                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (s.access == GalleryAccess.PARTIAL) {
                        Text(
                            stringResource(R.string.stop_photos_partial),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRequestAccess) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null)
                            Spacer(Modifier.padding(3.dp))
                            Text(stringResource(R.string.stop_photos_pick_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(p: GalleryPhoto, selected: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.stop_photos_item, p.takenAt?.displayTime().orEmpty())
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
    ) {
        AsyncImage(
            model = Uri.parse(p.uri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (!selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.35f), CircleShape),
        )
        p.takenAt?.let {
            Text(
                it.displayTime(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
