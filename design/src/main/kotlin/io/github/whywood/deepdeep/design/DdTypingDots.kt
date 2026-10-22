package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val DOT_COUNT = 3
private const val DOT_DURATION = 900
private const val DOT_STAGGER = 140
private const val DOT_ALPHA_MIN = 0.28f
private const val DOT_ALPHA_MAX = 0.95f
private const val DOT_SCALE_MIN = 0.82f
private const val DOT_SCALE_MAX = 1f

@Composable
fun DdTypingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 4.dp,
    spacing: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DOT_COUNT) { index ->
            val pulse = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(DOT_DURATION, easing = DdMotion.EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * DOT_STAGGER),
                ),
                label = "typing-dot-$index",
            )
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        alpha = DOT_ALPHA_MIN + (DOT_ALPHA_MAX - DOT_ALPHA_MIN) * pulse.value
                        val scale = DOT_SCALE_MIN + (DOT_SCALE_MAX - DOT_SCALE_MIN) * pulse.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
