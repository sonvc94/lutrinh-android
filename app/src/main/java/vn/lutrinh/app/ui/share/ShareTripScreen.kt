package vn.lutrinh.app.ui.share

import android.annotation.SuppressLint
import vn.lutrinh.app.ui.theme.TopoIcons
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lutrinh.app.R
import vn.lutrinh.app.share.TripExporter
import vn.lutrinh.app.ui.common.LoadingState
import vn.lutrinh.app.ui.common.MessageState
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.common.factory
import vn.lutrinh.app.ui.common.formatBytes
import vn.lutrinh.app.ui.theme.Radii
import vn.lutrinh.app.ui.theme.ledge

/** "Chia sẻ chuyến đi" (R18, docs/03-screens.md §3d): pick an output, preview it, share or save it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTripScreen(
    onBack: () -> Unit,
    vm: ShareTripViewModel = viewModel(factory = factory { c, h -> ShareTripViewModel(c, h) }),
) {
    val context = LocalContext.current
    val missing by vm.missing.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val hideNotes by vm.hideNotes.collectAsStateWithLifecycle()
    val image by vm.image.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val current = if (kind == ShareKind.IMAGE) image else page
    val ready = current as? ExportState.Ready
    val savedMsg = stringResource(R.string.share_saved, "%s")
    val failedMsg = stringResource(R.string.share_save_failed, "%s")

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    is ShareMessage.Saved -> savedMsg.format(m.folder)
                    is ShareMessage.SaveFailed -> failedMsg.format(m.reason)
                },
            )
        }
    }
    // Android 8–9: no MediaStore folders without a storage permission -> the system "save as" picker.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (kind == ShareKind.IMAGE) TripExporter.MIME_PNG else TripExporter.MIME_HTML)) { uri: Uri? ->
        val file = ready?.file
        if (uri != null && file != null) vm.copyTo(file, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_trip)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (missing) {
            MessageState(stringResource(R.string.not_found), "", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShareKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { vm.kind.value = k },
                        shape = SegmentedButtonDefaults.itemShape(i, ShareKind.entries.size, MaterialTheme.shapes.small),
                        icon = { SegmentedButtonDefaults.Icon(kind == k) { Icon(if (k == ShareKind.IMAGE) TopoIcons.Photo else TopoIcons.Page, null, Modifier.size(18.dp)) } },
                    ) { Text(stringResource(if (k == ShareKind.IMAGE) R.string.share_kind_image else R.string.share_kind_page)) }
                }
            }
            Text(
                stringResource(if (kind == ShareKind.IMAGE) R.string.share_kind_image_body else R.string.share_kind_page_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)
                    .ledge(Radii.Card).clip(RoundedCornerShape(Radii.Card)).background(MaterialTheme.colorScheme.surfaceVariant),
                Alignment.Center,
            ) {
                when (val s = current) {
                    ExportState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
                    }
                    is ExportState.Failed -> MessageState(stringResource(R.string.share_failed, s.message), "")
                    is ExportState.Ready -> if (kind == ShareKind.IMAGE) {
                        s.preview?.let { bmp ->
                            Image(
                                bmp.asImageBitmap(), stringResource(R.string.share_kind_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxHeight().aspectRatio(bmp.width.toFloat() / bmp.height).padding(12.dp)
                                    .clip(RoundedCornerShape(Radii.Chip))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radii.Chip)),
                            )
                        }
                    } else {
                        PagePreview(s)
                    }
                }
            }
            ready?.let { r ->
                val info = buildList {
                    if (kind == ShareKind.PAGE) add(stringResource(R.string.share_page_info, formatBytes(r.bytes), r.photos))
                    if (r.omittedPhotos > 0) add(stringResource(R.string.share_page_omitted, r.omittedPhotos))
                    if (!r.withBasemap) add(stringResource(R.string.share_no_basemap))
                }
                if (info.isNotEmpty()) Text(info.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (kind == ShareKind.PAGE) {
                val label = stringResource(R.string.share_hide_notes)
                // One card, the row is the toggle (as in "Cài đặt"); the switch only shows the state.
                TopoCard(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = hideNotes, role = Role.Switch, onValueChange = { vm.hideNotes.value = it })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.share_hide_notes_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hideNotes, onCheckedChange = null)
                    }
                }
            }
            Text(
                stringResource(R.string.share_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.small,
                    onClick = {
                        val file = ready?.file ?: return@OutlinedButton
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) vm.save(file, kind) else saveAs.launch(file.name)
                    },
                    enabled = ready != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(TopoIcons.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_save))
                }
                Button(shape = MaterialTheme.shapes.small,
                    onClick = { ready?.file?.let { context.startActivity(vm.shareIntent(it, kind)) } },
                    enabled = ready != null,
                    modifier = Modifier.weight(1f).semantics { stateDescription = if (ready != null) "ready" else "busy" },
                ) {
                    Icon(TopoIcons.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_action))
                }
            }
        }
    }
}

/** The generated page in a WebView with JavaScript and network loads off (it needs neither). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PagePreview(s: ExportState.Ready) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                // Only to read our own file in cacheDir/share; no content access.
                settings.allowFileAccess = true
                settings.allowContentAccess = false
            }
        },
        update = { web ->
            val url = Uri.fromFile(s.file).toString() + "?v=${s.version}"
            if (web.tag != url) {
                web.tag = url
                web.loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
