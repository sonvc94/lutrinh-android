package vn.lutrinh.app.ui.steps

import android.net.Uri
import vn.lutrinh.app.ui.theme.TopoIcons
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import vn.lutrinh.app.data.MediaCapture
import vn.lutrinh.app.data.VideoImport
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.VideoProblem
import vn.lutrinh.app.domain.usecase.VideoRules
import vn.lutrinh.app.ui.common.LocalMedia
import vn.lutrinh.app.ui.common.formatBytes
import java.time.temporal.ChronoUnit
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.data.GalleryAccess
import vn.lutrinh.app.data.GalleryReader
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.PhotoSource
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.GalleryPhoto
import vn.lutrinh.app.domain.usecase.GallerySelection
import vn.lutrinh.app.domain.usecase.AutoTitle
import vn.lutrinh.app.domain.usecase.PlaceLabel
import vn.lutrinh.app.domain.usecase.ExtendCase
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.domain.usecase.PhotoWindow
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepError
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.StopPhotos
import vn.lutrinh.app.domain.usecase.StopSuggestions
import vn.lutrinh.app.domain.usecase.TripDates
import vn.lutrinh.app.domain.usecase.TripExtension
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.common.VietnameseLocale
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.DiscardDialog
import vn.lutrinh.app.ui.common.DraftBadge
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.display
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.ChangeLocationButton
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.common.placeLines
import vn.lutrinh.app.ui.common.rememberGazetteer
import vn.lutrinh.app.ui.trips.DateField
import vn.lutrinh.app.ui.trips.DatePickDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class StepForm(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val trip: Trip? = null,
    val title: String = "",
    val note: String = "",
    val location: GeoPoint? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = NewStepDefaults.DEFAULT_TIME,
    /** Photo and video files in the order the user arranged (R27); the first is the step cover. */
    val photos: List<String> = emptyList(),
    /** R29: which of [photos] are videos, with their length in ms. */
    val videoDurations: Map<String, Long> = emptyMap(),
    /** R29: videos not added (too long / too large / unreadable) in the last pick or recording. */
    val videoRefusals: List<VideoRefusal> = emptyList(),
    /** R28/R29: no camera app answered "Chụp ảnh" / "Quay video". */
    val noCamera: Boolean = false,
    val importing: Boolean = false,
    val importFailed: Int = 0,
    val errors: Set<StepError> = emptySet(),
    val dirty: Boolean = false,
    val savedId: String? = null,
    val deleted: Boolean = false,
    /** Where the picker should open when no location is set yet. */
    val suggestedLocation: GeoPoint? = null,
    /** R17: asks whether to widen the trip for a step from a stop suggestion dated outside it. */
    val extendOffer: ExtendOffer? = null,
    /** R19: "Ảnh chụp trong lúc dừng"; only for a step opened from a stop suggestion. */
    val stopPhotos: StopPhotosState? = null,
    /** Steps of the trip (for "gần <điểm dừng>" under the location, F15); includes the edited one. */
    val tripSteps: List<Step> = emptyList(),
    /** R20: the edited step is a draft (shown as a badge); a new step is not. */
    val isDraft: Boolean = false,
    /** R20: the last save asked for a draft ("Lưu nháp"); kept for the R17 extend-trip retry. */
    val saveAsDraft: Boolean = false,
    /** R24: the place name last filled into [title] (null = none) and the title before it. */
    val autoTitle: String? = null,
    val titleBeforeAuto: String = "",
) {
    /** R24: the title after the location moved to a point named [place]; a typed title stays. */
    fun placed(place: String?): StepForm =
        AutoTitle(title, autoTitle, titleBeforeAuto).placed(place).let { copy(title = it.title, autoTitle = it.auto, titleBeforeAuto = it.beforeAuto) }

    /** Gallery photos that "Lưu" will copy into the step (R19), in grid order. */
    val galleryPhotos: List<GalleryPhoto> get() = stopPhotos?.selection?.selected.orEmpty()

    /** What the single photo grid shows and the "Ảnh (N)" title counts: picked files, then ticked gallery photos. */
    val photoCount: Int get() = photos.size + galleryPhotos.size
}

/** Gallery photos taken during the stop (R19): access, ticks and the time window. */
data class StopPhotosState(
    val window: PhotoWindow,
    val access: GalleryAccess = GalleryAccess.NONE,
    /** The system permission request was shown from this editor (NONE afterwards = denied). */
    val asked: Boolean = false,
    val loading: Boolean = false,
    /** True once the gallery was read with access (an empty grid then means "no photos"). */
    val loaded: Boolean = false,
    val selection: GallerySelection = GallerySelection(),
)

/** A video that was not added (R29), with what was read of it for the message. */
data class VideoRefusal(val problem: VideoProblem, val durationMs: Long?, val sizeBytes: Long?)

/** The trip dates now and the smallest dates that would hold the step (R17). */
data class ExtendOffer(val current: TripDates, val widened: TripDates)

class StepEditViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val stepId: String? = handle.get<String>("stepId")

    /** Set when the editor was opened from "Thêm điểm dừng" on a stop suggestion (R17). */
    private val fromStop: StopPrefill? = StopPrefill.from(handle)
    private val _form = MutableStateFlow(
        StepForm(stopPhotos = fromStop?.let { StopPhotosState(StopPhotos.window(it.startAt, it.endAt, ZoneId.systemDefault())) }),
    )
    val form = _form.asStateFlow()

    /** Files copied during this editing session; deleted again unless the step is saved with them. */
    private val importedThisSession = mutableSetOf<String>()

    /** EXIF capture time of the files imported in this session (R17). */
    private val takenAtByFile = mutableMapOf<String, LocalDateTime>()

    /** Gallery id / original size of the files imported in this session (R19 de-duplication). */
    private val sourceByFile = mutableMapOf<String, PhotoSource>()

    /** Gallery photo id -> file already copied for it in this session (a retried save reuses it, R19). */
    private val galleryFiles = mutableMapOf<String, String>()
    private var galleryJob: Job? = null
    private var finished = false

    init {
        // R28/R29: a capture target survives process death in the handle; without one, any file left in the capture folder is stale.
        if (handle.get<String>(KEY_CAPTURE) == null) viewModelScope.launch { c.capture.clearLeftovers() }
        viewModelScope.launch {
            val trip = c.trips.getTrip(tripId)
            val steps = c.steps.getSteps(tripId)
            val step = stepId?.let { c.steps.getStep(it) }
            if (trip == null || (stepId != null && step == null)) {
                _form.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            // R24: a stop suggestion's default title becomes the place there.
            val stopPlace = fromStop?.let { c.places.await()?.nearest(it.location)?.place?.name }
            _form.update {
                if (step != null) it.fromStep(trip, step).copy(tripSteps = steps)
                else if (fromStop != null) {
                    // R17: location = the stay's centre, arrival = its start, title = "Điểm dừng HH:mm".
                    val arrival = StopSuggestions.localMinute(fromStop.startAt, ZoneId.systemDefault())
                    it.copy(
                        loading = false,
                        trip = trip,
                        title = fromStop.title,
                        location = fromStop.location,
                        date = arrival.toLocalDate(),
                        time = arrival.toLocalTime(),
                        suggestedLocation = fromStop.location,
                        tripSteps = steps,
                    ).placed(stopPlace)
                } else {
                    val arrival = NewStepDefaults.arrival(trip, steps, LocalDateTime.now())
                    it.copy(
                        loading = false,
                        trip = trip,
                        date = arrival.toLocalDate(),
                        time = arrival.toLocalTime(),
                        suggestedLocation = NewStepDefaults.location(steps),
                        tripSteps = steps,
                    )
                }
            }
        }
    }

    private fun StepForm.fromStep(trip: Trip, s: Step) = copy(
        loading = false,
        trip = trip,
        title = s.title,
        note = s.note,
        location = s.location,
        date = s.arrivedAt.toLocalDate(),
        time = s.arrivedAt.toLocalTime(),
        photos = s.photos.map { it.fileName },
        videoDurations = s.photos.filter { it.isVideo }.associate { it.fileName to (it.durationMs ?: 0L) },
        isDraft = s.isDraft,
    )

    fun edit(change: StepForm.() -> StepForm) = _form.update { it.change().copy(dirty = true, errors = emptySet()) }

    /**
     * A new location (picker, search, "Vị trí của tôi"). R24: a title the user has not typed takes
     * [pickedName] (a search result) or the nearest gazetteer place.
     */
    fun setLocation(p: GeoPoint, pickedName: String? = null) {
        edit { copy(location = p) }
        viewModelScope.launch {
            val name = pickedName ?: c.places.await()?.nearest(p)?.place?.name
            _form.update { f -> if (f.location == p) f.placed(name) else f }
        }
    }

    /** Photo Picker result: photos and (R29) videos, in the order picked. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _form.update { it.copy(importing = true, importFailed = 0, videoRefusals = emptyList(), noCamera = false) }
        viewModelScope.launch {
            var failed = 0
            val added = mutableListOf<String>()
            val videos = mutableMapOf<String, Long>()
            val refused = mutableListOf<VideoRefusal>()
            val isVideo = uris.associateWith { c.photos.isVideo(it) }
            // EXIF date of the picked photos, read before import: the stored copy has no EXIF (R17).
            val images = uris.filterNot { isVideo.getValue(it) }
            val meta = runCatching { c.photoMeta.read(images) }.getOrDefault(emptyList())
            for (uri in uris) {
                if (isVideo.getValue(uri)) {
                    when (val r = c.photos.importVideo(uri)) {
                        is VideoImport.Added -> {
                            added += r.fileName
                            videos[r.fileName] = r.durationMs
                            importedThisSession += r.fileName
                            r.takenAt?.let { takenAtByFile[r.fileName] = it }
                        }
                        is VideoImport.Refused -> refused += VideoRefusal(r.problem, r.durationMs, r.sizeBytes)
                    }
                    continue
                }
                val name = c.photos.import(uri)
                if (name == null) failed++ else {
                    added += name
                    importedThisSession += name
                    meta.getOrNull(images.indexOf(uri))?.takenAt?.let { takenAtByFile[name] = it }
                    sourceByFile[name] = c.gallery.sourceOf(uri) // R19: not offered again from the gallery
                }
            }
            _form.update { it.copy(importing = false, importFailed = failed, videoRefusals = refused) }
            if (added.isNotEmpty()) edit { copy(photos = photos + added, videoDurations = videoDurations + videos) }
        }
    }

    /**
     * R28 "Chụp ảnh" / R29 "Quay video": a new target file for the system camera; its name is kept
     * in the saved state so the result is found again after process death. Returns its URI.
     */
    fun startCapture(kind: MediaCapture.Kind): Uri {
        val target = c.capture.newTarget(kind)
        handle[KEY_CAPTURE] = target.name
        _form.update { it.copy(importFailed = 0, videoRefusals = emptyList(), noCamera = false) }
        return c.capture.uriFor(target)
    }

    /** No camera app could be started: the target is dropped. */
    fun captureUnavailable() {
        onCaptureResult(ok = false, returned = null)
        _form.update { it.copy(noCamera = true) }
    }

    /**
     * The camera returned. OK: the file is imported like a picked one (photo: R11; video: R29
     * limits), its time is now (R28), it has no gallery origin. Cancelled: nothing is added and
     * the target file is deleted (R28: a cancelled capture leaves no file).
     */
    fun onCaptureResult(ok: Boolean, returned: Uri?) {
        val name = handle.get<String>(KEY_CAPTURE) ?: return
        handle.remove<String>(KEY_CAPTURE)
        val target = c.capture.target(name) ?: return
        val video = name.endsWith(".${MediaCapture.Kind.VIDEO.ext}")
        val ownUri = c.capture.uriFor(target)
        if (ok) _form.update { it.copy(importing = true) }
        viewModelScope.launch {
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            var failed = 0
            var refusal: VideoRefusal? = null
            var added: String? = null
            var duration: Long? = null
            c.capture.finish(target, ok, fallback = returned?.takeIf { it != ownUri }) { uri ->
                if (video) {
                    when (val r = c.photos.importVideo(uri)) {
                        is VideoImport.Added -> { added = r.fileName; duration = r.durationMs }
                        is VideoImport.Refused -> refusal = VideoRefusal(r.problem, r.durationMs, r.sizeBytes)
                    }
                } else {
                    added = c.photos.import(uri)
                    if (added == null) failed++
                }
            }
            added?.let { file ->
                importedThisSession += file
                takenAtByFile[file] = now
                sourceByFile[file] = PhotoSource()
            }
            _form.update { it.copy(importing = false, importFailed = failed, videoRefusals = listOfNotNull(refusal)) }
            added?.let { file ->
                edit { copy(photos = photos + file, videoDurations = duration?.let { videoDurations + (file to it) } ?: videoDurations) }
            }
        }
    }

    /** R27: the editor's new order after a drag (or a move action); stored by "Lưu". */
    fun reorderPhotos(order: List<String>) {
        if (order != _form.value.photos && order.toSet() == _form.value.photos.toSet()) edit { copy(photos = order) }
    }

    fun removePhoto(name: String) = edit { copy(photos = photos - name) }

    fun makeCover(name: String) = edit { copy(photos = listOf(name) + (photos - name)) }

    /**
     * R19: reads which gallery photos of the stop may be offered (on open, on resume, after the
     * permission answer). Without access the grid is empty and the section explains why.
     */
    fun refreshGallery() {
        val window = _form.value.stopPhotos?.window ?: return
        galleryJob?.cancel()
        galleryJob = viewModelScope.launch {
            val access = c.gallery.access()
            if (access == GalleryAccess.NONE) {
                updateStopPhotos { copy(access = access, loading = false, loaded = false, selection = selection.withPhotos(emptyList())) }
                return@launch
            }
            updateStopPhotos { copy(access = access, loading = true) }
            val candidates = c.gallery.candidates(window, ZoneId.systemDefault())
            val tripPhotos = c.steps.getSteps(tripId).flatMap { it.photos }
            val offered = StopPhotos.offer(candidates, window, tripPhotos)
            updateStopPhotos { copy(loading = false, loaded = true, selection = selection.withPhotos(offered)) }
        }
    }

    /** The system permission request is about to be shown (R19). */
    fun galleryAccessRequested() = updateStopPhotos { copy(asked = true) }

    fun toggleGalleryPhoto(id: String) = updateStopPhotos { copy(selection = selection.toggle(id)) }

    private fun updateStopPhotos(change: StopPhotosState.() -> StopPhotosState) =
        _form.update { f -> f.stopPhotos?.let { f.copy(stopPhotos = it.change()) } ?: f }

    /**
     * Copies the ticked gallery photos (R19) like picked ones (R11, capture time and source kept);
     * returns their files in grid order, or null when the save must stop (too many photos, or a
     * file could not be read: the user can untick it).
     */
    private suspend fun importTickedGalleryPhotos(f: StepForm): List<String>? {
        val ticked = f.stopPhotos?.selection?.selected.orEmpty()
        if (ticked.isEmpty()) return emptyList()
        if (f.photoCount > Limits.PHOTOS_PER_STEP_MAX) {
            _form.update { it.copy(errors = setOf(StepError.TOO_MANY_PHOTOS)) }
            return null
        }
        _form.update { it.copy(importing = true, importFailed = 0) }
        var failed = 0
        for (g in ticked) {
            if (g.id in galleryFiles) continue
            val name = c.photos.import(Uri.parse(g.uri))
            if (name == null) { failed++; continue }
            galleryFiles[g.id] = name
            importedThisSession += name
            g.takenAt?.let { takenAtByFile[name] = it }
            sourceByFile[name] = PhotoSource(g.id, g.sizeBytes)
        }
        _form.update { it.copy(importing = false, importFailed = failed) }
        return if (failed > 0) null else ticked.mapNotNull { galleryFiles[it.id] }
    }

    /**
     * [draft]: "Lưu nháp" (true) or "Lưu" (false: published, R20). [extendTrip]: the user accepted
     * widening the trip for this step (only offered from a stop suggestion).
     */
    fun save(draft: Boolean = false, extendTrip: Boolean = false) = viewModelScope.launch {
        _form.update { it.copy(extendOffer = null, saveAsDraft = draft) }
        val f = _form.value
        val fromGallery = importTickedGalleryPhotos(f) ?: return@launch
        val files = f.photos + fromGallery.filterNot { it in f.photos }
        val input = StepInput(
            f.title, f.note, f.location, LocalDateTime.of(f.date, f.time), files, takenAtByFile.toMap(), sourceByFile.toMap(),
            draft = draft,
            videoDurations = f.videoDurations,
        )
        when (val r = c.saveStep(tripId, stepId, input, extendTrip && fromStop != null)) {
            is SaveResult.Saved -> {
                finished = true
                fromStop?.let { stop ->
                    c.stopDecisions.saveDecision(
                        StopDecision(stop.trackId, stop.startAt, stop.endAt, StopDecisionKind.ADDED, r.id, c.clock.nowMillis()),
                    )
                }
                c.photos.delete(importedThisSession - files.toSet())
                _form.update { it.copy(savedId = r.id, dirty = false) }
            }
            is SaveResult.Invalid -> {
                // R17: only a date outside the trip, only from a suggestion; any other error is shown as usual.
                val offer = if (fromStop != null && r.errors.all { it == StepError.BEFORE_TRIP_START || it == StepError.AFTER_TRIP_END }) {
                    c.trips.getTrip(tripId)?.let { trip ->
                        TripExtension.widen(trip.startDate, trip.endDate, f.date)?.let { ExtendOffer(TripDates(trip.startDate, trip.endDate), it) }
                    }
                } else null
                _form.update { it.copy(errors = r.errors, extendOffer = offer) }
            }
        }
    }

    fun dismissExtendOffer() = _form.update { it.copy(extendOffer = null) }

    fun delete() = viewModelScope.launch {
        stepId?.let { c.deleteStep(it) }
        _form.update { it.copy(deleted = true) }
    }

    override fun onCleared() {
        if (!finished && importedThisSession.isNotEmpty()) {
            val leftovers = importedThisSession.toList()
            CoroutineScope(Dispatchers.IO).launch { c.photos.delete(leftovers) }
        }
    }
}

private const val KEY_CAPTURE = "pendingCapture"

/** Values passed by "Thêm điểm dừng" through the navigation arguments (Routes.stepFromStop). */
data class StopPrefill(
    val trackId: String,
    val startAt: Long,
    val endAt: Long,
    val location: GeoPoint,
    val title: String,
) {
    companion object {
        fun from(handle: SavedStateHandle): StopPrefill? {
            val track = handle.get<String>("stopTrack") ?: return null
            val start = handle.get<String>("stopStart")?.toLongOrNull() ?: return null
            val end = handle.get<String>("stopEnd")?.toLongOrNull() ?: return null
            val lat = handle.get<String>("lat")?.toDoubleOrNull() ?: return null
            val lng = handle.get<String>("lng")?.toDoubleOrNull() ?: return null
            return StopPrefill(track, start, end, GeoPoint(lat, lng), handle.get<String>("title").orEmpty())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditScreen(
    pickedLocation: GeoPoint?,
    onPickedConsumed: () -> Unit,
    onPickLocation: (current: GeoPoint?) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    pickedName: String? = null,
    onDeleted: () -> Unit,
    vm: StepEditViewModel = viewModel(factory = factory { c, h -> StepEditViewModel(c, h) }),
) {
    val f by vm.form.collectAsStateWithLifecycle()
    val isNew = vm.stepId == null
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(pickedLocation) {
        if (pickedLocation != null) {
            vm.setLocation(pickedLocation, pickedName)
            onPickedConsumed()
        }
    }
    LaunchedEffect(f.savedId) { if (f.savedId != null) onSaved() }
    LaunchedEffect(f.deleted) { if (f.deleted) onDeleted() }
    BackHandler(enabled = f.dirty) { confirmDiscard = true }

    // Android Photo Picker: no storage permission; falls back to the document picker on old devices.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_PER_STEP_MAX),
    ) { uris -> vm.addPhotos(uris) }
    // R28/R29: the system camera app writes into our capture file (no CAMERA permission).
    val camera = rememberLauncherForActivityResult(CaptureWithSystemCamera()) { r -> vm.onCaptureResult(r.ok, r.returned) }
    fun capture(kind: MediaCapture.Kind) {
        val uri = vm.startCapture(kind)
        try {
            camera.launch(CaptureWithSystemCamera.Request(uri, video = kind == MediaCapture.Kind.VIDEO))
        } catch (e: ActivityNotFoundException) {
            vm.captureUnavailable()
        }
    }

    // R19: gallery access for "Ảnh chụp trong lúc dừng"; re-read on every resume (settings may change).
    val galleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshGallery()
    }
    LifecycleResumeEffect(Unit) {
        vm.refreshGallery()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (isNew) R.string.step_create_title else R.string.step_edit_title))
                        if (f.isDraft) DraftBadge(Modifier.padding(start = 8.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (f.dirty) confirmDiscard = true else onBack() }) {
                        Icon(TopoIcons.Back, stringResource(R.string.action_back))
                    }
                },
                actions = { TextButton(onClick = { vm.save() }, enabled = !f.importing) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        when {
            f.loading -> LoadingState(Modifier.padding(padding))
            f.missing -> MessageState(stringResource(R.string.not_found), "", Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val titleError = when {
                    StepError.TITLE_BLANK in f.errors -> stringResource(R.string.err_step_title_blank)
                    StepError.TITLE_TOO_LONG in f.errors -> stringResource(R.string.err_step_title_long, Limits.STEP_TITLE_MAX)
                    else -> null
                }
                OutlinedTextField(
                    value = f.title,
                    onValueChange = { v -> vm.edit { copy(title = v.take(Limits.STEP_TITLE_MAX + 10)) } },
                    label = { Text(stringResource(R.string.step_field_title)) },
                    placeholder = { Text(stringResource(R.string.step_field_title_hint)) },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                LocationCard(f, vm.stepId, onPick = { onPickLocation(f.location ?: f.suggestedLocation) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(stringResource(R.string.step_field_date), f.date.display(), Modifier.weight(1f)) { pickDate = true }
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { pickTime = true }, modifier = Modifier.weight(0.7f).height(64.dp)) {
                        Icon(TopoIcons.Time, null)
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_field_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(f.time.displayTime(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                f.trip?.let { trip ->
                    if (StepError.BEFORE_TRIP_START in f.errors) {
                        Text(stringResource(R.string.err_step_before_trip, trip.startDate.display()), color = MaterialTheme.colorScheme.error)
                    }
                    if (StepError.AFTER_TRIP_END in f.errors && trip.endDate != null) {
                        Text(stringResource(R.string.err_step_after_trip, trip.endDate.display()), color = MaterialTheme.colorScheme.error)
                    }
                }
                OutlinedTextField(
                    value = f.note,
                    onValueChange = { v -> vm.edit { copy(note = v) } },
                    label = { Text(stringResource(R.string.step_field_note)) },
                    placeholder = { Text(stringResource(R.string.step_field_note_hint)) },
                    minLines = 4,
                    isError = StepError.NOTE_TOO_LONG in f.errors,
                    supportingText = if (StepError.NOTE_TOO_LONG in f.errors) {
                        { Text(stringResource(R.string.err_step_note_long, Limits.STEP_NOTE_MAX)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                // F3: one grid of what "Lưu" saves; the gallery card below only gives access / re-adds.
                PhotosSection(
                    f = f,
                    onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onTakePhoto = { capture(MediaCapture.Kind.PHOTO) },
                    onRecordVideo = { capture(MediaCapture.Kind.VIDEO) },
                    onReorder = vm::reorderPhotos,
                    onRemove = vm::removePhoto,
                    onCover = vm::makeCover,
                    onUntick = vm::toggleGalleryPhoto,
                )
                f.stopPhotos?.let { sp ->
                    StopPhotosSection(
                        sp,
                        onRequestAccess = { vm.galleryAccessRequested(); galleryPermission.launch(GalleryReader.permissions()) },
                        onToggle = vm::toggleGalleryPhoto,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // R20: "Lưu nháp" keeps the step out of the trip; "Lưu" publishes it (also a draft).
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { vm.save(draft = true) }, enabled = !f.importing, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.step_save_draft))
                    }
                    Button(shape = MaterialTheme.shapes.small, onClick = { vm.save() }, enabled = !f.importing, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                if (!isNew) {
                    OutlinedButton(shape = MaterialTheme.shapes.small,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(TopoIcons.Delete, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.step_delete_button))
                    }
                }
            }
        }
    }

    if (pickDate) DatePickDialog(f.date, { pickDate = false }) { d -> vm.edit { copy(date = d) } }
    if (pickTime) TimePickDialog(f.time, { pickTime = false }) { t -> vm.edit { copy(time = t) } }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.step_delete_confirm_title),
            body = stringResource(R.string.step_delete_confirm_body, f.title, f.photoCount),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    f.extendOffer?.let { offer ->
        // F12: three wordings (R17); an open-ended trip has no end to "kéo dài đến".
        val bodyRes = when (TripExtension.caseOf(offer.current, offer.widened)) {
            ExtendCase.BEFORE_START -> R.string.extend_trip_body_before_start
            ExtendCase.AFTER_END -> R.string.extend_trip_body_after_end
            ExtendCase.OPEN_ENDED -> R.string.extend_trip_body_open_ended
        }
        val (was, now) = when (TripExtension.caseOf(offer.current, offer.widened)) {
            ExtendCase.AFTER_END -> (offer.current.end ?: offer.current.start).display() to (offer.widened.end ?: offer.widened.start).display()
            else -> offer.current.start.display() to offer.widened.start.display()
        }
        AlertDialog(
            onDismissRequest = vm::dismissExtendOffer,
            title = { Text(stringResource(R.string.extend_trip_title)) },
            text = { Text(stringResource(bodyRes, was, now)) },
            confirmButton = { TextButton(onClick = { vm.save(draft = f.saveAsDraft, extendTrip = true) }) { Text(stringResource(R.string.extend_trip_confirm)) } },
            dismissButton = { TextButton(onClick = vm::dismissExtendOffer) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false })
}

@Composable
private fun LocationCard(f: StepForm, excludeId: String?, onPick: () -> Unit) {
    val error = when {
        StepError.LOCATION_MISSING in f.errors -> stringResource(R.string.err_step_location_missing)
        StepError.LOCATION_INVALID in f.errors -> stringResource(R.string.err_step_location_invalid)
        else -> null
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().let {
            if (error != null) it.border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium) else it
        },
    ) {
        val loc = f.location
        if (loc != null) {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                TripMap(markers = listOf(MapMarker("x", "•", loc, highlighted = true)), interactive = false, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().clickable(onClick = onPick))
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.step_location), style = MaterialTheme.typography.labelMedium)
                val lines = loc?.let { placeLines(it, f.tripSteps, excludeId, rememberGazetteer()) }
                Text(
                    lines?.first ?: stringResource(R.string.step_location_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                lines?.second?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            ChangeLocationButton(hasLocation = loc != null, onClick = onPick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotosSection(
    f: StepForm,
    onAdd: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onCover: (String) -> Unit,
    onUntick: (String) -> Unit,
) {
    val gallery = f.galleryPhotos
    // The first tile of the grid is the cover: a picked file, else the first gallery photo.
    val coverIsGallery = f.photos.isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${stringResource(R.string.step_photos)} (${f.photoCount})", style = MaterialTheme.typography.titleSmall)
        // R28/R29: picker (photos and videos), system camera photo, system camera video.
        val canAdd = !f.importing && f.photoCount < Limits.PHOTOS_PER_STEP_MAX
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddMediaButton(TopoIcons.AddPhotos, stringResource(R.string.step_photos_add), canAdd, onAdd)
            AddMediaButton(TopoIcons.Camera, stringResource(R.string.step_photos_take), canAdd, onTakePhoto)
            AddMediaButton(TopoIcons.Video, stringResource(R.string.step_photos_record), canAdd, onRecordVideo)
        }
        if (f.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (f.importFailed > 0) Text(stringResource(R.string.step_photos_import_failed, f.importFailed), color = MaterialTheme.colorScheme.error)
        if (f.noCamera) Text(stringResource(R.string.step_photos_no_camera), color = MaterialTheme.colorScheme.error)
        f.videoRefusals.forEach { r -> Text(videoRefusalText(r), color = MaterialTheme.colorScheme.error) }
        if (StepError.TOO_MANY_PHOTOS in f.errors) {
            Text(stringResource(R.string.err_step_too_many_photos, Limits.PHOTOS_PER_STEP_MAX), color = MaterialTheme.colorScheme.error)
        }
        if (f.photoCount > 0) {
            ReorderableMediaRow(
                files = f.photos,
                onReorder = onReorder,
                tile = { name, moveActions ->
                    PhotoTile(
                        isCover = name == f.photos.first(),
                        onClick = { onCover(name) },
                        onRemove = { onRemove(name) },
                        moveActions = moveActions,
                    ) {
                        LocalMedia(name, f.videoDurations[name], Modifier.fillMaxSize())
                    }
                },
            ) {
                items(gallery, key = { "g" + it.id }) { g ->
                    PhotoTile(isCover = coverIsGallery && g == gallery.first(), fromGallery = true, onClick = null, onRemove = { onUntick(g.id) }) {
                        AsyncImage(
                            model = Uri.parse(g.uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
            if (f.photos.isNotEmpty()) {
                Text(stringResource(R.string.step_photos_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddMediaButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.padding(2.dp))
        Text(label)
    }
}

@Composable
private fun videoRefusalText(r: VideoRefusal): String = when (r.problem) {
    VideoProblem.TOO_LONG -> stringResource(R.string.video_refused_long, DisplayFormat.clock(r.durationMs ?: 0L), VideoRules.MAX_SECONDS)
    VideoProblem.TOO_LARGE -> stringResource(R.string.video_refused_large, r.sizeBytes?.let(::formatBytes) ?: "", formatBytes(VideoRules.MAX_BYTES))
    VideoProblem.UNREADABLE -> stringResource(R.string.video_refused_unreadable)
}

/** One tile of the photo grid: the picture, "Ảnh bìa" on the first, "Từ thư viện" on a gallery photo, ✕ to remove. */
@Composable
private fun PhotoTile(
    isCover: Boolean,
    onClick: (() -> Unit)?,
    onRemove: () -> Unit,
    fromGallery: Boolean = false,
    moveActions: List<CustomAccessibilityAction> = emptyList(),
    picture: @Composable () -> Unit,
) {
    Box(
        Modifier.size(104.dp).clip(MaterialTheme.shapes.large)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .let { if (moveActions.isNotEmpty()) it.semantics { customActions = moveActions } else it },
    ) {
        picture()
        if (isCover) {
            Text(
                stringResource(R.string.step_photo_cover),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (fromGallery) {
            Text(
                stringResource(R.string.step_photo_from_gallery),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) { Icon(TopoIcons.Close, stringResource(R.string.step_photo_remove), tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { VietnameseLocale { TimePicker(state = state) } },
    )
}

/** Steps of the trip except [excludeId], numbered in timeline order (context pins for the picker). */
fun contextMarkers(steps: List<Step>, excludeId: String?): List<MapMarker> =
    steps.inTimelineOrder().mapIndexedNotNull { i, s ->
        if (s.id == excludeId) null else MapMarker(s.id, "${i + 1}", s.location)
    }
