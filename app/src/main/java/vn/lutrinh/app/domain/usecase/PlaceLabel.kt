package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step

/** The step of the trip that lies closest to a point, and how far (straight line). */
data class NearestStep(val title: String, val km: Double)

/**
 * "Gần <điểm dừng>, 3,2 km" (docs/03 "Display conventions"): a place without a name (a picked or
 * photographed point) is described by the nearest other step of the same trip, never by raw
 * coordinates alone. No gazetteer: with no other step there is no label (the coordinates remain).
 * Pure Kotlin, mirrored in Swift (docs/07).
 */
object PlaceLabel {
    /** Under this distance the point is "at" the step: the label drops the distance. */
    const val AT_STEP_KM = 0.05

    fun nearest(point: GeoPoint, steps: List<Step>, excludeId: String? = null): NearestStep? =
        steps.asSequence()
            .filter { it.id != excludeId }
            .map { NearestStep(it.title, Geo.distanceKm(point, it.location)) }
            .minWithOrNull(compareBy<NearestStep> { it.km }.thenBy { it.title })

    /** "gần Đồng Văn" / "gần Đồng Văn, 3,2 km"; null without a step. */
    fun label(point: GeoPoint, steps: List<Step>, excludeId: String? = null): String? =
        nearest(point, steps, excludeId)?.let {
            if (it.km < AT_STEP_KM) "gần ${it.title}" else "gần ${it.title}, ${DisplayFormat.km(it.km)}"
        }
}
