package vn.lutrinh.app.ui.steps

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
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
import vn.lutrinh.app.domain.usecase.PhotoGrouping
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.StepSuggestion
import vn.lutrinh.app.domain.usecase.suggestStepsFrom
import vn.lutrinh.app.map.MapMarker
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.display
import vn.lutrinh.app.ui.common.displayDateTime
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.ChangeLocationButton
import vn.lutrinh.app.ui.common.DiscardDialog
import vn.lutrinh.app.ui.common.displayTime
import vn.lutrinh.app.ui.common.placeLines
import java.time.LocalDateTime

/** "Thêm từ ảnh" flow state (docs/03-screens.md S9). */
data class FromPhotosState(
    val phase: Phase = Phase.PICKING,
    val trip: Trip? = null,
    val photoCount: Int = 0,
    val suggestions: List<StepSuggestion> = emptyList(),
    /** Where the location picker opens for a group without a location. */
    val fallbackLocation: GeoPoint? = null,
    /** Steps of the trip, for "gần <điểm dừng>" under a suggestion's place (F15). */
    val tripSteps: List<Step> = emptyList(),
    val savedSteps: Int = 0,
    val failedPhotos: Int = 0,
    val failedSteps: Int = 0,
) {
    enum class Phase { PICKING, READING, REVIEW, SAVING, DONE, MISSING }

    val toCreate: List<StepSuggestion> get() = suggestions.filter { it.include }

    fun titleOk(s: StepSuggestion) = s.title.isNotBlank() && s.title.trim().length <= Limits.STEP_TITLE_MAX

    val canConfirm: Boolean get() = toCreate.isNotEmpty() && toCreate.all { titleOk(it) && it.location != null }
}

class FromPhotosViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    private val _state = MutableStateFlow(FromPhotosState())
    val state = _state.asStateFlow()

    /** Index of the suggestion the location picker was opened for. */
    var placingIndex: Int? = null
        private set

    /** [titleFor] gives the default title of a step arriving at the given time ("Điểm dừng HH:mm", F17). */
    fun onPicked(uris: List<Uri>, titleFor: (LocalDateTime) -> String) {
        if (uris.isEmpty()) {
            _state.update { it.copy(phase = FromPhotosState.Phase.DONE) }
            return
        }
        _state.update { it.copy(phase = FromPhotosState.Phase.READING, photoCount = uris.size) }
        viewModelScope.launch {
            val trip = c.trips.getTrip(tripId)
            if (trip == null) {
                _state.update { it.copy(phase = FromPhotosState.Phase.MISSING) }
                return@launch
            }
            val steps = c.steps.getSteps(tripId)
            val groups = PhotoGrouping.group(c.photoMeta.read(uris))
            val suggestions = suggestStepsFrom(groups, trip, steps, LocalDateTime.now(), titleFor)
            _state.update {
                it.copy(
                    phase = FromPhotosState.Phase.REVIEW,
                    trip = trip,
                    suggestions = suggestions,
                    tripSteps = steps,
                    fallbackLocation = groups.firstNotNullOfOrNull { g -> g.location } ?: NewStepDefaults.location(steps),
                )
            }
        }
    }

    private fun change(index: Int, f: StepSuggestion.() -> StepSuggestion) = _state.update { s ->
        s.copy(suggestions = s.suggestions.mapIndexed { i, x -> if (i == index) x.f() else x })
    }

    fun setTitle(index: Int, title: String) = change(index) { copy(title = title) }

    fun setInclude(index: Int, include: Boolean) = change(index) {
        copy(include = include && location != null && !outsideTrip)
    }

    fun startPlacing(index: Int) {
        placingIndex = index
    }

    fun placeAt(p: GeoPoint) {
        val i = placingIndex ?: return
        placingIndex = null
        change(i) { copy(location = p, include = !outsideTrip) }
    }

    /** Imports the photos of every kept suggestion (R11 strips GPS) and saves one step each. */
    fun confirm() {
        val s = _state.value
        if (!s.canConfirm || s.phase != FromPhotosState.Phase.REVIEW) return
        _state.update { it.copy(phase = FromPhotosState.Phase.SAVING) }
        viewModelScope.launch {
            var saved = 0
            var failedPhotos = 0
            var failedSteps = 0
            for (sg in s.toCreate) {
                val files = mutableListOf<String>()
                val takenAt = mutableMapOf<String, java.time.LocalDateTime>()
                val sources = mutableMapOf<String, vn.lutrinh.app.domain.model.PhotoSource>()
                for (p in sg.photos) {
                    val uri = Uri.parse(p.key)
                    val name = c.photos.import(uri)
                    if (name == null) failedPhotos++ else {
                        files += name
                        p.takenAt?.let { takenAt[name] = it } // R17: the stored copy has no EXIF
                        sources[name] = c.gallery.sourceOf(uri) // R19: not offered again from the gallery
                    }
                }
                val input = StepInput(sg.title, "", sg.location, sg.arrivedAt, files, takenAt, sources)
                when (c.saveStep(tripId, null, input)) {
                    is SaveResult.Saved -> saved++
                    is SaveResult.Invalid -> { failedSteps++; c.photos.delete(files) }
                }
            }
            _state.update {
                it.copy(phase = FromPhotosState.Phase.DONE, savedSteps = saved, failedPhotos = failedPhotos, failedSteps = failedSteps)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FromPhotosScreen(
    pickedLocation: GeoPoint?,
    onPickedConsumed: () -> Unit,
    onPickLocation: (GeoPoint?) -> Unit,
    onBack: () -> Unit,
    /** Called when the flow ends; [created] steps were saved. */
    onDone: (created: Int, unreadablePhotos: Int) -> Unit,
    vm: FromPhotosViewModel = viewModel(factory = factory { c, h -> FromPhotosViewModel(c, h) }),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // R13: the system Photo Picker redacts GPS, so this flow uses the system file picker and
    // ACCESS_MEDIA_LOCATION (docs/06-photos.md). Denied -> photos come without location.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.onPicked(uris) { at -> context.getString(R.string.from_photos_default_title, at.displayTime()) }
    }
    val pick = { picker.launch(arrayOf("image/*")) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pick() }
    val startPicking = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) askLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        else pick()
    }
    // F14: explain before opening the system file picker (no auto-launch), and confirm before a review is thrown away.
    var introDone by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val reviewing = s.phase == FromPhotosState.Phase.REVIEW
    BackHandler(enabled = reviewing) { confirmDiscard = true }
    LaunchedEffect(pickedLocation) {
        if (pickedLocation != null) {
            vm.placeAt(pickedLocation)
            onPickedConsumed()
        }
    }
    LaunchedEffect(s.phase) { if (s.phase == FromPhotosState.Phase.DONE) onDone(s.savedSteps, s.failedPhotos) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.from_photos_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (reviewing) confirmDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (s.phase == FromPhotosState.Phase.REVIEW || s.phase == FromPhotosState.Phase.SAVING) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (s.phase == FromPhotosState.Phase.SAVING) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                        }
                        Button(
                            onClick = vm::confirm,
                            enabled = s.canConfirm && s.phase == FromPhotosState.Phase.REVIEW,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.from_photos_confirm, s.toCreate.size)) }
                    }
                }
            }
        },
    ) { padding ->
        when (s.phase) {
            FromPhotosState.Phase.PICKING, FromPhotosState.Phase.DONE ->
                MessageState(
                    Icons.Outlined.AddPhotoAlternate,
                    stringResource(R.string.from_photos_title),
                    stringResource(R.string.from_photos_pick_hint),
                    Modifier.padding(padding),
                    actionLabel = stringResource(R.string.from_photos_pick),
                    onAction = { introDone = true; startPicking() },
                )
            FromPhotosState.Phase.READING -> LoadingState(Modifier.padding(padding))
            FromPhotosState.Phase.MISSING ->
                MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
            FromPhotosState.Phase.REVIEW, FromPhotosState.Phase.SAVING ->
                Review(s, padding, vm, onPickLocation = { i, at ->
                    vm.startPlacing(i)
                    onPickLocation(at ?: s.fallbackLocation)
                })
        }
    }

    if (s.phase == FromPhotosState.Phase.PICKING && !introDone) {
        FromPhotosIntro(
            onChoose = { introDone = true; startPicking() },
            onCancel = onBack,
            onDismiss = { introDone = true },
        )
    }
    if (confirmDiscard) {
        DiscardDialog(
            title = stringResource(R.string.from_photos_discard_title),
            body = stringResource(R.string.from_photos_discard_body, s.photoCount),
            onDiscard = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** What "Thêm từ ảnh" does and asks for, shown before the system file picker opens (F14). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FromPhotosIntro(onChoose: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.from_photos_intro_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.from_photos_intro_gps), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.from_photos_intro_review), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.from_photos_intro_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.from_photos_pick)) }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun Review(
    s: FromPhotosState,
    padding: PaddingValues,
    vm: FromPhotosViewModel,
    onPickLocation: (index: Int, at: GeoPoint?) -> Unit,
) {
    val markers = s.suggestions.mapIndexedNotNull { i, sg ->
        sg.location?.takeIf { sg.include }?.let { MapMarker("g$i", "${i + 1}", it) }
    }
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                TripMap(markers = markers, showRoute = false, interactive = false, fitPaddingDp = 36, modifier = Modifier.fillMaxSize())
            }
        }
        item {
            Text(
                stringResource(R.string.from_photos_summary, s.photoCount, s.suggestions.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.from_photos_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        itemsIndexed(s.suggestions) { i, sg ->
            SuggestionCard(
                number = i + 1,
                sg = sg,
                titleOk = s.titleOk(sg),
                enabled = s.phase == FromPhotosState.Phase.REVIEW,
                tripDates = s.trip,
                tripSteps = s.tripSteps,
                onTitle = { vm.setTitle(i, it) },
                onInclude = { vm.setInclude(i, it) },
                onPickLocation = { onPickLocation(i, sg.location) },
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    number: Int,
    sg: StepSuggestion,
    titleOk: Boolean,
    enabled: Boolean,
    tripDates: Trip?,
    tripSteps: List<Step>,
    onTitle: (String) -> Unit,
    onInclude: (Boolean) -> Unit,
    onPickLocation: () -> Unit,
) {
    val dim = !sg.include
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (dim) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val keepLabel = stringResource(R.string.from_photos_keep, number)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = sg.include,
                    onCheckedChange = onInclude,
                    enabled = enabled && sg.location != null && !sg.outsideTrip,
                    modifier = Modifier.semantics { contentDescription = keepLabel },
                )
                Box(
                    Modifier.size(28.dp).background(
                        if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
                    Alignment.Center,
                ) { Text("$number", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = sg.title,
                    onValueChange = onTitle,
                    enabled = enabled,
                    singleLine = true,
                    isError = sg.include && !titleOk,
                    label = { Text(stringResource(R.string.step_field_title)) },
                    modifier = Modifier.weight(1f),
                )
            }
            InfoLine(Icons.Outlined.Schedule) {
                Text(
                    if (sg.datedFromPhotos) sg.arrivedAt.displayDateTime()
                    else stringResource(R.string.from_photos_no_date, sg.arrivedAt.displayDateTime()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (sg.outsideTrip && tripDates != null) {
                InfoLine(Icons.Outlined.EventBusy, MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(R.string.from_photos_outside_trip, sg.arrivedAt.toLocalDate().display()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val loc = sg.location
            InfoLine(if (loc == null) Icons.Outlined.LocationOff else Icons.Outlined.Place) {
                val lines = loc?.let { placeLines(it, tripSteps) }
                Column(Modifier.weight(1f)) {
                    Text(lines?.first ?: stringResource(R.string.from_photos_no_location), style = MaterialTheme.typography.bodyMedium)
                    lines?.second?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ChangeLocationButton(hasLocation = loc != null, onClick = onPickLocation, enabled = enabled && !sg.outsideTrip)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sg.photos, key = { it.key }) { p ->
                    AsyncImage(
                        model = Uri.parse(p.key),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Text(
                stringResource(R.string.from_photos_count, sg.photos.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        content()
    }
}
