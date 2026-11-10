package vn.lutrinh.app.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.DownloadProgress
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RouteDownload
import vn.lutrinh.app.domain.catalog.RouteImport
import vn.lutrinh.app.map.OfflineConfig
import java.time.LocalDate

/** One route download the user asked for (R40, page 05 screen 5). */
data class RouteDownloadRequest(
    val code: RouteCode,
    val start: LocalDate,
    /** "Tải luôn bản đồ offline" was ticked. */
    val offlineMap: Boolean,
    /** Trip id to replace ("Thay bản trong máy"), or null = a new trip. */
    val replace: String? = null,
    val paused: Boolean = false,
    /** Route title for the download screen (known from the preview). */
    val title: String = "",
    /** Size line numbers from the preview: total bytes and files (0 = unknown). */
    val bytes: Long = 0,
    val files: Int = 0,
    /** Name of the offline area to download afterwards ("Hà Giang"), or "" = the trip's own area. */
    val mapName: String = "",
) {
    fun encode() = listOf(code.value, start.toString(), offlineMap.toString(), replace.orEmpty(), paused.toString(), bytes.toString(), files.toString(), mapName, title)
        .joinToString("|")

    companion object {
        fun decode(s: String?): RouteDownloadRequest? = runCatching {
            val p = s!!.split("|", limit = 9)
            RouteDownloadRequest(
                RouteCode.of(p[0])!!, LocalDate.parse(p[1]), p[2].toBoolean(), p[3].ifEmpty { null }, p[4].toBoolean(),
                title = p.getOrElse(8) { "" }, mapName = p.getOrElse(7) { "" }, bytes = p.getOrNull(5)?.toLongOrNull() ?: 0, files = p.getOrNull(6)?.toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }
}

/** What the download screen shows. */
sealed interface RouteDownloadState {
    data object Waiting : RouteDownloadState
    data class Running(val progress: DownloadProgress?, val waitingForNetwork: Boolean) : RouteDownloadState
    data class Done(val tripId: String) : RouteDownloadState
    /** [error] = CatalogError.code, "unavailable" or "storage". */
    data class Failed(val error: String, val retryAfterS: Int?) : RouteDownloadState
}

/**
 * R40 background downloads with WorkManager: network required, survives the app being closed,
 * one unique work per code (a second tap joins it). Pause = stop the work and keep the staged
 * files; "Hủy tải" = stop and delete them. The active request is kept in [LocalPrefs.routeDownload].
 */
class RouteDownloads(private val context: Context, private val prefs: LocalPrefs) {
    private val work get() = WorkManager.getInstance(context)
    private val _active = MutableStateFlow(RouteDownloadRequest.decode(prefs.routeDownload))
    val active: StateFlow<RouteDownloadRequest?> = _active.asStateFlow()

    private fun remember(r: RouteDownloadRequest?) { prefs.routeDownload = r?.encode(); _active.value = r }

    fun start(r: RouteDownloadRequest) {
        val request = r.copy(paused = false)
        remember(request)
        val job = OneTimeWorkRequestBuilder<RouteDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_REQUEST to request.encode()))
            .addTag(TAG)
            .build()
        work.enqueueUniqueWork(name(r.code), ExistingWorkPolicy.KEEP, job)
    }

    fun pause(code: RouteCode) {
        _active.value?.takeIf { it.code == code }?.let { remember(it.copy(paused = true)) }
        work.cancelUniqueWork(name(code))
    }

    fun cancel(code: RouteCode) {
        work.cancelUniqueWork(name(code))
        RouteStagingDir.forCode(context.filesDir, code.value).clear()
        if (_active.value?.code == code) remember(null)
    }

    /** Called when the result was seen (trip opened or error shown). */
    fun finished(code: RouteCode) {
        if (_active.value?.code == code) remember(null)
        work.pruneWork()
    }

    fun stagedBytes(code: RouteCode): Long = RouteStagingDir.forCode(context.filesDir, code.value).totalBytes()

    fun observe(code: RouteCode): Flow<RouteDownloadState?> = work.getWorkInfosForUniqueWorkFlow(name(code)).map { infos ->
        val info = infos.lastOrNull() ?: return@map null
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                if (info.runAttemptCount > 0 || info.progress.keyValueMap.isNotEmpty()) RouteDownloadState.Running(progressOf(info.progress), true)
                else RouteDownloadState.Waiting
            WorkInfo.State.RUNNING -> RouteDownloadState.Running(progressOf(info.progress), false)
            WorkInfo.State.SUCCEEDED -> info.outputData.getString(KEY_TRIP)?.let { RouteDownloadState.Done(it) }
                ?: RouteDownloadState.Failed("storage", null)
            WorkInfo.State.FAILED -> RouteDownloadState.Failed(
                info.outputData.getString(KEY_ERROR) ?: "unavailable",
                info.outputData.getInt(KEY_RETRY, -1).takeIf { it >= 0 },
            )
            WorkInfo.State.CANCELLED -> null
        }
    }

    companion object {
        const val TAG = "RouteDownload"
        const val KEY_REQUEST = "request"
        const val KEY_TRIP = "tripId"
        const val KEY_ERROR = "error"
        const val KEY_RETRY = "retryAfterS"
        private const val P_READY = "ready"
        private const val P_FILES_DONE = "filesDone"
        private const val P_FILES = "files"
        private const val P_BYTES_DONE = "bytesDone"
        private const val P_BYTES = "bytes"
        private const val P_STOPS = "stops"
        private const val P_LEGS = "legs"

        /** No answer this many times in a row = give up ("Máy chủ đang bận"). */
        const val MAX_ATTEMPTS = 6

        fun name(code: RouteCode) = "route-download-${code.value}"

        fun progressData(p: DownloadProgress): Data = workDataOf(
            P_READY to p.routeReady, P_FILES_DONE to p.filesDone, P_FILES to p.files, P_BYTES_DONE to p.bytesDone, P_BYTES to p.bytes,
            P_STOPS to p.stops, P_LEGS to p.legs,
        )

        fun progressOf(d: Data): DownloadProgress? = if (!d.keyValueMap.containsKey(P_FILES)) null else DownloadProgress(
            d.getBoolean(P_READY, false), d.getInt(P_FILES_DONE, 0), d.getInt(P_FILES, 0), d.getLong(P_BYTES_DONE, 0), d.getLong(P_BYTES, 0),
            d.getInt(P_STOPS, 0), d.getInt(P_LEGS, 0),
        )
    }
}

/** Runs one [RouteDownload] and imports the result as a new trip (R40). */
class RouteDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as LuTrinhApp).container
        val request = RouteDownloadRequest.decode(inputData.getString(RouteDownloads.KEY_REQUEST))
            ?: return Result.failure(workDataOf(RouteDownloads.KEY_ERROR to "bad_request"))
        val staging = RouteStagingDir.forCode(applicationContext.filesDir, request.code.value)
        var lastReport = 0L
        val result = RouteDownload(c.routeCatalog, staging).run(request.code) { p ->
            val now = System.currentTimeMillis()
            if (now - lastReport >= 300 || p.filesDone == p.files) {
                lastReport = now
                setProgressAsync(RouteDownloads.progressData(p))
            }
        }
        return when (result) {
            is CatalogResult.Unavailable -> {
                Log.i(TAG, "unavailable, attempt $runAttemptCount")
                if (runAttemptCount + 1 >= RouteDownloads.MAX_ATTEMPTS) failure("unavailable", result.retryAfterS) else Result.retry()
            }
            is CatalogResult.Refused -> {
                Log.i(TAG, "refused ${result.reason.code}")
                failure(result.reason.code, result.retryAfterS)
            }
            is CatalogResult.Ok -> {
                val d = result.value
                val now = c.clock.nowMillis()
                val plan = RouteImport.plan(
                    d.pkg, request.code, d.sha256, request.start, now, { c.ids.newId() },
                    tripId = request.replace ?: c.ids.newId(),
                )
                val tripId = try {
                    c.routes.import(plan, staging::fileOf, replacing = request.replace)
                } catch (e: Exception) {
                    Log.w(TAG, "import failed", e)
                    return failure("storage", null)
                }
                staging.clear()
                Log.i(TAG, "imported ${d.pkg.stops.size} stops, ${d.pkg.legs.size} legs, ${d.pkg.media.size} media -> $tripId")
                if (request.offlineMap) {
                    val area = d.summary.readyMade?.let { OfflineConfig.readyMade(it) }
                        ?: OfflineConfig.tripArea(tripId, plan.trip.title, plan.steps.map { it.location })
                    // MapLibre's offline manager lives on the main thread.
                    area?.let { withContext(Dispatchers.Main) { c.offlineMaps.download(it) } }
                }
                Result.success(workDataOf(RouteDownloads.KEY_TRIP to tripId))
            }
        }
    }

    private fun failure(error: String, retryAfterS: Int?) =
        Result.failure(workDataOf(RouteDownloads.KEY_ERROR to error, RouteDownloads.KEY_RETRY to (retryAfterS ?: -1)))

    private companion object {
        const val TAG = "RouteDownload"
    }
}
