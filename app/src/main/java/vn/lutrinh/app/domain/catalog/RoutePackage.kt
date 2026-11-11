package vn.lutrinh.app.domain.catalog

/**
 * A route package, format `lutrinh-route` version [RoutePackage.FORMAT_VERSION] (docs/02 "Route
 * package"; schema `spec/route.json` of the server repo). A plan, not a trip: stops carry a day
 * number and a time; the real dates come from the start date the user picks (D-5).
 */
data class RoutePackage(
    val formatVersion: Int,
    val route: RouteInfo,
    val stops: List<RouteStop>,
    val legs: List<RouteLeg>,
    val media: List<RouteMedia>,
    /** `size.media`: sum of [RouteMedia.bytes]. */
    val sizeMedia: Long,
    /** `size.files`: number of media files (route.json not counted). */
    val sizeFiles: Int,
) {
    val photos: Int get() = media.count { !it.video }
    val videos: Int get() = media.count { it.video }

    /** Every distinct file to download, in package order. */
    fun files(): List<MediaRef> = media.distinctBy { it.sha256 }.map { MediaRef(it.sha256, it.bytes, it.video) }

    companion object {
        const val FORMAT = "lutrinh-route"
        const val FORMAT_VERSION = 1
    }
}

data class RouteInfo(
    val id: String,
    val version: Int,
    val publishedAt: String?,
    val title: String,
    val description: String,
    val days: Int,
    /** Media id of the cover photo, or null = automatic. */
    val cover: String?,
    val bounds: RouteBounds?,
    val readyMade: String?,
    val credits: List<String>,
)

data class RouteStop(
    val id: String,
    val day: Int,
    /** `HH:mm` or null. */
    val time: String?,
    val title: String,
    val note: String,
    val lat: Double,
    val lng: Double,
    /** TransportMode code of the leg ending here, or null (unknown codes read as null, R12). */
    val arrival: String?,
    val media: List<String>,
    /**
     * R41: note about the travel from the previous stop to this one (trimmed, 1..1000 characters),
     * or null when the key is absent. Never on the first stop (`arrival_note_on_first_stop`).
     */
    val arrivalNote: String? = null,
)

data class RouteLeg(
    /** Stop id where the leg ends. */
    val to: String,
    val profile: String,
    val usedProfile: String?,
    /** Router id (`valhalla`, `osrm`), `manual` or `gpx`. */
    val source: String,
    val status: String,
    val distanceM: Double,
    val durationS: Double,
    /** polyline6, empty unless [status] is `ok`. */
    val path: String,
)

data class RouteMedia(
    val id: String,
    val sha256: String,
    val video: Boolean,
    val bytes: Long,
    val w: Int?,
    val h: Int?,
    val durationMs: Long?,
    val credit: String,
)

/**
 * The integrity rules a reader checks after the structure is read (spec/README "Integrity
 * rules", same problem codes as the server's `package.check`). Empty = fine.
 */
object PackageRules {
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val TIME = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")

    fun check(p: RoutePackage): List<String> {
        val out = LinkedHashSet<String>()
        if (p.formatVersion > RoutePackage.FORMAT_VERSION) return listOf("format_unsupported")
        val stopIds = HashSet<String>()
        p.stops.forEach { if (!stopIds.add(it.id)) out += "duplicate_stop_id" }
        val mediaById = HashMap<String, RouteMedia>()
        p.media.forEach { if (mediaById.put(it.id, it) != null) out += "duplicate_media_id" }
        p.stops.forEach { if (it.day > p.route.days) out += "day_after_end" }
        val used = HashSet<String>()
        p.stops.forEach { s -> s.media.forEach { m -> if (m !in mediaById) out += "unknown_media" else used += m } }
        p.route.cover?.let { c ->
            val m = mediaById[c]
            if (m == null) out += "unknown_media" else { used += c; if (m.video) out += "cover_not_photo" }
        }
        if (p.media.any { it.id !in used }) out += "orphan_media"
        val position = HashMap<String, Int>()
        p.stops.forEachIndexed { i, s -> position.putIfAbsent(s.id, i) }
        if (p.stops.firstOrNull()?.arrivalNote != null) out += "arrival_note_on_first_stop"
        val legTargets = HashSet<String>()
        var last = -1
        for (leg in p.legs) {
            val at = position[leg.to]
            when {
                at == null -> out += "unknown_stop"
                at == 0 -> out += "leg_to_first_stop"
                else -> {
                    if (!legTargets.add(leg.to)) out += "duplicate_leg"
                    else if (at < last) out += "leg_order"
                    last = maxOf(last, at)
                }
            }
            if (!validPolyline6(leg.path)) out += "bad_path"
        }
        if (p.sizeMedia != p.media.sumOf { it.bytes } || p.sizeFiles != p.media.size) out += "size_mismatch"
        return out.toList()
    }

    /**
     * What the app itself needs before writing anything (on top of [check]): the docs/02 limits it
     * enforces on its own trips, real coordinates, lower-case hex file names. Empty = fine.
     */
    fun appLimits(p: RoutePackage): List<String> {
        val out = LinkedHashSet<String>()
        val r = p.route
        if (r.title.codePointCount(0, r.title.length) !in 1..60 || r.title != r.title.trim()) out += "title"
        if (r.description.codePointCount(0, r.description.length) > 1000) out += "description"
        if (r.days !in 1..30) out += "days"
        if (p.stops.size !in 2..200) out += "stops"
        p.stops.forEach { s ->
            if (s.title.codePointCount(0, s.title.length) !in 1..80) out += "stop_title"
            if (s.note.codePointCount(0, s.note.length) > 2000) out += "stop_note"
            if (s.arrivalNote != null && s.arrivalNote.codePointCount(0, s.arrivalNote.length) !in 1..1000) out += "arrival_note"
            if (s.lat.isNaN() || s.lat !in -90.0..90.0 || s.lng.isNaN() || s.lng !in -180.0..180.0) out += "position"
            if (s.day < 1) out += "day"
            if (s.time != null && !TIME.matches(s.time)) out += "time"
            if (s.media.size > 30) out += "media_per_stop"
        }
        p.media.forEach { m ->
            if (!SHA256.matches(m.sha256)) out += "sha256"
            if (m.bytes < 0) out += "bytes"
        }
        return out.toList()
    }

    /** True when [s] is a complete polyline6 string (pairs of terminated varints). */
    fun validPolyline6(s: String): Boolean {
        var values = 0
        var inValue = false
        for (ch in s) {
            val b = ch.code - 63
            if (b !in 0..63) return false
            inValue = b >= 0x20
            if (!inValue) values++
        }
        return !inValue && values % 2 == 0
    }
}
