package vn.lutrinh.app.ui.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.R
import vn.lutrinh.app.ui.common.LocalPhoto
import vn.lutrinh.app.ui.common.photoFile
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import vn.lutrinh.app.ui.common.factory

class PhotoViewerViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val startIndex: Int = handle.get<String>("index")?.toIntOrNull() ?: 0
    val step = c.steps.observeStep(checkNotNull(handle["stepId"]))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Full-screen, swipeable, pinch-to-zoom viewer for the photos (and R29 videos) of one step, in their stored order (R27). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    onClose: () -> Unit,
    vm: PhotoViewerViewModel = viewModel(factory = factory { c, h -> PhotoViewerViewModel(c, h) }),
) {
    val step by vm.step.collectAsStateWithLifecycle()
    val photos = step?.photos.orEmpty()
    // Black screen: light status-bar icons while it is open (the app default is dark icons).
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (photos.isNotEmpty()) {
            val pager = rememberPagerState(initialPage = vm.startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = photos[page]
                if (item.isVideo) {
                    // R29: plays full screen while its page is the current one.
                    VideoPage(item.fileName, active = pager.currentPage == page && !pager.isScrollInProgress)
                } else {
                    ZoomableBox { LocalPhoto(item.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
            }
            Text(
                stringResource(R.string.photo_viewer_counter, pager.currentPage + 1, photos.size),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White)
        }
    }
}

/**
 * R29: a stored video in the platform player (VideoView + MediaController: no player library).
 * Starts when the page becomes current, pauses when it is left; tap shows the play/pause/seek bar.
 */
@Composable
private fun VideoPage(fileName: String, active: Boolean) {
    val file = photoFile(fileName)
    var view by remember { mutableStateOf<VideoView?>(null) }
    var prepared by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        // VideoView keeps the video's aspect ratio inside what it is given; the full-screen
        // FrameLayout centres it (without it the video sits small in a corner).
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val video = VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnPreparedListener { mp -> mp.isLooping = false; prepared = true }
                    setVideoURI(Uri.fromFile(file))
                    contentDescription = ctx.getString(R.string.video_play)
                }
                view = video
                FrameLayout(ctx).apply {
                    addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                }
            },
            onRelease = { (it.getChildAt(0) as? VideoView)?.stopPlayback() },
        )
        if (!prepared) LocalPhoto(fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
    LaunchedEffect(active, view) {
        val v = view ?: return@LaunchedEffect
        if (active) v.start() else if (v.isPlaying) v.pause()
    }
}

@Composable
private fun ZoomableBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Pinch (2 fingers) zooms; 1-finger drags pan only while zoomed, otherwise
                // they are left to the pager so swiping between photos keeps working.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    ) { content() }
}
