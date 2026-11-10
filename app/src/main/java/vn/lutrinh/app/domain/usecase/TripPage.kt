package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * "Trang tóm tắt" (R18): one self-contained, offline HTML file of a trip. Everything is inline
 * (CSS in `<style>`, images as `data:` URIs) and a Content-Security-Policy forbids any request, so
 * the page never loads anything from the network. User text is always HTML-escaped. Pure Kotlin
 * (no Android types), mirrored in Swift (docs/07-ios-port.md).
 */
object TripPage {
    /** Target size of the file (R18). Above it, photos get smaller, then fewer. */
    const val CAP_BYTES = 15_000_000

    /** How photos are exported at one step of the size cap: long edge, JPEG quality, photos per step. */
    data class Level(val maxPx: Int, val quality: Int, val maxPhotosPerStep: Int = Int.MAX_VALUE)

    /** Tried in order until the page fits [CAP_BYTES] (R18); the first one is the normal export. */
    val LEVELS = listOf(
        Level(1280, 80),
        Level(1280, 65),
        Level(1024, 60),
        Level(800, 50),
        Level(800, 50, maxPhotosPerStep = 4),
        Level(800, 50, maxPhotosPerStep = 2),
        Level(800, 50, maxPhotosPerStep = 1),
        Level(800, 50, maxPhotosPerStep = 0),
    )

    /** What the page shows. [steps] in timeline order (R2); [mapJpeg] = the trip map, or null. */
    data class Content(
        val title: String,
        /** Already formatted trip dates, e.g. "3 thg 11 – 6 thg 11, 2025". */
        val dates: String,
        val description: String,
        val tripStart: LocalDate,
        val stats: ShareStats,
        val steps: List<Step>,
        val hideNotes: Boolean,
        val mapJpeg: ByteArray?,
        /** Map credit, shown as plain text under the map (never a link). */
        val attribution: String,
    )

    data class Result(val html: String, val bytes: Int, val level: Level, val photosIncluded: Int, val photosOmitted: Int)

    /**
     * Builds the page at the first [LEVELS] entry whose output is at most [cap] bytes; if none
     * fits, the last level (no photos). [encode] returns the JPEG bytes of a photo resized for a
     * level (long edge ≤ maxPx, quality), or null when the file is missing / unreadable (skipped).
     */
    fun buildWithinCap(content: Content, cap: Int = CAP_BYTES, encode: (Photo, Level) -> ByteArray?): Result {
        var cacheKey: Pair<Int, Int>? = null
        val cache = HashMap<String, ByteArray?>()
        var result: Result? = null
        for (level in LEVELS) {
            val key = level.maxPx to level.quality
            if (key != cacheKey) {
                cache.clear()
                cacheKey = key
            }
            val r = build(content, level) { p -> cache.getOrPut(p.id) { encode(p, level) } }
            result = r
            if (r.bytes <= cap) return r
        }
        return result!!
    }

    /** Builds the page with the photos allowed by [level]; [photoJpeg] gives a photo's bytes (null = skip). */
    fun build(content: Content, level: Level = LEVELS.first(), photoJpeg: (Photo) -> ByteArray?): Result {
        var included = 0
        var omitted = 0
        val sb = StringBuilder(64 * 1024)
        sb.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        // Defence in depth: even if something slipped through, the browser may load nothing.
        sb.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; font-src data:; style-src 'unsafe-inline'\">\n")
        sb.append("<meta name=\"generator\" content=\"Lữ Trình\">\n")
        sb.append("<title>").append(esc(content.title)).append("</title>\n<style>").append(FONT_FACE).append(CSS).append("</style>\n</head>\n<body>\n")

        sb.append("<header>\n<p class=\"dates\">").append(esc(content.dates)).append("</p>\n")
        sb.append("<h1>").append(esc(content.title)).append("</h1>\n")
        if (content.description.isNotBlank()) sb.append("<p class=\"desc\">").append(text(content.description)).append("</p>\n")
        sb.append("</header>\n")

        if (content.mapJpeg != null) {
            sb.append("<figure class=\"map\"><img alt=\"Bản đồ hành trình\" src=\"").append(dataUri(content.mapJpeg)).append("\">")
            sb.append("<figcaption>Bản đồ: ").append(esc(content.attribution)).append("</figcaption></figure>\n")
        }

        val s = content.stats
        sb.append("<ul class=\"stats\">")
        listOf(s.stepCount.toString() to "điểm dừng", s.photoCount.toString() to "ảnh", DisplayFormat.kmNumber(s.distanceKm.toLong()) to "km", s.days.toString() to "ngày").forEach { (n, label) ->
            sb.append("<li><b>").append(n).append("</b><span>").append(label).append("</span></li>")
        }
        sb.append("</ul>\n<main>\n")

        var day: LocalDate? = null
        content.steps.forEachIndexed { i, step ->
            val d = step.arrivedAt.toLocalDate()
            if (d != day) {
                if (day != null) sb.append("</section>\n")
                day = d
                val n = ChronoUnit.DAYS.between(content.tripStart, d) + 1
                sb.append("<section class=\"day\">\n<h2>Ngày ").append(n).append(" · ").append(esc(dayLabel(d))).append("</h2>\n")
            }
            val place = if (i == 0) " first" else if (i == content.steps.lastIndex) " last" else ""
            sb.append("<article class=\"step").append(place).append("\">\n<div class=\"num\">").append(i + 1).append("</div>\n<div class=\"body\">\n")
            sb.append("<h3>").append(esc(step.title)).append("</h3>\n")
            sb.append("<p class=\"time\">").append(step.arrivedAt.format(TIME)).append("</p>\n")
            if (!content.hideNotes && step.note.isNotBlank()) sb.append("<p class=\"note\">").append(text(step.note)).append("</p>\n")
            val photos = step.photos.sortedBy { it.position }
            val allowed = photos.take(level.maxPhotosPerStep)
            omitted += photos.size - allowed.size
            val encoded = allowed.mapNotNull { photoJpeg(it) }
            if (encoded.isNotEmpty()) {
                sb.append("<div class=\"photos\">")
                encoded.forEach { bytes -> sb.append("<img alt=\"\" src=\"").append(dataUri(bytes)).append("\">") }
                sb.append("</div>\n")
            }
            included += encoded.size
            sb.append("</div>\n</article>\n")
        }
        if (day != null) sb.append("</section>\n")
        sb.append("</main>\n<footer>")
        if (omitted > 0) sb.append("<p>Đã lược bớt ").append(omitted).append(" ảnh để trang nhẹ hơn.</p>")
        sb.append("<p>Tạo bằng Lữ Trình, nhật ký du lịch trên điện thoại.</p></footer>\n</body>\n</html>\n")
        val html = sb.toString()
        return Result(html, utf8Length(html), level, included, omitted)
    }

    /** HTML-escapes text for element content and quoted attributes. */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    /** Escaped multi-line text: line breaks kept as `<br>`. */
    private fun text(s: String) = esc(s.trim()).replace("\r\n", "\n").replace("\n", "<br>")

    private fun dataUri(jpeg: ByteArray) = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", DisplayFormat.VI)

    private fun dayLabel(d: LocalDate) = DisplayFormat.dayWithYear(d)

    private fun utf8Length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            n += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) -> { i++; 4 }
                else -> 3
            }
            i++
        }
        return n
    }

    /**
     * Topo-outdoor tokens (docs/08-design.md §2, §8), written as hex because this file is pure
     * Kotlin: Parchment #EFEBDA page, Card #FBFAF2 panels on a 2 px Ledge #D9D4BE (18/22 px
     * corners), Ink #2A2A22, InkMuted #6B6A58, Grid #E3DEC8, Sand #E8E3CE, Forest #1F2A24, Trail
     * #2D7A46 / Waypoint #2B6CB0 / Forest step discs with a 3 px white ring. Signal orange
     * #E4572E only as a line (the page's route mark), never as text. Fonts: Noto Sans for words and
     * Barlow Condensed for numbers. Noto Sans is by name only (a phone or computer without it
     * falls back to its sans); the digits are embedded as a subset in [FONT_FACE] (a `data:` font,
     * the page may load nothing else, CSP `font-src data:`), so numbers look the same everywhere.
     * No gradients, no links.
     */
    private const val FONT_FACE =
        "@font-face{font-family:\"Barlow Condensed\";font-weight:700;font-style:normal;" +
            "src:url(data:font/woff2;base64," + BarlowDigits.WOFF2_BASE64 + ") format(\"woff2\")}\n"

    private const val CSS = """
*{box-sizing:border-box}
body{margin:0;background:#EFEBDA;color:#2A2A22;font:16px/1.55 "Noto Sans",-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
header,main,footer,.stats,.map{max-width:760px;margin-left:auto;margin-right:auto}
header{padding:28px 16px 8px}
.dates{margin:0 0 2px;font-size:13px;line-height:18px;font-weight:700;letter-spacing:.06em;color:#6B6A58}
h1{margin:0;font-size:28px;line-height:36px;font-weight:700;color:#2A2A22}
.desc{margin:12px 0 0;font-size:17px;line-height:26px}
.map{margin:16px auto 0;padding:0 16px}
.map img{display:block;width:100%;height:auto;border-radius:22px;box-shadow:0 2px 0 #D9D4BE}
.map figcaption{font-size:12px;color:#6B6A58;text-align:right;margin-top:8px}
.stats{list-style:none;display:flex;gap:8px;padding:0;margin-top:16px;margin-bottom:0;background:#FBFAF2;border-radius:22px;box-shadow:0 2px 0 #D9D4BE;width:calc(100% - 32px);max-width:728px}
.stats li{flex:1;padding:16px 10px 14px 18px;min-width:0}
.stats b{display:block;font:700 34px/38px "Barlow Condensed","Arial Narrow","Roboto Condensed",sans-serif;letter-spacing:.01em;color:#2A2A22;font-variant-numeric:tabular-nums}
.stats span{display:block;font-size:13px;line-height:18px;font-weight:700;letter-spacing:.06em;color:#6B6A58}
main{padding:0 16px}
.day h2{font-size:13px;line-height:18px;font-weight:700;letter-spacing:.06em;color:#6B6A58;margin:24px 6px 8px}
.step{display:flex;gap:12px;padding:14px;margin:0 0 12px;background:#FBFAF2;border-radius:18px;box-shadow:0 2px 0 #D9D4BE}
.num{flex:none;width:32px;height:32px;border-radius:50%;background:#2B6CB0;color:#fff;border:3px solid #fff;box-shadow:0 0 0 1px #D9D4BE;display:flex;align-items:center;justify-content:center;font:700 16px/1 "Barlow Condensed","Arial Narrow","Roboto Condensed",sans-serif;font-variant-numeric:tabular-nums}
.first .num{background:#2D7A46}
.last .num{background:#1F2A24}
.body{flex:1;min-width:0}
h3{margin:2px 0 0;font-size:17px;line-height:24px;font-weight:700}
.time{margin:0;color:#6B6A58;font-size:14px;line-height:21px;font-variant-numeric:tabular-nums}
.note{margin:6px 0 0;white-space:normal;overflow-wrap:anywhere}
.photos{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:6px;margin-top:10px}
.photos img{width:100%;height:auto;border-radius:12px;display:block;background:#E8E3CE}
footer{padding:20px 22px 40px;color:#6B6A58;font-size:14px}
footer p{margin:4px 0}
"""
}
