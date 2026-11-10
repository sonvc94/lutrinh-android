package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.routing.LegRoute
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * "Chia sẻ chuyến đi" (R18, docs/04-business-rules.md): what goes into the summary image and the
 * summary page. Pure Kotlin, mirrored 1:1 in Swift (docs/07-ios-port.md).
 */

/** The four numbers of a shared trip (R18). */
data class ShareStats(val distanceKm: Int, val days: Int, val stepCount: Int, val photoCount: Int)

object TripShare {
    /** Photos on the summary image. */
    const val IMAGE_PHOTOS = 4

    /** R18 + R20: the steps a share shows: published steps in timeline order (drafts are never shared). */
    fun sharedSteps(steps: List<Step>): List<Step> = steps.published().inTimelineOrder()

    /**
     * R18 stats (drafts left out, R20): km and step/photo counts as on the trip screen (R10: the legs,
     * along the road where a usable route is stored (R36), straight otherwise; no GPS here; rounded); days = the trip's dates inclusive, or for an open-ended trip from the start
     * date to the last step's date (at least 1 day).
     */
    fun stats(trip: Trip, all: List<Step>, routes: Map<String, LegRoute> = emptyMap()): ShareStats {
        val steps = all.published()
        val base = vn.lutrinh.app.domain.usecase.statsOf(trip, steps, routes = routes)
        val last = trip.endDate ?: steps.maxOfOrNull { it.arrivedAt.toLocalDate() }?.coerceAtLeast(trip.startDate) ?: trip.startDate
        return ShareStats(
            distanceKm = base.distanceKm.roundToInt(),
            days = (ChronoUnit.DAYS.between(trip.startDate, last) + 1).toInt().coerceAtLeast(1),
            stepCount = base.stepCount,
            photoCount = base.photoCount,
        )
    }

    /**
     * R18 photo choice for the summary image: at most [max] photos, spread over the trip. With at
     * least [max] steps that have photos, the cover (first photo) of [max] such steps evenly
     * spaced along the timeline (first and last always in); with fewer, every such step's cover,
     * then their next photos in turn (step by step, timeline order) until [max]. Drafts' photos
     * are never chosen (R20).
     */
    fun imagePhotos(steps: List<Step>, max: Int = IMAGE_PHOTOS): List<Photo> {
        if (max <= 0) return emptyList()
        val withPhotos = sharedSteps(steps).filter { it.photos.isNotEmpty() }.map { s -> s.photos.sortedBy { it.position } }
        if (withPhotos.isEmpty()) return emptyList()
        if (withPhotos.size >= max) {
            if (max == 1) return listOf(withPhotos.first().first())
            val n = withPhotos.size
            return (0 until max).map { i -> withPhotos[(i.toDouble() * (n - 1) / (max - 1)).roundToInt()].first() }
        }
        val out = mutableListOf<Photo>()
        var round = 0
        while (out.size < max) {
            var added = false
            for (photos in withPhotos) {
                if (out.size == max) break
                photos.getOrNull(round)?.let { out += it; added = true }
            }
            if (!added) break
            round++
        }
        return out
    }

    private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * File name of an export: `LuTrinh-<title in ASCII, dashes>-<start yyyyMMdd>.<ext>`.
     * Vietnamese letters lose their marks (đ -> d), anything else becomes a dash; at most 40
     * characters of title; an empty result reads "chuyen-di".
     */
    fun fileName(title: String, start: LocalDate, ext: String): String {
        val ascii = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val slug = ascii.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-').ifEmpty { "chuyen-di" }
        return "LuTrinh-$slug-${start.format(FILE_DATE)}.$ext"
    }
}
