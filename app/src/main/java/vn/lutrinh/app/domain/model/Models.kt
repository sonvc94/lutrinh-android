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
)

/** Row for the trip list: trip + derived counters + cover photo. */
data class TripSummary(
    val trip: Trip,
    val stepCount: Int,
    val photoCount: Int,
    /** File name of the cover photo (first photo of the first step), or null. */
    val coverPhotoFile: String?,
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
    /** Ordered by [Photo.position]; first photo = step cover. */
    val photos: List<Photo> = emptyList(),
)

data class Photo(
    val id: String,
    val stepId: String,
    /** File name inside the app's private photo directory (never an absolute path). */
    val fileName: String,
    val position: Int,
    val createdAt: Long,
)
