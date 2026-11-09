package vn.lutrinh.app.map

import vn.lutrinh.app.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** A lat/lng rectangle (degrees). Never crosses the antimeridian (Vietnam does not). */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    init {
        require(south <= north && west <= east) { "empty bounds $this" }
    }

    operator fun contains(p: GeoPoint): Boolean =
        p.latitude in south..north && p.longitude in west..east
}

/**
 * One downloadable area of the basemap. [key] identifies it in the offline store:
 * a ready-made region id, or `trip:<tripId>` for "Tải vùng của chuyến đi này".
 */
data class OfflineArea(
    val key: String,
    val name: String,
    /** Places it covers, shown under the name ("" for a trip area). */
    val description: String,
    val bounds: GeoBounds,
)

/**
 * THE single place that defines offline maps (docs/04 R15, docs/05 "Offline maps"):
 * which areas exist, which zoom levels are stored, the size estimate and the limits.
 * Pure Kotlin, no Android, so the iOS port copies the numbers as they are.
 *
 * What is stored per area: the offline basemap's style (OpenFreeMap Positron), its sprite, its fonts (without
 * CJK ideographs) and every tile of the style's sources inside [OfflineArea.bounds] for zoom
 * [MIN_ZOOM]..[MAX_ZOOM]. The vector tiles stop at z14 anyway (MapConfig.MAX_ZOOM over-zooms
 * them up to z19), so street and village names stay readable at any zoom.
 */
object OfflineConfig {
    const val MIN_ZOOM = 0
    const val MAX_ZOOM = 14

    /** Margin around a trip's steps for "Tải vùng của chuyến đi này", in km on each side. */
    const val TRIP_MARGIN_KM = 10.0

    /**
     * R31: one download may be at most this big (estimate) and at most [MAX_AREA_TILES] tiles.
     * OpenFreeMap's terms allow offline downloads of "≤3,000 tiles or 50 MB per offline download";
     * a larger area is refused (never split automatically).
     */
    const val MAX_AREA_BYTES = 50L * 1_000_000

    /** R31: tiles of all the style's sources in one download (see [estimateTiles]). */
    const val MAX_AREA_TILES = 3_000L

    /** The style's raster relief source (Positron `ne2_shaded`) stops at this zoom; its tiles count too. */
    const val RASTER_MAX_ZOOM = 6

    /** Free storage kept on top of the estimate before a download is allowed to start. */
    const val STORAGE_RESERVE_BYTES = 50L * 1_000_000

    const val TRIP_KEY_PREFIX = "trip:"

    val HA_GIANG = OfflineArea(
        key = "ha-giang",
        name = "Hà Giang",
        description = "Vòng cung Hà Giang: TP Hà Giang, Quản Bạ, Yên Minh, Lũng Cú, Đồng Văn, Mã Pí Lèng, Mèo Vạc, Du Già",
        bounds = GeoBounds(south = 22.70, west = 104.85, north = 23.42, east = 105.60),
    )

    val NINH_BINH = OfflineArea(
        key = "ninh-binh",
        name = "Ninh Bình",
        description = "Tràng An, Tam Cốc, Hang Múa, Hoa Lư, Bái Đính, Vân Long, Cúc Phương",
        bounds = GeoBounds(south = 20.10, west = 105.48, north = 20.45, east = 106.05),
    )

    /** Ready-made regions, in the order the "Bản đồ offline" screen lists them. */
    val READY_MADE: List<OfflineArea> = listOf(HA_GIANG, NINH_BINH)

    /**
     * Average stored bytes of one tile per zoom level (index = zoom), measured on OpenFreeMap
     * planet tiles over northern Vietnam on 2026-11-09 (low zooms cover the whole region in
     * one big tile). Only used for the estimate shown before a download.
     */
    val BYTES_PER_TILE: LongArray = longArrayOf(
        85_000, 230_000, 1_340_000, 740_000, 510_000, 350_000, 225_000, 165_000,
        80_000, 80_000, 40_000, 16_000, 16_000, 6_000, 3_000,
    )

    /**
     * Style, sprite and the fonts' glyph ranges (most of it), stored once but counted in every
     * area's size. Calibrated on 2026-11-09: Hà Giang stored 26,3 MB, Ninh Bình 24,5 MB.
     */
    const val FIXED_BYTES = 17L * 1_000_000

    fun tripKey(tripId: String) = TRIP_KEY_PREFIX + tripId

    fun tripIdOf(key: String): String? = key.removePrefix(TRIP_KEY_PREFIX).takeIf { key.startsWith(TRIP_KEY_PREFIX) }

    fun readyMade(key: String): OfflineArea? = READY_MADE.firstOrNull { it.key == key }

    /**
     * The area of a trip: the box around all its steps plus [TRIP_MARGIN_KM] on every side.
     * Null when the trip has no step.
     */
    fun tripArea(tripId: String, title: String, steps: List<GeoPoint>): OfflineArea? {
        if (steps.isEmpty()) return null
        val bounds = withMarginKm(
            GeoBounds(
                south = steps.minOf { it.latitude },
                west = steps.minOf { it.longitude },
                north = steps.maxOf { it.latitude },
                east = steps.maxOf { it.longitude },
            ),
            TRIP_MARGIN_KM,
        )
        return OfflineArea(tripKey(tripId), title, "", bounds)
    }

    /** [b] grown by [km] on each side (longitude margin measured at the box's widest latitude). */
    fun withMarginKm(b: GeoBounds, km: Double): GeoBounds {
        val dLat = km / KM_PER_DEG_LAT
        val widest = min(85.0, max(kotlin.math.abs(b.south), kotlin.math.abs(b.north)))
        val dLng = km / (KM_PER_DEG_LAT * cos(Math.toRadians(widest)))
        return GeoBounds(
            south = (b.south - dLat).coerceAtLeast(-MAX_LAT),
            west = (b.west - dLng).coerceAtLeast(-180.0),
            north = (b.north + dLat).coerceAtMost(MAX_LAT),
            east = (b.east + dLng).coerceAtMost(180.0),
        )
    }

    /** Number of web-mercator tiles covering [b] at zoom [z]. */
    fun tileCount(b: GeoBounds, z: Int): Long {
        val (x0, y0) = tileXY(b.north, b.west, z)
        val (x1, y1) = tileXY(b.south, b.east, z)
        return (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
    }

    /** Tiles of one source for [b] over [MIN_ZOOM]..[MAX_ZOOM]. */
    fun tileCount(b: GeoBounds): Long = (MIN_ZOOM..MAX_ZOOM).sumOf { tileCount(b, it) }

    /** Estimated download size of [b] in bytes, shown before the download. */
    fun estimateBytes(b: GeoBounds): Long =
        FIXED_BYTES + (MIN_ZOOM..MAX_ZOOM).sumOf { z -> tileCount(b, z) * BYTES_PER_TILE[z] }

    /**
     * Estimated tiles of one download of [b]: the vector source at [MIN_ZOOM]..[MAX_ZOOM] plus the
     * raster relief at [MIN_ZOOM]..[RASTER_MAX_ZOOM]. Hà Giang: 1,757 + 7 estimated, 1,763 stored (2026-11-09).
     */
    fun estimateTiles(b: GeoBounds): Long = tileCount(b) + (MIN_ZOOM..min(MAX_ZOOM, RASTER_MAX_ZOOM)).sumOf { tileCount(b, it) }

    /** R31: true when an area of [b] may be downloaded at all ([MAX_AREA_BYTES] and [MAX_AREA_TILES]). */
    fun withinLimit(b: GeoBounds): Boolean = withinLimit(estimateBytes(b), estimateTiles(b))

    /** R31 on estimates: both limits are inclusive. */
    fun withinLimit(estimateBytes: Long, estimateTiles: Long): Boolean =
        estimateBytes <= MAX_AREA_BYTES && estimateTiles <= MAX_AREA_TILES

    /** True when [estimate] plus [STORAGE_RESERVE_BYTES] fits in [freeBytes]. */
    fun fitsInStorage(estimate: Long, freeBytes: Long): Boolean = estimate + STORAGE_RESERVE_BYTES <= freeBytes

    /** The area whose box contains [p] among [areas], or null. */
    fun covering(areas: Collection<OfflineArea>, p: GeoPoint): OfflineArea? = areas.firstOrNull { p in it.bounds }

    private const val KM_PER_DEG_LAT = 111.32
    private const val MAX_LAT = 85.05112878

    private fun tileXY(lat: Double, lng: Double, z: Int): Pair<Int, Int> {
        val n = 1 shl z
        val clampedLat = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val x = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val rad = Math.toRadians(clampedLat)
        val y = floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }
}
