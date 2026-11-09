package vn.lutrinh.app.ui.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.data.BackupManager
import vn.lutrinh.app.domain.usecase.BackupCounts
import vn.lutrinh.app.domain.usecase.BackupException
import vn.lutrinh.app.domain.usecase.BackupProblem
import vn.lutrinh.app.domain.usecase.BackupRules
import vn.lutrinh.app.domain.usecase.DisplayFormat
import vn.lutrinh.app.domain.usecase.GpxError
import vn.lutrinh.app.domain.usecase.GpxException
import vn.lutrinh.app.domain.usecase.GpxFormat
import vn.lutrinh.app.domain.usecase.GpxParser
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatBytes
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * GPX export/import (R25) and backup/restore (R26): file pickers (Storage Access Framework, no
 * storage permission), progress, errors and the restore confirmation. Used from the trip list
 * (import, backup, restore, a card's "Xuất GPX") and the trip screen ("Xuất GPX").
 */
class TransferViewModel(private val c: AppContainer) : ViewModel() {
    sealed interface UiState {
        data object Idle : UiState
        data class Busy(val what: Work) : UiState
        data class ConfirmRestore(val prepared: BackupManager.Prepared) : UiState
        data class GpxFailed(val error: GpxError) : UiState
        data class RestoreFailed(val problem: BackupProblem) : UiState
        data class Failed(val message: String) : UiState
        data class RestoreError(val message: String) : UiState
    }

    enum class Work { GPX, BACKUP, RESTORE }

    sealed interface Event {
        data class Message(val text: (Context) -> String) : Event
        data class OpenTrip(val tripId: String) : Event
        data class Launch(val intent: Intent) : Event
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dismiss() {
        (state.value as? UiState.ConfirmRestore)?.let { c.backup.discard(it.prepared) }
        _state.value = UiState.Idle
    }

    /** Suggested name of the trip's GPX file. */
    suspend fun gpxName(tripId: String): String? = c.trips.getTrip(tripId)?.let(GpxFormat::fileName)

    fun saveGpx(context: Context, tripId: String, target: Uri) = work(Work.GPX) {
        val (_, gpx) = c.exportGpx(tripId, zone) ?: error("trip")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(gpx.toByteArray(Charsets.UTF_8)) } ?: error("no output stream")
        }
        Log.i(TAG, "gpx saved to $target (${gpx.length} chars)")
        _events.send(Event.Message { it.getString(R.string.gpx_exported) })
    }

    fun shareGpx(tripId: String) = work(Work.GPX) {
        val (trip, gpx) = c.exportGpx(tripId, zone) ?: error("trip")
        c.exporter.clearCache()
        val file = withContext(Dispatchers.IO) {
            File(c.exporter.shareDir, GpxFormat.fileName(trip)).apply { writeText(gpx, Charsets.UTF_8) }
        }
        Log.i(TAG, "gpx share ${file.name} ${file.length()} B")
        _events.send(Event.Launch(c.exporter.shareIntent(file, GpxFormat.MIME, trip.title)))
    }

    fun importGpx(context: Context, source: Uri) = work(Work.GPX) {
        val name = displayName(context, source)
        val doc = try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(source)?.use { GpxParser.parse(it.buffered()) }
                    ?: throw GpxException(GpxError.Unreadable)
            }
        } catch (e: GpxException) {
            Log.w(TAG, "gpx import refused: ${e.error}")
            _state.value = UiState.GpxFailed(e.error)
            return@work
        }
        val places = c.places.await()
        val imported = try {
            c.importGpx(doc, name, zone) { p -> places?.nearest(p)?.place?.name }
        } catch (e: GpxException) {
            Log.w(TAG, "gpx import refused: ${e.error}")
            _state.value = UiState.GpxFailed(e.error)
            return@work
        }
        Log.i(TAG, "gpx imported ${imported.trip.id}: ${imported.steps.size} steps, ${imported.pointCount} points")
        _events.send(Event.Message { it.getString(R.string.gpx_imported, imported.trip.title, imported.steps.size, imported.pointCount) })
        _events.send(Event.OpenTrip(imported.trip.id))
    }

    fun backup(context: Context, target: Uri) = work(Work.BACKUP) {
        var bytes = 0L
        val manifest = try {
            withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(target, "wt") ?: error("no output stream")
                CountingStream(out) { bytes += it }.use { c.backup.write(it, Instant.now()) }
            }
        } catch (e: Exception) {
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw e
        }
        Log.i(TAG, "backup written to $target: $bytes B")
        _events.send(Event.Message { ctx -> ctx.getString(R.string.backup_done, countsText(ctx, manifest.counts), formatBytes(bytes)) })
    }

    fun prepareRestore(context: Context, source: Uri) = work(Work.RESTORE) {
        if (c.recorder.active.value != null) {
            _state.value = UiState.RestoreFailed(BackupProblem.Recording)
            return@work
        }
        val prepared = try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(source)?.use { c.backup.prepare(it) } ?: throw BackupException(BackupProblem.NotABackup)
            }
        } catch (e: BackupException) {
            Log.w(TAG, "restore refused: ${e.problem}", e)
            _state.value = UiState.RestoreFailed(e.problem)
            return@work
        }
        Log.i(TAG, "restore ready: ${prepared.manifest}, current ${prepared.current}")
        _state.value = UiState.ConfirmRestore(prepared)
    }

    fun confirmRestore() {
        val prepared = (state.value as? UiState.ConfirmRestore)?.prepared ?: return
        work(Work.RESTORE) {
            if (c.recorder.active.value != null) {
                c.backup.discard(prepared)
                _state.value = UiState.RestoreFailed(BackupProblem.Recording)
                return@work
            }
            val counts = try {
                c.backup.restore(prepared)
            } catch (e: Exception) {
                _state.value = UiState.RestoreError(e.message ?: e.javaClass.simpleName)
                return@work
            }
            // A track left open in the backup is closed like after a process death (R16).
            c.recorder.recoverInterrupted()
            _events.send(Event.Message { ctx -> ctx.getString(R.string.restore_done, countsText(ctx, counts)) })
        }
    }

    /** Runs [block] to the end even when the screen goes away (files and the database stay consistent). */
    private fun work(what: Work, block: suspend () -> Unit) {
        if (_state.value is UiState.Busy) return
        _state.value = UiState.Busy(what)
        viewModelScope.launch(NonCancellable) {
            try {
                block()
                if (_state.value is UiState.Busy) _state.value = UiState.Idle
            } catch (e: Exception) {
                Log.w(TAG, "$what failed", e)
                _state.value = UiState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private class CountingStream(out: OutputStream, private val count: (Int) -> Unit) : FilterOutputStream(out) {
        override fun write(b: Int) { out.write(b); count(1) }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count(len) }
    }

    companion object {
        private const val TAG = "Transfer"

        fun countsText(ctx: Context, n: BackupCounts): String =
            ctx.getString(R.string.backup_counts, n.trips, n.steps, n.photos, n.tracks, n.trackPoints)
    }
}

/** The actions screens call; dialogs and pickers are drawn by [rememberTransfer]. */
class TransferActions internal constructor(
    val exportGpx: (tripId: String) -> Unit,
    val importGpx: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
)

/**
 * Registers the file pickers and draws progress, errors, the GPX export choice and the restore
 * confirmation. Messages go to [snackbar]; [onOpenTrip] opens a trip just imported (null = stay).
 */
@Composable
fun rememberTransfer(snackbar: SnackbarHostState, onOpenTrip: ((String) -> Unit)? = null): TransferActions {
    val vm: TransferViewModel = viewModel(key = "transfer", factory = factory { c, _ -> TransferViewModel(c) })
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var exportTrip by rememberSaveable { mutableStateOf<String?>(null) }
    var exportChoice by rememberSaveable { mutableStateOf(false) }

    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GpxFormat.MIME)) { uri ->
        val id = exportTrip
        if (uri != null && id != null) vm.saveGpx(context, id, uri)
        exportTrip = null
    }
    val openGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importGpx(context, uri)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupRules.MIME)) { uri ->
        if (uri != null) vm.backup(context, uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(context, uri)
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TransferViewModel.Event.Message -> snackbar.showSnackbar(e.text(context))
                is TransferViewModel.Event.OpenTrip -> onOpenTrip?.invoke(e.tripId)
                is TransferViewModel.Event.Launch -> runCatching { context.startActivity(e.intent) }
            }
        }
    }

    // "Xuất GPX": save as a file, or share.
    val tripId = exportTrip
    if (exportChoice && tripId != null) {
        var title by remember { mutableStateOf("") }
        var fileName by remember { mutableStateOf("") }
        LaunchedEffect(tripId) {
            fileName = vm.gpxName(tripId) ?: ""
            title = fileName.removeSuffix("." + GpxFormat.EXTENSION)
        }
        AlertDialog(
            onDismissRequest = { exportChoice = false; exportTrip = null },
            title = { Text(stringResource(R.string.gpx_export_title)) },
            text = { Text(stringResource(R.string.gpx_export_body, title)) },
            confirmButton = {
                TextButton(onClick = { exportChoice = false; saveGpx.launch(fileName.ifBlank { "chuyen-di.gpx" }) }) {
                    Text(stringResource(R.string.gpx_export_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { exportChoice = false; exportTrip = null; vm.shareGpx(tripId) }) {
                    Text(stringResource(R.string.gpx_export_share))
                }
            },
        )
    }

    when (val s = state) {
        TransferViewModel.UiState.Idle -> Unit
        is TransferViewModel.UiState.Busy -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(
                            when (s.what) {
                                TransferViewModel.Work.GPX -> R.string.gpx_working
                                TransferViewModel.Work.BACKUP -> R.string.backup_working
                                TransferViewModel.Work.RESTORE -> R.string.restore_working
                            },
                        ),
                    )
                }
            },
        )
        is TransferViewModel.UiState.ConfirmRestore -> {
            val m = s.prepared.manifest
            val day = runCatching {
                val at = Instant.parse(m.createdAt).atZone(ZoneId.systemDefault())
                DisplayFormat.dayWithYear(at.toLocalDate()) + " " + DisplayFormat.time(at.toLocalTime())
            }.getOrDefault(m.createdAt)
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.restore_confirm_body, day,
                                TransferViewModel.countsText(context, m.counts), TransferViewModel.countsText(context, s.prepared.current),
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = vm::confirmRestore,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.restore_confirm_action)) }
                },
                dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        is TransferViewModel.UiState.GpxFailed -> ErrorDialog(stringResource(R.string.gpx_error_title), gpxErrorText(s.error), vm::dismiss)
        is TransferViewModel.UiState.RestoreFailed -> ErrorDialog(stringResource(R.string.restore_error_title), restoreErrorText(s.problem), vm::dismiss)
        is TransferViewModel.UiState.RestoreError ->
            ErrorDialog(stringResource(R.string.restore_error_title), stringResource(R.string.restore_error_failed, s.message), vm::dismiss)
        is TransferViewModel.UiState.Failed -> ErrorDialog(null, stringResource(R.string.transfer_failed, s.message), vm::dismiss)
    }

    return remember(vm) {
        TransferActions(
            exportGpx = { id -> exportTrip = id; exportChoice = true },
            importGpx = { openGpx.launch(arrayOf("*/*")) },
            backup = { saveBackup.launch(BackupRules.fileName(java.time.LocalDate.now())) },
            restore = { openBackup.launch(arrayOf("*/*")) },
        )
    }
}

@Composable
private fun ErrorDialog(title: String?, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun gpxErrorText(e: GpxError): String = when (e) {
    is GpxError.NotXml -> e.line?.let { stringResource(R.string.gpx_error_not_xml, it) } ?: stringResource(R.string.gpx_error_not_xml_noline)
    GpxError.NotGpx -> stringResource(R.string.gpx_error_not_gpx)
    is GpxError.BadPoint -> stringResource(R.string.gpx_error_bad_point, e.line ?: 0)
    GpxError.Empty -> stringResource(R.string.gpx_error_empty)
    GpxError.Unreadable -> stringResource(R.string.gpx_error_unreadable)
}

@Composable
private fun restoreErrorText(p: BackupProblem): String = when (p) {
    BackupProblem.NotABackup -> stringResource(R.string.restore_error_not_backup)
    is BackupProblem.NewerFormat -> stringResource(R.string.restore_error_newer, p.backup, p.app)
    is BackupProblem.NewerSchema -> stringResource(R.string.restore_error_newer, p.backup, p.app)
    is BackupProblem.Damaged -> stringResource(R.string.restore_error_damaged, p.detail)
    BackupProblem.Recording -> stringResource(R.string.restore_error_recording)
}
