package vn.lutrinh.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** Role of a step pin (docs/05-map.md "Step pins"). */
internal enum class PinKind { STEP, START, END, CONTEXT, DRAFT }

/** Colours of the pins, taken from the app theme (docs/08 "Map"). */
internal data class PinColors(val step: Int, val start: Int, val end: Int, val accent: Int, val context: Int)

/** Map image id of a pin; one bitmap per label × kind × selection, drawn once per style. */
internal fun pinImageId(label: String, kind: PinKind, selected: Boolean) = "pin-$label-${kind.name}-$selected"

/** Forest edge around a pin (31 % forest, docs/08): a line instead of a drop shadow, so a pin still reads on imagery. */
private const val EDGE = 0x501F2A24

/**
 * Numbered pin drawn in code (no image assets), edged with a 1 dp forest line (docs/08 "Pins"),
 * after the specimen's map circles (a filled disc in a 3 px white ring):
 * * STEP: waypoint-blue disc (24 dp), 3 dp white ring, white number.
 * * START: trail-green disc (26 dp), 3 dp white ring, white number: "set off here".
 * * END: forest disc (26 dp), 3 dp white ring, white number: "arrived".
 * * CONTEXT (picker): small forest disc (22 dp), white ring.
 * * DRAFT (R20): hollow pin (22 dp): translucent white disc, dashed waypoint ring, no number.
 * * selected: the same pin 1.3× larger, signal-orange ring, on a translucent signal halo (54 dp).
 */
internal fun pinBitmap(
    context: Context, label: String, kind: PinKind, selected: Boolean, colors: PinColors,
    density: Float = context.resources.displayMetrics.density,
): Bitmap {
    val d = density
    val base = when (kind) {
        PinKind.STEP -> 24f
        PinKind.START -> 26f
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
        drawEdge(c, cx, r, d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * d
        paint.color = if (selected) colors.accent else colors.step
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3.5f * d, 2.5f * d), 0f)
        c.drawCircle(cx, cx, r - paint.strokeWidth / 2, paint)
        return bmp
    }
    val (fill, ring, text) = when (kind) {
        PinKind.STEP -> Triple(colors.step, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        PinKind.START -> Triple(colors.start, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        PinKind.END -> Triple(colors.end, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        PinKind.CONTEXT, PinKind.DRAFT -> Triple(colors.context, android.graphics.Color.WHITE, android.graphics.Color.WHITE)
    }
    val ringWidth = (if (kind == PinKind.CONTEXT) 2f else 3f) * d * (if (selected) 1.2f else 1f)
    // Ring disc, the fill inside it, then the forest edge.
    paint.color = if (selected) colors.accent else ring
    c.drawCircle(cx, cx, r, paint)
    paint.color = fill
    c.drawCircle(cx, cx, r - ringWidth, paint)
    drawEdge(c, cx, r, d, paint)
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

/** The 1 dp forest edge just outside a pin's disc. */
private fun drawEdge(c: Canvas, cx: Float, r: Float, d: Float, paint: Paint) {
    val style = paint.style
    val width = paint.strokeWidth
    val effect = paint.pathEffect
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * d
    paint.pathEffect = null
    paint.color = EDGE
    c.drawCircle(cx, cx, r + 0.5f * d, paint)
    paint.style = style
    paint.strokeWidth = width
    paint.pathEffect = effect
}
