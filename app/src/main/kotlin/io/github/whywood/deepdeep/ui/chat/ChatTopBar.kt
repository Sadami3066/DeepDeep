package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdSpinner
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
    val onOpenSchedules: () -> Unit,
    val onOpenUsage: () -> Unit,
    val onOpenJobs: () -> Unit,
    val onOpenFiles: () -> Unit = {},
)

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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.Menu,
                onClick = actions.onMenu,
                contentDescription = stringResource(R.string.assistant_menu),
                modifier = Modifier.testTag("assistant_menu"),
                tint = colors.inkSecondary,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(2.dp))
            Spacer(Modifier.weight(1f))
            if (state.reloading) {
                DdSpinner(
                    modifier = Modifier.padding(end = 6.dp).testTag("assistant_reloading"),
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
                    showPercent = !compact,
                )
            }
            JobsMeter(
                count = state.jobCount,
                onClick = actions.onOpenJobs,
            )
            if (state.reminderCount > 0) {
                DdIconButton(
                    icon = DdIcons.Clock,
                    onClick = actions.onOpenSchedules,
                    contentDescription = stringResource(R.string.assistant_reminders),
                    modifier = Modifier.testTag("assistant_reminders"),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Box {
                DdIconButton(
                    icon = DdIcons.DotsVertical,
                    onClick = { menuOpen = true },
                    contentDescription = stringResource(R.string.assistant_more),
                    modifier = Modifier.testTag("assistant_more"),
                    tint = colors.inkSecondary,
                    iconSize = 21.dp,
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
