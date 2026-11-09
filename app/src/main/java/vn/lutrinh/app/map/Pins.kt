package vn.lutrinh.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** Role of a step pin (docs/05-map.md "Step pins"). */
internal enum class PinKind { STEP, START, END, CONTEXT, DRAFT }

/** Colours of the pins, taken from the app theme. */
internal data class PinColors(val route: Int, val end: Int, val accent: Int, val context: Int)

/** Map image id of a pin; one bitmap per label × kind × selection, drawn once per style. */
internal fun pinImageId(label: String, kind: PinKind, selected: Boolean) = "pin-$label-${kind.name}-$selected"

/** Ink edge around a pin (31 % ink, docs/08): a line instead of a drop shadow, so a pin still reads on imagery. */
private const val EDGE = 0x50171E23

/**
 * Numbered pin drawn in code (no image assets), edged with a 1 dp ink line (no shadow, docs/08).
 * The three route roles differ by shape as well as colour, like the route cover and the app mark:
 * * STEP: route chàm disc (24 dp), 2 dp white ring, white number.
 * * START: white disc (28 dp), 3 dp chàm ring, chàm number: an open ring, "set off here".
 * * END: ink square (26 dp, 4 dp corners), 2.5 dp white ring, white number: "arrived".
 * * CONTEXT (picker): small ink disc (22 dp), white ring.
 * * DRAFT (R20): hollow pin (22 dp): translucent white disc, dashed chàm ring, no number.
 * * selected: the same pin 1.3× larger, bright chàm ring, on a translucent bright chàm halo (54 dp).
 */
internal fun pinBitmap(
    context: Context, label: String, kind: PinKind, selected: Boolean, colors: PinColors,
    density: Float = context.resources.displayMetrics.density,
): Bitmap {
    val d = density
    val base = when (kind) {
        PinKind.STEP -> 24f
        PinKind.START -> 28f
        PinKind.END -> 26f
        PinKind.CONTEXT, PinKind.DRAFT -> 22f
    }
    val disc = (if (selected) base * 1.3f else base) * d
    val halo = if (selected) 54f * d else 0f
    val size = (maxOf(disc + 8f * d, halo)).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val r = disc / 2f
    if (selected) {
        paint.color = colors.accent
        paint.alpha = 70
        c.drawCircle(cx, cx, halo / 2f, paint)
        paint.alpha = 255
    }
    if (kind == PinKind.DRAFT) {
        // Hollow and dashed: "not on the route yet".
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 225
        c.drawCircle(cx, cx, r, paint)
        paint.alpha = 255
        drawEdge(c, cx, r, false, d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * d
        paint.color = if (selected) colors.accent else colors.route
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3.5f * d, 2.5f * d), 0f)
        c.drawCircle(cx, cx, r - paint.strokeWidth / 2, paint)
        return bmp
    }
    val (fill, ring, text) = when (kind) {
        PinKind.STEP -> Triple(colors.route, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        PinKind.START -> Triple(android.graphics.Color.WHITE, colors.route, colors.route)
        PinKind.END -> Triple(colors.end, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        PinKind.CONTEXT, PinKind.DRAFT -> Triple(colors.context, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
    }
    val ringWidth = when (kind) {
        PinKind.START -> 3f
        PinKind.END -> 2.5f
        else -> 2f
    } * d * (if (selected) 1.2f else 1f)
    // Ring shape, the fill inside it, then the ink edge.
    val square = kind == PinKind.END
    paint.color = if (selected) colors.accent else ring
    drawShape(c, cx, r, square, d, paint)
    paint.color = fill
    drawShape(c, cx, r - ringWidth, square, d, paint)
    drawEdge(c, cx, r, square, d, paint)
    if (label.isNotEmpty()) {
        paint.color = text
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = (if (label.length > 2) 9.5f else 12f) * d * (disc / (base * d))
        val y = cx - (paint.descent() + paint.ascent()) / 2
        c.drawText(label, cx, y, paint)
    }
    return bmp
}

/** A disc, or for END a square with 4 dp corners, of half-size [r] around ([cx], [cx]). */
private fun drawShape(c: Canvas, cx: Float, r: Float, square: Boolean, d: Float, paint: Paint) {
    if (square) {
        val corner = (4f * d * r / (13f * d)).coerceAtLeast(1f)
        c.drawRoundRect(cx - r, cx - r, cx + r, cx + r, corner, corner, paint)
    } else {
        c.drawCircle(cx, cx, r, paint)
    }
}

/** The 1 dp ink edge just outside a pin's shape. */
private fun drawEdge(c: Canvas, cx: Float, r: Float, square: Boolean, d: Float, paint: Paint) {
    val style = paint.style
    val width = paint.strokeWidth
    val effect = paint.pathEffect
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * d
    paint.pathEffect = null
    paint.color = EDGE
    drawShape(c, cx, r + 0.5f * d, square, d, paint)
    paint.style = style
    paint.strokeWidth = width
    paint.pathEffect = effect
}
