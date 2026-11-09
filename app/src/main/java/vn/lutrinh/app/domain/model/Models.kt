package vn.lutrinh.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain models. Pure Kotlin (no Android imports) so they map 1:1 to Swift structs.
 * See docs/02-data-model.md for the platform-neutral definition.
 */

/** WGS84 coordinate in decimal degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !latitude.isNaN() && !longitude.isNaN()
}

data class Trip(
    val id: String,
    val title: String,
    val description: String,
    val startDate: LocalDate,
    /** Null = open-ended trip (still travelling / end not decided yet). */
    val endDate: LocalDate?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Photo chosen as the trip's cover (R21, DB version 6): a [Photo.id] of one of the trip's
     * steps, or null = automatic (first photo of the first step). A stale id (photo deleted)
     * reads as automatic.
     */
    val coverPhotoId: String? = null,
)

/** Row for the trip list: trip + derived counters + cover photo. */
data class TripSummary(
    val trip: Trip,
    /** Published steps only (R20: drafts are not in the trip's numbers). */
    val stepCount: Int,
    /** Photos of published steps only (R20). */
    val photoCount: Int,
    /** File name of the cover photo (R21: the chosen one, else the first photo of the first step), or null. */
    val coverPhotoFile: String?,
    /** Draft steps (R20) and their photos; deleting the trip removes them too. */
    val draftCount: Int = 0,
    val draftPhotoCount: Int = 0,
)

data class Step(
    val id: String,
    val tripId: String,
    val title: String,
    val note: String,
    val location: GeoPoint,
    /** Local wall-clock arrival time at the place (no time zone). */
    val arrivedAt: LocalDateTime,
    val createdAt: Long,
    val updatedAt: Long,
    /** Ordered by [Photo.position] (R27: the order the user arranged); first item = step cover. */
    val photos: List<Photo> = emptyList(),
    /**
     * How the traveller got here from the previous step in timeline order (the leg that ENDS at
     * this step), or null = not set. Ignored for the first step of a trip (R12).
     */
    val arrivalTransport: TransportMode? = null,
    /**
     * R20 (DB version 6): a draft ("Nháp") is kept in the trip's timeline but is not part of the
     * trip yet: no number, no legs, not in stats, share or "Bản đồ của tôi". False = published.
     */
    val isDraft: Boolean = false,
)

/**
 * Transport mode of a leg (step N -> step N+1). [code] is the stored value (docs/02); never
 * rename a code, only add new ones.
 */
enum class TransportMode(val code: String) {
    WALK("walk"),
    MOTORBIKE("motorbike"),
    CAR("car"),
    BUS("bus"),
    TRAIN("train"),
    PLANE("plane"),
    BOAT("boat"),
    ;

    companion object {
        /** Unknown codes (e.g. written by a newer app version) read as "not set". */
        fun fromCode(code: String?): TransportMode? = entries.firstOrNull { it.code == code }
    }
}

data class Photo(
    val id: String,
    val stepId: String,
    /** File name inside the app's private photo directory (never an absolute path). */
    val fileName: String,
    val position: Int,
    val createdAt: Long,
    /**
     * Local wall-clock capture time from the picked file's EXIF date (no time zone, minute
     * precision, like [Step.arrivedAt]), or null when unknown (no EXIF date, or imported before
     * DB version 4). Used to count photos in a stop suggestion's window (R17).
     */
    val takenAt: LocalDateTime? = null,
    /**
     * Where the file was copied from, to avoid offering the same gallery photo twice (R19, DB
     * version 5): the gallery's id of the original (Android MediaStore `_ID`, iOS PHAsset
     * `localIdentifier`), or null when unknown (Photo Picker, older photos).
     */
    val sourceId: String? = null,
    /** Byte size of the original file, or null when unknown (R19, DB version 5). */
    val sourceSize: Long? = null,
    /** Photo or short video (R29, DB version 7); existing rows are photos. */
    val mediaType: MediaType = MediaType.PHOTO,
    /** Length of a video in milliseconds (R29), null for a photo or when unknown. */
    val durationMs: Long? = null,
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

/** Kind of a step media item (R29). [code] is the stored value (docs/02); unknown codes read as photo. */
enum class MediaType(val code: String) {
    PHOTO("photo"),
    VIDEO("video"),
    ;

    companion object {
        fun fromCode(code: String?): MediaType = entries.firstOrNull { it.code == code } ?: PHOTO
    }
}

/** Origin of a newly imported photo file (R19): gallery id and original byte size, each may be unknown. */
data class PhotoSource(val id: String? = null, val sizeBytes: Long? = null)
