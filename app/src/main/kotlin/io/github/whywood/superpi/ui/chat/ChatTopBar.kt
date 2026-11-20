package io.github.whywood.superpi.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.design.DdIconButton
import io.github.whywood.superpi.design.DdIcons
import io.github.whywood.superpi.design.DdMenu
import io.github.whywood.superpi.design.DdMenuItem
import io.github.whywood.superpi.design.DdSpacing
import io.github.whywood.superpi.design.DdSpinner
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import io.github.whywood.superpi.runtime.SessionUsage
import io.github.whywood.superpi.ui.common.BotAvatar

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
    val onOpenBotSettings: (String) -> Unit = {},
)

private val TopBarEdge = 6.dp
private val TopBarIconButtonSize = 42.dp

@Composable
internal fun ChatTopBar(
    state: ChatTopBarState,
    actions: ChatTopBarActions,
    modifier: Modifier = Modifier,
    activeBot: RuntimeWorkspace? = null,
) {
    val colors = superPiColors()
    var menuOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        val compact = maxWidth < 370.dp || LocalDensity.current.fontScale > 1.15f
        val crowded = state.jobCount > 0 || state.reminderCount > 0
        val botTitleMaxWidth = maxWidth * 0.52f
        Box(Modifier.fillMaxSize()) {
            if (activeBot != null) {
                val remote = activeBot.remote
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = botTitleMaxWidth)
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (remote == null) {
                                Modifier.clickable { actions.onOpenBotSettings(activeBot.id) }
                            } else {
                                Modifier
                            },
                        )
                        .testTag("assistant_bot"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BotAvatar(workspace = activeBot, size = 24.dp)
                    if (remote == null) {
                        Text(
                            text = activeBot.name,
                            style = DdType.label.copy(fontSize = 16.sp),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Column {
                            Text(
                                text = remote.botName,
                                style = DdType.label.copy(fontSize = 15.sp, lineHeight = 19.sp),
                                color = if (remote.online) colors.ink else colors.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (remote.online) {
                                    remote.deviceName
                                } else {
                                    stringResource(R.string.workspace_remote_offline_annotation, remote.deviceName)
                                },
                                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                color = colors.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = TopBarEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.reloading) {
                        DdSpinner(
                            modifier = Modifier.padding(end = DdSpacing.Xs).testTag("assistant_reloading"),
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
                            if (activeBot?.remote == null) {
                                DdMenuItem(
                                    text = stringResource(R.string.files_menu),
                                    icon = DdIcons.Folder,
                                    onClick = {
                                        menuOpen = false
                                        actions.onOpenFiles()
                                    },
                                    testTag = "assistant_files",
                                )
                            }
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
