package vn.lutrinh.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.lutrinh.app.ui.theme.Lift
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.Radii
import vn.lutrinh.app.ui.theme.Space
import vn.lutrinh.app.ui.theme.ledge

/*
 * The topo-outdoor building blocks (docs/08 §5): card, panel, eyebrow, heading, badge, map chip.
 * Screens compose these instead of styling Material surfaces themselves.
 */

/**
 * The specimen's `.card`: card paper, 18 dp corners, a 2 dp ledge under it, 14 dp padding.
 * Lies on the parchment; [onClick] makes the whole card one target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.M),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radii.Card)
    Column(
        modifier
            .ledge(Radii.Card)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The specimen's `.pan`: card paper with 22 dp corners and 16/18 dp padding. Over the map
 * ([floating]) it casts the panel shadow; on paper it has none (the readout on trip detail).
 */
@Composable
fun TopoPanel(
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Space.L),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (floating) Lift.Panel else 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The specimen's `.lbl`: 13 bold, +0.06 em, muted ink. Over a title, a stat or a group of rows. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** A screen heading in the body (specimen list screen): optional [eyebrow] over a 28 bold title. */
@Composable
fun ScreenHeading(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

/** Badge tones: the specimen's three difficulty pairs, reused as neutral / attention / alert. */
enum class BadgeTone {
    Easy, Moderate, Hard,

    /** Ink on Sand: a plain label that is not a status ("Lộ trình HG4N-7KQE · v3", R40). */
    Neutral,
}

/** The specimen's `.dif`: 13 bold on a tinted 8 dp block. Short status words only ("Nháp"). */
@Composable
fun TopoBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        BadgeTone.Easy -> Palette.EasyWash to Palette.EasyInk
        BadgeTone.Moderate -> Palette.ModerateWash to Palette.ModerateInk
        BadgeTone.Hard -> Palette.HardWash to Palette.HardInk
        BadgeTone.Neutral -> Palette.Sand to Palette.Ink
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        color = fg,
        modifier = modifier.background(bg, RoundedCornerShape(Radii.Badge)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * The specimen's map chip (`.chip`): bold 14 text on card paper over the map, 12 dp corners,
 * chip shadow over the map ([floating]). [dark] = the forest chip that marks the current point.
 */
@Composable
fun MapChip(text: String, modifier: Modifier = Modifier, dark: Boolean = false, floating: Boolean = true) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (dark) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (dark) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (floating) Lift.Chip else 0.dp,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1)
    }
}

/**
 * Text fields (docs/08 §5 "Inputs"): outlined, 12 dp corners, filled with card paper so a form
 * reads as cards on the parchment; the outline is the 3:1 Outline token, forest when focused.
 */
@Composable
fun topoFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)
