package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架占位条：加载时的单行灰条，颜色在 wash/hairline 间缓慢呼吸。
 */
@Composable
fun DdSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = DdSpacing.Md,
) {
    Box(
        modifier
            .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
            .height(height)
            .clip(DdShapes.Pill)
            .background(rememberSkeletonColor()),
    )
}

/**
 * 骨架行：两行占位文字，用于列表首载。
 */
@Composable
fun DdSkeletonRow(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
    ) {
        repeat(lines.coerceAtLeast(1)) { index ->
            DdSkeletonLine(widthFraction = if (index == 0) 1f else 0.62f)
        }
    }
}

/**
 * 骨架卡片：带描边的圆角卡，内部承载多行占位文字，用于详情/卡片首载。
 */
@Composable
fun DdSkeletonCard(
    modifier: Modifier = Modifier,
    rows: Int = 2,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DdShapes.Card)
            .background(colors.surface)
            .border(1.dp, colors.hairline, DdShapes.Card)
            .padding(DdSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DdSpacing.Lg),
    ) {
        repeat(rows.coerceAtLeast(1)) {
            DdSkeletonRow()
        }
    }
}

@Composable
private fun rememberSkeletonColor(): Color {
    val colors = deepDeepColors()
    val transition = rememberInfiniteTransition(label = "dd-skeleton")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = DdMotion.PAGE * 2,
                easing = DdMotion.EaseInOut,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dd-skeleton-fraction",
    )
    return lerp(colors.wash, colors.hairline, fraction)
}
