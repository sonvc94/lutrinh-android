package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.TripCover
import vn.lutrinh.app.ui.common.LocalPhoto

/**
 * R21 "Ảnh bìa": "Tự động" first (the automatic cover), then every photo of the trip's steps in
 * timeline order. [chosenId] = the stored choice (null or stale = automatic). Picking closes the
 * sheet through [onPick] (null = "Tự động").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPickerSheet(steps: List<Step>, chosenId: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val photos = TripCover.candidates(steps)
    val auto = TripCover.resolve(null, steps)
    val chosen = chosenId?.takeIf { TripCover.isChosen(it, steps) }
    val stepTitle = steps.associate { it.id to it.title }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.trip_cover_change),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (photos.isEmpty()) {
                Text(
                    stringResource(R.string.trip_cover_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CoverTile(
                        photo = auto, selected = chosen == null, label = stringResource(R.string.trip_cover_auto),
                        hint = stringResource(R.string.trip_cover_auto_hint), wide = true, onClick = { onPick(null) },
                    )
                }
                items(photos, key = { it.id }) { p ->
                    CoverTile(
                        photo = p, selected = p.id == chosen,
                        label = null, hint = null, desc = stringResource(R.string.trip_cover_photo_of, stepTitle[p.stepId].orEmpty()), wide = false, onClick = { onPick(p.id) })
                }
            }
        }
    }
}

@Composable
private fun CoverTile(
    photo: Photo?, selected: Boolean, label: String?, hint: String?, wide: Boolean, onClick: () -> Unit,
    desc: String = label ?: stringResource(R.string.trip_cover),
) {
    val shape = MaterialTheme.shapes.small
    val ring = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (wide) 3f else 1f).clip(shape).then(ring).clickable(onClick = onClick)
            .semantics { contentDescription = desc; this.selected = selected },
    ) {
        if (photo != null) LocalPhoto(photo.fileName, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        if (label != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White)
                Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center)
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle, stringResource(R.string.trip_cover_chosen),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.White, CircleShape),
            )
        }
    }
}
