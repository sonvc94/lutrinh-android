package vn.lutrinh.app.ui.steps

import android.net.Uri
import vn.lutrinh.app.ui.theme.TopoIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
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
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.theme.Radii

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
    TopoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TopoIcons.Photo, null, tint = MaterialTheme.colorScheme.primary)
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
                    FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onRequestAccess) { Text(stringResource(R.string.stop_photos_allow)) }
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
                        MessageState(
                            title = stringResource(R.string.stop_photos_none_title),
                            body = stringResource(R.string.stop_photos_none),
                            compact = true,
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Icon(TopoIcons.AddPhotos, null)
                            Spacer(Modifier.padding(4.dp))
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
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
    ) {
        AsyncImage(
            model = Uri.parse(p.uri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // Unticked = veiled in card paper with an empty ring; ticked = a forest disc with a check.
        if (!selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)))
        val tick = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
        if (selected) {
            Icon(
                TopoIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = tick.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radii.Badge)).padding(4.dp),
            )
        } else {
            Box(
                tick.background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.35f), RoundedCornerShape(Radii.Badge))
                    .border(2.dp, MaterialTheme.colorScheme.inverseOnSurface, RoundedCornerShape(Radii.Badge)),
            )
        }
        p.takenAt?.let {
            Text(
                it.displayTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f), RoundedCornerShape(Radii.Badge))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}
