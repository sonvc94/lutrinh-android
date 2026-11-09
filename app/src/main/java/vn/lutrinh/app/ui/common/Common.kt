package vn.lutrinh.app.ui.common

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import vn.lutrinh.app.domain.model.Photo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import vn.lutrinh.app.domain.usecase.DisplayFormat

val VI: Locale = DisplayFormat.VI
private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM, yyyy", VI)
private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", VI)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", VI)

fun LocalDate.display(): String = format(DATE_FMT)
/** In-trip day: "Thứ Hai, 20/10". */
fun LocalDate.displayDay(): String = DisplayFormat.day(this)
fun LocalDateTime.displayTime(): String = format(TIME_FMT)
fun java.time.LocalTime.displayTime(): String = format(TIME_FMT)
/** In-trip moment: "Thứ Hai, 20/10 · 08:10" (a date with year only where a trip is picked: [display]). */
fun LocalDateTime.displayDateTime(): String = "${toLocalDate().displayDay()} · ${displayTime()}"

/**
 * Runs [content] with a Vietnamese configuration, whatever the device language. Used around the
 * Material date/time pickers, which take month/day names, date formats and their own button/
 * headline strings from `LocalConfiguration` / `LocalContext` resources (docs/03-screens.md).
 * Must wrap the picker INSIDE the dialog content: a Dialog window has its own ComposeView, which
 * re-provides the device configuration and would undo a wrapper placed around the dialog.
 * Not applied app-wide: MapLibre's MapView needs the real Activity context.
 */
@Composable
fun VietnameseLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val parentConfig = LocalConfiguration.current
    val viConfig = remember(parentConfig) {
        Configuration(parentConfig).apply { setLocales(LocaleList(VI)) }
    }
    val viContext = remember(base, viConfig) {
        val viResources = base.createConfigurationContext(viConfig).resources
        object : ContextWrapper(base) {
            override fun getResources(): Resources = viResources
        }
    }
    CompositionLocalProvider(LocalConfiguration provides viConfig, LocalContext provides viContext, content = content)
}

fun dateRange(start: LocalDate, end: LocalDate?, openEnded: (String) -> String): String = when {
    end == null -> openEnded(start.display())
    start == end -> start.display()
    start.year == end.year -> "${start.format(DATE_SHORT)} – ${end.display()}"
    else -> "${start.display()} – ${end.display()}"
}

/** Storage size for the UI: "9,4 MB", "1,2 GB" (Vietnamese decimal comma, 1 MB = 10^6 bytes). */
fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) String.format(VI, "%.1f GB", mb / 1000) else String.format(VI, "%.1f MB", mb)
}

fun formatCoord(lat: Double, lng: Double): String = String.format(Locale.US, "%.5f, %.5f", lat, lng)

@Composable
fun photoFile(fileName: String): File {
    val app = LocalContext.current.applicationContext as LuTrinhApp
    return app.container.photos.fileFor(fileName)
}

/** Thumbnail / full image from app-private storage. */
@Composable
fun LocalPhoto(fileName: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = photoFile(fileName),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        error = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                Icon(Icons.Outlined.BrokenImage, stringResource(R.string.photo_missing))
            }
        },
    )
}

/** A step media item (R29): a photo, or a video's still frame with a play badge and its length. */
@Composable
fun LocalMedia(photo: Photo, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, badge: Boolean = true) =
    LocalMedia(photo.fileName, if (photo.isVideo) photo.durationMs ?: 0L else null, modifier, contentScale, badge)

/** [videoDurationMs] non-null = the file is a video of that length (R29). */
@Composable
fun LocalMedia(
    fileName: String,
    videoDurationMs: Long?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    badge: Boolean = true,
) {
    Box(modifier) {
        LocalPhoto(fileName, Modifier.fillMaxSize(), contentScale)
        if (videoDurationMs != null && badge) VideoBadge(videoDurationMs, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** "▶ 0:42" on a dark pill: marks a video thumbnail (R29). */
@Composable
fun VideoBadge(durationMs: Long, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.video_badge, DisplayFormat.clock(durationMs))
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .padding(start = 2.dp, end = 5.dp, top = 1.dp, bottom = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(DisplayFormat.clock(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

/**
 * Empty and error states (docs/08-design.md): type only, left-aligned, no icon disc. The title says
 * what is missing, the body says what to do, the button does it.
 */
@Composable
fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(Modifier.width(32.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DiscardDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.discard_title),
    body: String = stringResource(R.string.discard_body),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep_editing)) } },
    )
}
