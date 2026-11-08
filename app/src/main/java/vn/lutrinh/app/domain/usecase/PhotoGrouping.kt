package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * What the app knows about one picked photo before it is imported (R13, docs/06-photos.md).
 * [key] identifies the photo (the picker URI on Android); [index] is its position in the pick.
 */
data class PhotoMeta(
    val key: String,
    val index: Int,
    val takenAt: LocalDateTime?,
    val location: GeoPoint?,
)

/**
 * One suggested step. [location] is the centroid of the photos, null for the group of photos
 * without a location; [arrivedAt] is the earliest capture time (to the minute), null when no
 * photo in the group has a date.
 */
data class PhotoGroup(
    val photos: List<PhotoMeta>,
    val location: GeoPoint?,
    val arrivedAt: LocalDateTime?,
) {
    val day: LocalDate? get() = arrivedAt?.toLocalDate()
}

/** R13: groups picked photos into suggested steps — same day and within [SAME_PLACE_KM]. */
object PhotoGrouping {
    const val SAME_PLACE_KM = 2.0

    private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Located photos are taken in capture order (undated ones last, then pick order). A photo
     * joins the current group when it has the same day (or both are undated), lies within
     * [SAME_PLACE_KM] of the group's centroid, and the group has room (Limits.PHOTOS_PER_STEP_MAX);
     * otherwise it starts a new group. All photos without a usable location form one last group.
     */
    fun group(photos: List<PhotoMeta>): List<PhotoGroup> {
        val (located, unlocated) = photos.partition { isUsable(it.location) }
        val order = compareBy<PhotoMeta, LocalDateTime?>(nullsLast()) { it.takenAt }.thenBy { it.index }
        val groups = mutableListOf<MutableList<PhotoMeta>>()
        for (p in located.sortedWith(order)) {
            val current = groups.lastOrNull()
            val fits = current != null &&
                current.size < Limits.PHOTOS_PER_STEP_MAX &&
                current.first().takenAt?.toLocalDate() == p.takenAt?.toLocalDate() &&
                Geo.distanceKm(centroid(current), p.location!!) <= SAME_PLACE_KM
            if (fits) current!!.add(p) else groups += mutableListOf(p)
        }
        val result = groups.map { PhotoGroup(it, centroid(it), earliest(it)) }.toMutableList()
        unlocated.sortedWith(order).chunked(Limits.PHOTOS_PER_STEP_MAX).forEach {
            result += PhotoGroup(it, null, earliest(it))
        }
        return result
    }

    /** (0, 0) and out-of-range values are what broken or redacted EXIF looks like: no location. */
    fun isUsable(p: GeoPoint?): Boolean =
        p != null && p.isValid && !(p.latitude == 0.0 && p.longitude == 0.0)

    /** Parses an EXIF date ("yyyy:MM:dd HH:mm:ss"); null when missing or malformed. */
    fun parseExifDateTime(value: String?): LocalDateTime? {
        val text = value?.trim()?.takeIf { it.length >= 19 }?.substring(0, 19) ?: return null
        return try {
            LocalDateTime.parse(text, EXIF_FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun centroid(photos: List<PhotoMeta>): GeoPoint {
        val points = photos.map { it.location!! }
        return GeoPoint(points.sumOf { it.latitude } / points.size, points.sumOf { it.longitude } / points.size)
    }

    private fun earliest(photos: List<PhotoMeta>): LocalDateTime? =
        photos.mapNotNull { it.takenAt }.minOrNull()?.truncatedTo(ChronoUnit.MINUTES)
}

/** A group turned into an editable step proposal for the review screen (R13). */
data class StepSuggestion(
    val photos: List<PhotoMeta>,
    val title: String,
    val location: GeoPoint?,
    val arrivedAt: LocalDateTime,
    /** False when no photo had a date and [arrivedAt] is the R6 default. */
    val datedFromPhotos: Boolean,
    /** The photos' date lies outside the trip: such a step cannot be saved (R5). */
    val outsideTrip: Boolean,
    /** Created on confirm. Off for groups without a location until one is placed. */
    val include: Boolean,
)

/**
 * R13: one suggestion per group, titled by [titleFor] (1-based number after the existing steps).
 * Undated groups get the R6 default arrival; groups outside the trip dates are flagged and not included.
 */
fun suggestStepsFrom(
    groups: List<PhotoGroup>,
    trip: Trip,
    existingSteps: List<Step>,
    now: LocalDateTime,
    titleFor: (Int) -> String,
): List<StepSuggestion> = groups.mapIndexed { i, g ->
    val arrival = g.arrivedAt ?: NewStepDefaults.arrival(trip, existingSteps, now)
    val day = arrival.toLocalDate()
    val outside = day.isBefore(trip.startDate) || (trip.endDate != null && day.isAfter(trip.endDate))
    StepSuggestion(
        photos = g.photos,
        title = titleFor(existingSteps.size + i + 1),
        location = g.location,
        arrivedAt = arrival,
        datedFromPhotos = g.arrivedAt != null,
        outsideTrip = outside,
        include = g.location != null && !outside,
    )
}
