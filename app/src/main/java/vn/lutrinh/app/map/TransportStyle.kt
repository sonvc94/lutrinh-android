package vn.lutrinh.app.map

import vn.lutrinh.app.ui.theme.TopoIcons
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex

/**
 * How a leg line is drawn (docs/05-map.md "Legs"): every line is the route chàm; the kind only
 * changes the stroke. GROUND = solid with white casing + soft shadow, FLIGHT = dashed lifted
 * great-circle arc, BOAT = dotted, WALK = thin dotted. [code] is the map feature property value.
 */
enum class LegLine(val code: String) { GROUND("ground"), FLIGHT("flight"), BOAT("boat"), WALK("walk") }

/**
 * Look of each transport mode: one table used by the map, the timeline and the mode sheet.
 * [color] tints the mode's icon (map badge, timeline chip, sheet): one colour for all modes, the
 * route chàm (docs/08 "one accent"); modes differ by icon and by the line kind, not by hue.
 */
data class TransportStyle(
    val color: Color,
    val line: LegLine,
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    val hex: String get() = color.hex()

    companion object {
        fun of(mode: TransportMode): TransportStyle = when (mode) {
            TransportMode.WALK -> TransportStyle(MODE_TINT, LegLine.WALK, TopoIcons.Walk, R.string.transport_walk)
            TransportMode.MOTORBIKE -> TransportStyle(MODE_TINT, LegLine.GROUND, TopoIcons.Motorbike, R.string.transport_motorbike)
            TransportMode.CAR -> TransportStyle(MODE_TINT, LegLine.GROUND, TopoIcons.Car, R.string.transport_car)
            TransportMode.BUS -> TransportStyle(MODE_TINT, LegLine.GROUND, TopoIcons.Bus, R.string.transport_bus)
            TransportMode.TRAIN -> TransportStyle(MODE_TINT, LegLine.GROUND, TopoIcons.Train, R.string.transport_train)
            TransportMode.PLANE -> TransportStyle(MODE_TINT, LegLine.FLIGHT, TopoIcons.Plane, R.string.transport_plane)
            TransportMode.BOAT -> TransportStyle(MODE_TINT, LegLine.BOAT, TopoIcons.Boat, R.string.transport_boat)
        }
    }
}

/** The one tint of every transport mode (docs/08): the route's signal orange, as text-safe SignalDeep. */
private val MODE_TINT: Color = Palette.SignalDeep

/** "#RRGGBB" for MapLibre style expressions. */
internal fun Color.hex(): String = rgbHex()

/** Line kind of a leg; a leg without a mode is a plain ground leg. */
internal fun TransportMode?.legLine(): LegLine = this?.let { TransportStyle.of(it).line } ?: LegLine.GROUND

/** Map image id of the mode badge drawn at the middle of a leg. */
internal fun TransportMode.badgeId(selected: Boolean = false) = "mode-$code" + if (selected) "-sel" else ""

/**
 * Mode badges as bitmaps for MapLibre, keyed by [badgeId]: a white disc with a soft shadow, a
 * thin ring and the icon in the mode colour; the selected variant is filled with the mode
 * colour, white ring and icon, and slightly larger.
 */
@Composable
internal fun rememberTransportBadges(sizeDp: Float = 24f): Map<String, Bitmap> {
    val density = LocalDensity.current
    val painters = TransportMode.entries.associateWith { rememberVectorPainter(TransportStyle.of(it).icon) }
    return remember(density, sizeDp) {
        val d = density.density
        buildMap {
            TransportMode.entries.forEach { mode ->
                listOf(false, true).forEach { selected ->
                    val painter = painters.getValue(mode)
                    val modeColor = TransportStyle.of(mode).color
                    val disc = (if (selected) sizeDp + 4f else sizeDp) * d
                    val shadow = 3f * d
                    val px = (disc + 2 * shadow).toInt()
                    val image = ImageBitmap(px, px)
                    val bmp = image.asAndroidBitmap()
                    val c = android.graphics.Canvas(bmp)
                    val r = disc / 2f
                    val cx = px / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color = if (selected) modeColor.toArgb() else android.graphics.Color.WHITE
                    paint.setShadowLayer(2.5f * d, 0f, 1f * d, 0x55102A20)
                    c.drawCircle(cx, cx, r, paint)
                    paint.clearShadowLayer()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = (if (selected) 2f else 1.5f) * d
                    paint.color = if (selected) android.graphics.Color.WHITE else modeColor.copy(alpha = 0.55f).toArgb()
                    c.drawCircle(cx, cx, r - paint.strokeWidth / 2, paint)
                    val icon = disc * 0.6f
                    val inset = (px - icon) / 2f
                    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
                        translate(inset, inset) {
                            with(painter) {
                                draw(Size(icon, icon), colorFilter = ColorFilter.tint(if (selected) Color.White else modeColor))
                            }
                        }
                    }
                    put(mode.badgeId(selected), bmp)
                }
            }
        }
    }
}
