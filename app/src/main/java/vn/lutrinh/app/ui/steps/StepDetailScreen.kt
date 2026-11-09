package vn.lutrinh.app.ui.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.inTimelineOrder
import vn.lutrinh.app.domain.usecase.stepNumbers
import vn.lutrinh.app.map.TripMap
import vn.lutrinh.app.ui.common.ConfirmDeleteDialog
import vn.lutrinh.app.ui.common.DraftBadge
import vn.lutrinh.app.ui.common.Loadable
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.LocalMedia
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.displayDateTime
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.placeLines
import vn.lutrinh.app.ui.common.rememberGazetteer
import vn.lutrinh.app.ui.trips.toMarkers
import vn.lutrinh.app.ui.theme.hairline

/** [number] = timeline number (null for a draft, R20); [index] = position in [tripSteps] (drafts included). */
data class StepDetail(val step: Step, val number: Int?, val index: Int, val tripSteps: List<Step>)

class StepDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val tripId: String = checkNotNull(handle["tripId"])
    val stepId: String = checkNotNull(handle["stepId"])
    val deleted = MutableStateFlow(false)

    val state: StateFlow<Loadable<StepDetail>> =
        combine(c.steps.observeStep(stepId), c.steps.observeSteps(tripId)) { step, steps ->
            if (step == null) Loadable.Missing
            else {
                val ordered = steps.inTimelineOrder()
                Loadable.Ready(StepDetail(step, stepNumbers(ordered)[step.id], ordered.indexOfFirst { it.id == step.id }, ordered))
            }
        }
            .catch { emit(Loadable.Failed(it.message ?: it.javaClass.simpleName)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** R20 "Đăng". */
    fun publish() = viewModelScope.launch { c.publishStep(stepId) }

    fun delete() = viewModelScope.launch {
        c.deleteStep(stepId)
        deleted.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StepDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPhoto: (index: Int) -> Unit,
    /** Prev/next step of the trip (F25): replaces this screen, so Back still returns to the trip. */
    onOpenStep: (String) -> Unit,
    vm: StepDetailViewModel = viewModel(factory = factory { c, h -> StepDetailViewModel(c, h) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val d = (state as? Loadable.Ready)?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.let { if (it.number != null) "${it.number}. ${it.step.title}" else it.step.title } ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (d != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
        bottomBar = { if (d != null) StepNeighbours(d, onOpenStep) },
    ) { padding ->
        when (val s = state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))
            Loadable.Missing -> MessageState(stringResource(R.string.not_found), "", Modifier.padding(padding))
            is Loadable.Failed -> MessageState(stringResource(R.string.state_error, s.message), "", Modifier.padding(padding))
            is Loadable.Ready -> {
                val step = s.value.step
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (step.photos.isNotEmpty()) {
                        LocalMedia(
                            step.photos.first(),
                            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable { onOpenPhoto(0) },
                        )
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (step.isDraft) DraftCard(onPublish = vm::publish)
                        InfoRow(Icons.Outlined.Schedule, step.arrivedAt.displayDateTime())
                        val (place, coords) = placeLines(step.location, s.value.tripSteps, step.id, rememberGazetteer())
                        InfoRow(Icons.Outlined.Place, place)
                        coords?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 26.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            step.note.ifBlank { stringResource(R.string.step_detail_no_note) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (step.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large)) {
                        TripMap(
                            markers = s.value.tripSteps.toMarkers(step.id),
                            interactive = true,
                            fitPaddingDp = 40,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (step.photos.isNotEmpty()) {
                        Text(
                            stringResource(R.string.step_detail_photos, step.photos.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                        )
                        val cell = (LocalConfiguration.current.screenWidthDp.dp - 32.dp - 12.dp) / 3
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            step.photos.forEachIndexed { i, p ->
                                LocalMedia(p, Modifier.size(cell).clip(MaterialTheme.shapes.small).clickable { onOpenPhoto(i) })
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (confirmDelete && d != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.step_delete_confirm_title),
            body = stringResource(R.string.step_delete_confirm_body, d.step.title, d.step.photos.size),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Previous / next step in timeline order (F25); a side without a neighbour is left empty. */
@Composable
private fun StepNeighbours(d: StepDetail, onOpenStep: (String) -> Unit) {
    val i = d.index
    val prev = d.tripSteps.getOrNull(i - 1)
    val next = d.tripSteps.getOrNull(i + 1)
    if (prev == null && next == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, border = hairline) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), Alignment.CenterStart) {
                if (prev != null) TextButton(onClick = { onOpenStep(prev.id) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                    Text(stringResource(R.string.step_detail_prev, prev.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f), Alignment.CenterEnd) {
                if (next != null) TextButton(onClick = { onOpenStep(next.id) }) {
                    Text(stringResource(R.string.step_detail_next, next.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

/** R20: what a draft is, and "Đăng" to publish it. */
@Composable
private fun DraftCard(onPublish: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DraftBadge()
                Text(
                    stringResource(R.string.step_draft_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(onClick = onPublish, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.step_publish)) }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
