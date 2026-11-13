package vn.lutrinh.app.domain.usecase

/**
 * "Đoán vị trí theo tên" (R43): a shared Google Maps place whose link has no coordinates is looked
 * up by its name in the offline gazetteer (R23). Pure Kotlin, mirrored in Swift (docs/07); nothing
 * here touches the network, the name never leaves the device.
 *
 * Precision is the gazetteer's: a commune / ward or a well-known place (sight, lake, pass, …). A
 * business ("Cà phê X") matches nothing by name; then the address parts of the text or of the link
 * (`Name, Ward, District, Province, Vietnam`) give the commune / ward / town it stands in. A
 * province alone is never a guess (too coarse to centre a map on).
 */
object PlaceGuess {
    /** The guessed gazetteer place, or null (no guess is better than a wrong one). */
    fun of(g: Gazetteer, name: String, address: String?, pathName: String?): Place? {
        val nameParts = segments(name)
        val pathParts = segments(pathName.orEmpty())
        val heads = listOfNotNull(nameParts.firstOrNull(), pathParts.firstOrNull()).distinct()
        val tails = (nameParts.drop(1) + pathParts.drop(1) + segments(address.orEmpty()))
            .filter { it !in heads }.distinct()
        val tailTokens = tails.flatMapTo(HashSet()) { it.split(' ') }
        for (h in heads) byName(g, h, tailTokens)?.let { return it }
        return byArea(g, tails)
    }

    /** [Resolved.NoCoordinates] / [Resolved.Unreachable] with the guess filled in; any other result unchanged. */
    fun apply(g: Gazetteer, r: SharedPlaces.Resolved): SharedPlaces.Resolved = when (r) {
        is SharedPlaces.Resolved.Found -> r
        is SharedPlaces.Resolved.NoCoordinates -> r.copy(guess = of(g, r.name, r.address, r.pathName)?.location)
        is SharedPlaces.Resolved.Unreachable -> r.copy(guess = of(g, r.name, r.address, null)?.location)
    }

    // ---- the name ----

    private fun byName(g: Gazetteer, head: String, tailTokens: Set<String>): Place? {
        if (head.length < MIN_HEAD) return null
        pick(g.search(head, limit = POOL), head, tailTokens, kinds = null)?.let { return it }
        // An English type word ("Hoan Kiem Lake", "Mount Fansipan"): drop it and ask for that kind of place.
        val words = head.split(' ')
        val lead = TYPE_WORDS[words.first()]
        val trail = if (words.size > 1) TYPE_WORDS[words.last()] else null
        val (rest, kinds) = when {
            trail != null -> words.dropLast(1) to trail
            lead != null && words.size > 1 -> words.drop(1) to lead
            else -> return null
        }
        val q = rest.joinToString(" ")
        return pick(g.search(q, limit = POOL), q, tailTokens, kinds)
    }

    /**
     * The best of [hits] for query [q], or null when none is good enough or the best one is not
     * clearly better than another in a different province. A single word has to be an exact name.
     */
    private fun pick(hits: List<PlaceHit>, q: String, tailTokens: Set<String>, kinds: Set<PlaceKind>?): Place? {
        val single = !q.contains(' ')
        val ok = hits.filter { h ->
            h.place.kind != PlaceKind.PROVINCE &&
                (kinds == null || h.place.kind in kinds) &&
                h.tier <= (if (single) 0 else 2) &&
                (h.place.kind != PlaceKind.VILLAGE && h.place.kind != PlaceKind.HAMLET || inRegion(h.place, tailTokens))
        }
        if (ok.isEmpty()) return null
        // Tier first, then "its province is named in the address", then importance (the search order).
        val best = ok.sortedWith(compareBy<PlaceHit> { it.tier }.thenByDescending { inRegion(it.place, tailTokens) }.thenByDescending { it.score }).first()
        if (inRegion(best.place, tailTokens)) return best.place
        val rival = ok.firstOrNull { it !== best && it.tier == best.tier && it.place.region != best.place.region }
        return if (rival == null || best.score >= rival.score + CLEAR_LEAD) best.place else null
    }

    // ---- the address ----

    /** The commune / ward / town / city named by an address part (most specific first), in a province the address names or the only one. */
    private fun byArea(g: Gazetteer, tails: List<String>): Place? {
        for ((i, part) in tails.withIndex()) {
            val q = stripAdmin(part)
            if (q.length < MIN_HEAD) continue
            val others = tails.filterIndexed { j, _ -> j != i }.flatMapTo(HashSet()) { it.split(' ') }
            // A city that is the province's own name ("Hà Nội") is as coarse as the province: no guess.
            val hits = g.search(q, limit = POOL).filter {
                it.tier == 0 && it.place.kind in AREA_KINDS && PlaceText.normalize(it.place.name) != PlaceText.normalize(it.place.region.name)
            }
            if (hits.isEmpty()) continue
            hits.firstOrNull { inRegion(it.place, others) }?.let { return it.place }
            if (hits.map { it.place.region }.distinct().size == 1) return hits.first().place
        }
        return null
    }

    // ---- text ----

    /** The text cut at commas into normalised parts, without the country, postal codes and street numbers. */
    private fun segments(s: String): List<String> =
        s.split(',').map { PlaceText.normalize(it) }
            .filter { it.isNotEmpty() && it != "vietnam" && it != "viet nam" && it != "vn" && it.any { c -> c in 'a'..'z' } }

    /** "quan hoan kiem" / "hoan kiem district" / "tp ha giang" → "hoan kiem" / "ha giang". */
    private fun stripAdmin(s: String): String {
        var w = s.split(' ')
        if (w.size > 1 && w.last() in ADMIN_TAIL) w = w.dropLast(1)
        for (p in ADMIN_LEAD) {
            val pw = p.split(' ')
            if (w.size > pw.size && w.take(pw.size) == pw) { w = w.drop(pw.size); break }
        }
        return w.joinToString(" ")
    }

    /** The place's province (current or former name) is spelt out in the address. */
    private fun inRegion(p: Place, tailTokens: Set<String>): Boolean =
        (listOf(p.region.name) + p.region.formerNames).any { n -> PlaceText.tokens(n).let { t -> t.isNotEmpty() && t.all { it in tailTokens } } }

    private const val MIN_HEAD = 3
    private const val POOL = 30

    /** Importance points the best hit must lead by when two provinces have the same name at the same tier. */
    private const val CLEAR_LEAD = 10.0
    private val AREA_KINDS = setOf(PlaceKind.COMMUNE, PlaceKind.TOWN, PlaceKind.CITY)
    private val ADMIN_LEAD = listOf("thanh pho", "thi tran", "thi xa", "quan", "huyen", "phuong", "xa", "tp", "tx", "tt")
    private val ADMIN_TAIL = setOf("district", "ward", "commune", "city", "town", "province")

    /** English words that name a kind of place, normalised: "lake" → a lake. */
    private val TYPE_WORDS: Map<String, Set<PlaceKind>> = mapOf(
        "lake" to setOf(PlaceKind.LAKE),
        "pass" to setOf(PlaceKind.PASS),
        "waterfall" to setOf(PlaceKind.WATERFALL),
        "falls" to setOf(PlaceKind.WATERFALL),
        "beach" to setOf(PlaceKind.BEACH),
        "bay" to setOf(PlaceKind.BAY),
        "cave" to setOf(PlaceKind.CAVE),
        "island" to setOf(PlaceKind.ISLAND),
        "mountain" to setOf(PlaceKind.PEAK),
        "mount" to setOf(PlaceKind.PEAK),
        "peak" to setOf(PlaceKind.PEAK),
        "temple" to setOf(PlaceKind.WORSHIP, PlaceKind.HERITAGE),
        "pagoda" to setOf(PlaceKind.WORSHIP),
        "church" to setOf(PlaceKind.WORSHIP),
        "cathedral" to setOf(PlaceKind.WORSHIP),
        "viewpoint" to setOf(PlaceKind.VIEWPOINT),
    )
}
