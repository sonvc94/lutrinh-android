package vn.lutrinh.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import vn.lutrinh.app.domain.model.GeoPoint

/**
 * Cover of a trip without a photo (docs/08-design.md "Imagery"): the trip's own route drawn on
 * paper, with a graticule, instead of a gradient. Steps in timeline order; the start is an open
 * ring, the end a filled ink dot. No steps = plain paper with the graticule.
 */
@Composable
fun RouteCover(route: List<GeoPoint>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier.background(scheme.surfaceContainerLow)) {
        val pad = 28.dp.toPx()
        val fit = RouteFit.of(route, size.width, size.height, pad)
        val hair = 1.dp.toPx()
        fit.gridX.forEach { x -> drawLine(scheme.outlineVariant, Offset(x, 0f), Offset(x, size.height), hair) }
        fit.gridY.forEach { y -> drawLine(scheme.outlineVariant, Offset(0f, y), Offset(size.width, y), hair) }
        val pts = fit.points.map { Offset(it.first, it.second) }
        if (pts.size >= 2) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, scheme.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.subList(1, pts.size - 1).forEach { drawCircle(scheme.primary, 3.dp.toPx(), it) }
            drawCircle(scheme.onSurface, 5.dp.toPx(), pts.last())
        }
        pts.firstOrNull()?.let {
            drawCircle(scheme.surfaceContainerLowest, 5.dp.toPx(), it)
            drawCircle(scheme.primary, 5.dp.toPx(), it, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * Pure maths of [RouteCover]: an equirectangular projection (longitude scaled by cos of the mean
 * latitude) fitted into [w] × [h] px minus [pad] on each side, centred; plus graticule lines at a
 * round step (about 3-6 lines across). Screen y grows downwards.
 */
data class RouteFit(val points: List<Pair<Float, Float>>, val gridX: List<Float>, val gridY: List<Float>) {
    companion object {
        private val STEPS = listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)

        fun of(route: List<GeoPoint>, w: Float, h: Float, pad: Float): RouteFit {
            if (route.isEmpty()) return RouteFit(emptyList(), listOf(w / 3, 2 * w / 3), listOf(h / 2))
            val minLat = route.minOf { it.latitude }; val maxLat = route.maxOf { it.latitude }
            val minLng = route.minOf { it.longitude }; val maxLng = route.maxOf { it.longitude }
            val k = cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = max((maxLng - minLng) * k, 1e-4)
            val spanY = max(maxLat - minLat, 1e-4)
            val scale = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
            val cx = (minLng + maxLng) / 2; val cy = (minLat + maxLat) / 2
            fun x(lng: Double) = (w / 2 + (lng - cx) * k * scale).toFloat()
            fun y(lat: Double) = (h / 2 - (lat - cy) * scale).toFloat()
            val visibleDeg = max(w / (k * scale), h / scale)
            val step = STEPS.firstOrNull { visibleDeg / it <= 6 } ?: STEPS.last()
            fun lines(from: Double, to: Double, toPx: (Double) -> Float): List<Float> {
                val out = mutableListOf<Float>()
                var v = floor(from / step) * step
                while (v <= to) { out += toPx(v); v += step }
                return out
            }
            val halfW = w / 2 / (k * scale); val halfH = h / 2 / scale
            return RouteFit(
                points = route.map { x(it.longitude) to y(it.latitude) },
                gridX = lines(cx - halfW, cx + halfW, ::x).filter { it in 0f..w },
                gridY = lines(cy - halfH, cy + halfH, ::y).filter { it in 0f..h },
            )
        }
    }
}
