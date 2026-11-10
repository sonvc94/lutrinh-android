package vn.lutrinh.app.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lutrinh.app.R
import vn.lutrinh.app.data.RouteDownloadState
import vn.lutrinh.app.domain.catalog.CatalogError
import vn.lutrinh.app.domain.catalog.CodeProblem
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.ui.common.ConfirmDialog
import vn.lutrinh.app.ui.common.Eyebrow
import vn.lutrinh.app.ui.common.KmReadout
import vn.lutrinh.app.ui.common.NoticeDialog
import vn.lutrinh.app.ui.common.ReadoutCount
import vn.lutrinh.app.ui.common.RouteCover
import vn.lutrinh.app.ui.common.ScreenHeading
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.TopoPanel
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatBytes
import vn.lutrinh.app.ui.common.topoFieldColors
import vn.lutrinh.app.ui.theme.IconSize
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.Radii
import vn.lutrinh.app.ui.theme.Space
import vn.lutrinh.app.ui.theme.TopoIcons
import vn.lutrinh.app.ui.theme.numerals
import vn.lutrinh.app.ui.trips.DateField
import vn.lutrinh.app.ui.trips.DatePickDialog

/**
 * "Nhập mã lộ trình" (R40, docs/03, design page 05 screens 2–5 and 8). [onOpenTrip] gets the new
 * trip's id and whether an offline map was asked for (the trip's snackbar offers one otherwise).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCodeScreen(onBack: () -> Unit, onOpenTrip: (String, Boolean) -> Unit) {
    val vm: RouteCodeViewModel = viewModel(factory = factory { c, h -> RouteCodeViewModel(c, h) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.opened.collect { (id, map) -> onOpenTrip(id, map) } }
    val stage = ui.stage
    BackHandler(enabled = stage is RouteStage.Preview) { vm.backToCode() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (stage) {
                                RouteStage.Code -> R.string.route_code_title
                                is RouteStage.Preview -> R.string.route_preview_title
                                RouteStage.Download -> R.string.route_dl_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (stage is RouteStage.Preview) vm.backToCode() else onBack() }) {
                        Icon(TopoIcons.Back, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            when (stage) {
                RouteStage.Code -> CodeEntry(ui, vm::onInput, vm::lookUp)
                is RouteStage.Preview -> Preview(stage, vm)
                RouteStage.Download -> download?.let { DownloadProgressCard(it, vm) }
            }
        }
    }
    ui.dialog?.let { d ->
        val retry: () -> Unit = { vm.dismissDialog(); if (stage is RouteStage.Preview) vm.startDownload() else vm.lookUp() }
        when (d) {
            RouteDialog.Offline -> NoticeDialog(
                TopoIcons.CloudOff, stringResource(R.string.route_offline_title), stringResource(R.string.route_offline_body),
                stringResource(R.string.action_retry), retry, vm::dismissDialog, stringResource(R.string.action_close),
            )
            RouteDialog.Busy -> NoticeDialog(
                TopoIcons.CloudOff, stringResource(R.string.route_busy_title), stringResource(R.string.route_busy_body),
                stringResource(R.string.action_retry), retry, vm::dismissDialog, stringResource(R.string.action_close),
            )
            RouteDialog.UpdateApp -> NoticeDialog(
                TopoIcons.Download, stringResource(R.string.route_update_app_title), stringResource(R.string.route_update_app_body),
                stringResource(R.string.action_close), vm::dismissDialog, vm::dismissDialog,
            )
            is RouteDialog.Storage -> NoticeDialog(
                TopoIcons.Download, stringResource(R.string.route_storage_title, formatBytes(d.needBytes)),
                stringResource(R.string.route_storage_body), stringResource(R.string.action_close), vm::dismissDialog, vm::dismissDialog,
            )
            is RouteDialog.Metered -> ConfirmDialog(
                stringResource(R.string.route_metered_title, formatBytes(d.bytes)), stringResource(R.string.route_metered_body),
                stringResource(R.string.route_metered_ok), stringResource(R.string.action_cancel),
                onConfirm = { vm.startDownload(confirmedMetered = true) }, onDismiss = vm::dismissDialog,
            )
        }
    }
}

/** `HG4N7KQE` is drawn `HG4N-7KQE`: the dash is not part of the text. */
private object CodeDash : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length <= 4) return TransformedText(text, OffsetMapping.Identity)
        val out = s.substring(0, 4) + "-" + s.substring(4)
        return TransformedText(
            AnnotatedString(out),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset <= 4) offset else offset + 1
                override fun transformedToOriginal(offset: Int) = (if (offset <= 4) offset else offset - 1).coerceIn(0, s.length)
            },
        )
    }
}

@Composable
private fun CodeEntry(ui: RouteCodeUi, onInput: (String) -> Unit, onLookUp: () -> Unit) {
    ScreenHeading(stringResource(R.string.route_code_heading), eyebrow = stringResource(R.string.route_code_eyebrow))
    Text(stringResource(R.string.route_code_body), style = MaterialTheme.typography.bodyMedium)
    val error = ui.fieldError
    OutlinedTextField(
        value = ui.input,
        onValueChange = onInput,
        label = { Text(stringResource(R.string.route_code_field)) },
        singleLine = true,
        isError = error != null,
        textStyle = MaterialTheme.typography.headlineMedium.numerals(),
        visualTransformation = CodeDash,
        supportingText = { Text(error?.let { errorText(it) } ?: stringResource(R.string.route_code_hint)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { if (ui.canLookUp) onLookUp() }),
        colors = topoFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(shape = MaterialTheme.shapes.small, onClick = onLookUp, enabled = ui.canLookUp, modifier = Modifier.fillMaxWidth()) {
        if (ui.looking) {
            CircularProgressIndicator(Modifier.size(IconSize.S), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.route_code_looking), Modifier.padding(start = Space.S))
        } else {
            Text(stringResource(R.string.route_code_view))
        }
    }
    Text(
        stringResource(R.string.route_code_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun errorText(e: CodeFieldError): String = when (e) {
    is CodeFieldError.Typed -> when (e.problem) {
        CodeProblem.CONFUSABLE -> stringResource(R.string.route_err_confusable)
        CodeProblem.CHECK -> stringResource(R.string.route_err_check)
        CodeProblem.LENGTH, CodeProblem.CHAR -> stringResource(R.string.route_err_format)
    }
    is CodeFieldError.Server -> refusalText(e.error.code, e.retryAfterS)
}

/** The sentence for an API error code (docs/02 error table) or a download failure. */
@Composable
internal fun refusalText(code: String, retryAfterS: Int?): String = when (CatalogError.fromCode(code)) {
    CatalogError.BAD_FORMAT -> stringResource(R.string.route_err_format)
    CatalogError.BAD_CHECK -> stringResource(R.string.route_err_check)
    CatalogError.CODE_NOT_FOUND -> stringResource(R.string.route_err_not_found)
    CatalogError.CODE_REVOKED -> stringResource(R.string.route_err_revoked)
    CatalogError.CODE_EXPIRED -> stringResource(R.string.route_err_expired)
    CatalogError.ROUTE_UNPUBLISHED -> stringResource(R.string.route_err_unpublished)
    CatalogError.RATE_LIMITED -> retryAfterS?.let { stringResource(R.string.route_err_rate_limited, (it + 59) / 60) }
        ?: stringResource(R.string.route_err_rate_limited_later)
    CatalogError.FORMAT_UNSUPPORTED -> stringResource(R.string.route_update_app_title)
    CatalogError.TOKEN_EXPIRED, CatalogError.BAD_PACKAGE -> stringResource(R.string.route_err_bad_package)
    null -> if (code == "storage") stringResource(R.string.route_err_storage) else stringResource(R.string.route_busy_title)
}

@Composable
private fun ColumnScope.Preview(p: RouteStage.Preview, vm: RouteCodeViewModel) {
    val s = p.summary
    RouteCover(s.overview, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(Radii.Card)))
    ScreenHeading(s.title, eyebrow = stringResource(R.string.route_preview_eyebrow, s.code.display, s.version))
    if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium)
    TopoPanel(Modifier.fillMaxWidth()) {
        KmReadout(
            km = s.distanceM / 1000.0,
            kmUnit = "km",
            counts = listOf(
                ReadoutCount(DisplayFormat.count(s.stops.toLong()), stringResource(R.string.route_stat_stops)),
                ReadoutCount(DisplayFormat.count(s.days.toLong()), stringResource(R.string.route_stat_days)),
                ReadoutCount(DisplayFormat.count((s.photos + s.videos).toLong()), stringResource(R.string.route_stat_media)),
            ),
        )
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    DateField(stringResource(R.string.route_start_date), DisplayFormat.dayWithYear(p.start), Modifier.fillMaxWidth()) { picking = true }
    if (picking) DatePickDialog(p.start, onDismiss = { picking = false }, onPicked = vm::setStart)
    p.offer?.let { offer ->
        val where = if (offer.readyMade) stringResource(R.string.route_offline_map_area, offer.area.name)
        else stringResource(R.string.route_offline_map_trip)
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Checkbox) { vm.setWithMap(!p.withMap) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = p.withMap, onCheckedChange = null, modifier = Modifier.padding(Space.M))
            Text(stringResource(R.string.route_offline_map, where, formatBytes(offer.estimateBytes)), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        stringResource(R.string.route_size_line, formatBytes(s.bytes), s.files, formatBytes(vm.neededBytes(p))),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(shape = MaterialTheme.shapes.small, onClick = { vm.startDownload() }, modifier = Modifier.fillMaxWidth()) {
        Icon(TopoIcons.Download, null, Modifier.size(IconSize.S))
        Text(stringResource(R.string.route_download), Modifier.padding(start = Space.S))
    }
}

@Composable
private fun DownloadProgressCard(v: DownloadView, vm: RouteCodeViewModel) {
    val r = v.request
    val state = v.state
    val progress = (state as? RouteDownloadState.Running)?.progress
    val failed = state as? RouteDownloadState.Failed
    Text(r.title, style = MaterialTheme.typography.titleLarge)
    TopoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.L)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.M)) {
            val status = when {
                failed != null -> stringResource(R.string.route_dl_failed)
                r.paused -> stringResource(R.string.route_dl_paused, formatBytes(v.stagedBytes))
                progress == null -> stringResource(R.string.route_dl_waiting)
                else -> stringResource(R.string.route_dl_percent, progress.percent, formatBytes(progress.bytesDone), formatBytes(progress.bytes))
            }
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (failed != null) Text(refusalText(failed.error, failed.retryAfterS), style = MaterialTheme.typography.bodyMedium)
            if (failed == null) {
                val fraction = when {
                    progress != null -> progress.percent / 100f
                    r.bytes > 0 -> (v.stagedBytes.toFloat() / r.bytes).coerceIn(0f, 1f)
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = status },
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            val lines = buildList {
                add(
                    if (progress?.routeReady == true) stringResource(R.string.route_dl_route, progress.stops, progress.legs)
                    else stringResource(R.string.route_dl_route_wait),
                )
                add(stringResource(R.string.route_dl_media, progress?.filesDone ?: 0, progress?.files ?: r.files))
                if (r.offlineMap) add(stringResource(R.string.route_dl_map, r.mapName.ifEmpty { stringResource(R.string.route_offline_map_trip) }))
            }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                when {
                    failed != null -> {
                        Button(shape = MaterialTheme.shapes.small, onClick = vm::resume) { Text(stringResource(R.string.action_retry)) }
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = vm::closeFailed) { Text(stringResource(R.string.action_close)) }
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.route_dl_cancel)) }
                    }
                    r.paused -> {
                        Button(shape = MaterialTheme.shapes.small, onClick = vm::resume) { Text(stringResource(R.string.route_dl_resume)) }
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.route_dl_cancel)) }
                    }
                    else -> {
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = vm::pause) { Text(stringResource(R.string.route_dl_pause)) }
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.route_dl_cancel)) }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.route_dl_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!v.online && failed == null && !r.paused) {
        // The forest line at the bottom: only while offline (page 05 screen 5).
        Row(
            Modifier.fillMaxWidth().background(Palette.Forest, RoundedCornerShape(Radii.Chip)).padding(horizontal = Space.M, vertical = Space.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            Icon(TopoIcons.CloudOff, null, Modifier.size(IconSize.S), tint = Palette.Card)
            Text(
                stringResource(R.string.route_dl_offline, progress?.filesDone ?: 0, progress?.files ?: r.files),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Card,
            )
        }
    }
}
