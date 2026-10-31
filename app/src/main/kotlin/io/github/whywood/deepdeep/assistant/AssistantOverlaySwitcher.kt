package io.github.whywood.deepdeep.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace

internal sealed interface OverlaySwitcherLevel {
    data object Workspaces : OverlaySwitcherLevel
    data class Sessions(val workspaceId: String) : OverlaySwitcherLevel
}

@Composable
internal fun AssistantOverlaySwitcher(
    state: AssistantOverlayState,
    actions: AssistantOverlayActions,
    visible: Boolean,
    level: OverlaySwitcherLevel,
    width: Dp,
    onClose: () -> Unit,
    onLevelChange: (OverlaySwitcherLevel) -> Unit,
) {
    val colors = deepDeepColors()

    LaunchedEffect(visible) {
        if (visible) actions.onRefreshLists()
    }
    BackHandler(enabled = visible) { onClose() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(DdMotion.FAST)),
            exit = fadeOut(tween(DdMotion.BASE)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.30f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(DdMotion.SHEET, easing = DdMotion.EaseOut),
                initialOffsetX = { it },
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = slideOutHorizontally(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseInOut),
                targetOffsetX = { it },
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            Column(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(colors.raised)
                    .testTag("assistant_overlay_switcher"),
            ) {
                SwitcherHeader(
                    level = level,
                    state = state,
                    onBack = { onLevelChange(OverlaySwitcherLevel.Workspaces) },
                    onClose = onClose,
                )
                DdHairline()
                when (level) {
                    OverlaySwitcherLevel.Workspaces -> WorkspaceSwitcher(
                        state = state,
                        onSelectWorkspace = { workspaceId ->
                            onLevelChange(OverlaySwitcherLevel.Sessions(workspaceId))
                        },
                        modifier = Modifier.weight(1f),
                    )

                    is OverlaySwitcherLevel.Sessions -> SessionSwitcher(
                        state = state,
                        workspace = state.workspaces.firstOrNull { it.id == level.workspaceId },
                        onOpenSession = { sessionId ->
                            onClose()
                            actions.onOpenSession(sessionId)
                        },
                        onNewSession = {
                            onClose()
                            actions.onNewSessionInWorkspace(level.workspaceId)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitcherHeader(
    level: OverlaySwitcherLevel,
    state: AssistantOverlayState,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    val title = when (level) {
        OverlaySwitcherLevel.Workspaces -> stringResource(R.string.workspace_section)
        is OverlaySwitcherLevel.Sessions -> state.workspaces
            .firstOrNull { it.id == level.workspaceId }
            ?.name
            ?: stringResource(R.string.assistant_overlay_session)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level is OverlaySwitcherLevel.Sessions) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.assistant_overlay_back),
                modifier = Modifier.testTag("assistant_overlay_switcher_back"),
                size = 36.dp,
                iconSize = 18.dp,
                tint = colors.inkSecondary,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (level is OverlaySwitcherLevel.Sessions) 4.dp else 12.dp),
            style = DdType.label.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onClose,
            contentDescription = stringResource(R.string.assistant_close),
            modifier = Modifier.testTag("assistant_overlay_switcher_close"),
            size = 36.dp,
            iconSize = 17.dp,
            tint = colors.inkSecondary,
        )
    }
}

@Composable
private fun WorkspaceSwitcher(
    state: AssistantOverlayState,
    onSelectWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        state.workspaces.forEachIndexed { index, workspace ->
            DdListRow(
                title = workspace.name,
                subtitle = workspace.cwd,
                icon = DdIcons.Folder,
                onClick = { onSelectWorkspace(workspace.id) },
                trailing = {
                    if (workspace.id == state.workspaceId) {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                    }
                },
                modifier = Modifier.testTag("assistant_overlay_switcher_workspace_${workspace.id}"),
            )
            if (index != state.workspaces.lastIndex) DdHairline()
        }
    }
}

@Composable
private fun SessionSwitcher(
    state: AssistantOverlayState,
    workspace: RuntimeWorkspace?,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val sessions = remember(state.allSessions, workspace) {
        state.allSessions
            .filter { workspace != null && it.cwd == workspace.cwd }
            .sortedByDescending(ConversationSummary::updatedAt)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        DdListRow(
            title = stringResource(R.string.drawer_workspace_new_session),
            icon = DdIcons.Plus,
            onClick = onNewSession,
            modifier = Modifier.testTag("assistant_overlay_switcher_new_session"),
        )
        DdHairline()
        if (sessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.assistant_overlay_switcher_empty),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                sessions.forEachIndexed { index, session ->
                    DdListRow(
                        title = session.title.ifBlank {
                            stringResource(R.string.assistant_overlay_session)
                        },
                        onClick = { onOpenSession(session.id) },
                        trailing = {
                            if (session.id == state.sessionId) {
                                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                            }
                        },
                        modifier = Modifier.testTag("assistant_overlay_switcher_session_${session.id}"),
                    )
                    if (index != sessions.lastIndex) DdHairline()
                }
            }
        }
    }
}
