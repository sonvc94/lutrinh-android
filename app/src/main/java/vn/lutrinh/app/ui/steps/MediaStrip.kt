package vn.lutrinh.app.ui.steps

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.usecase.MediaOrder
import vn.lutrinh.app.domain.usecase.VideoRules

/**
 * R27: the step editor's photo strip. Long-press a tile, then drag it sideways; the others make
 * room as it passes their middle, and the strip scrolls when it reaches an edge. The new order is
 * reported once, on release ([onReorder]); "Lưu" stores it. Each tile also gets the accessibility
 * actions "Đưa lên trước" / "Đưa ra sau" (TalkBack, UI tests). [trailing] adds items after the
 * files that do not take part (R19 ticked gallery photos, which become files only on save).
 */
@Composable
fun ReorderableMediaRow(
    files: List<String>,
    onReorder: (List<String>) -> Unit,
    tile: @Composable (name: String, moveActions: List<CustomAccessibilityAction>) -> Unit,
    trailing: LazyListScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    // While a tile is dragged the strip shows [dragOrder]; otherwise [files] as stored in the form.
    var dragOrder by remember { mutableStateOf(files) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val order = if (dragging != null) dragOrder else files
    val currentOrder by rememberUpdatedState(order)
    val report by rememberUpdatedState(onReorder)
    val earlier = stringResource(R.string.step_photo_move_earlier)
    val later = stringResource(R.string.step_photo_move_later)

    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(order, key = { _, name -> name }) { index, name ->
            val isDragged = name == dragging
            val actions = buildList {
                if (index > 0) add(CustomAccessibilityAction(earlier) { report(MediaOrder.moveEarlier(currentOrder, currentOrder.indexOf(name))); true })
                if (index < order.lastIndex) add(CustomAccessibilityAction(later) { report(MediaOrder.moveLater(currentOrder, currentOrder.indexOf(name))); true })
            }
            Box(
                Modifier
                    .then(
                        if (isDragged) {
                            Modifier.zIndex(1f).graphicsLayer {
                                translationX = dragOffset
                                scaleX = 1.06f
                                scaleY = 1.06f
                                shadowElevation = 12f
                            }
                        } else Modifier.animateItem(),
                    )
                    .pointerInput(name) {
                        val edge = 32.dp.toPx()
                        fun end() {
                            val final = dragOrder
                            dragging = null
                            dragOffset = 0f
                            report(final)
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragOrder = currentOrder; dragging = name; dragOffset = 0f },
                            onDragEnd = { end() },
                            onDragCancel = { end() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                val layout = listState.layoutInfo
                                val me = layout.visibleItemsInfo.firstOrNull { it.key == name } ?: return@detectDragGesturesAfterLongPress
                                val centre = me.offset + me.size / 2f + dragOffset
                                val target = layout.visibleItemsInfo.firstOrNull { other ->
                                    other.key != name && other.key in dragOrder && centre >= other.offset && centre <= other.offset + other.size
                                }
                                if (target != null) {
                                    val from = dragOrder.indexOf(name)
                                    val to = dragOrder.indexOf(target.key)
                                    // Keep the scroll where it is: by default the list follows its first visible item when it moves.
                                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                    dragOrder = MediaOrder.move(dragOrder, from, to)
                                    dragOffset += me.offset - target.offset
                                }
                                // Near an edge: scroll the strip along with the finger.
                                val left = me.offset + dragOffset
                                val right = left + me.size
                                val scroll = when {
                                    amount.x > 0 && right > layout.viewportEndOffset - edge -> amount.x
                                    amount.x < 0 && left < layout.viewportStartOffset + edge -> amount.x
                                    else -> 0f
                                }
                                if (scroll != 0f) dragOffset += listState.dispatchRawDelta(scroll)
                            },
                        )
                    },
            ) { tile(name, actions) }
        }
        trailing()
    }
}

/**
 * R28 / R29: `ACTION_IMAGE_CAPTURE` or `ACTION_VIDEO_CAPTURE` (60 s limit) into [Request.target], a
 * FileProvider URI of our capture folder, with a one-time write grant to the camera app.
 */
class CaptureWithSystemCamera : ActivityResultContract<CaptureWithSystemCamera.Request, CaptureWithSystemCamera.Result>() {
    data class Request(val target: Uri, val video: Boolean)

    /** [returned]: a URI the camera handed back instead of writing our file (some video cameras). */
    data class Result(val ok: Boolean, val returned: Uri?)

    override fun createIntent(context: Context, input: Request): Intent =
        Intent(if (input.video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input.target)
            if (input.video) putExtra(MediaStore.EXTRA_DURATION_LIMIT, VideoRules.CAPTURE_LIMIT_SECONDS)
            clipData = ClipData.newRawUri("", input.target)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Result = Result(resultCode == Activity.RESULT_OK, intent?.data)
}
