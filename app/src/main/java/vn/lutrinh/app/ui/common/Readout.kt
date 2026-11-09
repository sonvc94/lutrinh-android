package vn.lutrinh.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.KmScale
import vn.lutrinh.app.ui.theme.numerals

/** One count of the readout line: "10" + "điểm dừng". */
data class ReadoutCount(val value: String, val label: String)

/**
 * The trip's numbers as an atlas readout (docs/08 §5 "Stats"), in place of the old stat tiles:
 * the km total set large in the numeral face with its unit and [kmNote], a chàm scale bar of that
 * total under it ([KmScaleBar]), and the counts as one line of type ("10 điểm dừng   12 ảnh").
 * No boxes, no fills: the number and the bar are the picture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KmReadout(
    km: Double,
    kmUnit: String,
    counts: List<ReadoutCount>,
    modifier: Modifier = Modifier,
    kmNote: String? = null,
    /** A map overlay ("Bản đồ của tôi"): a smaller total, the counts on its line, right-aligned. */
    compact: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val numberStyle = (if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge).numerals()
    val countStyle = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall).numerals()
    val countRow: @Composable (ReadoutCount) -> Unit = { c ->
        // One node per count for TalkBack ("10 điểm dừng"); the texts stay findable.
        Row(Modifier.semantics(mergeDescendants = true) {}) {
            Text(c.value, style = countStyle, color = ink, modifier = Modifier.alignByBaseline())
            Text(c.label, style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.alignByBaseline().padding(start = 4.dp))
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(DisplayFormat.kmNumber(km), style = numberStyle, color = ink, modifier = Modifier.alignByBaseline())
            Text(kmUnit, style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.alignByBaseline().padding(start = 6.dp))
            if (kmNote != null) {
                Text(kmNote, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.alignByBaseline().padding(start = 8.dp))
            }
            if (compact) {
                Spacer(Modifier.weight(1f))
                counts.forEach { c -> Box(Modifier.alignByBaseline().padding(start = 16.dp)) { countRow(c) } }
            }
        }
        KmScaleBar(km, Modifier.fillMaxWidth().padding(top = 4.dp))
        if (!compact && counts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                counts.forEach { countRow(it) }
            }
        }
    }
}

/**
 * A cartographic scale bar whose length is [km] ([KmScale]): a 6 dp bar in chàm, alternate blocks
 * filled, ticks under the block edges with their km labelled in the numeral face. Nothing for 0 km.
 */
@Composable
fun KmScaleBar(km: Double, modifier: Modifier = Modifier) {
    val scale = remember(km) { KmScale.of(km) } ?: return
    val cham = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.numerals().copy(color = muted)
    Canvas(modifier.height(30.dp).clearAndSetSemantics { contentDescription = "" }) {
        val stroke = 1.dp.toPx()
        val barH = 6.dp.toPx()
        val w = size.width - stroke
        val left = stroke / 2
        val top = stroke / 2
        drawRect(paper, Offset(left, top), Size(w, barH))
        scale.ticks.forEachIndexed { i, t ->
            if (i % 2 == 0) {
                val x0 = left + w * scale.fraction(t).toFloat()
                val x1 = left + w * scale.fraction(t + scale.step).toFloat()
                if (x1 > x0) drawRect(cham, Offset(x0, top), Size(x1 - x0, barH))
            }
        }
        drawRect(cham, Offset(left, top), Size(w, barH), style = Stroke(stroke))
        val tickTop = top + barH
        val tickLen = 4.dp.toPx()
        scale.ticks.forEach { t ->
            val x = left + w * scale.fraction(t).toFloat()
            drawLine(cham, Offset(x, tickTop), Offset(x, tickTop + tickLen), stroke)
            val text = measurer.measure(scale.label(t), labelStyle)
            val lx = (x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(lx, tickTop + tickLen + 1.dp.toPx()))
        }
    }
}
