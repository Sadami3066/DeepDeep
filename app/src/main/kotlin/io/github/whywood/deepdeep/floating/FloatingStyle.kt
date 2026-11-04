package io.github.whywood.deepdeep.floating

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DeepDeepColors

internal val FloatingBubbleSize = 64.dp
internal val FloatingBubbleWindowPadding = 8.dp
internal val FloatingBubbleWindowSize = FloatingBubbleSize + FloatingBubbleWindowPadding * 2
internal val FloatingEdgeMargin = 8.dp
internal val FloatingSurfaceWindowPadding = 16.dp
internal val FloatingSurfaceGap = 6.dp
internal val FloatingCardWidth = 280.dp
internal val FloatingCardCorner = 20.dp
internal val FloatingArrowWidth = 20.dp
internal val FloatingArrowHeight = 9.dp
internal val FloatingPanelWidth = 300.dp
internal val FloatingCompletedRemoveThreshold = 88.dp
internal val FloatingVirtualPreviewWidth = 132.dp
internal val FloatingVirtualPreviewPadding = 12.dp
internal val FloatingVirtualPreviewChipHeight = 46.dp
internal val FloatingVirtualPreviewCorner = 14.dp
internal const val FloatingVirtualPreviewMaxHeightRatio = 0.45f
internal val FloatingVirtualPreviewChipBackground = Color(0xE60B1220)
internal val FloatingVirtualPreviewChipText = Color(0xF2FFFFFF)
internal const val FloatingCardMaxHeightRatio = 0.45f
internal const val FloatingPanelMaxHeightRatio = 0.70f
internal val FloatingSurfaceNearPadding = 2.dp
internal val FloatingRingGap = 3.dp
internal val FloatingRingStroke = 2.5.dp
internal val FloatingRingStart = Color(0xFF7DD3FC)
internal val FloatingRingEnd = Color(0xFF2563EB)
internal const val FloatingRingSweepMs = 300
internal const val FloatingRingFadeMs = 180
internal const val FloatingRingStartDelayMs = 120L
internal const val FloatingRingResetDelayMs = 200L

internal fun Modifier.floatingLift(shape: Shape, colors: DeepDeepColors): Modifier = shadow(
    elevation = 12.dp,
    shape = shape,
    clip = false,
    ambientColor = if (colors.dark) Color(0xFF071426) else Color(0x332563EB),
    spotColor = if (colors.dark) Color(0x4D0B2A4A) else Color(0x6638BDF8),
)
