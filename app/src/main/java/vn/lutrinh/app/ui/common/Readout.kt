package vn.lutrinh.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.KmScale
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.numerals

/** One stat of the readout: "10" over "điểm dừng". */
data class ReadoutCount(val value: String, val label: String)

/**
 * The trip's numbers as the specimen's stats panel (docs/08 §5 "Stats"): [kmNote] as the eyebrow
 * ("theo đường đi"), then one row of stats, each a large numeral (Barlow Condensed 700) over its
 * muted label: the km total first, then [counts]; under them a scale bar of that total in signal
 * orange ([KmScaleBar]), where the specimen draws its profile line. The caller puts it in a panel.
 */
@Composable
fun KmReadout(
    km: Double,
    kmUnit: String,
    counts: List<ReadoutCount>,
    modifier: Modifier = Modifier,
    kmNote: String? = null,
    /** A map overlay ("Bản đồ của tôi"): smaller numerals, no eyebrow. */
    compact: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val numberStyle = (if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge).numerals()
    val stat: @Composable (String, String) -> Unit = { value, label ->
        // One node per stat for TalkBack ("10 điểm dừng"); the texts stay findable.
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(value, style = numberStyle, color = ink)
            Eyebrow(label)
        }
    }
    Column(modifier) {
        if (kmNote != null && !compact) Eyebrow(kmNote, Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stat(DisplayFormat.kmNumber(km), kmUnit)
            counts.forEach { stat(it.value, it.label) }
        }
        KmScaleBar(km, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/**
 * A scale bar whose length is [km] ([KmScale]): a 6 dp bar with rounded ends, alternate blocks
 * in signal orange on a sand track, ticks under the block edges with their km labelled in the
 * numeral face. Nothing for 0 km.
 */
@Composable
fun KmScaleBar(km: Double, modifier: Modifier = Modifier) {
    val scale = remember(km) { KmScale.of(km) } ?: return
    val signal = Palette.Signal
    val track = Palette.Sand
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.numerals().copy(color = muted)
    Canvas(modifier.height(30.dp).clearAndSetSemantics { contentDescription = "" }) {
        val stroke = 1.dp.toPx()
        val barH = 6.dp.toPx()
        val w = size.width - stroke
        val left = stroke / 2
        val top = stroke / 2
        val r = CornerRadius(barH / 2, barH / 2)
        clipPath(Path().apply { addRoundRect(RoundRect(left, top, left + w, top + barH, r)) }) {
            drawRect(track, Offset(left, top), Size(w, barH))
            scale.ticks.forEachIndexed { i, t ->
                if (i % 2 == 0) {
                    val x0 = left + w * scale.fraction(t).toFloat()
                    val x1 = left + w * scale.fraction(t + scale.step).toFloat()
                    if (x1 > x0) drawRect(signal, Offset(x0, top), Size(x1 - x0, barH))
                }
            }
        }
        val tickTop = top + barH
        val tickLen = 4.dp.toPx()
        scale.ticks.forEach { t ->
            val x = left + w * scale.fraction(t).toFloat()
            drawLine(muted, Offset(x, tickTop), Offset(x, tickTop + tickLen), stroke)
            val text = measurer.measure(scale.label(t), labelStyle)
            val lx = (x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(lx, tickTop + tickLen + 1.dp.toPx()))
        }
    }
}
