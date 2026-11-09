package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import java.text.Normalizer
import kotlin.math.ln

/**
 * The offline gazetteer of Vietnam (R23 search, R24 auto place name; docs/05 "Place search").
 * Data: `assets/places/vn_places.tsv`, built from OpenStreetMap (© OpenStreetMap contributors,
 * ODbL 1.0) by `tools/gazetteer/build_gazetteer.py`. Nothing here touches the network.
 * Pure Kotlin, mirrored in Swift (docs/07).
 */

/** Text as search compares it: lower case, no diacritics, đ → d, punctuation as spaces. */
object PlaceText {
    fun normalize(s: String): String {
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        val out = StringBuilder(nfd.length)
        var space = false
        for (ch in nfd) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            val c = when (ch) { 'đ', 'Đ' -> 'd'; else -> ch.lowercaseChar() }
            if (c in 'a'..'z' || c in '0'..'9') {
                if (space && out.isNotEmpty()) out.append(' ')
                space = false
                out.append(c)
            } else space = true
        }
        return out.toString()
    }

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotEmpty() }

    /**
     * Generic words in front of a name ("Xã Đồng Văn", "Khu du lịch Tràng An"): a query equal to the
     * rest still counts as an exact match. Normalised, longest first. Words that also start real
     * names once the accents are gone are left out: "dong" (Động / Đồng Văn), "ho" (Hồ / Hồ Chí
     * Minh), "ban" (Bản / Bản Giốc), "lang" (Làng / Lạng Sơn), "hang", "thon", "bai".
     */
    private val GENERIC_PREFIXES = listOf(
        "khu du lich sinh thai", "khu du lich", "quan the danh thang", "thanh pho", "thi tran", "thi xa",
        "bai bien", "cot co", "pho co", "phuong", "xa", "tinh", "huyen", "tp", "deo", "nui", "thac", "chua", "den",
    )

    /** The name without one generic leading word ("xa dong van" → "dong van"). */
    fun core(normalized: String): String {
        for (p in GENERIC_PREFIXES) {
            if (normalized.length > p.length + 1 && normalized.startsWith("$p ")) return normalized.substring(p.length + 1)
        }
        return normalized
    }
}

/** A province or centrally-run city of the 34 in force since 1 July 2025, with the merged former ones. */
data class Region(val id: Int, val name: String, val formerNames: List<String>, val center: GeoPoint) {
    internal val words: Set<String> = (listOf(name) + formerNames).flatMapTo(HashSet(), PlaceText::tokens)
}

/** What a gazetteer place is; [weight] is its importance in search ranking (R23). */
enum class PlaceKind(val code: Char, val weight: Int, val isSight: Boolean) {
    PROVINCE('R', 100, false),
    CITY('c', 80, false),
    TOWN('t', 60, false),
    COMMUNE('a', 55, false),
    BAY('b', 60, true),
    SIGHT('s', 50, true),
    VIEWPOINT('w', 45, true),
    PEAK('k', 45, true),
    PASS('p', 50, true),
    WATERFALL('f', 45, true),
    CAVE('g', 45, true),
    BEACH('e', 45, true),
    ISLAND('i', 45, true),
    LAKE('l', 45, true),
    HERITAGE('m', 40, true),
    WORSHIP('r', 45, true),
    CAMP('n', 30, true),
    VILLAGE('v', 30, false),
    HAMLET('h', 20, false);

    companion object {
        private val BY_CODE = entries.associateBy { it.code }
        fun of(code: Char): PlaceKind? = BY_CODE[code]
    }
}

data class Place(
    val name: String,
    val kind: PlaceKind,
    val region: Region,
    val location: GeoPoint,
    /** Has a Wikidata/Wikipedia entry in OSM: ranks higher. */
    val notable: Boolean = false,
    val aliases: List<String> = emptyList(),
) {
    val importance: Int get() = kind.weight + if (notable) NOTABLE_BONUS else 0

    /** Normalised name, aliases and their cores; computed when the gazetteer is read, not per search. */
    internal val keys: List<String> =
        (listOf(name) + aliases).map(PlaceText::normalize).flatMap { listOf(it, PlaceText.core(it)) }.distinct()
    internal val words: Set<String> = keys.flatMapTo(HashSet()) { it.split(' ') }
    internal val regionWords: Set<String> get() = region.words

    companion object {
        const val NOTABLE_BONUS = 20
    }
}

/** A search result: the place, how well it matched (0 best), and which former name matched, if any. */
data class PlaceHit(val place: Place, val tier: Int, val score: Double, val viaFormer: String? = null)

/** The place nearest to a point and how far it is (R24 auto name, place labels). */
data class NearPlace(val place: Place, val km: Double)

class Gazetteer(val regions: List<Region>, val places: List<Place>) {

    /**
     * R23: offline search. Every query word must start a word of the place's name or aliases, or of
     * its province (current or former name). Ranking: match tier (0 exact name, 1 name starts with
     * the query, 2 all words in the name, 3 words also in the province), then importance minus a
     * distance penalty from the trip's steps ([near]); same-name duplicates within 5 km in one
     * province are folded.
     */
    fun search(query: String, near: List<GeoPoint> = emptyList(), limit: Int = 20): List<PlaceHit> {
        val q = PlaceText.normalize(query)
        if (q.length < MIN_QUERY) return emptyList()
        val qTokens = q.split(' ')
        val hits = ArrayList<PlaceHit>()
        for (p in places) {
            val tier = tier(p, q, qTokens) ?: continue
            hits += PlaceHit(p, tier, score(p, near), formerMatch(p.region, qTokens))
        }
        val sorted = hits.sortedWith(compareBy<PlaceHit> { it.tier }.thenByDescending { it.score }.thenBy { it.place.name })
        val out = ArrayList<PlaceHit>()
        for (h in sorted) {
            if (out.any { it.place.region == h.place.region && PlaceText.core(it.place.keys[0]) == PlaceText.core(h.place.keys[0]) &&
                    Geo.distanceKm(it.place.location, h.place.location) < FOLD_KM }) continue
            out += h
            if (out.size == limit) break
        }
        return out
    }

    private fun tier(p: Place, q: String, qTokens: List<String>): Int? {
        if (p.keys.any { it == q }) return 0
        if (p.keys.any { it.startsWith(q) }) return 1
        val inName = qTokens.all { t -> p.words.any { it.startsWith(t) } }
        if (inName) return 2
        val anyInName = qTokens.any { t -> p.words.any { it.startsWith(t) } }
        val allSomewhere = qTokens.all { t -> p.words.any { it.startsWith(t) } || p.regionWords.any { it.startsWith(t) } }
        return if (anyInName && allSomewhere && p.kind != PlaceKind.PROVINCE) 3 else null
    }

    /** The former province name the query spells out ("ha giang" → "Hà Giang"), shown with the result. */
    private fun formerMatch(r: Region, qTokens: List<String>): String? =
        r.formerNames.firstOrNull { f -> PlaceText.tokens(f).let { ft -> ft.isNotEmpty() && ft.all { w -> qTokens.any { w.startsWith(it) } } } }

    private fun score(p: Place, near: List<GeoPoint>): Double {
        if (near.isEmpty()) return p.importance.toDouble()
        val km = near.minOf { Geo.distanceKm(it, p.location) }
        return p.importance - DISTANCE_WEIGHT * ln(1 + km / 10)
    }

    /**
     * R24: the place a point is at. A sight, village or town within [NEAR_KM] (the closest one);
     * otherwise the closest commune, town or city centre within [AREA_KM]; otherwise null.
     */
    fun nearest(point: GeoPoint): NearPlace? {
        var best: NearPlace? = null
        var area: NearPlace? = null
        for (p in places) {
            if (p.kind == PlaceKind.PROVINCE) continue
            // cheap box test first: 0.15° ≈ 16 km
            if (kotlin.math.abs(p.location.latitude - point.latitude) > 0.15 || kotlin.math.abs(p.location.longitude - point.longitude) > 0.15) continue
            val km = Geo.distanceKm(point, p.location)
            if (p.kind != PlaceKind.COMMUNE && km <= NEAR_KM && (best == null || km < best.km)) best = NearPlace(p, km)
            if (p.kind in AREA_KINDS && km <= AREA_KM && (area == null || km < area.km)) area = NearPlace(p, km)
        }
        return best ?: area
    }

    companion object {
        const val MIN_QUERY = 2
        /** R24: radius for a sight/village/town name. */
        const val NEAR_KM = 3.0
        /** R24: radius for a commune/town/city centre when nothing is within [NEAR_KM]. */
        const val AREA_KM = 15.0
        /** Ranking: importance points lost per ln(1 + km/10) from the nearest trip step. */
        const val DISTANCE_WEIGHT = 12.0
        const val FOLD_KM = 5.0
        private val AREA_KINDS = setOf(PlaceKind.COMMUNE, PlaceKind.TOWN, PlaceKind.CITY)

        /** Reads the TSV asset (format in tools/gazetteer/build_gazetteer.py); bad lines are skipped. */
        fun parse(lines: Sequence<String>): Gazetteer {
            val regions = ArrayList<Region>()
            val byId = HashMap<Int, Region>()
            val places = ArrayList<Place>()
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val f = line.split('\t')
                if (f[0] == "R" && f.size >= 6) {
                    val r = Region(f[1].toInt(), f[2], f[3].split(';').filter { it.isNotBlank() }, GeoPoint(f[4].toInt() / 1e5, f[5].toInt() / 1e5))
                    regions += r
                    byId[r.id] = r
                    places += Place(r.name, PlaceKind.PROVINCE, r, r.center, aliases = r.formerNames)
                    continue
                }
                if (f.size < 6 || f[0].length != 1) continue
                val kind = PlaceKind.of(f[0][0]) ?: continue
                val region = f[1].toIntOrNull()?.let(byId::get) ?: continue
                val lat = f[2].toIntOrNull() ?: continue
                val lng = f[3].toIntOrNull() ?: continue
                val aliases = f.getOrNull(6)?.split(';')?.filter { it.isNotBlank() }.orEmpty()
                places += Place(f[5], kind, region, GeoPoint(lat / 1e5, lng / 1e5), f[4] == "n", aliases)
            }
            return Gazetteer(regions, places)
        }
    }
}

/**
 * R24: the step title follows the place while the user has not typed one. A title is "not typed"
 * when it is blank, still the default "Điểm dừng HH:mm" (F17), or the name this rule put there.
 */
object AutoName {
    private val DEFAULT = Regex("Điểm dừng \\d{1,2}:\\d{2}")

    fun isDefault(title: String): Boolean = title.isBlank() || DEFAULT.matches(title.trim())

    /** The title may be replaced: the user has not typed it. */
    fun replaceable(title: String, lastAuto: String?): Boolean = isDefault(title) || (lastAuto != null && title == lastAuto)
}

/**
 * A step title with its R24 bookkeeping: [auto] = the name this rule last filled in (null when
 * none), [beforeAuto] = the title before that (blank or the default), restored when the point
 * moves somewhere without a place.
 */
data class AutoTitle(val title: String, val auto: String? = null, val beforeAuto: String = "") {
    /** The title after the location moved to a point named [place] (null = no place there). */
    fun placed(place: String?): AutoTitle {
        val wasAuto = auto != null && title == auto
        return when {
            !AutoName.replaceable(title, auto) -> this
            place != null -> AutoTitle(place, place, if (wasAuto) beforeAuto else title)
            wasAuto -> AutoTitle(beforeAuto, null, beforeAuto)
            else -> this
        }
    }
}
