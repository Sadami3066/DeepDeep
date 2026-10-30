package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdTypingDots
import io.github.whywood.deepdeep.design.deepDeepColors
import java.util.Locale
import kotlinx.coroutines.delay

internal fun formatElapsed(
    totalSeconds: Long,
    hoursMinutesSeconds: String,
    minutesSeconds: String,
    secondsOnly: String,
): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val seconds = clamped % 60
    return when {
        hours > 0 -> String.format(Locale.ROOT, hoursMinutesSeconds, hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.ROOT, minutesSeconds, minutes, seconds)
        else -> String.format(Locale.ROOT, secondsOnly, seconds)
    }
}

@Composable
internal fun WorkingIndicator(
    activityText: String?,
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
    val elapsedSeconds = startedAt?.let { ((now - it).coerceAtLeast(0L) / 1000L) }
    Row(
        modifier = modifier.heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdTypingDots(color = colors.inkTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = activityText ?: stringResource(R.string.assistant_working),
            modifier = Modifier.weight(1f, fill = false),
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (elapsedSeconds != null) {
            Text(
                text = " · ",
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
            Text(
                text = formatElapsed(
                    elapsedSeconds,
                    stringResource(R.string.assistant_elapsed_hours_minutes_seconds),
                    stringResource(R.string.assistant_elapsed_minutes_seconds),
                    stringResource(R.string.assistant_elapsed_seconds),
                ),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
        }
    }
}
