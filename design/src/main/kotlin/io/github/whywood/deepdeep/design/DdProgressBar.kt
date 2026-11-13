package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private val ProgressHeight = 4.dp
private const val IndeterminateSegmentFraction = 0.30f
private const val IndeterminateStaticOffset = 0.20f

@Composable
public fun DdProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val motionEnabled = LocalDdMotionEnabled.current
    if (progress == null) {
        IndeterminateProgressBar(
            modifier = modifier,
            motionEnabled = motionEnabled,
            track = colors.wash,
            fill = colors.accent,
        )
    } else {
        DeterminateProgressBar(
            progress = progress,
            modifier = modifier,
            motionEnabled = motionEnabled,
            track = colors.wash,
            fill = colors.accent,
        )
    }
}

@Composable
private fun DeterminateProgressBar(
    progress: Float,
    modifier: Modifier,
    motionEnabled: Boolean,
    track: Color,
    fill: Color,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motionEnabled) {
            tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut)
        } else {
            snap()
        },
        label = "dd-progress",
    )
    val fraction = if (motionEnabled) animated else target
    ProgressCanvas(modifier, track) { width, height, corner ->
        if (fraction > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset.Zero,
                size = Size(width * fraction, height),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun IndeterminateProgressBar(
    modifier: Modifier,
    motionEnabled: Boolean,
    track: Color,
    fill: Color,
) {
    val phase = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "dd-progress-indeterminate")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = DdMotion.PAGE * 2,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dd-progress-phase",
        )
    } else {
        null
    }
    ProgressCanvas(modifier, track) { width, height, corner ->
        val segment = width * IndeterminateSegmentFraction
        val position = phase?.value
        val left = if (position == null) {
            width * IndeterminateStaticOffset
        } else {
            -segment + position * (width + segment)
        }
        drawRoundRect(
            color = fill,
            topLeft = Offset(left, 0f),
            size = Size(segment, height),
            cornerRadius = corner,
        )
    }
}

@Composable
private fun ProgressCanvas(
    modifier: Modifier,
    track: Color,
    drawContent: DrawScope.(width: Float, height: Float, corner: CornerRadius) -> Unit,
) {
    Canvas(modifier.fillMaxWidth().height(ProgressHeight)) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = track,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = corner,
        )
        drawContent(size.width, size.height, corner)
    }
}
