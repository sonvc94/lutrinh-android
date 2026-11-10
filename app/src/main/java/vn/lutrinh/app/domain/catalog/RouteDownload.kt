package vn.lutrinh.app.domain.catalog

/**
 * Where downloaded files wait until the whole route is there and checked (one folder per code,
 * files named by sha256). Kept across app restarts, so a download resumes where it stopped;
 * "Hủy tải" clears it. Implemented by data/RouteStagingDir.
 */
interface RouteStaging {
    /** Bytes of [sha256] already on disk (0 = none). */
    fun stagedBytes(sha256: String): Long

    /** Opens the file of [sha256] (appending when [append]) and runs [block] with it. */
    suspend fun <T> write(sha256: String, append: Boolean, block: suspend (ByteSink) -> T): T

    /** sha256 (lower-case hex) of the staged file of [sha256], or null when there is none. */
    fun hashOf(sha256: String): String?

    fun delete(sha256: String)

    /** Removes every staged file of this download. */
    fun clear()
}

/** Where a download stands (the download screen's lines). */
data class DownloadProgress(
    val routeReady: Boolean,
    val filesDone: Int,
    val files: Int,
    val bytesDone: Long,
    val bytes: Long,
    /** Stops and legs of the checked route.json (0 until [routeReady]). */
    val stops: Int = 0,
    val legs: Int = 0,
) {
    val percent: Int get() = if (bytes <= 0) (if (routeReady) 100 else 0) else ((bytesDone * 100) / bytes).toInt().coerceIn(0, 100)
}

/** Every file is staged and matches its sha256: ready to import in one transaction. */
data class DownloadedRoute(val summary: RouteSummary, val sha256: String, val pkg: RoutePackage)

/**
 * R40 download: resolve → route.json (checked) → every media file (resume from the staged size,
 * sha256 per file, a corrupted file is fetched again once, an expired token is renewed once by
 * resolving the code again). Pure: the network is a [RouteCatalog], the disk a [RouteStaging].
 */
class RouteDownload(
    private val catalog: RouteCatalog,
    private val staging: RouteStaging,
) {
    suspend fun run(code: RouteCode, onProgress: (DownloadProgress) -> Unit = {}): CatalogResult<DownloadedRoute> {
        var summary = when (val r = catalog.resolve(code)) {
            is CatalogResult.Ok -> r.value
            is CatalogResult.Refused -> return r
            is CatalogResult.Unavailable -> return r
        }
        var renewed = false

        /** Resolves the code again once after a 401 token_expired; false = give up. */
        suspend fun renew(): CatalogResult<Nothing>? {
            if (renewed) return CatalogResult.Refused(CatalogError.TOKEN_EXPIRED)
            renewed = true
            return when (val again = catalog.resolve(code)) {
                is CatalogResult.Ok -> { summary = again.value; null }
                is CatalogResult.Refused -> again
                is CatalogResult.Unavailable -> again
            }
        }

        var fetched: FetchedRoute? = null
        while (fetched == null) {
            when (val r = catalog.fetchRoute(summary.ticket)) {
                is CatalogResult.Ok -> fetched = r.value
                is CatalogResult.Refused ->
                    if (r.reason == CatalogError.TOKEN_EXPIRED) renew()?.let { return it } else return r
                is CatalogResult.Unavailable -> return r
            }
        }
        val pkg = fetched.pkg
        if (pkg.route.version != summary.version || pkg.route.id != summary.routeId) return CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        if (pkg.formatVersion > RoutePackage.FORMAT_VERSION) return CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED)
        if (PackageRules.check(pkg).isNotEmpty() || PackageRules.appLimits(pkg).isNotEmpty()) {
            return CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        }

        val files = pkg.files()
        val total = files.sumOf { it.bytes }
        var doneBytes = 0L
        var doneFiles = 0
        fun report(partial: Long = 0) = onProgress(DownloadProgress(true, doneFiles, files.size, doneBytes + partial, total, pkg.stops.size, pkg.legs.size))
        report()

        for (ref in files) {
            var attempt = 0
            while (true) {
                val have = staging.stagedBytes(ref.sha256)
                if (have > ref.bytes) staging.delete(ref.sha256)
                if (have == ref.bytes && staging.hashOf(ref.sha256) == ref.sha256) break
                if (have == ref.bytes) {
                    // Complete but wrong: fetched again from zero once, then the package is bad.
                    staging.delete(ref.sha256)
                    if (++attempt > 1) return CatalogResult.Refused(CatalogError.BAD_PACKAGE)
                    continue
                }
                val from = staging.stagedBytes(ref.sha256)
                var written = from
                val r = staging.write(ref.sha256, append = from > 0) { sink ->
                    catalog.fetchMedia(summary.ticket, ref, { buf, len ->
                        sink.write(buf, len)
                        written += len
                        report(written)
                    }, from)
                }
                when (r) {
                    is CatalogResult.Ok -> if (staging.stagedBytes(ref.sha256) < ref.bytes) {
                        // The answer ended early: ask again from where it stopped (counts as a try).
                        if (++attempt > 2) return CatalogResult.Unavailable()
                    }
                    is CatalogResult.Refused ->
                        if (r.reason == CatalogError.TOKEN_EXPIRED) renew()?.let { return it } else return r
                    is CatalogResult.Unavailable -> return r
                }
            }
            doneBytes += ref.bytes
            doneFiles++
            report()
        }
        return CatalogResult.Ok(DownloadedRoute(summary, fetched.sha256, pkg))
    }

    companion object {
        /** Free space a download needs: the staged files, their copies in the photo folder, a margin. */
        fun neededBytes(bytes: Long): Long = bytes * 2 + 16L * 1024 * 1024
    }
}
