package vn.lutrinh.app.map

/** Where one offline area is in its life (docs/04 R15). Pure, shared with the iOS port. */
enum class OfflinePhase { NOT_DOWNLOADED, DOWNLOADING, WAITING_NETWORK, PAUSED, FAILED, COMPLETE }

/** Why a download cannot go on. */
enum class OfflineProblem { NO_NETWORK, STORAGE_FULL, SERVER, OTHER }

/** What the "Bản đồ offline" screen shows for one area. */
data class OfflineAreaState(
    val area: OfflineArea,
    val phase: OfflinePhase,
    /** Bytes stored so far for this area (the actual size once [OfflinePhase.COMPLETE]). */
    val completedBytes: Long = 0,
    val completedResources: Long = 0,
    val requiredResources: Long = 0,
    val problem: OfflineProblem? = null,
) {
    val estimateBytes: Long get() = OfflineConfig.estimateBytes(area.bounds)
    val estimateTiles: Long get() = OfflineConfig.estimateTiles(area.bounds)

    /** 0..1; resource counts, as the total size is only known at the end. */
    val progress: Float
        get() = if (requiredResources <= 0) 0f else (completedResources.toFloat() / requiredResources).coerceIn(0f, 1f)
}

object OfflinePhases {
    /**
     * [stored] = the area exists in the offline store; [complete] = every resource is stored;
     * [paused] = the user paused it; [problem] = last error (cleared by progress); [online] =
     * the device has a network.
     */
    fun of(stored: Boolean, complete: Boolean, paused: Boolean, problem: OfflineProblem?, online: Boolean): OfflinePhase = when {
        !stored -> if (problem != null) OfflinePhase.FAILED else OfflinePhase.NOT_DOWNLOADED
        complete -> OfflinePhase.COMPLETE
        paused -> OfflinePhase.PAUSED
        problem == OfflineProblem.STORAGE_FULL || problem == OfflineProblem.OTHER -> OfflinePhase.FAILED
        !online || problem == OfflineProblem.NO_NETWORK -> OfflinePhase.WAITING_NETWORK
        else -> OfflinePhase.DOWNLOADING
    }

    /** Maps a MapLibre/MLN offline error (reason + message) to a [OfflineProblem]. */
    fun problemOf(reason: String, message: String): OfflineProblem {
        val m = message.lowercase()
        return when {
            listOf("full", "no space", "disk", "enospc").any { it in m } -> OfflineProblem.STORAGE_FULL
            reason == "REASON_CONNECTION" || reason == "CONNECTION" -> OfflineProblem.NO_NETWORK
            reason == "REASON_SERVER" || reason == "SERVER" || reason == "REASON_NOT_FOUND" || reason == "NOT_FOUND" -> OfflineProblem.SERVER
            else -> OfflineProblem.OTHER
        }
    }
}
