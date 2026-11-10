package vn.lutrinh.app.ui.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.data.RouteDownloadRequest
import vn.lutrinh.app.data.RouteDownloadState
import vn.lutrinh.app.domain.catalog.CatalogError
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.CodeCheck
import vn.lutrinh.app.domain.catalog.CodeProblem
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RouteDownload
import vn.lutrinh.app.domain.catalog.RouteImport
import vn.lutrinh.app.domain.catalog.RoutePackage
import vn.lutrinh.app.domain.catalog.RouteSummary
import vn.lutrinh.app.map.GeoBounds
import vn.lutrinh.app.map.OfflineArea
import vn.lutrinh.app.map.OfflineConfig
import vn.lutrinh.app.map.OfflinePhase
import java.time.LocalDate

/** Why the code field shows an error (page 05 screen 3): typed (on the device) or the server's answer. */
sealed interface CodeFieldError {
    data class Typed(val problem: CodeProblem) : CodeFieldError
    data class Server(val error: CatalogError, val retryAfterS: Int?) : CodeFieldError
}

/** The R38 notices of page 05 screen 8. */
sealed interface RouteDialog {
    data object Offline : RouteDialog
    data object Busy : RouteDialog
    data object UpdateApp : RouteDialog
    data class Storage(val needBytes: Long) : RouteDialog
    data class Metered(val bytes: Long) : RouteDialog
}

/** "Tải luôn bản đồ offline …": the area that would be downloaded after the route, or none to offer. */
data class OfflineOffer(val area: OfflineArea, val readyMade: Boolean, val estimateBytes: Long)

sealed interface RouteStage {
    data object Code : RouteStage
    data class Preview(val summary: RouteSummary, val start: LocalDate, val offer: OfflineOffer?, val withMap: Boolean) : RouteStage
    data object Download : RouteStage
}

data class RouteCodeUi(
    /** Normalised characters typed so far (at most 8; the dash is only drawn). */
    val input: String = "",
    val looking: Boolean = false,
    val serverError: CodeFieldError.Server? = null,
    val dialog: RouteDialog? = null,
    val stage: RouteStage = RouteStage.Code,
) {
    val check: CodeCheck get() = RouteCode.parse(input)

    /** Typed problems show as soon as they are certain: a wrong character at once, the check at 8. */
    val fieldError: CodeFieldError?
        get() = serverError ?: when (val ch = check) {
            is CodeCheck.Valid -> null
            is CodeCheck.Invalid -> if (ch.problem == CodeProblem.LENGTH) firstBadChar() else CodeFieldError.Typed(ch.problem)
        }

    private fun firstBadChar(): CodeFieldError? {
        val bad = input.firstOrNull { it !in RouteCode.ALPHABET } ?: return null
        return CodeFieldError.Typed(if (bad in "O0I1LU") CodeProblem.CONFUSABLE else CodeProblem.CHAR)
    }

    val canLookUp: Boolean get() = check is CodeCheck.Valid && !looking
}

/** The download screen (page 05 screen 5): the request, the work's state and the network. */
data class DownloadView(val request: RouteDownloadRequest, val state: RouteDownloadState?, val online: Boolean, val stagedBytes: Long)

/**
 * "Nhập mã lộ trình" (R40, page 05 screens 2–5 and 8): code → lookup → preview → background
 * download. Only the code goes to the server; the download runs in WorkManager and outlives the
 * screen. An active download opens this screen on its progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCodeViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val downloads = c.routeDownloads
    private val _ui = MutableStateFlow(
        RouteCodeUi(
            input = RouteCode.normalise(handle.get<String>("code").orEmpty()).take(RouteCode.LENGTH),
            stage = if (downloads.active.value != null) RouteStage.Download else RouteStage.Code,
        ),
    )
    val ui: StateFlow<RouteCodeUi> = _ui.asStateFlow()

    /** The trip to open once a download is in (trip id, offline map was asked for). */
    private val _opened = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 1)
    val opened: SharedFlow<Pair<String, Boolean>> = _opened

    val download: StateFlow<DownloadView?> = downloads.active.flatMapLatest { r ->
        if (r == null) flowOf(null)
        else combine(downloads.observe(r.code), c.network.online) { state, online ->
            DownloadView(r, state, online, if (state is RouteDownloadState.Running) 0 else downloads.stagedBytes(r.code))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            download.collect { v ->
                val done = v?.state as? RouteDownloadState.Done ?: return@collect
                downloads.finished(v.request.code)
                _opened.tryEmit(done.tripId to v.request.offlineMap)
            }
        }
        // A prefilled valid code ("Tải thành chuyến đi mới") is looked up at once.
        if (_ui.value.stage == RouteStage.Code && _ui.value.canLookUp) lookUp()
    }

    fun onInput(raw: String) {
        val s = RouteCode.normalise(raw).take(RouteCode.LENGTH)
        _ui.update { if (it.input == s) it else it.copy(input = s, serverError = null) }
    }

    fun lookUp() {
        val code = (_ui.value.check as? CodeCheck.Valid)?.code ?: return
        if (!c.network.online.value) { _ui.update { it.copy(dialog = RouteDialog.Offline) }; return }
        _ui.update { it.copy(looking = true, serverError = null, dialog = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { c.routeCatalog.resolve(code) }
            _ui.update { s ->
                val base = s.copy(looking = false)
                when (r) {
                    is CatalogResult.Ok ->
                        if (r.value.formatVersion > RoutePackage.FORMAT_VERSION) base.copy(dialog = RouteDialog.UpdateApp)
                        else {
                            val offer = offerFor(r.value)
                            base.copy(stage = RouteStage.Preview(r.value, RouteImport.defaultStartDate(c.clock.today()), offer, offer != null))
                        }
                    is CatalogResult.Unavailable -> base.copy(dialog = RouteDialog.Busy)
                    is CatalogResult.Refused -> when (r.reason) {
                        CatalogError.FORMAT_UNSUPPORTED -> base.copy(dialog = RouteDialog.UpdateApp)
                        else -> base.copy(serverError = CodeFieldError.Server(r.reason, r.retryAfterS))
                    }
                }
            }
        }
    }

    /** The offline area offered in the preview: the R15 region, else the route's box; none when stored or above R31. */
    private fun offerFor(s: RouteSummary): OfflineOffer? {
        val ready = s.readyMade?.let { OfflineConfig.readyMade(it) }
        val area = ready ?: s.bounds?.let { b ->
            OfflineArea("", "", "", OfflineConfig.withMarginKm(GeoBounds(b.south, b.west, b.north, b.east), OfflineConfig.TRIP_MARGIN_KM))
        } ?: return null
        val stored = c.offlineMaps.states.value[area.key]?.phase == OfflinePhase.COMPLETE
        if (ready != null && stored) return null
        if (!OfflineConfig.withinLimit(area.bounds)) return null
        return OfflineOffer(area, ready != null, OfflineConfig.estimateBytes(area.bounds))
    }

    fun setStart(d: LocalDate) = updatePreview { it.copy(start = d) }

    fun setWithMap(on: Boolean) = updatePreview { it.copy(withMap = on && it.offer != null) }

    private fun updatePreview(f: (RouteStage.Preview) -> RouteStage.Preview) =
        _ui.update { s -> (s.stage as? RouteStage.Preview)?.let { s.copy(stage = f(it)) } ?: s }

    fun backToCode() = _ui.update { it.copy(stage = RouteStage.Code) }

    fun dismissDialog() = _ui.update { it.copy(dialog = null) }

    /** Free space the preview's size line names. */
    fun neededBytes(p: RouteStage.Preview): Long =
        RouteDownload.neededBytes(p.summary.bytes) + if (p.withMap) p.offer?.estimateBytes ?: 0 else 0

    /** "Tải về máy": network, free space, mobile data over 50 MB ([confirmedMetered] = "Vẫn tải"), then start. */
    fun startDownload(confirmedMetered: Boolean = false) {
        val p = _ui.value.stage as? RouteStage.Preview ?: return
        val need = neededBytes(p)
        val dialog = when {
            !c.network.online.value -> RouteDialog.Offline
            c.offlineMaps.freeBytes() < need -> RouteDialog.Storage(need)
            !confirmedMetered && c.network.metered() && p.summary.bytes > METERED_ASK_BYTES -> RouteDialog.Metered(p.summary.bytes)
            else -> null
        }
        if (dialog != null) { _ui.update { it.copy(dialog = dialog) }; return }
        downloads.start(
            RouteDownloadRequest(
                code = p.summary.code,
                start = p.start,
                offlineMap = p.withMap && p.offer != null,
                title = p.summary.title,
                bytes = p.summary.bytes,
                files = p.summary.files,
                mapName = if (p.offer?.readyMade == true) p.offer.area.name else "",
            ),
        )
        _ui.update { it.copy(stage = RouteStage.Download, dialog = null) }
    }

    fun pause() = download.value?.let { downloads.pause(it.request.code) }

    fun resume() = download.value?.let { downloads.start(it.request) }

    /** "Hủy tải": stops the work and deletes the staged files; nothing is created. */
    fun cancel() {
        download.value?.let { downloads.cancel(it.request.code) }
        _ui.update { it.copy(stage = RouteStage.Code) }
    }

    /** A failed download's "Đóng": back to the code, staged files kept for a retry. */
    fun closeFailed() {
        download.value?.let { downloads.finished(it.request.code) }
        _ui.update { it.copy(stage = RouteStage.Code) }
    }

    companion object {
        /** Above this size a download over mobile data asks first (page 05). */
        const val METERED_ASK_BYTES = 50_000_000L
    }
}
