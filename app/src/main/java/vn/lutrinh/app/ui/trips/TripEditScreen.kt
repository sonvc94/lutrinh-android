package vn.lutrinh.app.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.TripError
import vn.lutrinh.app.domain.usecase.TripInput
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.DiscardDialog
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.VietnameseLocale
import vn.lutrinh.app.ui.common.display
import vn.lutrinh.app.ui.common.factory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class TripForm(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val errors: Set<TripError> = emptySet(),
    val stepDates: ClosedRange<LocalDate>? = null,
    val stepCount: Int = 0,
    val photoCount: Int = 0,
    val dirty: Boolean = false,
    val savedId: String? = null,
    val deleted: Boolean = false,
)

class TripEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String? = handle.get<String>("tripId")
    private val _form = MutableStateFlow(TripForm(loading = tripId != null, startDate = c.clock.today()))
    val form = _form.asStateFlow()

    init {
        if (tripId != null) viewModelScope.launch {
            val trip = c.trips.getTrip(tripId)
            if (trip == null) {
                _form.update { it.copy(loading = false, missing = true) }
            } else {
                val steps = c.steps.getSteps(tripId)
                _form.update {
                    it.copy(
                        loading = false,
                        title = trip.title,
                        description = trip.description,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        stepDates = c.steps.stepDateRange(tripId),
                        stepCount = steps.size,
                        photoCount = steps.sumOf { s -> s.photos.size },
                    )
                }
            }
        }
    }

    fun edit(change: TripForm.() -> TripForm) = _form.update { it.change().copy(dirty = true, errors = emptySet()) }

    fun save() = viewModelScope.launch {
        val f = _form.value
        when (val r = c.saveTrip(tripId, TripInput(f.title, f.description, f.startDate, f.endDate))) {
            is SaveResult.Saved -> _form.update { it.copy(savedId = r.id, dirty = false) }
            is SaveResult.Invalid -> _form.update { it.copy(errors = r.errors) }
        }
    }

    fun delete() = viewModelScope.launch {
        tripId?.let { c.deleteTrip(it) }
        _form.update { it.copy(deleted = true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    onBack: () -> Unit,
    onSaved: (tripId: String, created: Boolean) -> Unit,
    onDeleted: () -> Unit,
    vm: TripEditViewModel = viewModel(factory = factory { c, h -> TripEditViewModel(c, h) }),
) {
    val f by vm.form.collectAsStateWithLifecycle()
    val isNew = vm.tripId == null
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, isNew) } }
    LaunchedEffect(f.deleted) { if (f.deleted) onDeleted() }
    val back = { if (f.dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = f.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.trip_create_title else R.string.trip_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!isNew) TextButton(onClick = vm::save) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        when {
            f.loading -> LoadingState(Modifier.padding(padding))
            f.missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.not_found), "", Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val titleError = when {
                    TripError.TITLE_BLANK in f.errors -> stringResource(R.string.err_trip_title_blank)
                    TripError.TITLE_TOO_LONG in f.errors -> stringResource(R.string.err_trip_title_long, Limits.TRIP_TITLE_MAX)
                    else -> null
                }
                OutlinedTextField(
                    value = f.title,
                    onValueChange = { v -> vm.edit { copy(title = v.take(Limits.TRIP_TITLE_MAX + 10)) } },
                    label = { Text(stringResource(R.string.trip_field_title)) },
                    placeholder = { Text(stringResource(R.string.trip_field_title_hint)) },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = {
                        Row {
                            Text(titleError ?: "", Modifier.weight(1f))
                            Text("${f.title.trim().length}/${Limits.TRIP_TITLE_MAX}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = f.description,
                    onValueChange = { v -> vm.edit { copy(description = v) } },
                    label = { Text(stringResource(R.string.trip_field_description)) },
                    minLines = 3,
                    isError = TripError.DESCRIPTION_TOO_LONG in f.errors,
                    supportingText = if (TripError.DESCRIPTION_TOO_LONG in f.errors) {
                        { Text(stringResource(R.string.err_trip_description_long, Limits.TRIP_DESCRIPTION_MAX)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        label = stringResource(R.string.trip_field_start),
                        value = f.startDate.display(),
                        modifier = Modifier.weight(1f),
                        onClick = { pickStart = true },
                    )
                    DateField(
                        label = stringResource(R.string.trip_field_end),
                        value = f.endDate?.display() ?: stringResource(R.string.trip_field_end_none),
                        modifier = Modifier.weight(1f),
                        onClick = { pickEnd = true },
                    )
                }
                if (TripError.END_BEFORE_START in f.errors) {
                    Text(stringResource(R.string.err_trip_end_before_start), color = MaterialTheme.colorScheme.error)
                }
                if (TripError.STEPS_OUTSIDE_DATES in f.errors && f.stepDates != null) {
                    Text(
                        stringResource(R.string.err_trip_steps_outside, f.stepDates!!.start.display(), f.stepDates!!.endInclusive.display()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (f.endDate != null) {
                    TextButton(onClick = { vm.edit { copy(endDate = null) } }) {
                        Icon(Icons.Outlined.EventBusy, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.trip_field_end_clear))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(if (isNew) R.string.trip_create_button else R.string.action_save))
                }
                if (!isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.trip_delete_button))
                    }
                }
            }
        }
    }

    if (pickStart) DatePickDialog(f.startDate, { pickStart = false }) { d -> vm.edit { copy(startDate = d) } }
    if (pickEnd) DatePickDialog(f.endDate ?: f.startDate, { pickEnd = false }) { d -> vm.edit { copy(endDate = d) } }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.trip_delete_confirm_title),
            body = stringResource(R.string.trip_delete_confirm_body, f.title, f.stepCount, f.photoCount),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false })
}

@Composable
fun DateField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(64.dp)) {
        Icon(Icons.Outlined.CalendarMonth, null)
        Spacer(Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Material date picker working in UTC millis <-> LocalDate (no zone shift), always in Vietnamese. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        // Inside the dialog: its window re-provides the device configuration (docs/03-screens.md).
        VietnameseLocale { DatePicker(state = state) }
    }
}
