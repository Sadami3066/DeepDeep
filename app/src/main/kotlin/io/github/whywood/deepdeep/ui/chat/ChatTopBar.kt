package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.SessionUsage

data class ChatTopBarState(
    val reminderCount: Int,
    val reloading: Boolean,
    val usage: SessionUsage?,
    val jobCount: Int,
)

data class ChatTopBarActions(
    val onMenu: () -> Unit,
    val onReload: () -> Unit,
    val onOpenTimers: () -> Unit,
    val onOpenUsage: () -> Unit,
    val onOpenJobs: () -> Unit,
    val onOpenFiles: () -> Unit = {},
    val onExportSession: () -> Unit = {},
)

private val TopBarEdge = 6.dp
private val TopBarGap = 4.dp
private val TopBarIconButtonSize = 42.dp
private val WordmarkMaxSize = 27f
private val WordmarkMinSize = 18f

@Composable
internal fun ChatTopBar(
    state: ChatTopBarState,
    actions: ChatTopBarActions,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var menuOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        val compact = maxWidth < 370.dp || LocalDensity.current.fontScale > 1.15f
        val crowded = state.jobCount > 0 || state.reminderCount > 0
        val density = LocalDensity.current
        var leadingWidthPx by remember { mutableStateOf(0) }
        var trailingWidthPx by remember { mutableStateOf(0) }
        val leadingWidth = with(density) { leadingWidthPx.toDp() }
        val trailingWidth = with(density) { trailingWidthPx.toDp() }
        val wordmarkStyle = wordmarkStyle(maxWidth, leadingWidth, trailingWidth, compact)
        Box(Modifier.fillMaxSize()) {
            DdWordmark(
                modifier = Modifier.align(Alignment.Center),
                color = colors.ink,
                dotColor = colors.accent,
                style = wordmarkStyle,
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = TopBarEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        leadingWidthPx = coordinates.size.width
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIconButton(
                        icon = DdIcons.Menu,
                        onClick = actions.onMenu,
                        contentDescription = stringResource(R.string.assistant_menu),
                        modifier = Modifier.testTag("assistant_menu"),
                        size = TopBarIconButtonSize,
                        iconSize = 20.dp,
                        tint = colors.inkSecondary,
                    )
                    JobsMeter(
                        count = state.jobCount,
                        onClick = actions.onOpenJobs,
                    )
                    if (state.reminderCount > 0) {
                        DdIconButton(
                            icon = DdIcons.Clock,
                            onClick = actions.onOpenTimers,
                            contentDescription = stringResource(R.string.assistant_reminders),
                            modifier = Modifier.testTag("assistant_reminders"),
                            size = 36.dp,
                            iconSize = 16.dp,
                            tint = colors.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        trailingWidthPx = coordinates.size.width
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.reloading) {
                        DdSpinner(
                            modifier = Modifier.padding(end = 4.dp).testTag("assistant_reloading"),
                            size = 16.dp,
                            strokeWidth = 1.5.dp,
                        )
                    }
                    val usage = state.usage
                    if (usage != null && usage.contextWindow > 0L) {
                        ContextMeter(
                            usedTokens = usage.contextTokens,
                            windowTokens = usage.contextWindow,
                            onClick = actions.onOpenUsage,
                            showPercent = !compact && !crowded,
                        )
                    }
                    Box {
                        DdIconButton(
                            icon = DdIcons.DotsVertical,
                            onClick = { menuOpen = true },
                            contentDescription = stringResource(R.string.assistant_more),
                            modifier = Modifier.testTag("assistant_more"),
                            size = TopBarIconButtonSize,
                            iconSize = 19.dp,
                            tint = colors.inkSecondary,
                        )
                        DdMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            menuWidth = 148.dp,
                        ) {
                            DdMenuItem(
                                text = stringResource(R.string.files_menu),
                                icon = DdIcons.Folder,
                                onClick = {
                                    menuOpen = false
                                    actions.onOpenFiles()
                                },
                                testTag = "assistant_files",
                            )
                            DdMenuItem(
                                text = stringResource(R.string.assistant_export_session),
                                icon = DdIcons.Download,
                                onClick = {
                                    menuOpen = false
                                    actions.onExportSession()
                                },
                                testTag = "chat_export",
                            )
                            DdMenuItem(
                                text = stringResource(R.string.assistant_reload),
                                icon = DdIcons.Refresh,
                                onClick = {
                                    menuOpen = false
                                    actions.onReload()
                                },
                                testTag = "assistant_reload",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun wordmarkStyle(
    maxWidth: Dp,
    leadingWidth: Dp,
    trailingWidth: Dp,
    compact: Boolean,
): TextStyle {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseWidth = with(density) {
        measurer.measure("deepdeep.", DdType.wordmark).size.width.toDp()
    }
    val fontSize = wordmarkFontSize(maxWidth, leadingWidth, trailingWidth, baseWidth, compact)
    return DdType.wordmark.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 5f).sp,
    )
}

internal fun wordmarkFontSize(
    maxWidth: Dp,
    leadingWidth: Dp,
    trailingWidth: Dp,
    baseWidth: Dp,
    compact: Boolean,
): Float {
    if (compact) return WordmarkMinSize
    val occupied = maxOf(leadingWidth, trailingWidth)
    val budget = ((maxWidth / 2 - TopBarEdge - occupied - TopBarGap) * 2).coerceAtLeast(0.dp)
    return when {
        baseWidth <= 0.dp || budget >= baseWidth -> WordmarkMaxSize
        else -> (WordmarkMaxSize * (budget / baseWidth)).coerceAtLeast(WordmarkMinSize)
    }
}
