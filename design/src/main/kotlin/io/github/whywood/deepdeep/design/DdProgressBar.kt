package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * 线性进度条：fraction 为 null 时显示不确定态（来回滑动），否则显示确定进度。
 */
@Composable
fun DdProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    height: Dp = DdSpacing.Xs,
    color: Color = deepDeepColors().accent,
    trackColor: Color = deepDeepColors().wash,
) {
    val shape = DdShapes.Pill
    if (fraction == null) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(trackColor),
        ) {
            val transition = rememberInfiniteTransition(label = "dd-progress")
            val position by transition.animateFloat(
                initialValue = -0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = DdMotion.PAGE * 2,
                        easing = DdMotion.EaseInOut,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dd-progress-position",
            )
            BoxWithConstraints(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.38f)
                        .offset(x = maxWidth * position)
                        .clip(shape)
                        .background(color),
                )
            }
        }
    } else {
        val target = fraction.coerceIn(0f, 1f)
        val animatedFraction by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = DdMotion.BASE,
                easing = DdMotion.EaseOut,
            ),
            label = "dd-progress-fraction",
        )
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(trackColor)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(animatedFraction, 0f..1f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color),
            )
        }
    }
}
