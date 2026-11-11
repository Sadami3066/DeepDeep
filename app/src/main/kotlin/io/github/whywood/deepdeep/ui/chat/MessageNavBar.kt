package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.abs

private val NavHitWidth = 24.dp
private val NavTickWidth = 8.dp
private val NavTickHeight = 2.dp
private val NavTickRowHeight = 16.dp
private val NavTickShape = RoundedCornerShape(NavTickHeight / 2)

@Composable
internal fun MessageNavBar(
    userMessageIndices: List<Int>,
    activeMessageIndex: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = userMessageIndices.size
    if (count <= ChatHistoryNavWindowTicks) return
    val colors = deepDeepColors()
    val latestOnJump by rememberUpdatedState(onJump)
    val activeSlot = navAnchorSlot(userMessageIndices, activeMessageIndex)
    val listState = rememberLazyListState()
    val navDescription = stringResource(R.string.chat_history_nav)
    LaunchedEffect(activeSlot, count) {
        val currentTop = listState.firstVisibleItemIndex
        val target = navFollowTopSlot(activeSlot, currentTop, count, ChatHistoryNavWindowTicks)
        if (target == currentTop) return@LaunchedEffect
        if (abs(target - currentTop) > 3) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .width(NavHitWidth)
            .height(NavTickRowHeight * ChatHistoryNavWindowTicks)
            .semantics { contentDescription = navDescription }
            .testTag("chat_message_nav"),
    ) {
        items(count) { slot ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavTickRowHeight)
                    .clickable(role = Role.Button) { latestOnJump(userMessageIndices[slot]) },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(NavTickWidth)
                        .height(NavTickHeight)
                        .clip(NavTickShape)
                        .background(
                            if (slot == activeSlot) {
                                colors.accent
                            } else {
                                colors.inkTertiary.copy(alpha = 0.30f)
                            },
                        ),
                )
            }
        }
    }
}
