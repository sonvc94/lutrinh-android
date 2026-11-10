package vn.lutrinh.app.ui.share

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.TrackMath
import vn.lutrinh.app.domain.usecase.TripPage
import vn.lutrinh.app.domain.usecase.TripShare
import vn.lutrinh.app.domain.usecase.legsOf
import vn.lutrinh.app.domain.routing.LegRoute
import vn.lutrinh.app.domain.routing.LegRoutes
import vn.lutrinh.app.map.StaticTripMap
import vn.lutrinh.app.share.SummaryImage
import vn.lutrinh.app.share.TripExporter
import java.io.File

enum class ShareKind { IMAGE, PAGE }

/** One generated export, or the work towards it. */
sealed interface ExportState {
    data object Loading : ExportState
    data class Failed(val message: String) : ExportState
    data class Ready(
        val file: File,
        val bytes: Long,
        /** The summary image itself (image only). */
        val preview: Bitmap? = null,
        val photos: Int = 0,
        val omittedPhotos: Int = 0,
        /** False when the basemap could not be loaded and the route is on a plain background. */
        val withBasemap: Boolean = true,
        /** Changes on every rebuild so the page preview reloads. */
        val version: Long = System.nanoTime(),
    ) : ExportState
}

sealed interface ShareMessage {
    data class Saved(val folder: String) : ShareMessage
    data class SaveFailed(val reason: String) : ShareMessage
}

/**
 * R18: keeps both exports in step with the trip (live preview): the map and the summary image
 * are rebuilt when the trip, its steps or its tracks change; the page when it is shown and
 * the trip or "Ẩn ghi chú" changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareTripViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val kind = MutableStateFlow(ShareKind.IMAGE)
    val hideNotes = MutableStateFlow(false)
    private val _messages = Channel<ShareMessage>(Channel.BUFFERED)
    val messages: Flow<ShareMessage> = _messages.receiveAsFlow()

    private data class Source(
        val trip: Trip,
        val steps: List<Step>,
        val tracks: List<List<vn.lutrinh.app.domain.model.GeoPoint>>,
        /** R36: stored leg routes by arrival step id (empty while routing is off). */
        val routes: Map<String, LegRoute> = emptyMap(),
    )

    private data class Rendered(val source: Source, val map: StaticTripMap.Output)

    private val source: Flow<Source?> = combine(
        c.trips.observeTrip(tripId), c.steps.observeSteps(tripId), c.tracks.observePoints(tripId),
        combine(c.legRoutes.observeForTrip(tripId), c.routingSettings.enabled, c.routes.observeLink(tripId)) { stored, _, link ->
            c.routingSettings.shown(stored) { link != null } // D-13: a downloaded route keeps its lines
        },
    ) { trip, steps, points, routes ->
        // R20: drafts are never shared (map, image and page).
        trip?.let { Source(it, TripShare.sharedSteps(steps), TrackMath.displayLines(points), routes) }
    }.distinctUntilChanged()

    val missing: StateFlow<Boolean> = source.map { it == null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var cleaned = false

    private val rendered: StateFlow<Rendered?> = source.filterNotNull().transformLatest { s ->
        emit(null)
        // Exports of an earlier visit are temporary: start clean (before this screen writes any).
        if (!cleaned) { c.exporter.clearCache(); cleaned = true }
        val legs = legsOf(s.steps)
        val used = legs.map { LegRoutes.usable(it, s.routes) }
        val credits = used.mapNotNullTo(sortedSetOf()) { it?.provider }.mapNotNull { c.routers.byId(it)?.credit }.distinct()
        val map = c.exporter.map(
            StaticTripMap.Input(s.steps.map { it.location }, legs.map { it.mode }, s.tracks, legs.mapIndexed { i, l -> LegRoutes.path(l, used[i]) }, credits),
        )
        emit(Rendered(s, map))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val image: StateFlow<ExportState> = rendered.transformLatest { r ->
        if (r == null) return@transformLatest emit(ExportState.Loading)
        emit(ExportState.Loading)
        emit(
            attempt {
                val t = r.source.trip
                val bmp = c.exporter.summaryImage(
                    SummaryImage.Input(t.title, c.exporter.datesOf(t), TripShare.stats(t, r.source.steps, r.source.routes), r.map.bitmap, emptyList()),
                    TripShare.imagePhotos(r.source.steps),
                )
                val file = c.exporter.writePng(bmp, TripShare.fileName(t.title, t.startDate, "png"))
                ExportState.Ready(file, file.length(), preview = bmp, photos = TripShare.imagePhotos(r.source.steps).size, withBasemap = r.map.withBasemap)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportState.Loading)

    /** Built only once the page is chosen, then kept up to date. */
    val page: StateFlow<ExportState> = combine(rendered, hideNotes, kind) { r, hide, k -> Triple(r, hide, k) }
        .filter { it.third == ShareKind.PAGE }
        .map { it.first to it.second }
        .distinctUntilChanged()
        .transformLatest { (r, hide) ->
            if (r == null) return@transformLatest emit(ExportState.Loading)
            emit(ExportState.Loading)
            emit(
                attempt {
                    val t = r.source.trip
                    val content = TripPage.Content(
                        title = t.title,
                        dates = c.exporter.datesOf(t),
                        description = t.description,
                        tripStart = t.startDate,
                        stats = TripShare.stats(t, r.source.steps, r.source.routes),
                        steps = r.source.steps,
                        hideNotes = hide,
                        mapJpeg = c.exporter.mapJpeg(r.map.bitmap),
                        attribution = r.map.attribution,
                    )
                    val (file, result) = c.exporter.writePage(content, TripShare.fileName(t.title, t.startDate, "html"))
                    ExportState.Ready(file, file.length(), photos = result.photosIncluded, omittedPhotos = result.photosOmitted, withBasemap = r.map.withBasemap)
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportState.Loading)

    fun shareIntent(file: File, kind: ShareKind): Intent =
        c.exporter.shareIntent(file, mimeOf(kind), file.name).also { Log.i(TAG, "share ${file.name} as ${c.exporter.uriFor(file)}") }

    fun save(file: File, kind: ShareKind) = viewModelScope.launch {
        runCatching { c.exporter.saveToDevice(file, mimeOf(kind)) }
            .onSuccess { _messages.send(ShareMessage.Saved(it)) }
            .onFailure { _messages.send(ShareMessage.SaveFailed(it.message ?: it.javaClass.simpleName)) }
    }

    fun copyTo(file: File, target: Uri) = viewModelScope.launch {
        runCatching { c.exporter.copyTo(file, target) }
            .onSuccess { _messages.send(ShareMessage.Saved(file.name)) }
            .onFailure { _messages.send(ShareMessage.SaveFailed(it.message ?: it.javaClass.simpleName)) }
    }

    private suspend fun attempt(block: suspend () -> ExportState): ExportState = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "export failed", e)
        ExportState.Failed(e.message ?: e.javaClass.simpleName)
    }

    private fun mimeOf(kind: ShareKind) = if (kind == ShareKind.IMAGE) TripExporter.MIME_PNG else TripExporter.MIME_HTML

    private companion object {
        const val TAG = "ShareTrip"
    }
}
