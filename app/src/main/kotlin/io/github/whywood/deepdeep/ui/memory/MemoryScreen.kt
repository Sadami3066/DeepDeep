package io.github.whywood.deepdeep.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
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
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = if (preview != null) preview.title else stringResource(R.string.memory_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
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
            .padding(horizontal = DdSpacing.Xxl),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        if (status == null) {
            DdLoadingBlock()
            return@Column
        }
        val zones = listOf(
            MemoryOpenRequest(stringResource(R.string.memory_profile), "profile") to status.profileChars,
            MemoryOpenRequest(stringResource(R.string.memory_context), "context") to status.contextChars,
            MemoryOpenRequest(stringResource(R.string.memory_cookbook), "cookbook") to status.cookbookChars,
        )
        DdGroupCard {
            zones.forEach { (request, chars) ->
                DdListRow(
                    title = request.title,
                    value = stringResource(R.string.memory_chars, chars),
                    onClick = { onOpen(request) },
                    showChevron = true,
                )
            }
        }
        Spacer(Modifier.height(DdSpacing.Lg))
        val timeline = listOf(
            MemoryOpenRequest(stringResource(R.string.memory_today), "today") to status.todayChars,
            MemoryOpenRequest(stringResource(R.string.memory_month), "month") to status.monthChars,
            MemoryOpenRequest(stringResource(R.string.memory_year), "year") to status.yearChars,
        )
        DdGroupCard {
            timeline.forEach { (request, chars) ->
                DdListRow(
                    title = request.title,
                    value = stringResource(R.string.memory_chars, chars),
                    onClick = { onOpen(request) },
                    showChevron = true,
                )
            }
        }
        if (recentDays.isNotEmpty()) {
            Spacer(Modifier.height(DdSpacing.Xxl))
            DdSectionLabel(stringResource(R.string.memory_recent_days))
            Spacer(Modifier.height(DdSpacing.Sm))
            DdGroupCard {
                recentDays.forEach { day ->
                    DdListRow(
                        title = day,
                        onClick = { onOpen(MemoryOpenRequest(day, "today", day)) },
                        showChevron = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(DdSpacing.Xxl))
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
        Spacer(Modifier.height(DdSpacing.Lg))
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
        Spacer(Modifier.height(DdSpacing.Xxl))
    }
}

@Composable
private fun MemoryPreviewPanel(preview: MemoryPreview, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Xxl),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        when {
            preview.loading -> DdLoadingBlock()
            preview.content != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DdShapes.Large)
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = DdSpacing.Lg),
            ) {
                ChatMarkdown(text = preview.content!!)
            }
            else -> DdEmptyState(stringResource(R.string.memory_empty))
        }
        Spacer(Modifier.height(DdSpacing.Xxl))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
