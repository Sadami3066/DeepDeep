package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.SessionUsage
import java.util.Locale

private const val COMPACTION_RESERVE_TOKENS = 16_384L

@Composable
fun UsageSheet(usage: SessionUsage, onDismiss: () -> Unit) {
    val colors = deepDeepColors()
    val progress = if (usage.contextWindow > 0L) {
        (usage.contextTokens.toDouble() / usage.contextWindow.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val threshold = (usage.contextWindow - COMPACTION_RESERVE_TOKENS).coerceAtLeast(0L)
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(R.string.usage_title),
            onClose = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.Xxl),
        ) {
            Spacer(Modifier.height(DdSpacing.Sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.usage_context),
                    modifier = Modifier.weight(1f),
                    style = DdType.label,
                    color = colors.inkSecondary,
                )
                Text(
                    text = stringResource(
                        R.string.usage_context_value,
                        formatContextTokens(usage.contextTokens),
                        formatContextTokens(usage.contextWindow),
                    ),
                    style = DdType.numeric,
                    color = colors.ink,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.wash),
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
            Spacer(Modifier.height(DdSpacing.Sm))
            Text(
                text = stringResource(
                    R.string.usage_compact_threshold,
                    formatContextTokens(threshold),
                ),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            Text(
                text = stringResource(R.string.usage_cumulative),
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(DdSpacing.Sm))
            UsageStatRow(
                label = stringResource(R.string.usage_input),
                value = formatTokens(usage.inputTokens),
            )
            DdHairline()
            UsageStatRow(
                label = stringResource(R.string.usage_output),
                value = formatTokens(usage.outputTokens),
            )
            DdHairline()
            UsageStatRow(
                label = stringResource(R.string.usage_cache_read),
                value = formatTokens(usage.cacheReadTokens),
            )
            DdHairline()
            UsageStatRow(
                label = stringResource(R.string.usage_cache_write),
                value = formatTokens(usage.cacheWriteTokens),
            )
            DdHairline()
            UsageStatRow(
                label = stringResource(R.string.usage_cost),
                value = stringResource(R.string.usage_cost_value, formatCost(usage.cost)),
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            Text(
                text = stringResource(R.string.usage_footnote),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun UsageStatRow(label: String, value: String) {
    val colors = deepDeepColors()
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = DdType.numeric,
            color = colors.ink,
            maxLines = 1,
        )
    }
}

internal fun formatTokens(value: Long): String = when {
    value < 1_000L -> value.toString()
    value < 1_000_000L -> {
        val thousands = value / 1_000.0
        if (thousands < 10.0) {
            String.format(Locale.US, "%.1fK", thousands)
        } else {
            "${value / 1_000L}K"
        }
    }

    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
}

private fun formatContextTokens(value: Long): String = when {
    value < 1_000L -> value.toString()
    value < 1_000_000L -> compact(value / 1_000.0) + "K"
    else -> compact(value / 1_000_000.0) + "M"
}

private fun compact(value: Double): String =
    String.format(Locale.US, "%.1f", value).removeSuffix(".0")

private fun formatCost(cost: Double): String = String.format(Locale.US, "%.3f", cost)
