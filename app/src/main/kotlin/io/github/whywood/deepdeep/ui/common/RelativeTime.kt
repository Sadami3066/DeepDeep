package io.github.whywood.deepdeep.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R

@Composable
fun rememberRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1L -> stringResource(R.string.relative_just_now)
        minutes < 60L -> stringResource(R.string.relative_minutes, minutes.toInt())
        minutes < 24L * 60L -> stringResource(R.string.relative_hours, (minutes / 60L).toInt())
        minutes < 48L * 60L -> stringResource(R.string.relative_yesterday)
        else -> stringResource(R.string.relative_days, (minutes / (24L * 60L)).toInt())
    }
}
