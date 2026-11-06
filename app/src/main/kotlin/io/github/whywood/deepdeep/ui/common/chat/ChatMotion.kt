package io.github.whywood.deepdeep.ui.common.chat

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal fun chatAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

@Composable
internal fun rememberShimmerBrush(active: Boolean, base: Color, highlight: Color): Brush? {
    if (!active || !chatAnimationsEnabled()) return null
    val transition = rememberInfiniteTransition(label = "chat_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "chat_shimmer_progress",
    )
    val start = progress * 640f - 200f
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + 220f, 0f),
    )
}
