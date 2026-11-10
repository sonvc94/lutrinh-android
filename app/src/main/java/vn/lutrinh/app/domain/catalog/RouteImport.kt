package vn.lutrinh.app.domain.catalog

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRouteStatus
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.usecase.Polyline
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * The link between a trip and the route it was downloaded from (table `route_links`, DB v10,
 * docs/02). Holds nothing about the user's trip that the server could ask for: the update check
 * sends only [code], [routeId] and [version].
 */
data class RouteLink(
    val tripId: String,
    val routeId: String,
    val code: RouteCode,
    val version: Int,
    /** sha256 of the downloaded `route.json`. */
    val packageSha256: String,
    val downloadedAt: Long,
    /** Last update check (epoch ms), or null = never. */
    val checkedAt: Long? = null,
    val remoteStatus: RemoteStatus = RemoteStatus.ACTIVE,
    /** The user changed the trip since the download: "Thay bản trong máy" is no longer offered. */
    val edited: Boolean = false,
    /** `route.credits` joined by new lines (shown when the badge is tapped). */
    val credits: String = "",
    /** Newest version the last check reported, or null (badge dot while > [version]). */
    val latestVersion: Int? = null,
    /** Version the user answered "Để sau" for: its sheet is not shown again by itself. */
    val skippedVersion: Int? = null,
    /** The unpublished/revoked banner was closed. */
    val noticeClosed: Boolean = false,
) {
    val hasNewer: Boolean get() = latestVersion != null && latestVersion > version
    val shared: Boolean get() = remoteStatus == RemoteStatus.ACTIVE

    /** Badge text "Lộ trình HG4N-7KQE · v3". */
    val badge: String get() = "Lộ trình ${code.display} · v$version"
}

/** What [RouteImport.plan] writes in one transaction. */
data class RouteImportPlan(
    val trip: Trip,
    /** Steps with their photos (file names new, files still in staging). */
    val steps: List<Step>,
    val legRoutes: List<LegRoute>,
    /** Photo file name → sha256 of the staged file to copy into it. */
    val files: Map<String, String>,
    val link: RouteLink,
)

/** R40 package → new trip mapping (docs/02 "Route package → trip"), pure. */
object RouteImport {
    /** Arrival time of a stop without `time`: 08:00 + its position in minutes (keeps the order). */
    private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

    /** D-5: the preview's start date defaults to the next Saturday (never today). */
    fun defaultStartDate(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    fun endDate(start: LocalDate, days: Int): LocalDate = start.plusDays((days - 1).coerceAtLeast(0).toLong())

    /**
     * Maps [pkg] to a NEW trip starting on [start]: new ids from [newId], dates from the start
     * date, photos named `<id>.jpg|mp4`, every leg stored with the server geometry and the stops'
     * own coordinates (so R36 never asks a router again for it; D-13 draws it even with routing
     * off). [tripId] keeps the old id when a copy is replaced ("Thay bản trong máy").
     */
    fun plan(
        pkg: RoutePackage,
        code: RouteCode,
        packageSha256: String,
        start: LocalDate,
        now: Long,
        newId: () -> String,
        tripId: String = newId(),
    ): RouteImportPlan {
        val r = pkg.route
        val mediaById = pkg.media.associateBy { it.id }
        val stepIds = HashMap<String, String>()
        val photoIds = HashMap<String, String>()
        val files = LinkedHashMap<String, String>()
        val steps = pkg.stops.mapIndexed { i, s ->
            val stepId = newId()
            stepIds.putIfAbsent(s.id, stepId)
            val time = s.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: DEFAULT_TIME.plusMinutes(i.toLong())
            val photos = s.media.mapIndexedNotNull { pos, mid ->
                val m = mediaById[mid] ?: return@mapIndexedNotNull null
                val id = newId()
                val fileName = id + if (m.video) ".mp4" else ".jpg"
                photoIds.putIfAbsent(mid, id)
                files[fileName] = m.sha256
                Photo(
                    id = id,
                    stepId = stepId,
                    fileName = fileName,
                    position = pos,
                    createdAt = now + i,
                    mediaType = if (m.video) MediaType.VIDEO else MediaType.PHOTO,
                    durationMs = if (m.video) m.durationMs else null,
                )
            }
            Step(
                id = stepId,
                tripId = tripId,
                title = s.title,
                note = s.note,
                location = GeoPoint(s.lat, s.lng),
                arrivedAt = start.plusDays((s.day - 1).toLong()).atTime(time),
                createdAt = now + i,
                updatedAt = now + i,
                photos = photos,
                arrivalTransport = TransportMode.fromCode(s.arrival),
            )
        }
        val stepById = pkg.stops.mapIndexed { i, s -> s.id to i }.toMap()
        val legRoutes = pkg.legs.mapNotNull { leg ->
            val at = stepById[leg.to] ?: return@mapNotNull null
            if (at == 0) return@mapNotNull null
            val profile = RouteProfile.fromId(leg.profile) ?: return@mapNotNull null
            val status = LegRouteStatus.entries.firstOrNull { it.id == leg.status } ?: LegRouteStatus.NO_ROUTE
            LegRoute(
                toStepId = steps[at].id,
                from = steps[at - 1].location,
                to = steps[at].location,
                profile = profile,
                provider = leg.source,
                status = status,
                distanceM = leg.distanceM,
                durationS = leg.durationS,
                path = if (status == LegRouteStatus.OK) Polyline.decode6(leg.path) else emptyList(),
                fetchedAt = now,
                usedProfile = RouteProfile.fromId(leg.usedProfile) ?: profile,
            )
        }
        val cover = r.cover?.let { photoIds[it] }
        val trip = Trip(
            id = tripId,
            title = r.title,
            description = r.description,
            startDate = start,
            endDate = endDate(start, r.days),
            createdAt = now,
            updatedAt = now,
            coverPhotoId = cover,
        )
        val link = RouteLink(
            tripId = tripId,
            routeId = r.id,
            code = code,
            version = r.version,
            packageSha256 = packageSha256,
            downloadedAt = now,
            checkedAt = now,
            credits = r.credits.joinToString("\n"),
        )
        return RouteImportPlan(trip, steps, legRoutes, files, link)
    }
}

/** When and how a downloaded route is checked for a newer version (R40, page 05 sheet 7). */
object RouteUpdates {
    const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000

    /** Ask the server at most once a day, only while online and only while the route is shared. */
    fun due(link: RouteLink, now: Long, online: Boolean): Boolean =
        online && link.shared && (link.checkedAt == null || now - link.checkedAt >= CHECK_EVERY_MS)

    /** "Thay bản trong máy" only while the copy is unedited (D-4). */
    fun canReplace(link: RouteLink): Boolean = !link.edited

    /** The sheet opens by itself once per version: not after "Để sau" for that version. */
    fun showSheet(link: RouteLink): Boolean = link.hasNewer && link.skippedVersion != link.latestVersion

    /** The link after an update answer arrived at [now]. */
    fun apply(link: RouteLink, answer: RouteStatus, now: Long): RouteLink = link.copy(
        checkedAt = now,
        remoteStatus = if (answer.status == RemoteStatus.EXPIRED) RemoteStatus.REVOKED else answer.status,
        latestVersion = answer.newer?.version ?: link.latestVersion,
    )
}
