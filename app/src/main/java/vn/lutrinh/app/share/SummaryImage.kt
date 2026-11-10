package vn.lutrinh.app.share

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.PathParser
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.KmScale
import vn.lutrinh.app.domain.usecase.ShareStats
import androidx.compose.ui.graphics.toArgb
import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.Palette

/**
 * "Ảnh tóm tắt" (R18): one portrait 1080 × 1920 PNG drawn on a Canvas (docs/03-screens.md
 * "Share trip" has the layout): title, dates, the trip map, the km readout (total, counts, scale
 * bar), up to four photos and a small "Lữ Trình" mark. Fixed pixel sizes, so the image looks the same on every phone.
 */
object SummaryImage {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    private const val MARGIN = 60

    /** The map area; the map is rendered at this size with [MAP_PIXEL_RATIO]. */
    const val MAP_WIDTH = WIDTH - 2 * MARGIN
    const val MAP_HEIGHT = 980
    const val MAP_PIXEL_RATIO = 2.5f
    const val MAP_PADDING = 70

    // docs/08-design.md tokens: topo parchment, ink text, the signal-orange route accent, grid lines.
    private val PAPER = Palette.Parchment.toArgb()
    private val INK = Palette.Ink.toArgb()
    private val MUTED = Palette.InkMuted.toArgb()
    private val SIGNAL = Palette.Signal.toArgb()
    private val LINE = Palette.Grid.toArgb()
    private val CARD = Palette.Card.toArgb()
    private val LEDGE = Palette.Ledge.toArgb()
    private val FOREST = Palette.Forest.toArgb()

    /** The specimen's radii at 3 px per dp (1080 px ≈ a 360 dp phone): panel 22, thumbnail 12. */
    private const val PANEL_R = 66f
    private const val THUMB_R = 36f

    /** Faces of the image: Noto Sans for words, Barlow Condensed for numbers (as in the app). */
    data class Fonts(
        val title: Typeface = Typeface.DEFAULT_BOLD,
        val text: Typeface = Typeface.DEFAULT,
        val numerals: Typeface = Typeface.DEFAULT_BOLD,
    )

    data class Input(
        val title: String,
        val dates: String,
        val stats: ShareStats,
        val map: Bitmap,
        /** At most four, already chosen (TripShare.imagePhotos) and decoded. */
        val photos: List<Bitmap>,
        val fonts: Fonts = Fonts(),
    )

    fun render(input: Input): Bitmap {
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)
        val contentW = WIDTH - 2 * MARGIN
        var y = 96f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 64f; typeface = input.fonts.title }
        val title = StaticLayout.Builder.obtain(input.title, 0, input.title.length, titlePaint, contentW)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f).build()
        c.save(); c.translate(MARGIN.toFloat(), y); title.draw(c); c.restore()
        y += title.height + 14f
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 38f; typeface = input.fonts.text }
        c.drawText(TextUtils.ellipsize(input.dates, datePaint, contentW.toFloat(), TextUtils.TruncateAt.END).toString(), MARGIN.toFloat(), y - datePaint.ascent(), datePaint)
        y += datePaint.descent() - datePaint.ascent() + 36f

        // Map as a panel: 22 dp corners and a grid line.
        val mapRect = RectF(MARGIN.toFloat(), y, (MARGIN + MAP_WIDTH).toFloat(), y + MAP_HEIGHT)
        val shader = BitmapShader(input.map, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(MAP_WIDTH.toFloat() / input.map.width, MAP_HEIGHT.toFloat() / input.map.height)
                postTranslate(mapRect.left, mapRect.top)
            })
        }
        c.drawRoundRect(mapRect, PANEL_R, PANEL_R, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        c.drawRoundRect(mapRect, PANEL_R, PANEL_R, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = LINE })
        y = mapRect.bottom + 40f

        // Stats as a readout panel (docs/08 §5, the specimen's `.pan`): a Card panel with a solid
        // ledge; the km total large, the counts on its baseline to the right (or under it when they
        // do not fit), then a signal-orange scale bar of the total.
        val s = input.stats
        val pad = 48f
        val panelTop = y
        val panelX = MARGIN.toFloat()
        val panelW = contentW.toFloat()
        val gap = 20f
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 112f; typeface = input.fonts.numerals; fontFeatureSettings = "tnum" }
        val unit = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 44f; typeface = input.fonts.title }
        val countNum = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 60f; typeface = input.fonts.numerals; fontFeatureSettings = "tnum" }
        val lab = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 32f; typeface = input.fonts.text }
        val kmText = DisplayFormat.kmNumber(s.distanceKm.toLong())
        val inL = panelX + pad
        val inR = panelX + panelW - pad
        val baseline = y + pad - num.ascent() * 0.86f
        val kmEnd = inL + num.measureText(kmText) + 14f
        val counts = listOf(
            DisplayFormat.count(s.stepCount.toLong()) to "điểm dừng",
            DisplayFormat.count(s.photoCount.toLong()) to "ảnh",
            DisplayFormat.count(s.days.toLong()) to "ngày",
        )
        val countW = counts.map { (v, l) -> countNum.measureText(v) + 10f + lab.measureText(l) }
        val countsW = countW.sum() + 36f * (counts.size - 1)
        val countsFit = kmEnd + unit.measureText("km") + 48f + countsW <= inR
        var countsY = baseline
        var cx = inR - countsW
        if (!countsFit) {
            countsY = baseline + 84f
            cx = inL
        }
        val scale = KmScale.of(s.distanceKm.toDouble())
        val panelBottom = countsY + 28f + (if (scale != null) 16f + 12f + 4f + 40f else 0f) + pad
        c.drawRoundRect(RectF(panelX, panelTop + 6f, panelX + panelW, panelBottom + 6f), PANEL_R, PANEL_R, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEDGE })
        c.drawRoundRect(RectF(panelX, panelTop, panelX + panelW, panelBottom), PANEL_R, PANEL_R, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        c.drawText(kmText, inL, baseline, num)
        c.drawText("km", kmEnd, baseline, unit)
        counts.forEachIndexed { i, (v, l) ->
            c.drawText(v, cx, countsY, countNum)
            c.drawText(l, cx + countNum.measureText(v) + 10f, countsY, lab)
            cx += countW[i] + 36f
        }
        y = countsY + 28f
        drawScaleBar(c, scale, inL, y, inR - inL, input.fonts.numerals)
        y = panelBottom + 6f + 36f

        // Photos: one row of up to four squares, centre-cropped.
        if (input.photos.isNotEmpty()) {
            val size = (contentW - gap * 3) / 4
            val rowW = input.photos.size * size + (input.photos.size - 1) * gap
            var x = MARGIN + (contentW - rowW) / 2
            input.photos.take(4).forEach { bmp ->
                drawCropped(c, bmp, RectF(x, y, x + size, y + size), THUMB_R)
                x += size + gap
            }
        }

        // Brand mark, bottom centre: the app's own route glyph (TopoIcons.ROUTE, as in the app
        // icon: open start ring, a switchback, the end dot) and the app name.
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 40f; typeface = input.fonts.title }
        val name = "Lữ Trình"
        val nameW = brand.measureText(name)
        val glyph = 52f
        val total = glyph + 18f + nameW
        val bx = (WIDTH - total) / 2
        val by = HEIGHT - 112f
        c.save()
        c.translate(bx, by)
        c.scale(glyph / 24f, glyph / 24f)
        c.drawPath(PathParser.createPathFromPathData(TopoIcons.ROUTE), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SIGNAL; style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        c.drawPath(PathParser.createPathFromPathData(TopoIcons.ROUTE_DOT), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOREST })
        c.restore()
        c.drawText(name, bx + glyph + 18f, by + glyph / 2 - (brand.ascent() + brand.descent()) / 2, brand)
        return out
    }

    /**
     * The km scale bar ([KmScale], docs/08 §5) from ([x], [top]), [w] wide: a 16 px signal bar with
     * alternate blocks filled, ticks and their km labels under it. Returns the bottom; nothing for 0 km.
     */
    private fun drawScaleBar(c: Canvas, scale: KmScale?, x: Float, top: Float, w: Float, numerals: Typeface): Float {
        scale ?: return top
        val stroke = 3f
        val barH = 16f
        val bar = RectF(x + stroke / 2, top + stroke / 2, x + w - stroke / 2, top + barH)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SIGNAL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SIGNAL; style = Paint.Style.STROKE; strokeWidth = stroke }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 30f; typeface = numerals; fontFeatureSettings = "tnum" }
        scale.ticks.forEachIndexed { i, t ->
            if (i % 2 == 0) {
                val x0 = bar.left + bar.width() * scale.fraction(t).toFloat()
                val x1 = bar.left + bar.width() * scale.fraction(t + scale.step).toFloat()
                if (x1 > x0) c.drawRect(x0, bar.top, x1, bar.bottom, fill)
            }
        }
        c.drawRect(bar, line)
        val tickEnd = bar.bottom + 12f
        scale.ticks.forEach { t ->
            val tx = bar.left + bar.width() * scale.fraction(t).toFloat()
            c.drawLine(tx, bar.bottom, tx, tickEnd, line)
            val text = scale.label(t)
            val tw = label.measureText(text)
            c.drawText(text, (tx - tw / 2).coerceIn(x, x + w - tw), tickEnd + 4f - label.ascent(), label)
        }
        return tickEnd + 4f + label.descent() - label.ascent()
    }

    private fun drawCropped(c: Canvas, bmp: Bitmap, dst: RectF, radius: Float) {
        val scale = maxOf(dst.width() / bmp.width, dst.height() / bmp.height)
        val dx = dst.left + (dst.width() - bmp.width * scale) / 2
        val dy = dst.top + (dst.height() - bmp.height * scale) / 2
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) })
        }
        c.drawRoundRect(dst, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
    }
}
