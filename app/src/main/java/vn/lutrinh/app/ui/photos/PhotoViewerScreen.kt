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
import androidx.compose.runtime.Composable
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
import vn.lutrinh.app.ui.common.factory

class PhotoViewerViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val startIndex: Int = handle.get<String>("index")?.toIntOrNull() ?: 0
    val step = c.steps.observeStep(checkNotNull(handle["stepId"]))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Full-screen, swipeable, pinch-to-zoom viewer for the photos of one step. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    onClose: () -> Unit,
    vm: PhotoViewerViewModel = viewModel(factory = factory { c, h -> PhotoViewerViewModel(c, h) }),
) {
    val step by vm.step.collectAsStateWithLifecycle()
    val photos = step?.photos.orEmpty()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (photos.isNotEmpty()) {
            val pager = rememberPagerState(initialPage = vm.startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableBox { LocalPhoto(photos[page].fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
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
