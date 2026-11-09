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

private const val SHADOW = 0x50102A20

/**
 * Numbered pin drawn in code (no image assets), with a soft drop shadow:
 * * STEP: route-green disc (24 dp), 2 dp white ring, white number.
 * * START: white disc (28 dp), 3 dp route-green ring, green number — reads as "set off here".
 * * END: clay disc (28 dp), 2.5 dp white ring, white number.
 * * CONTEXT (picker): small clay disc (22 dp), white ring.
 * * DRAFT (R20): hollow pin (22 dp): translucent white disc, dashed route-green ring, no number.
 * * selected: the same pin 1.3× larger, rice-gold ring, on a translucent gold halo (54 dp).
 */
internal fun pinBitmap(
    context: Context, label: String, kind: PinKind, selected: Boolean, colors: PinColors,
    density: Float = context.resources.displayMetrics.density,
): Bitmap {
    val d = density
    val base = when (kind) {
        PinKind.STEP -> 24f
        PinKind.START, PinKind.END -> 28f
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
        paint.setShadowLayer(2f * d, 0f, 1f * d, SHADOW)
        c.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()
        paint.alpha = 255
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
    // Ring disc with the shadow, then the fill inside it.
    paint.color = if (selected) colors.accent else ring
    paint.setShadowLayer(2.5f * d, 0f, 1.2f * d, SHADOW)
    c.drawCircle(cx, cx, r, paint)
    paint.clearShadowLayer()
    paint.color = fill
    c.drawCircle(cx, cx, r - ringWidth, paint)
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
