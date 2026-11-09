package vn.lutrinh.app.domain.usecase

/**
 * R27 photo order: the step editor's list of media files, rearranged by long-press + drag (or the
 * "Đưa lên trước" / "Đưa ra sau" accessibility actions). The list order is what "Lưu" stores as
 * `position` 0..n-1 (R8); the first item is the step cover.
 */
object MediaOrder {
    /**
     * [items] with the element at [from] moved to index [to] (the others keep their relative
     * order). Out-of-range indices leave the list unchanged; [to] is clamped to the list.
     */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices) return items
        val target = to.coerceIn(0, items.lastIndex)
        if (target == from) return items
        val out = items.toMutableList()
        val item = out.removeAt(from)
        out.add(target, item)
        return out
    }

    /** One place towards the front ("Đưa lên trước"); the first item stays. */
    fun <T> moveEarlier(items: List<T>, index: Int): List<T> = if (index <= 0) items else move(items, index, index - 1)

    /** One place towards the end ("Đưa ra sau"); the last item stays. */
    fun <T> moveLater(items: List<T>, index: Int): List<T> = if (index >= items.lastIndex) items else move(items, index, index + 1)
}

/** Why a video is not added (R29). */
enum class VideoProblem {
    /** Longer than [VideoRules.MAX_SECONDS]. */
    TOO_LONG,

    /** Bigger than [VideoRules.MAX_BYTES]. */
    TOO_LARGE,

    /** Its length cannot be read (not a playable video): it cannot be checked, so it is refused. */
    UNREADABLE,
}

/**
 * R29 short videos: copied into app storage as they are (no trimming, no re-encoding) when they
 * are at most 60 s long and at most 200 MB; longer or larger ones are refused with a message.
 */
object VideoRules {
    /** Whole seconds: a recording stopped by the camera's 60 s limit (60.0–60.9 s) is accepted. */
    const val MAX_SECONDS = 60

    /** Decimal megabytes, like every size in the app (formatBytes). */
    const val MAX_BYTES = 200L * 1_000_000

    /** System camera limit asked for "Quay video" (EXTRA_DURATION_LIMIT, seconds). */
    const val CAPTURE_LIMIT_SECONDS = MAX_SECONDS

    /**
     * Null when the video may be added. [durationMs] null = unreadable; [sizeBytes] null = not
     * known yet (the copy is checked again while it is written, see [exceedsSize]).
     */
    fun check(durationMs: Long?, sizeBytes: Long?): VideoProblem? = when {
        sizeBytes != null && exceedsSize(sizeBytes) -> VideoProblem.TOO_LARGE
        durationMs == null || durationMs < 0 -> VideoProblem.UNREADABLE
        durationMs / 1000 > MAX_SECONDS -> VideoProblem.TOO_LONG
        else -> null
    }

    fun exceedsSize(bytes: Long): Boolean = bytes > MAX_BYTES
}
