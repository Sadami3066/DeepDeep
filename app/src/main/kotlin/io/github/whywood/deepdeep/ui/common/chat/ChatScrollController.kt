package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

data class ScrollAnchor(val index: Int, val offset: Int, val atBottom: Boolean = false)

@Stable
internal class ChatScrollController(
    private val listState: LazyListState,
    private val thresholdPx: Float,
    private val deadbandPx: Float,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    var following by mutableStateOf(true)
        private set

    var atBottom by mutableStateOf(true)
        private set

    val showJumpToBottom: Boolean
        get() = !following && !atBottom

    private var dragging = false
    private var dragSeen = false
    private var previousContentBottomPx: Float? = null
    private var lastGrowthAtMs: Long? = null
    private val nestedWindow = MovementWindow(ScrollFollowPolicy.NestedScrollHoldMs, nowMillis)

    private val nestedConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (abs(available.y) > 0.5f) nestedWindow.mark()
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (abs(available.y) <= 0.5f) return Offset.Zero
            nestedWindow.mark()
            return Offset(0f, available.y)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (abs(available.y) > 0.5f) nestedWindow.mark()
            return Velocity(0f, available.y)
        }
    }

    fun nestedScrollConnection(): NestedScrollConnection = nestedConnection

    fun onDraggingChanged(value: Boolean) {
        dragging = value
        if (value) dragSeen = true
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

    suspend fun followContent() {
        if (!following || dragging || nestedWindow.active()) return
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return
        val last = info.visibleItemsInfo.lastOrNull() ?: return
        if (last.index != info.totalItemsCount - 1) {
            previousContentBottomPx = null
            return
        }
        val viewportBottom = info.viewportEndOffset - info.afterContentPadding
        val contentBottom = (last.offset + last.size).toFloat()
        val overflow = (contentBottom - viewportBottom).coerceAtLeast(0f)
        val previous = previousContentBottomPx
        previousContentBottomPx = contentBottom
        val now = nowMillis()
        val action = ScrollFollowPolicy.decideFollowAction(
            overflowPx = overflow,
            contentBottomDeltaPx = if (previous == null) 0f else contentBottom - previous,
            sinceGrowthMs = lastGrowthAtMs?.let { now - it },
            deadbandPx = deadbandPx,
            debounceMs = ScrollFollowPolicy.RetractDebounceMs,
        )
        if (action != FollowAction.Reveal) return
        lastGrowthAtMs = now
        revealBy(ScrollFollowPolicy.revealDeltaPx(overflow))
        previousContentBottomPx = null
    }

    suspend fun settleScroll() {
        if (!dragSeen) return
        dragSeen = false
        previousContentBottomPx = null
        val distance = bottomDistancePx()
        if (distance == null) {
            following = false
            return
        }
        if (ScrollFollowPolicy.nearBottom(distance, thresholdPx)) {
            following = true
            atBottom = distance <= 0f
            if (distance > 0f && listState.canScrollForward) {
                revealBy(ScrollFollowPolicy.revealDeltaPx(distance))
            }
        } else {
            following = false
        }
    }

    fun onBottomChanged(value: Boolean) {
        atBottom = value
        if (value) following = true
    }

    private suspend fun revealBy(deltaPx: Float) {
        if (deltaPx <= 0f) return
        if (chatAnimationsEnabled()) {
            listState.animateScrollBy(deltaPx, tween(ScrollFollowPolicy.RevealDurationMs, easing = DdMotion.EaseOut))
        } else {
            listState.scrollBy(deltaPx)
        }
    }

    private suspend fun awaitSettled() {
        withFrameNanos { }
        withFrameNanos { }
    }

    private fun bottomDistancePx(): Float? {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return 0f
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
        if (last.index != info.totalItemsCount - 1) return null
        val viewportBottom = info.viewportEndOffset - info.afterContentPadding
        return (last.offset + last.size - viewportBottom).toFloat().coerceAtLeast(0f)
    }

    private fun overflowToBottom(): Float {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return 0f
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
        if (last.index != info.totalItemsCount - 1) return 0f
        val viewportBottom = info.viewportEndOffset - info.afterContentPadding
        return (last.offset + last.size - viewportBottom).toFloat().coerceAtLeast(0f)
    }
}

@Composable
internal fun rememberChatScrollController(listState: LazyListState): ChatScrollController {
    val density = LocalDensity.current
    val thresholdPx = with(density) { ScrollFollowPolicy.NearBottomThresholdDp.dp.toPx() }
    val deadbandPx = with(density) { ScrollFollowPolicy.RetractDeadbandDp.dp.toPx() }
    val controller = remember(listState, thresholdPx, deadbandPx) {
        ChatScrollController(listState, thresholdPx, deadbandPx)
    }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(controller, dragged) {
        controller.onDraggingChanged(dragged)
    }
    LaunchedEffect(controller, listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling -> if (!scrolling) controller.settleScroll() }
    }
    LaunchedEffect(controller, listState) {
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { atEnd -> controller.onBottomChanged(atEnd) }
    }
    return controller
}
