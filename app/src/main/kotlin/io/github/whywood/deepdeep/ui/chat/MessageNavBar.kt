package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputScope
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

private val NavHitWidth = 24.dp
private val NavTrackPadding = 8.dp
private val NavSegmentWidth = 16.dp
private val NavSegmentHeight = 3.dp
private val NavActiveSegmentHeight = 4.dp
private val NavTrackWidth = 1.5.dp
private val NavThumbWidth = 2.dp
private val NavThumbHeight = 14.dp
private val NavPreviewWidth = 168.dp
private val NavPreviewGap = 20.dp

@Composable
internal fun MessageNavBar(
    messages: List<AssistantMessage>,
    segments: List<Int>,
    activeSegmentIndex: Int,
    viewportFraction: Float,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val colors = deepDeepColors()
    val density = LocalDensity.current
    val latestOnJump by rememberUpdatedState(onJump)
    var dragSegment by remember(segments) { mutableStateOf<Int?>(null) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val navDescription = stringResource(R.string.chat_history_nav)
    val highlightedSegment = dragSegment ?: activeSegmentIndex
    val previewText = dragSegment
        ?.let { segments.getOrNull(it) }
        ?.let { messages.getOrNull(it) }
        ?.text
        ?.trim()
        ?.replace('\n', ' ')
        ?.take(80)
        ?.takeIf { it.isNotEmpty() }
    val previewY = dragSegment
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
            .width(NavHitWidth)
            .fillMaxHeight()
            .testTag("chat_message_nav"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(segments) {
                    detectTapGestures { offset ->
                        segmentAt(offset.y, segments.size, size.height)?.let { segment ->
                            segments.getOrNull(segment)?.let { latestOnJump(it) }
                        }
                    }
                }
                .pointerInput(segments) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragSegment = segmentAt(offset.y, segments.size, size.height)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragSegment = segmentAt(change.position.y, segments.size, size.height)
                        },
                        onDragEnd = {
                            dragSegment?.let { segment ->
                                segments.getOrNull(segment)?.let { latestOnJump(it) }
                            }
                            dragSegment = null
                        },
                        onDragCancel = { dragSegment = null },
                    )
                }
                .semantics { contentDescription = navDescription },
        ) {
            val paddingPx = NavTrackPadding.toPx()
            val centerX = size.width / 2f
            val trackTop = paddingPx
            val trackBottom = (size.height - paddingPx).coerceAtLeast(trackTop)
            val trackWidthPx = NavTrackWidth.toPx()
            drawRoundRect(
                color = colors.inkTertiary.copy(alpha = 0.10f),
                topLeft = Offset(centerX - trackWidthPx / 2f, trackTop),
                size = Size(trackWidthPx, trackBottom - trackTop),
                cornerRadius = CornerRadius(trackWidthPx / 2f),
            )
            val thumbWidthPx = NavThumbWidth.toPx()
            val thumbHeightPx = NavThumbHeight.toPx()
            val thumbCenterY = trackTop + (trackBottom - trackTop) * viewportFraction.coerceIn(0f, 1f)
            drawRoundRect(
                color = colors.accent.copy(alpha = 0.85f),
                topLeft = Offset(
                    1.dp.toPx(),
                    (thumbCenterY - thumbHeightPx / 2f).coerceIn(trackTop, (trackBottom - thumbHeightPx).coerceAtLeast(trackTop)),
                ),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f),
            )
            segments.forEachIndexed { index, _ ->
                val centerY = navSegmentCenter(index, segments.size, size.height, paddingPx)
                val highlighted = index == highlightedSegment
                val segmentHeight = if (highlighted) NavActiveSegmentHeight.toPx() else NavSegmentHeight.toPx()
                val segmentWidthPx = NavSegmentWidth.toPx()
                drawRoundRect(
                    color = if (highlighted) colors.accent else colors.inkTertiary.copy(alpha = 0.38f),
                    topLeft = Offset(centerX - segmentWidthPx / 2f, centerY - segmentHeight / 2f),
                    size = Size(segmentWidthPx, segmentHeight),
                    cornerRadius = CornerRadius(segmentHeight / 2f),
                )
            }
        }
        if (previewText != null && previewY != null) {
            Text(
                text = previewText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(NavPreviewWidth + NavPreviewGap), y = previewY)
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

private fun PointerInputScope.segmentAt(
    positionY: Float,
    segmentCount: Int,
    trackHeightPx: Int,
): Int? {
    if (segmentCount <= 0 || trackHeightPx <= 0) return null
    val padding = NavTrackPadding.toPx()
    val usable = (trackHeightPx - padding * 2f).coerceAtLeast(1f)
    val fraction = ((positionY - padding) / usable).coerceIn(0f, 1f)
    return navSegmentForFraction(fraction, segmentCount)
}
