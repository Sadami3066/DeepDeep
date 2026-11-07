package io.github.whywood.deepdeep.ui.memory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.memory.MemoryStatus
import java.util.Locale

@Composable
internal fun MemoryScreen(
    status: MemoryStatus?,
    busy: Boolean,
    notice: String?,
    canSummarize: Boolean,
    onSummarize: () -> Unit,
    onRebuild: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.memory_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier.testTag("memory_page"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.memory_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            if (status == null) {
                Text(
                    text = stringResource(R.string.memory_loading),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                val snapshotText = status.snapshotAt?.let { at ->
                    buildString {
                        append(stringResource(R.string.memory_snapshot_value, at, status.snapshotReason))
                        if (status.dirty) {
                            append(" · ")
                            append(stringResource(R.string.memory_snapshot_dirty))
                        }
                    }
                } ?: stringResource(R.string.memory_snapshot_none)
                DdGroupCard {
                    DdListRow(
                        title = stringResource(R.string.memory_profile),
                        value = stringResource(R.string.memory_chars, status.profileChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_context),
                        value = stringResource(R.string.memory_chars, status.contextChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_cookbook),
                        value = stringResource(R.string.memory_chars, status.cookbookChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_today),
                        value = stringResource(R.string.memory_chars, status.todayChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_month),
                        value = stringResource(R.string.memory_chars, status.monthChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_year),
                        value = stringResource(R.string.memory_chars, status.yearChars),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_daily),
                        value = stringResource(R.string.memory_days, status.dayCount),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_index),
                        value = stringResource(
                            R.string.memory_index_value,
                            status.indexedChunks,
                            status.indexBuildMs,
                        ),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_snapshot),
                        value = snapshotText,
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_size),
                        value = formatBytes(status.totalBytes),
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_path),
                        subtitle = status.rootDir,
                        subtitleMaxLines = 2,
                    )
                }
                Spacer(Modifier.height(16.dp))
                DdGroupCard {
                    DdListRow(
                        title = stringResource(R.string.memory_summarize),
                        onClick = onSummarize,
                        enabled = !busy && canSummarize,
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_rebuild),
                        onClick = onRebuild,
                        enabled = !busy,
                    )
                    DdListRow(
                        title = stringResource(R.string.memory_refresh),
                        onClick = onRefresh,
                        enabled = !busy,
                    )
                }
            }
            if (notice != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = notice,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
