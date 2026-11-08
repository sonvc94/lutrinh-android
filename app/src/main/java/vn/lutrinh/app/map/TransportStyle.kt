package vn.lutrinh.app.map

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
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

/** How a leg line is stroked (docs/05-map.md "Legs"). */
enum class LineDash { SOLID, DASHED, DOTTED }

/**
 * Look of each transport mode: one table used by the map, the timeline and the mode sheet.
 * Colours are chosen to stay apart from each other and from the theme green used for legs
 * without a mode.
 */
data class TransportStyle(
    val color: Color,
    val dash: LineDash,
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    val hex: String get() = color.hex()

    companion object {
        fun of(mode: TransportMode): TransportStyle = when (mode) {
            TransportMode.WALK -> TransportStyle(Color(0xFF6D4C41), LineDash.DOTTED, Icons.AutoMirrored.Filled.DirectionsWalk, R.string.transport_walk)
            TransportMode.MOTORBIKE -> TransportStyle(Color(0xFFE65100), LineDash.SOLID, Icons.Filled.TwoWheeler, R.string.transport_motorbike)
            TransportMode.CAR -> TransportStyle(Color(0xFF3949AB), LineDash.SOLID, Icons.Filled.DirectionsCar, R.string.transport_car)
            TransportMode.BUS -> TransportStyle(Color(0xFF8E24AA), LineDash.SOLID, Icons.Filled.DirectionsBus, R.string.transport_bus)
            TransportMode.TRAIN -> TransportStyle(Color(0xFF455A64), LineDash.SOLID, Icons.Filled.Train, R.string.transport_train)
            TransportMode.PLANE -> TransportStyle(Color(0xFF039BE5), LineDash.DASHED, Icons.Filled.Flight, R.string.transport_plane)
            TransportMode.BOAT -> TransportStyle(Color(0xFF00897B), LineDash.DASHED, Icons.Filled.DirectionsBoat, R.string.transport_boat)
        }
    }
}

/** "#RRGGBB" for MapLibre style expressions. */
internal fun Color.hex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/** Map image id of the round mode badge drawn at the middle of a leg. */
internal fun TransportMode.badgeId() = "mode-$code"

/** Round badges (white ring, mode colour, white icon) for every mode, as bitmaps for MapLibre. */
@Composable
internal fun rememberTransportBadges(sizeDp: Float = 26f): Map<TransportMode, Bitmap> {
    val density = LocalDensity.current
    val painters = TransportMode.entries.associateWith { rememberVectorPainter(TransportStyle.of(it).icon) }
    return remember(density, sizeDp) {
        val px = (sizeDp * density.density).toInt()
        painters.mapValues { (mode, painter) ->
            val image = ImageBitmap(px, px)
            val size = Size(px.toFloat(), px.toFloat())
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
                val r = px / 2f
                drawCircle(Color.White, r, Offset(r, r))
                drawCircle(TransportStyle.of(mode).color, r - 2f * density.density, Offset(r, r))
                val inset = px * 0.22f
                translate(inset, inset) {
                    with(painter) { draw(Size(px - 2 * inset, px - 2 * inset), colorFilter = ColorFilter.tint(Color.White)) }
                }
            }
            image.asAndroidBitmap()
        }
    }
}
