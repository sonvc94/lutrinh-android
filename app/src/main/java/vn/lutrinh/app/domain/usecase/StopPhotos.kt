package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * "Ảnh chụp trong lúc dừng" (R19, docs/04-business-rules.md): photos already in the phone's
 * gallery, taken during a stop suggestion, offered in the step editor opened from that
 * suggestion. Pure Kotlin, mirrored 1:1 in Swift (docs/07-ios-port.md).
 */

/** A photo of the device gallery, not imported (R19). [uri] is only an opaque handle for loading it. */
data class GalleryPhoto(
    /** Gallery id (Android MediaStore `_ID`, iOS PHAsset `localIdentifier`). */
    val id: String,
    val uri: String,
    /** Local wall-clock capture time, minute precision, or null when unknown. */
    val takenAt: LocalDateTime?,
    /** Byte size of the original file, or null when unknown. */
    val sizeBytes: Long?,
)

/** Inclusive local wall-clock window, minute precision. */
data class PhotoWindow(val from: LocalDateTime, val to: LocalDateTime) {
    operator fun contains(t: LocalDateTime): Boolean {
        val m = t.truncatedTo(ChronoUnit.MINUTES)
        return !m.isBefore(from) && !m.isAfter(to)
    }
}

object StopPhotos {
    /** Margin added before the stay's start and after its end. */
    const val MARGIN_MIN = 10L

    /** Slack for the gallery pre-query: a gallery's stored date may be off by the time-zone offset. */
    const val QUERY_SLACK_MS = 26L * 60 * 60 * 1000

    /** start − 10 min … end + 10 min, both converted to local time and truncated to the minute. */
    fun window(startAt: Long, endAt: Long, zone: ZoneId): PhotoWindow = PhotoWindow(
        StopSuggestions.localMinute(startAt, zone).minusMinutes(MARGIN_MIN),
        StopSuggestions.localMinute(endAt, zone).plusMinutes(MARGIN_MIN),
    )

    /** Epoch-ms range for the gallery query (window ± [QUERY_SLACK_MS]); the exact filter is [offer]. */
    fun queryRange(w: PhotoWindow, zone: ZoneId): LongRange =
        (w.from.atZone(zone).toInstant().toEpochMilli() - QUERY_SLACK_MS)..
            (w.to.plusMinutes(1).atZone(zone).toInstant().toEpochMilli() + QUERY_SLACK_MS)

    /**
     * Already imported into the trip: same gallery id, or same capture minute and same original
     * byte size (covers photos added with the Photo Picker, which gives no gallery id).
     */
    fun isAlreadyImported(g: GalleryPhoto, tripPhotos: List<Photo>): Boolean = tripPhotos.any { p ->
        (p.sourceId != null && p.sourceId == g.id) ||
            (g.takenAt != null && g.sizeBytes != null && p.takenAt != null && p.sourceSize != null &&
                p.sourceSize == g.sizeBytes &&
                p.takenAt.truncatedTo(ChronoUnit.MINUTES) == g.takenAt.truncatedTo(ChronoUnit.MINUTES))
    }

    /**
     * Photos to offer: capture time known and inside [w], not already in the trip, each gallery id
     * once; oldest first (ties by id).
     */
    fun offer(candidates: List<GalleryPhoto>, w: PhotoWindow, tripPhotos: List<Photo>): List<GalleryPhoto> =
        candidates
            .filter { it.takenAt != null && it.takenAt in w }
            .filterNot { isAlreadyImported(it, tripPhotos) }
            .distinctBy { it.id }
            .sortedWith(compareBy<GalleryPhoto> { it.takenAt }.thenBy { it.id })
}

/**
 * Ticks of the "Ảnh chụp trong lúc dừng" grid. Every offered photo starts ticked; only the
 * user's unticks are remembered, so photos that appear later (more access granted) arrive ticked
 * and an untick survives a refresh.
 */
data class GallerySelection(
    val photos: List<GalleryPhoto> = emptyList(),
    val unticked: Set<String> = emptySet(),
) {
    val selected: List<GalleryPhoto> get() = photos.filter { it.id !in unticked }

    fun isSelected(id: String): Boolean = photos.any { it.id == id } && id !in unticked

    fun toggle(id: String): GallerySelection =
        if (photos.none { it.id == id }) this
        else copy(unticked = if (id in unticked) unticked - id else unticked + id)

    /** New list of offered photos; unticks of photos still offered are kept, others forgotten. */
    fun withPhotos(offered: List<GalleryPhoto>): GallerySelection {
        val ids = offered.map { it.id }.toSet()
        return GallerySelection(offered, unticked.filterTo(mutableSetOf()) { it in ids })
    }
}
