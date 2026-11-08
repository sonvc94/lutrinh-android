package vn.lutrinh.app.ui.steps

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.domain.usecase.NewStepDefaults
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepError
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.common.VietnameseLocale
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.DiscardDialog
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.display
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatCoord
import vn.lutrinh.app.ui.trips.DateField
import vn.lutrinh.app.ui.trips.DatePickDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class StepForm(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val trip: Trip? = null,
    val title: String = "",
    val note: String = "",
    val location: GeoPoint? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = NewStepDefaults.DEFAULT_TIME,
    val photos: List<String> = emptyList(),
    val importing: Boolean = false,
    val importFailed: Int = 0,
    val errors: Set<StepError> = emptySet(),
    val dirty: Boolean = false,
    val savedId: String? = null,
    val deleted: Boolean = false,
    /** Where the picker should open when no location is set yet. */
    val suggestedLocation: GeoPoint? = null,
)

class StepEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val stepId: String? = handle.get<String>("stepId")
    private val _form = MutableStateFlow(StepForm())
    val form = _form.asStateFlow()

    /** Files copied during this editing session; deleted again unless the step is saved with them. */
    private val importedThisSession = mutableSetOf<String>()
    private var finished = false

    init {
        viewModelScope.launch {
            val trip = c.trips.getTrip(tripId)
            val steps = c.steps.getSteps(tripId)
            val step = stepId?.let { c.steps.getStep(it) }
            if (trip == null || (stepId != null && step == null)) {
                _form.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            _form.update {
                if (step != null) it.fromStep(trip, step)
                else {
                    val arrival = NewStepDefaults.arrival(trip, steps, LocalDateTime.now())
                    it.copy(
                        loading = false,
                        trip = trip,
                        date = arrival.toLocalDate(),
                        time = arrival.toLocalTime(),
                        suggestedLocation = NewStepDefaults.location(steps),
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
    )

    fun edit(change: StepForm.() -> StepForm) = _form.update { it.change().copy(dirty = true, errors = emptySet()) }

    fun setLocation(p: GeoPoint) = edit { copy(location = p) }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _form.update { it.copy(importing = true, importFailed = 0) }
        viewModelScope.launch {
            var failed = 0
            val added = mutableListOf<String>()
            for (uri in uris) {
                val name = c.photos.import(uri)
                if (name == null) failed++ else { added += name; importedThisSession += name }
            }
            _form.update { it.copy(importing = false, importFailed = failed) }
            edit { copy(photos = photos + added) }
        }
    }

    fun removePhoto(name: String) = edit { copy(photos = photos - name) }

    fun makeCover(name: String) = edit { copy(photos = listOf(name) + (photos - name)) }

    fun save() = viewModelScope.launch {
        val f = _form.value
        val input = StepInput(f.title, f.note, f.location, LocalDateTime.of(f.date, f.time), f.photos)
        when (val r = c.saveStep(tripId, stepId, input)) {
            is SaveResult.Saved -> {
                finished = true
                c.photos.delete(importedThisSession - f.photos.toSet())
                _form.update { it.copy(savedId = r.id, dirty = false) }
            }
            is SaveResult.Invalid -> _form.update { it.copy(errors = r.errors) }
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditScreen(
    pickedLocation: GeoPoint?,
    onPickedConsumed: () -> Unit,
    onPickLocation: (current: GeoPoint?) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
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
            vm.setLocation(pickedLocation)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.step_create_title else R.string.step_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (f.dirty) confirmDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = { TextButton(onClick = vm::save, enabled = !f.importing) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        when {
            f.loading -> LoadingState(Modifier.padding(padding))
            f.missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
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
                LocationCard(f, onPick = { onPickLocation(f.location ?: f.suggestedLocation) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(stringResource(R.string.step_field_date), f.date.display(), Modifier.weight(1f)) { pickDate = true }
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(0.7f).height(64.dp)) {
                        Icon(Icons.Outlined.Schedule, null)
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_field_time), style = MaterialTheme.typography.labelSmall)
                            Text("%02d:%02d".format(f.time.hour, f.time.minute), style = MaterialTheme.typography.bodyMedium)
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
                PhotosSection(
                    f = f,
                    onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = vm::removePhoto,
                    onCover = vm::makeCover,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = vm::save, enabled = !f.importing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.action_save))
                }
                if (!isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null)
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
            body = stringResource(R.string.step_delete_confirm_body, f.title, f.photos.size),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false })
}

@Composable
private fun LocationCard(f: StepForm, onPick: () -> Unit) {
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
                Text(
                    loc?.let { formatCoord(it.latitude, it.longitude) } ?: stringResource(R.string.step_location_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            FilledTonalButton(onClick = onPick) {
                Icon(Icons.Outlined.EditLocationAlt, null)
                Spacer(Modifier.padding(3.dp))
                Text(stringResource(if (loc == null) R.string.step_location_pick else R.string.step_location_change))
            }
        }
    }
}

@Composable
private fun PhotosSection(f: StepForm, onAdd: () -> Unit, onRemove: (String) -> Unit, onCover: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${stringResource(R.string.step_photos)} (${f.photos.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onAdd, enabled = !f.importing && f.photos.size < Limits.PHOTOS_PER_STEP_MAX) {
                Icon(Icons.Outlined.AddPhotoAlternate, null)
                Spacer(Modifier.padding(3.dp))
                Text(stringResource(R.string.step_photos_add))
            }
        }
        if (f.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (f.importFailed > 0) Text(stringResource(R.string.step_photos_import_failed, f.importFailed), color = MaterialTheme.colorScheme.error)
        if (StepError.TOO_MANY_PHOTOS in f.errors) {
            Text(stringResource(R.string.err_step_too_many_photos, Limits.PHOTOS_PER_STEP_MAX), color = MaterialTheme.colorScheme.error)
        }
        if (f.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(f.photos, key = { it }) { name ->
                    val isCover = name == f.photos.first()
                    Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).clickable { onCover(name) }) {
                        LocalPhoto(name, Modifier.fillMaxSize())
                        if (isCover) {
                            Text(
                                stringResource(R.string.step_photo_cover),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        IconButton(
                            onClick = { onRemove(name) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        ) { Icon(Icons.Filled.Close, stringResource(R.string.step_photo_remove), tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            Text(stringResource(R.string.step_photos_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
