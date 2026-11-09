package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale

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
        sb.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">\n")
        sb.append("<meta name=\"generator\" content=\"Lữ Trình\">\n")
        sb.append("<title>").append(esc(content.title)).append("</title>\n<style>").append(CSS).append("</style>\n</head>\n<body>\n")

        sb.append("<header>\n<h1>").append(esc(content.title)).append("</h1>\n")
        sb.append("<p class=\"dates\">").append(esc(content.dates)).append("</p>\n")
        if (content.description.isNotBlank()) sb.append("<p class=\"desc\">").append(text(content.description)).append("</p>\n")
        sb.append("</header>\n")

        if (content.mapJpeg != null) {
            sb.append("<figure class=\"map\"><img alt=\"Bản đồ hành trình\" src=\"").append(dataUri(content.mapJpeg)).append("\">")
            sb.append("<figcaption>Bản đồ: ").append(esc(content.attribution)).append("</figcaption></figure>\n")
        }

        val s = content.stats
        sb.append("<ul class=\"stats\">")
        listOf(s.distanceKm to "km", s.days to "ngày", s.stepCount to "điểm dừng", s.photoCount to "ảnh").forEach { (n, label) ->
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
            sb.append("<article class=\"step\">\n<div class=\"num\">").append(i + 1).append("</div>\n<div class=\"body\">\n")
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
        sb.append("<p>Tạo bằng Lữ Trình — nhật ký du lịch trên điện thoại.</p></footer>\n</body>\n</html>\n")
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

    private val VI: Locale = Locale.forLanguageTag("vi-VN")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d/M/yyyy", VI)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", VI)

    private fun dayLabel(d: LocalDate) = d.format(DAY).replaceFirstChar { it.titlecase(VI) }

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

    /** Lữ Trình colours (docs/03 theme): cream paper, terrace green, clay accent. */
    private const val CSS = """
*{box-sizing:border-box}
body{margin:0;background:#FBF8F2;color:#1C2421;font:16px/1.55 -apple-system,"Segoe UI",Roboto,"Noto Sans",Arial,sans-serif}
header,main,footer,.stats,.map{max-width:760px;margin-left:auto;margin-right:auto}
header{padding:28px 20px 8px}
h1{margin:0 0 4px;font-size:30px;line-height:1.2;color:#1F5C45}
.dates{margin:0;color:#4A524E}
.desc{margin:12px 0 0}
.map{margin:16px auto 0;padding:0 20px}
.map img{display:block;width:100%;height:auto;border-radius:16px;border:1px solid #E9E6DE}
.map figcaption{font-size:12px;color:#7A837E;text-align:right;margin-top:4px}
.stats{list-style:none;display:flex;gap:8px;padding:16px 20px;margin-top:0;margin-bottom:0}
.stats li{flex:1;background:#CDE8D9;border-radius:12px;padding:8px 10px}
.stats b{display:block;font-size:22px;color:#0B2E21}
.stats span{font-size:12px;color:#0B2E21}
main{padding:0 20px}
.day h2{font-size:15px;color:#B85C2E;margin:24px 0 8px;text-transform:none}
.step{display:flex;gap:12px;padding:6px 0 14px}
.num{flex:none;width:30px;height:30px;border-radius:50%;background:#1F5C45;color:#fff;font-weight:700;display:flex;align-items:center;justify-content:center;font-size:14px}
.body{flex:1;min-width:0}
h3{margin:2px 0 0;font-size:18px}
.time{margin:0;color:#4A524E;font-size:13px}
.note{margin:6px 0 0;white-space:normal;overflow-wrap:anywhere}
.photos{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:6px;margin-top:10px}
.photos img{width:100%;height:auto;border-radius:10px;display:block}
footer{padding:24px 20px 40px;color:#7A837E;font-size:13px}
footer p{margin:4px 0}
"""
}
