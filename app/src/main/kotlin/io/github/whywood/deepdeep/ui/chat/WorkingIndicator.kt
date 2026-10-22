package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.delay

@Composable
internal fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = pulse)),
    )
}

@Composable
internal fun WorkingIndicator(
    activity: String?,
    startedAt: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedSeconds = startedAt?.let { ((now - it).coerceAtLeast(0L) / 1000L).toInt() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(color = colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(
            text = activity ?: stringResource(R.string.assistant_working),
            style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = colors.inkSecondary,
        )
        if (elapsedSeconds != null) {
            Text(
                text = " · ",
                style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.inkTertiary,
            )
            Text(
                text = stringResource(R.string.assistant_elapsed_seconds, elapsedSeconds),
                style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.inkTertiary,
            )
        }
    }
}
