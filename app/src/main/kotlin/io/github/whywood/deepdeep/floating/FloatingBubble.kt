package io.github.whywood.deepdeep.floating

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

internal enum class FloatingBubbleRing {
    NONE,
    FILLING,
    DRAINING,
    FADING,
}

@Composable
internal fun FloatingBubble(
    mood: FloatingWhaleMood,
    pending: Int,
    ring: FloatingBubbleRing,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val description = if (pending > 0) {
        stringResource(R.string.floating_bubble_pending, pending)
    } else {
        stringResource(R.string.floating_bubble_description)
    }
    Box(
        modifier
            .size(FloatingBubbleWindowSize)
            .semantics { contentDescription = description }
            .testTag("floating_bubble"),
        contentAlignment = Alignment.Center,
    ) {
        FloatingLongPressRing(ring)
        Box(
            Modifier
                .size(FloatingBubbleSize)
                .floatingLift(CircleShape, colors)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = 0.94f)),
        )
        Image(
            painter = painterResource(mood.drawable()),
            contentDescription = null,
            modifier = Modifier
                .size(FloatingBubbleSize - 4.dp)
                .offset(y = 1.dp),
            contentScale = ContentScale.Fit,
        )
        if (pending > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(20.dp)
                    .floatingLift(CircleShape, colors)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (pending > 99) "99+" else pending.toString(),
                    style = DdType.numeric,
                    color = colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FloatingLongPressRing(ring: FloatingBubbleRing) {
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(ring) {
        when (ring) {
            FloatingBubbleRing.FILLING -> {
                alpha.animateTo(1f, tween(60))
                progress.snapTo(0f)
                progress.animateTo(1f, tween(FloatingRingSweepMs, easing = LinearEasing))
            }

            FloatingBubbleRing.DRAINING -> {
                alpha.animateTo(1f, tween(60))
                progress.snapTo(1f)
                progress.animateTo(0f, tween(FloatingRingSweepMs, easing = LinearEasing))
            }

            FloatingBubbleRing.FADING -> alpha.animateTo(0f, tween(FloatingRingFadeMs))
            FloatingBubbleRing.NONE -> {
                alpha.animateTo(0f, tween(FloatingRingFadeMs))
                progress.snapTo(0f)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val visible = alpha.value
        val sweep = 360f * progress.value
        if (visible <= 0.001f || sweep <= 0.001f) return@Canvas
        val radius = (FloatingBubbleSize / 2 + FloatingRingGap).toPx()
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(FloatingRingStart, FloatingRingEnd, FloatingRingStart),
                center = center,
            ),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = visible,
            style = Stroke(width = FloatingRingStroke.toPx(), cap = StrokeCap.Round),
        )
    }
}

@DrawableRes
internal fun FloatingWhaleMood.drawable(): Int = when (this) {
    FloatingWhaleMood.IDLE -> R.drawable.v2_wave
    FloatingWhaleMood.THINKING -> R.drawable.v2_thinking
    FloatingWhaleMood.SLEEPY -> R.drawable.v2_waiting
    FloatingWhaleMood.WAITING -> R.drawable.v2_confused
    FloatingWhaleMood.DONE -> R.drawable.v2_done
    FloatingWhaleMood.ALERT -> R.drawable.v2_alert
}
