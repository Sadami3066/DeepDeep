package io.github.whywood.deepdeep.floating

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun FloatingBubble(
    mood: FloatingWhaleMood,
    pending: Int,
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

@DrawableRes
internal fun FloatingWhaleMood.drawable(): Int = when (this) {
    FloatingWhaleMood.IDLE -> R.drawable.whale_idle
    FloatingWhaleMood.WAVE -> R.drawable.whale_wave
    FloatingWhaleMood.THINKING -> R.drawable.whale_thinking
    FloatingWhaleMood.SLEEPY -> R.drawable.whale_sleepy
}
