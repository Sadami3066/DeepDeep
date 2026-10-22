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
import kotlinx.coroutines.delay

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
    Row(
        modifier = modifier.heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdTypingDots(color = colors.inkTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = activity ?: stringResource(R.string.assistant_working),
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
                text = stringResource(R.string.assistant_elapsed_seconds, elapsedSeconds),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
        }
    }
}
