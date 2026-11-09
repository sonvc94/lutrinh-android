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
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.ShareStats
import androidx.compose.ui.graphics.toArgb
import vn.lutrinh.app.ui.theme.Palette

/**
 * "Ảnh tóm tắt" (R18): one portrait 1080 × 1920 PNG drawn on a Canvas (docs/03-screens.md
 * "Share trip" has the layout): title, dates, the trip map, four stats, up to four photos and
 * a small "Lữ Trình" mark. Fixed pixel sizes, so the image looks the same on every phone.
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

    // docs/08-design.md tokens: limestone paper, ink text, one chàm accent, hairlines.
    private val PAPER = Palette.Paper.toArgb()
    private val INK = Palette.Ink.toArgb()
    private val MUTED = Palette.InkMuted.toArgb()
    private val CHAM = Palette.Cham.toArgb()
    private val LINE = Palette.Hairline.toArgb()

    /** Faces of the image: Be Vietnam Pro for words, Barlow Condensed for numbers (as in the app). */
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

        // Map with rounded corners and a hairline border.
        val mapRect = RectF(MARGIN.toFloat(), y, (MARGIN + MAP_WIDTH).toFloat(), y + MAP_HEIGHT)
        val shader = BitmapShader(input.map, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(MAP_WIDTH.toFloat() / input.map.width, MAP_HEIGHT.toFloat() / input.map.height)
                postTranslate(mapRect.left, mapRect.top)
            })
        }
        c.drawRoundRect(mapRect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        c.drawRoundRect(mapRect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = LINE })
        y = mapRect.bottom + 40f

        // Stats: four cells on hairlines (no tinted fill).
        val s = input.stats
        val tiles = listOf("${s.stepCount}" to "điểm dừng", "${s.photoCount}" to "ảnh", DisplayFormat.kmNumber(s.distanceKm.toLong()) to "km", "${s.days}" to "ngày")
        val gap = 20f
        val tileW = (contentW - gap * (tiles.size - 1)) / tiles.size
        val tileH = 160f
        val tileLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = LINE }
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 64f; typeface = input.fonts.numerals; fontFeatureSettings = "tnum" }
        val lab = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 32f; typeface = input.fonts.text }
        tiles.forEachIndexed { i, (value, label) ->
            val left = MARGIN + i * (tileW + gap)
            c.drawRoundRect(RectF(left + 1f, y + 1f, left + tileW - 1f, y + tileH - 1f), 12f, 12f, tileLine)
            c.drawText(TextUtils.ellipsize(value, num, tileW - 48f, TextUtils.TruncateAt.END).toString(), left + 24f, y + 24f - num.ascent(), num)
            c.drawText(label, left + 24f, y + tileH - 28f, lab)
        }
        y += tileH + 40f

        // Photos: one row of up to four squares, centre-cropped.
        if (input.photos.isNotEmpty()) {
            val size = (contentW - gap * 3) / 4
            val rowW = input.photos.size * size + (input.photos.size - 1) * gap
            var x = MARGIN + (contentW - rowW) / 2
            input.photos.take(4).forEach { bmp ->
                drawCropped(c, bmp, RectF(x, y, x + size, y + size), 12f)
                x += size + gap
            }
        }

        // Brand mark, bottom centre: a small route glyph and the app name.
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 40f; typeface = input.fonts.title }
        val name = "Lữ Trình"
        val nameW = brand.measureText(name)
        val glyph = 44f
        val total = glyph + 18f + nameW
        val bx = (WIDTH - total) / 2
        val by = HEIGHT - 110f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.color = CHAM; c.drawCircle(bx + 10f, by + glyph - 10f, 9f, dot)
        dot.color = INK; c.drawCircle(bx + glyph - 10f, by + 10f, 9f, dot)
        val link = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHAM; style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(bx + 10f, by + glyph - 10f, bx + glyph - 10f, by + 10f, link)
        c.drawText(name, bx + glyph + 18f, by + glyph / 2 - (brand.ascent() + brand.descent()) / 2, brand)
        return out
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
