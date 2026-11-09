package vn.lutrinh.app.ui.common

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

val VI: Locale = Locale.forLanguageTag("vi-VN")
private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM, yyyy", VI)
private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", VI)
private val DAY_FMT = DateTimeFormatter.ofPattern("EEEE, d/M", VI)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", VI)

fun LocalDate.display(): String = format(DATE_FMT)
fun LocalDate.displayDay(): String = format(DAY_FMT).replaceFirstChar { it.titlecase(VI) }
fun LocalDateTime.displayTime(): String = format(TIME_FMT)
fun LocalDateTime.displayDateTime(): String = "${toLocalDate().display()} · ${displayTime()}"

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

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
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
fun DiscardDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep_editing)) } },
    )
}
