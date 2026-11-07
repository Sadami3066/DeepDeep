package io.github.whywood.deepdeep.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdLoadingBlock
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.memory.MemoryStatus
import io.github.whywood.deepdeep.ui.common.chat.ChatMarkdown
import java.util.Locale

internal data class MemoryPreview(
    val title: String,
    val loading: Boolean,
    val content: String?,
)

internal data class MemoryOpenRequest(
    val title: String,
    val target: String,
    val date: String? = null,
)

@Composable
internal fun MemoryScreen(
    status: MemoryStatus?,
    recentDays: List<String>,
    busy: Boolean,
    notice: String?,
    canSummarize: Boolean,
    preview: MemoryPreview?,
    onOpen: (MemoryOpenRequest) -> Unit,
    onClosePreview: () -> Unit,
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
                onClick = if (preview != null) onClosePreview else onBack,
                contentDescription = stringResource(R.string.memory_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier.testTag("memory_page"),
    ) {
        if (preview != null) {
            MemoryPreviewPanel(preview, Modifier.weight(1f))
        } else {
            MemoryWorkbench(
                modifier = Modifier.weight(1f),
                status = status,
                recentDays = recentDays,
                busy = busy,
                notice = notice,
                canSummarize = canSummarize,
                onOpen = onOpen,
                onSummarize = onSummarize,
                onRebuild = onRebuild,
                onRefresh = onRefresh,
            )
        }
    }
}

@Composable
private fun MemoryWorkbench(
    status: MemoryStatus?,
    recentDays: List<String>,
    busy: Boolean,
    notice: String?,
    canSummarize: Boolean,
    onOpen: (MemoryOpenRequest) -> Unit,
    onSummarize: () -> Unit,
    onRebuild: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
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
            DdLoadingBlock()
            return@Column
        }
        val zones = listOf(
            Triple(
                stringResource(R.string.memory_profile),
                DdIcons.Brain,
                MemoryOpenRequest(stringResource(R.string.memory_profile), "profile"),
            ) to status.profileChars,
            Triple(
                stringResource(R.string.memory_context),
                DdIcons.Sparkles,
                MemoryOpenRequest(stringResource(R.string.memory_context), "context"),
            ) to status.contextChars,
            Triple(
                stringResource(R.string.memory_cookbook),
                DdIcons.Book,
                MemoryOpenRequest(stringResource(R.string.memory_cookbook), "cookbook"),
            ) to status.cookbookChars,
        )
        DdGroupCard {
            zones.forEach { (entry, chars) ->
                val (title, icon, request) = entry
                DdListRow(
                    title = title,
                    value = stringResource(R.string.memory_chars, chars),
                    icon = icon,
                    onClick = { onOpen(request) },
                    showChevron = true,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val timeline = listOf(
            Triple(
                stringResource(R.string.memory_today),
                DdIcons.Sun,
                MemoryOpenRequest(stringResource(R.string.memory_today), "today"),
            ) to status.todayChars,
            Triple(
                stringResource(R.string.memory_month),
                DdIcons.Folder,
                MemoryOpenRequest(stringResource(R.string.memory_month), "month"),
            ) to status.monthChars,
            Triple(
                stringResource(R.string.memory_year),
                DdIcons.Clock,
                MemoryOpenRequest(stringResource(R.string.memory_year), "year"),
            ) to status.yearChars,
        )
        DdGroupCard {
            timeline.forEach { (entry, chars) ->
                val (title, icon, request) = entry
                DdListRow(
                    title = title,
                    value = stringResource(R.string.memory_chars, chars),
                    icon = icon,
                    onClick = { onOpen(request) },
                    showChevron = true,
                )
            }
        }
        if (recentDays.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            DdSectionLabel(stringResource(R.string.memory_recent_days))
            Spacer(Modifier.height(8.dp))
            DdGroupCard {
                recentDays.forEach { day ->
                    DdListRow(
                        title = day,
                        icon = DdIcons.File,
                        onClick = { onOpen(MemoryOpenRequest(day, "today", day)) },
                        showChevron = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                R.string.memory_footer,
                status.indexedChunks,
                formatBytes(status.totalBytes),
            ),
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.inkTertiary,
        )
        if (notice != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = notice!!,
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MemoryPreviewPanel(preview: MemoryPreview, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preview.title,
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        when {
            preview.loading -> DdLoadingBlock()
            preview.content != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                ChatMarkdown(text = preview.content!!)
            }
            else -> DdEmptyState(stringResource(R.string.memory_empty))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
