package vn.lutrinh.app.ui.common

import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.Radii
import vn.lutrinh.app.ui.theme.numerals
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                Icon(TopoIcons.PhotoOff, stringResource(R.string.photo_missing))
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

/** "▶ 0:42" on a forest badge (specimen dark chip, 8 dp corners): marks a video thumbnail (R29). */
@Composable
fun VideoBadge(durationMs: Long, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.video_badge, DisplayFormat.clock(durationMs))
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    Row(
        modifier
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(Radii.Badge))
            .padding(start = 3.dp, end = 6.dp, top = 1.dp, bottom = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TopoIcons.Play, null, tint = ink, modifier = Modifier.size(14.dp))
        Text(DisplayFormat.clock(durationMs), color = ink, style = MaterialTheme.typography.labelSmall.numerals())
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

/**
 * Empty and error states (docs/08 §5 "Empty states", R38): the one component for "nothing here" and
 * "could not load". A contour mark in Contour brown (40 dp), the title (titleLarge 22 bold), the
 * body (bodyLarge, muted ink) and at most one Forest button; left-aligned like the screen headings.
 * The title says what is missing, the body says what to do, the button does it.
 * [compact] is the same state inside a card or a panel (stop photos, search results, "Bản đồ của
 * tôi"): no fill-the-screen, mark 24 dp beside the text, title titleMedium, body bodyMedium, no button.
 */
@Composable
fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    if (compact) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(TopoIcons.Atlas, null, tint = Palette.Contour, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (body.isNotEmpty()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(TopoIcons.Atlas, null, tint = Palette.Contour, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(shape = MaterialTheme.shapes.small, onClick = onAction) { Text(actionLabel) }
        }
    }
}

/*
 * Dialogs live in Dialogs.kt (docs/08 §5, R38). These two are the Confirm type for the two
 * questions several screens ask; the destructive confirm is in the error red.
 */

@Composable
fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = title, body = body,
        confirmLabel = stringResource(R.string.action_delete), dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm, onDismiss = onDismiss, destructive = true,
    )
}

@Composable
fun DiscardDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.discard_title),
    body: String = stringResource(R.string.discard_body),
) {
    ConfirmDialog(
        title = title, body = body,
        confirmLabel = stringResource(R.string.discard_confirm), dismissLabel = stringResource(R.string.keep_editing),
        onConfirm = onDiscard, onDismiss = onDismiss, destructive = true,
    )
}
