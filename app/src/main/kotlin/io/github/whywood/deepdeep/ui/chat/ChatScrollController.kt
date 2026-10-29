package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged

data class ScrollAnchor(val index: Int, val offset: Int, val atBottom: Boolean = false)

@Stable
internal class ChatScrollController(private val listState: LazyListState) {
    var following by mutableStateOf(true)
        private set

    var atBottom by mutableStateOf(true)
        private set

    val showJumpToBottom: Boolean
        get() = !following && !atBottom

    suspend fun followContent() {
        if (!following) return
        awaitSettled()
        settleByOverflow()
    }

    suspend fun anchorToBottom() {
        if (!following) return
        withFrameNanos { }
        val overflow = overflowToBottom()
        if (overflow > 0f) {
            listState.scrollBy(overflow)
        }
    }

    suspend fun jumpToBottom(animate: Boolean) {
        following = true
        awaitSettled()
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex < 0) return
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (last == null || last.index != lastIndex) {
            if (animate) {
                listState.animateScrollToItem(lastIndex)
            } else {
                listState.scrollToItem(lastIndex)
            }
            awaitSettled()
        }
        val overflow = overflowToBottom()
        if (overflow <= 0f) return
        if (animate) listState.animateScrollBy(overflow) else listState.scrollBy(overflow)
    }

    suspend fun restoreAnchor(index: Int, offset: Int) {
        following = false
        awaitSettled()
        val count = listState.layoutInfo.totalItemsCount
        if (count <= 0) {
            jumpToBottom(animate = false)
            return
        }
        listState.scrollToItem(index.coerceIn(0, count - 1), offset.coerceAtLeast(0))
        awaitSettled()
        if (!listState.canScrollForward) onBottomChanged(true)
    }

    fun onUserGesture() {
        following = false
    }

    fun onBottomChanged(value: Boolean) {
        atBottom = value
        if (value) following = true
    }

    private suspend fun awaitSettled() {
        withFrameNanos { }
        withFrameNanos { }
    }

    private fun overflowToBottom(): Float {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return 0f
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
        if (last.index != info.totalItemsCount - 1) return 0f
        val viewportBottom = info.viewportEndOffset - info.afterContentPadding
        return (last.offset + last.size - viewportBottom).toFloat().coerceAtLeast(0f)
    }

    private suspend fun settleByOverflow() {
        repeat(2) {
            if (!following) return
            val info = listState.layoutInfo
            val lastIndex = info.totalItemsCount - 1
            if (lastIndex < 0) return
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || last.index != lastIndex) {
                listState.scrollToItem(lastIndex)
                withFrameNanos { }
                return@repeat
            }
            val overflow = overflowToBottom()
            if (overflow <= 0f) return
            listState.scrollBy(overflow)
            withFrameNanos { }
        }
    }
}

@Composable
internal fun rememberChatScrollController(listState: LazyListState): ChatScrollController {
    val controller = remember(listState) { ChatScrollController(listState) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(controller, dragged) {
        if (dragged) {
            controller.onUserGesture()
        } else if (!listState.canScrollForward) {
            controller.onBottomChanged(true)
        }
    }
    LaunchedEffect(controller, listState) {
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { controller.onBottomChanged(it) }
    }
    return controller
}
