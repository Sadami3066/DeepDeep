package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage

private val NavTrackPadding = 6.dp
private val NavSegmentWidth = 2.5.dp
private val NavSegmentHeight = 5.dp
private val NavActiveSegmentHeight = 8.dp
private val NavPreviewWidth = 168.dp
private val NavPreviewGap = 18.dp

@Composable
internal fun MessageNavBar(
    messages: List<AssistantMessage>,
    userMessageIndices: List<Int>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val segments = remember(userMessageIndices) {
        navSegmentIndices(userMessageIndices, ChatHistoryNavMaxSegments)
    }
    if (segments.isEmpty()) return
    val density = LocalDensity.current
    var activeSegment by remember(segments) { mutableStateOf<Int?>(null) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val navDescription = stringResource(R.string.chat_history_nav)
    val activeIndex = activeSegment
    val previewText = activeIndex
        ?.let { segment -> segments.getOrNull(segment) }
        ?.let { messageIndex -> messages.getOrNull(messageIndex) }
        ?.text
        ?.trim()
        ?.replace('\n', ' ')
        ?.take(80)
        ?.takeIf { it.isNotEmpty() }
    val previewOffset = activeIndex
        ?.let { segment ->
            with(density) {
                navSegmentCenter(
                    index = segment,
                    count = segments.size,
                    height = trackHeightPx.toFloat(),
                    padding = NavTrackPadding.toPx(),
                ).toDp()
            }
        }
        ?.let { (it - 20.dp).coerceAtLeast(0.dp) }
    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .testTag("chat_message_nav"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(segments) {
                    fun segmentAt(positionY: Float): Int? {
                        val height = size.height.toFloat()
                        if (height <= 0f || segments.isEmpty()) return null
                        val padding = NavTrackPadding.toPx()
                        val usable = (height - padding * 2f).coerceAtLeast(1f)
                        val fraction = ((positionY - padding) / usable).coerceIn(0f, 1f)
                        return navSegmentForFraction(fraction, segments.size)
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> activeSegment = segmentAt(offset.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            activeSegment = segmentAt(change.position.y)
                        },
                        onDragEnd = {
                            activeSegment?.let { segment -> segments.getOrNull(segment)?.let(onJump) }
                            activeSegment = null
                        },
                        onDragCancel = { activeSegment = null },
                    )
                }
                .semantics { contentDescription = navDescription },
        ) {
            val paddingPx = NavTrackPadding.toPx()
            val segmentWidthPx = NavSegmentWidth.toPx()
            val idleHeightPx = NavSegmentHeight.toPx()
            val activeHeightPx = NavActiveSegmentHeight.toPx()
            val centerX = size.width / 2f
            segments.forEachIndexed { index, _ ->
                val centerY = navSegmentCenter(index, segments.size, size.height, paddingPx)
                val segmentHeight = if (index == activeSegment) activeHeightPx else idleHeightPx
                drawRoundRect(
                    color = if (index == activeSegment) {
                        colors.inkSecondary.copy(alpha = 0.8f)
                    } else {
                        colors.inkTertiary.copy(alpha = 0.32f)
                    },
                    topLeft = Offset(centerX - segmentWidthPx / 2f, centerY - segmentHeight / 2f),
                    size = Size(segmentWidthPx, segmentHeight),
                    cornerRadius = CornerRadius(segmentWidthPx / 2f),
                )
            }
        }
        if (previewText != null && previewOffset != null) {
            Text(
                text = previewText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(NavPreviewWidth + NavPreviewGap), y = previewOffset)
                    .widthIn(max = NavPreviewWidth)
                    .clip(RoundedCornerShape(DdSpacing.Sm))
                    .background(colors.raised)
                    .padding(horizontal = DdSpacing.Sm, vertical = DdSpacing.Xs)
                    .testTag("chat_message_nav_preview"),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
