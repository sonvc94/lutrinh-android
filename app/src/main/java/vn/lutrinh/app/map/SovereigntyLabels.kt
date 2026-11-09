package vn.lutrinh.app.map

import vn.lutrinh.app.domain.model.GeoPoint

/**
 * R30 (docs/04, docs/05 "Hoàng Sa / Trường Sa"): the map never shows Chinese administrative names
 * for Vietnam's Hoàng Sa (Paracel) and Trường Sa (Spratly) archipelagos, and labels both in
 * Vietnamese. Pure Kotlin, no Android: the iOS port copies these lists as they are.
 *
 * OpenStreetMap (OpenFreeMap tiles 2026-11-01) places Chinese admin units there: city "三沙市"
 * (name:vi "Tam Sa"), "西沙区" ("Quận Tây Sa"), "南沙区" ("Quận Nam Sa"), towns "永兴镇",
 * "永乐群岛", "七连屿", "渚碧岛", villages "甘泉社区" ("Xã khu Cam Tuyền") …, airports
 * "三沙永兴机场" ("Sân bay Tam Sa Vĩnh Hưng") and Chinese-only island/undersea names.
 *
 * Rule, applied as a filter to every basemap label layer (live maps, share snapshot, offline
 * packs, which render the same style): a label whose point lies in one of [AREAS] is hidden when
 * - any of its [NAME_FIELDS] is in [BLOCKED_NAMES], or
 * - its local `name` is in a non-Latin script (OpenMapTiles sets `name:nonlatin`), unless it is a
 *   natural feature ([NATURAL_CLASSES], e.g. an island) that has a Vietnamese `name:vi`, which is
 *   what the app shows (MapConfig.LABEL_FIELD), e.g. "Đảo Phú Lâm" for 永兴岛.
 * Latin-script names (Vietnamese "Song Tử Tây", "Trường Sa", Philippine, Malaysian) stay.
 * The app then draws [LABELS] ("Quần đảo Hoàng Sa", "Quần đảo Trường Sa") itself at
 * [LABEL_MIN_ZOOM]..[LABEL_MAX_ZOOM].
 */
object SovereigntyLabels {
    /** One archipelago: the Vietnamese label, where it is drawn, and the box its filter covers. */
    data class Archipelago(val label: String, val at: GeoPoint, val area: GeoBounds)

    /** Paracels: reefs 15.7–17.1 N, 111.1–112.9 E. Box keeps clear of Hainan (18.1 N) and the coast (108–109 E). */
    val HOANG_SA = Archipelago(
        label = "Quần đảo Hoàng Sa",
        at = GeoPoint(16.45, 111.95),
        area = GeoBounds(south = 15.4, west = 110.9, north = 17.4, east = 113.2),
    )

    /** Spratlys: reefs 7.4–11.5 N, 111.5–116.0 E. East edge 116.3 E keeps Palawan/Balabac (≥ 116.9 E) and Sabah out. */
    val TRUONG_SA = Archipelago(
        label = "Quần đảo Trường Sa",
        at = GeoPoint(10.0, 114.3),
        area = GeoBounds(south = 6.8, west = 111.3, north = 12.2, east = 116.3),
    )

    val ARCHIPELAGOS = listOf(HOANG_SA, TRUONG_SA)
    val AREAS: List<GeoBounds> = ARCHIPELAGOS.map { it.area }

    /** Feature properties compared with [BLOCKED_NAMES]. */
    val NAME_FIELDS = listOf("name", "name:vi", "name:latin", "name_en", "name:en", "name_int", "name:zh")

    /** Chinese administrative names over the archipelagos, every script seen in the tiles. */
    val BLOCKED_NAMES: Set<String> = setOf(
        "三沙市", "三沙", "Tam Sa", "Thành phố Tam Sa", "Sansha", "Sansha City",
        "西沙区", "西沙", "Quận Tây Sa", "Tây Sa", "Xisha", "Xisha District",
        "南沙区", "南沙", "Quận Nam Sa", "Nam Sa", "Nansha", "Nansha District",
        "永兴镇", "Yongxing", "永乐群岛", "Yongle Qundao", "七连屿", "Qilianyu", "渚碧岛", "Zhubi",
    )

    /** Natural-feature classes (place `island`, water names) that keep a Vietnamese `name:vi`. */
    val NATURAL_CLASSES = setOf("island", "ocean", "sea", "bay", "strait", "lake")

    const val NONLATIN_FIELD = "name:nonlatin"
    const val VI_FIELD = "name:vi"
    const val CLASS_FIELD = "class"

    /** Our labels are drawn from this zoom (Vietnam overview is z5) up to, not including, [LABEL_MAX_ZOOM]. */
    const val LABEL_MIN_ZOOM = 3.0
    const val LABEL_MAX_ZOOM = 11.0

    /** True when [point] lies in one of [AREAS]. */
    fun inAreas(point: GeoPoint): Boolean = AREAS.any { point in it }

    /**
     * True when a basemap label with these feature [props] at [point] must be hidden (the rule
     * above; the map filter in `SovereigntyLayers.kt` is the same rule as a style expression).
     */
    fun hides(props: Map<String, Any?>, point: GeoPoint): Boolean {
        if (!inAreas(point)) return false
        if (NAME_FIELDS.any { props[it] in BLOCKED_NAMES }) return true
        val keptNatural = props[CLASS_FIELD] in NATURAL_CLASSES && props[VI_FIELD] != null
        return props[NONLATIN_FIELD] != null && !keptNatural
    }

    /** [ARCHIPELAGOS] as a GeoJSON FeatureCollection of points with a `name` property. */
    fun labelsGeoJson(): String = ARCHIPELAGOS.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") {
        """{"type":"Feature","properties":{"name":"${it.label}"},""" +
            """"geometry":{"type":"Point","coordinates":[${it.at.longitude},${it.at.latitude}]}}"""
    }
}
