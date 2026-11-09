package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step

/*
 * R20 draft steps and R21 trip cover (docs/04-business-rules.md). Pure Kotlin, mirrored 1:1 in
 * Swift (docs/07-ios-port.md).
 */

/** R20: the steps that are part of the trip (not drafts), in the order given. */
fun List<Step>.published(): List<Step> = filterNot { it.isDraft }

/**
 * R20: timeline numbers. Published steps are numbered 1..n in timeline order (R2); a draft has
 * no number (absent from the map).
 */
fun stepNumbers(steps: List<Step>): Map<String, Int> =
    steps.published().inTimelineOrder().mapIndexed { i, s -> s.id to i + 1 }.toMap()

/** R21: which photo is a trip's cover. */
object TripCover {
    /** Every photo the cover can be chosen from: the photos of the trip's steps, timeline order then position. */
    fun candidates(steps: List<Step>): List<Photo> =
        steps.inTimelineOrder().flatMap { s -> s.photos.sortedBy { it.position } }

    /**
     * The chosen photo ([coverPhotoId]) while it still belongs to one of [steps]; otherwise
     * (automatic, or the chosen photo was removed) the first photo of the first step that has
     * one; null for a trip without photos.
     */
    fun resolve(coverPhotoId: String?, steps: List<Step>): Photo? {
        val all = candidates(steps)
        return all.firstOrNull { it.id == coverPhotoId } ?: all.firstOrNull()
    }

    /** True when [coverPhotoId] names a photo of [steps] (a stale or null id = automatic). */
    fun isChosen(coverPhotoId: String?, steps: List<Step>): Boolean =
        coverPhotoId != null && candidates(steps).any { it.id == coverPhotoId }
}
