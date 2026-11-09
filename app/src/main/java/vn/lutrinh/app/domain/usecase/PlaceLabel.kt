package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step

/** The step of the trip that lies closest to a point, and how far (straight line). */
data class NearestStep(val title: String, val km: Double)

/**
 * "Gần <điểm dừng>, 3,2 km" (docs/03 "Display conventions"): a place without a name (a picked or
 * photographed point) is described by the nearest other step of the same trip, never by raw
 * coordinates alone. A step within [NEAR_STEP_KM] names the place; otherwise the offline gazetteer
 * does (R24: "gần Lũng Cú (Tuyên Quang), 1,2 km"); then a farther step; else no label (the
 * coordinates remain).
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

    /** A step this close describes a point better than any gazetteer place. */
    const val NEAR_STEP_KM = 5.0

    /** Under this distance the point is "at" the gazetteer place: no "gần", no distance. */
    const val AT_PLACE_KM = 0.3

    /** "gần Đồng Văn" / "gần Đồng Văn, 3,2 km"; null without a step. */
    fun label(point: GeoPoint, steps: List<Step>, excludeId: String? = null): String? =
        nearest(point, steps, excludeId)?.let(::stepLabel)

    /** Like [label], with the gazetteer for points with no step within [NEAR_STEP_KM]. */
    fun label(point: GeoPoint, steps: List<Step>, excludeId: String?, places: Gazetteer?): String? {
        val step = nearest(point, steps, excludeId)
        if (step != null && step.km <= NEAR_STEP_KM) return stepLabel(step)
        return places?.nearest(point)?.let(::placeLabel) ?: step?.let(::stepLabel)
    }

    fun stepLabel(it: NearestStep): String =
        if (it.km < AT_STEP_KM) "gần ${it.title}" else "gần ${it.title}, ${DisplayFormat.km(it.km)}"

    /** "Đèo Mã Pí Lèng, Tuyên Quang" / "gần Lũng Cú (Tuyên Quang), 1,2 km". */
    fun placeLabel(n: NearPlace): String =
        if (n.km < AT_PLACE_KM) "${n.place.name}, ${n.place.region.name}"
        else "gần ${n.place.name} (${n.place.region.name}), ${DisplayFormat.km(n.km)}"
}
