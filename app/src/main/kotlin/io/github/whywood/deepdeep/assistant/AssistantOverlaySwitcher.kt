package io.github.whywood.deepdeep.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.common.rememberRelativeTime

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
    val density = LocalDensity.current

    LaunchedEffect(visible) {
        if (visible) actions.onRefreshLists()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardMaxHeight = minOf(396.dp, maxHeight * 0.58f)
        val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val scrimColor = colors.scrim.copy(alpha = if (colors.dark) 0.24f else 0.10f)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(DdMotion.SHEET)),
            exit = fadeOut(tween(DdMotion.BASE)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 68.dp, end = 12.dp),
            enter = fadeIn(tween(DdMotion.SHEET)) +
                scaleIn(
                    initialScale = 0.97f,
                    animationSpec = tween(DdMotion.SHEET, easing = DdMotion.EaseOut),
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideInHorizontally(
                    animationSpec = tween(DdMotion.SHEET, easing = DdMotion.EaseOut),
                    initialOffsetX = { it / 8 },
                ),
            exit = fadeOut(tween(DdMotion.BASE)),
        ) {
            SwitcherCard(
                state = state,
                level = level,
                width = width,
                maxHeight = cardMaxHeight,
                onClose = onClose,
                onLevelChange = onLevelChange,
                onOpenSession = { sessionId ->
                    onClose()
                    actions.onOpenSession(sessionId)
                },
                onNewSession = { workspaceId ->
                    onClose()
                    actions.onNewSessionInWorkspace(workspaceId)
                },
            )
        }
    }
}

@Composable
private fun SwitcherCard(
    state: AssistantOverlayState,
    level: OverlaySwitcherLevel,
    width: Dp,
    maxHeight: Dp,
    onClose: () -> Unit,
    onLevelChange: (OverlaySwitcherLevel) -> Unit,
    onOpenSession: (String) -> Unit,
    onNewSession: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(22.dp)
    val workspace = when (level) {
        OverlaySwitcherLevel.Workspaces -> null
        is OverlaySwitcherLevel.Sessions -> state.workspaces.firstOrNull { it.id == level.workspaceId }
    }
    val title = when (level) {
        OverlaySwitcherLevel.Workspaces -> stringResource(R.string.workspace_section)
        is OverlaySwitcherLevel.Sessions -> workspace?.name
            ?: stringResource(R.string.assistant_overlay_session)
    }
    Column(
        modifier = Modifier
            .width(width)
            .heightIn(max = maxHeight)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF071426),
                spotColor = Color(0x4D0B2A4A),
            )
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, colors.hairline, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("assistant_overlay_switcher"),
    ) {
        SwitcherHeader(
            title = title,
            showBack = level is OverlaySwitcherLevel.Sessions,
            onBack = { onLevelChange(OverlaySwitcherLevel.Workspaces) },
            onClose = onClose,
        )
        DdHairline()
        when (level) {
            OverlaySwitcherLevel.Workspaces -> WorkspaceList(
                state = state,
                onSelectWorkspace = { workspaceId ->
                    onLevelChange(OverlaySwitcherLevel.Sessions(workspaceId))
                },
                modifier = Modifier.weight(1f, fill = false),
            )

            is OverlaySwitcherLevel.Sessions -> {
                val workspaceId = level.workspaceId
                SessionList(
                    state = state,
                    workspace = workspace,
                    onOpenSession = onOpenSession,
                    onNewSession = { onNewSession(workspaceId) },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun SwitcherHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            DdPressable(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("assistant_overlay_switcher_back"),
                shape = CircleShape,
                contentDescription = stringResource(R.string.assistant_overlay_back),
            ) {
                DdIcon(icon = DdIcons.ArrowLeft, tint = colors.inkSecondary, size = 16.dp)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            style = DdType.label.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DdPressable(
            onClick = onClose,
            modifier = Modifier.size(32.dp).testTag("assistant_overlay_switcher_close"),
            shape = CircleShape,
            contentDescription = stringResource(R.string.assistant_close),
        ) {
            DdIcon(icon = DdIcons.Close, tint = colors.inkSecondary, size = 15.dp)
        }
    }
}

@Composable
private fun WorkspaceList(
    state: AssistantOverlayState,
    onSelectWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        state.workspaces.forEach { workspace ->
            WorkspaceRow(
                workspace = workspace,
                selected = workspace.id == state.workspaceId,
                onClick = { onSelectWorkspace(workspace.id) },
            )
        }
    }
}

@Composable
private fun WorkspaceRow(
    workspace: RuntimeWorkspace,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("assistant_overlay_switcher_workspace_${workspace.id}"),
        shape = RoundedCornerShape(14.dp),
        background = if (selected) colors.wash else Color.Transparent,
        contentDescription = workspace.name,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.surface else colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(
                    icon = DdIcons.Folder,
                    tint = colors.inkSecondary,
                    size = 15.dp,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = workspace.name,
                modifier = Modifier.weight(1f),
                style = DdType.label.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                DdIcon(icon = DdIcons.Check, tint = colors.inkSecondary, size = 15.dp)
                Spacer(Modifier.width(2.dp))
            }
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
        }
    }
}

@Composable
private fun SessionList(
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
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        NewSessionRow(onClick = onNewSession)
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.assistant_overlay_switcher_empty),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
            )
        } else {
            val scrollState = rememberScrollState()
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sessions.forEach { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == state.sessionId,
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
                if (scrollState.canScrollForward) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.raised),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ConversationSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val relativeTime = rememberRelativeTime(session.updatedAt)
    DdPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("assistant_overlay_switcher_session_${session.id}"),
        shape = RoundedCornerShape(14.dp),
        background = if (selected) colors.wash else Color.Transparent,
        contentDescription = session.title,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.title.ifBlank { stringResource(R.string.assistant_overlay_session) },
                modifier = Modifier.weight(1f),
                style = DdType.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (relativeTime.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime,
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                DdIcon(icon = DdIcons.Check, tint = colors.inkSecondary, size = 15.dp)
            }
        }
    }
}

@Composable
private fun NewSessionRow(onClick: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag("assistant_overlay_switcher_new_session"),
        shape = RoundedCornerShape(14.dp),
        contentDescription = stringResource(R.string.drawer_workspace_new_session),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Compose, tint = colors.inkSecondary, size = 15.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.drawer_workspace_new_session),
                style = DdType.label.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                color = colors.inkSecondary,
                maxLines = 1,
            )
        }
    }
}
