package vn.lutrinh.app.ui.trips

import androidx.compose.foundation.clickable
import vn.lutrinh.app.ui.theme.TopoIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.routing.LegMeasure
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.Leg
import vn.lutrinh.app.domain.usecase.LegNotes
import vn.lutrinh.app.domain.usecase.LegReattach
import vn.lutrinh.app.domain.usecase.Limits
import vn.lutrinh.app.ui.common.topoFieldColors
import vn.lutrinh.app.map.TransportStyle

/**
 * The distance part of a leg chip (R36): "48,5 km · ~1 giờ 32 phút" along the road, "27,3 km chim
 * bay" for a straight leg (no stored route, routing off, or a train/plane/boat leg). A Xe máy leg
 * drawn with the car profile (no motorbike route) ends with "· tuyến ô tô".
 */
@Composable
fun legDistanceText(m: LegMeasure): String =
    if (m.byRoad) {
        val km = stringResource(R.string.leg_km, DisplayFormat.kmNumber(m.km))
        val road = m.durationS?.let { "$km · ${DisplayFormat.approxDuration(it)}" } ?: km
        if (m.carRoute) "$road · ${stringResource(R.string.leg_car_route)}" else road
    } else stringResource(R.string.leg_km_straight, DisplayFormat.kmNumber(m.km))

/**
 * Compact chip on the timeline / map card: the leg's mode (or a prompt), its distance and (road)
 * time. The specimen's chip on paper: card paper, 12 dp corners, a grid hairline instead of the
 * map chip's shadow, bold 13 text.
 */
@Composable
fun LegChip(leg: Leg, measure: LegMeasure, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mode = leg.mode
    val style = mode?.let { TransportStyle.of(it) }
    val label = style?.let { stringResource(it.label) } ?: stringResource(R.string.leg_set_mode)
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                "$label · ${legDistanceText(measure)}",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                style?.icon ?: TopoIcons.Route,
                null,
                Modifier.size(AssistChipDefaults.IconSize),
                tint = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    )
}

/**
 * R41: a leg's note under its chip (timeline): the note icon, at most two lines; a longer note
 * shows "Xem thêm" and opens in place ("Thu gọn" closes it again).
 */
@Composable
fun LegNote(note: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(note) { mutableStateOf(false) }
    var overflows by remember(note) { mutableStateOf(false) }
    val more = overflows || expanded
    val toggle = stringResource(if (expanded) R.string.leg_note_less else R.string.leg_note_more)
    Row(
        modifier.clip(MaterialTheme.shapes.small)
            .clickable(enabled = more, onClickLabel = toggle) { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(TopoIcons.Note, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (more) Text(toggle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Bottom sheet of one leg: its note (R41) and its transport (R12). Topo sheet: the distance as
 * the eyebrow over the leg's title; the note row (or "Thêm ghi chú chặng") opens the note field
 * in the same sheet; the transport rows on the sheet's card paper, the chosen row on sand with a
 * forest check; "Chưa chọn" clears the transport.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegModeSheet(
    leg: Leg,
    measure: LegMeasure,
    onPick: (TransportMode?) -> Unit,
    onSaveNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingNote by rememberSaveable(leg.to.id) { mutableStateOf(false) }
    // Fully expanded: all 8 choices fit on a phone without dragging the sheet up first.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                if (measure.byRoad && measure.durationS != null) {
                    stringResource(
                        if (measure.carRoute) R.string.leg_sheet_subtitle_road_car else R.string.leg_sheet_subtitle_road,
                        DisplayFormat.kmNumber(measure.km), DisplayFormat.approxDuration(measure.durationS),
                    )
                } else stringResource(R.string.leg_sheet_subtitle, DisplayFormat.kmNumber(leg.distanceKm)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.leg_sheet_title, leg.from.title, leg.to.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            )
            if (editingNote) {
                LegNoteEditor(leg.note.orEmpty(), onSave = onSaveNote, onCancel = { editingNote = false })
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(if (leg.note == null) R.string.leg_note_add else R.string.leg_note_label)) },
                    supportingContent = {
                        Text(leg.note ?: stringResource(R.string.leg_note_hint), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Icon(TopoIcons.Note, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Icon(TopoIcons.Edit, stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 12.dp).clip(MaterialTheme.shapes.small).clickable { editingNote = true },
                )
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                (TransportMode.entries + listOf<TransportMode?>(null)).forEach { mode ->
                    val style = mode?.let { TransportStyle.of(it) }
                    ListItem(
                        headlineContent = { Text(style?.let { stringResource(it.label) } ?: stringResource(R.string.transport_none)) },
                        leadingContent = {
                            Icon(style?.icon ?: TopoIcons.Route, null, tint = style?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = { if (mode == leg.mode) Icon(TopoIcons.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (mode == leg.mode) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).clip(MaterialTheme.shapes.small).clickable { onPick(mode) },
                    )
                }
            }
        }
    }
}

/**
 * R41: the note field of a leg (topo field on card paper, example placeholder, a count against
 * the limit); "Lưu" stores the trimmed text (empty = no note), "Xóa ghi chú" clears it.
 */
@Composable
private fun LegNoteEditor(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val length = LegNotes.length(text.trim())
    val tooLong = length > Limits.LEG_NOTE_MAX
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.leg_note_label)) },
            placeholder = { Text(stringResource(R.string.leg_note_hint)) },
            minLines = 3,
            maxLines = 8,
            isError = tooLong,
            supportingText = {
                Text(
                    if (tooLong) stringResource(R.string.leg_note_too_long, Limits.LEG_NOTE_MAX)
                    else stringResource(R.string.leg_note_count, length, Limits.LEG_NOTE_MAX),
                )
            },
            colors = topoFieldColors(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (initial.isNotEmpty()) TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.leg_note_clear)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(shape = MaterialTheme.shapes.small, enabled = !tooLong, onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/**
 * R44: a stop was moved or inserted and a leg that carried a note or a transport is gone. One
 * sheet per such leg: the new legs (named "A → B") it can go to, or "Bỏ". Nothing is stored here:
 * [onDone] gets one choice per [LegReattach.Plan.displaced] (null = drop, else an index into
 * [LegReattach.Plan.targets]); [onCancel] (back, outside tap, the text button) leaves everything as it was.
 * A new leg takes one leg's data only, so a target already chosen is not offered again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegChoiceSheet(plan: LegReattach.Plan, cancelLabel: String, onDone: (List<Int?>) -> Unit, onCancel: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val choices = remember { mutableListOf<Int?>() }
    val leg = plan.displaced[index]
    val taken = choices.filterNotNull().toSet()
    fun pick(choice: Int?) {
        choices += choice
        if (index == plan.displaced.lastIndex) onDone(choices.toList()) else index++
    }
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.leg_choice_eyebrow, index + 1, plan.displaced.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.leg_choice_title, stringResource(R.string.leg_pair, leg.from.title, leg.to.title)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
            )
            val mode = leg.data.mode?.let { stringResource(TransportStyle.of(it).label) }
            val note = leg.data.note?.let { stringResource(R.string.leg_choice_note, it) }
            Text(
                listOfNotNull(mode, note).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
            )
            Text(
                stringResource(R.string.leg_choice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            plan.targets.forEachIndexed { t, target ->
                if (t in taken) return@forEachIndexed
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.leg_choice_attach, stringResource(R.string.leg_pair, target.from.title, target.to.title)))
                    },
                    leadingContent = { Icon(TopoIcons.Route, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 12.dp).clip(MaterialTheme.shapes.small).clickable { pick(t) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.leg_choice_drop)) },
                leadingContent = { Icon(TopoIcons.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 12.dp).clip(MaterialTheme.shapes.small).clickable { pick(null) },
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(horizontal = 12.dp)) { Text(cancelLabel) }
        }
    }
}
