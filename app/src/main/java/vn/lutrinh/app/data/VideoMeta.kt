package vn.lutrinh.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Length / recording time of a video and a still frame of it (R29), via the platform retriever. */
object VideoMeta {
    data class Info(val durationMs: Long?, val takenAt: LocalDateTime?)

    fun read(context: Context, uri: Uri): Info = withRetriever({ setDataSource(context, uri) }) {
        Info(
            durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            takenAt = parseDate(extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)),
        )
    }

    /**
     * A frame near the start of [file] (the share image/page shows videos as this still, R29),
     * long edge at most [maxPx]; null when no frame can be decoded.
     */
    fun still(file: File, maxPx: Int): Bitmap? = runCatching {
        withRetriever({ setDataSource(file.path) }) {
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                getScaledFrameAtTime(STILL_AT_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxPx, maxPx)
            } else {
                getFrameAtTime(STILL_AT_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            frame?.let { scaleDown(it, maxPx) }
        }
    }.getOrNull()

    private fun scaleDown(b: Bitmap, maxPx: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= maxPx) return b
        val f = maxPx.toFloat() / long
        val scaled = Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        if (scaled !== b) b.recycle()
        return scaled
    }

    private inline fun <T> withRetriever(open: MediaMetadataRetriever.() -> Unit, use: MediaMetadataRetriever.() -> T): T {
        val r = MediaMetadataRetriever()
        try {
            r.open()
            return r.use()
        } finally {
            runCatching { r.release() }
        }
    }

    /** "20251020T083015.000Z" (UTC, as Android writes it) -> local wall-clock minute; null when absent or unparsable. */
    fun parseDate(raw: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val utc = ZoneId.of("UTC")
        val parsed = FORMATS.firstNotNullOfOrNull { f ->
            runCatching { java.time.LocalDateTime.parse(raw.trim(), f) }.getOrNull()
        } ?: return null
        // 1904/1970 placeholders some encoders write for "unknown".
        if (parsed.year < 2000) return null
        return ZonedDateTime.of(parsed, utc).withZoneSameInstant(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
    }

    private val FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"),
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
    )

    private const val STILL_AT_US = 500_000L
}
