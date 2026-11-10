package vn.lutrinh.app.domain.catalog

import vn.lutrinh.app.domain.model.GeoPoint

/*
 * R40 "Lộ trình theo mã": ready-made routes the admin publishes on the Lữ Trình route server; a
 * user types the route's code, previews it and downloads it as a NEW trip. The app talks to a
 * route source only through [RouteCatalog] (like maps R35 and routing R36); the server's API lives
 * in catalog/server, a hand-carried package in catalog/file, the address only in catalog/Catalogs.kt
 * (CatalogBoundaryTest). Pure Kotlin, mirrored 1:1 in Swift (docs/07 "Route codes").
 */

/** A source of ready-made routes (the route server today, a package file, a future provider). */
interface RouteCatalog {
    /** Stable id: "lutrinh-server", "file". */
    val id: String

    /** Looks up [code]: the preview summary and a short-lived download ticket. */
    suspend fun resolve(code: RouteCode): CatalogResult<RouteSummary>

    /** `route.json` of exactly the version the ticket was issued for. */
    suspend fun fetchRoute(ticket: DownloadTicket): CatalogResult<FetchedRoute>

    /** Writes the bytes of [ref] from byte [from] on (resume) into [sink]. */
    suspend fun fetchMedia(ticket: DownloadTicket, ref: MediaRef, sink: ByteSink, from: Long): CatalogResult<Unit>

    /** State of downloaded routes (≤ [MAX_UPDATE_LINKS] per call), same order as [links]. */
    suspend fun checkUpdates(links: List<LinkRef>): CatalogResult<List<RouteStatus>>

    companion object {
        const val MAX_UPDATE_LINKS = 50
    }
}

/** Receives downloaded bytes (a file in the download staging area). */
fun interface ByteSink {
    fun write(buffer: ByteArray, length: Int)
}

sealed interface CatalogResult<out T> {
    data class Ok<T>(val value: T) : CatalogResult<T>

    /** The source answered no: show [reason]'s sentence (the app keeps the Vietnamese text). */
    data class Refused(val reason: CatalogError, val retryAfterS: Int? = null) : CatalogResult<Nothing>

    /** No answer (no network, timeout, 5xx, maintenance): try again later. */
    data class Unavailable(val retryAfterS: Int? = null) : CatalogResult<Nothing>
}

/** The API error codes (docs/02 "Route server API", error table). */
enum class CatalogError(val code: String) {
    BAD_FORMAT("bad_format"),
    BAD_CHECK("bad_check"),
    CODE_NOT_FOUND("code_not_found"),
    CODE_REVOKED("code_revoked"),
    CODE_EXPIRED("code_expired"),
    ROUTE_UNPUBLISHED("route_unpublished"),
    TOKEN_EXPIRED("token_expired"),
    FORMAT_UNSUPPORTED("format_unsupported"),
    RATE_LIMITED("rate_limited"),

    /** The package or a file failed our checks (schema-level reading, integrity, sha256 twice). */
    BAD_PACKAGE("bad_package"),
    ;

    companion object {
        fun fromCode(code: String?): CatalogError? = entries.firstOrNull { it.code == code }
    }
}

/** What the code lookup returns: enough for the preview, before any photo is downloaded. */
data class RouteSummary(
    val code: RouteCode,
    val routeId: String,
    val version: Int,
    val formatVersion: Int,
    val title: String,
    val description: String,
    val days: Int,
    val stops: Int,
    val distanceM: Double,
    val photos: Int,
    val videos: Int,
    /** Sum of the media files' bytes (route.json not counted). */
    val bytes: Long,
    /** Number of media files. */
    val files: Int,
    val bounds: RouteBounds?,
    /** R15 ready-made region key ("ha-giang") or null. */
    val readyMade: String?,
    /** Simplified line of the whole route for the preview map. */
    val overview: List<GeoPoint>,
    val ticket: DownloadTicket,
)

/** Download permission for one code + version, valid about 2 h ([CatalogError.TOKEN_EXPIRED] after). */
data class DownloadTicket(val code: RouteCode, val routeId: String, val version: Int, val token: String)

/** Box around every stop and every `ok` leg line (`region.bounds`). */
data class RouteBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** One media file of a package: named by its sha256 (lower-case hex). */
data class MediaRef(val sha256: String, val bytes: Long, val video: Boolean)

/** `route.json` as received: its bytes' sha256 (the package hash) and the parsed package. */
data class FetchedRoute(val sha256: String, val pkg: RoutePackage)

/** A downloaded route, as sent to "POST /updates" (nothing about the user's trip). */
data class LinkRef(val code: RouteCode, val routeId: String, val version: Int)

/** Remote state of a downloaded route. */
enum class RemoteStatus(val id: String) {
    ACTIVE("active"),
    UNPUBLISHED("unpublished"),
    REVOKED("revoked"),
    EXPIRED("expired"),
    ;

    companion object {
        fun fromId(id: String?): RemoteStatus = entries.firstOrNull { it.id == id } ?: ACTIVE
    }
}

/** One answer of the update check. [newer] is set when a newer version is published. */
data class RouteStatus(val link: LinkRef, val status: RemoteStatus, val newer: RouteChange? = null)

/** What a newer version changes (the update sheet's lines). */
data class RouteChange(
    val version: Int,
    /** UTC ISO-8601, or null. */
    val publishedAt: String?,
    val stopsAdded: List<String> = emptyList(),
    val stopsRemoved: List<String> = emptyList(),
    val stopsEdited: Int = 0,
    val mediaAdded: Int = 0,
    /** Bytes of the files the new version adds. */
    val bytes: Long = 0,
    /** The admin's version note, or null. */
    val note: String? = null,
)
