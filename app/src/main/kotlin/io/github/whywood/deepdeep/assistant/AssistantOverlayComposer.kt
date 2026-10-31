package io.github.whywood.deepdeep.assistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.ChatInputField
import io.github.whywood.deepdeep.ui.chat.ComposerChip
import io.github.whywood.deepdeep.ui.chat.ModelPickerSheet
import io.github.whywood.deepdeep.ui.chat.QueueStrip
import io.github.whywood.deepdeep.ui.chat.thinkingLabel
import io.github.whywood.deepdeep.ui.chat.state.mergeDroppedInputText
import androidx.compose.foundation.verticalScroll

private sealed interface OverlaySheet {
    data object Workspace : OverlaySheet
    data class WorkspaceSessions(val workspaceId: String) : OverlaySheet
    data object Session : OverlaySheet
    data object Model : OverlaySheet
    data object Thinking : OverlaySheet
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AssistantOverlayComposer(
    state: AssistantOverlayState,
    value: String,
    onValueChange: (String) -> Unit,
    listening: Boolean,
    onToggleMic: () -> Unit,
    onSubmit: (Boolean) -> Unit,
    config: AssistantOverlayConfig,
    actions: AssistantOverlayActions,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(28.dp)
    val busy = state.conversation.sending
    val canSend = value.isNotBlank() && state.conversation.loaded
    var sheet by remember { mutableStateOf<OverlaySheet?>(null) }
    val workspaceLabel = state.workspaces.firstOrNull { it.id == state.workspaceId }?.name
        ?: stringResource(R.string.workspace_section)
    val sessionLabel = state.sessionTitle.ifBlank { stringResource(R.string.assistant_overlay_session) }
    val modelLabel = state.modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val micDescription = stringResource(R.string.assistant_overlay_mic)
    val sendDescription = stringResource(
        if (busy) R.string.assistant_send_steer else R.string.assistant_overlay_send,
    )

    LaunchedEffect(sheet) {
        if (sheet != null) actions.onRefreshLists()
    }

    Column(
        modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF0B1220),
                spotColor = Color(0x3D0B1220),
            )
            .clip(shape)
            .background(colors.surface)
            .then(
                if (colors.paper.luminance() < 0.5f) {
                    Modifier.border(1.dp, colors.ink.copy(alpha = 0.08f), shape)
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
            val queued = state.conversation.queuedInputs
            if (queued.isNotEmpty()) {
                QueueStrip(
                    queuedInputs = queued,
                    onClear = {
                        val dropped = actions.onClearQueue()
                        if (dropped.isNotEmpty()) onValueChange(mergeDroppedInputText(value, dropped))
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                DdPressable(
                    onClick = onToggleMic,
                    modifier = Modifier.size(36.dp).testTag("assistant_overlay_mic"),
                    shape = CircleShape,
                    background = if (listening) colors.accentSoft else colors.wash,
                    contentDescription = micDescription,
                ) {
                    DdIcon(
                        icon = if (listening) DdIcons.AudioLines else DdIcons.Mic,
                        tint = if (listening) colors.accent else colors.inkSecondary,
                        size = 18.dp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    ChatInputField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = stringResource(R.string.assistant_overlay_input_hint),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        focusRequester = focusRequester,
                        testTag = "assistant_overlay_input",
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (busy) {
                    DdPressable(
                        onClick = { actions.onStop() },
                        modifier = Modifier.size(36.dp).testTag("assistant_overlay_stop"),
                        shape = CircleShape,
                        background = colors.wash,
                        contentDescription = stringResource(R.string.assistant_overlay_stop),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.danger),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSend) colors.accent else colors.wash)
                        .combinedClickable(
                            enabled = canSend,
                            role = Role.Button,
                            onClick = { onSubmit(false) },
                            onLongClick = { if (busy) onSubmit(true) },
                        )
                        .semantics { contentDescription = sendDescription }
                        .testTag("assistant_overlay_send"),
                    contentAlignment = Alignment.Center,
                ) {
                    DdIcon(
                        icon = DdIcons.ArrowUp,
                        tint = if (canSend) colors.onAccent else colors.inkTertiary,
                        size = 18.dp,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerChip(
                text = workspaceLabel,
                onClick = { sheet = OverlaySheet.Workspace },
                enabled = true,
                contentDescription = stringResource(R.string.assistant_overlay_workspace),
                testTag = "assistant_overlay_workspace_chip",
                maxWidth = 130.dp,
            )
            ComposerChip(
                text = sessionLabel,
                onClick = { sheet = OverlaySheet.Session },
                enabled = true,
                contentDescription = stringResource(R.string.assistant_overlay_session),
                testTag = "assistant_overlay_session_chip",
                maxWidth = 170.dp,
            )
            ComposerChip(
                text = modelLabel,
                onClick = { sheet = OverlaySheet.Model },
                enabled = !busy,
                contentDescription = stringResource(R.string.assistant_model_sheet),
                testTag = "assistant_overlay_model_chip",
                maxWidth = 150.dp,
            )
            if (state.thinkingLevels.size > 1) {
                ComposerChip(
                    text = thinkingLabel(state.thinkingLevel),
                    onClick = { sheet = OverlaySheet.Thinking },
                    enabled = !busy,
                    contentDescription = stringResource(R.string.assistant_thinking_sheet),
                    testTag = "assistant_overlay_thinking_chip",
                    maxWidth = 110.dp,
                )
            }
        }
    }

    when (val current = sheet) {
        OverlaySheet.Workspace -> WorkspaceSheet(
            state = state,
            onDismiss = { sheet = null },
            onSelectWorkspace = { workspaceId ->
                actions.onSelectWorkspace(workspaceId)
                sheet = OverlaySheet.WorkspaceSessions(workspaceId)
            },
        )

        is OverlaySheet.WorkspaceSessions -> SessionSheet(
            state = state,
            sessions = state.sessions,
            title = state.workspaces.firstOrNull { it.id == current.workspaceId }?.name
                ?: stringResource(R.string.workspace_section),
            showBack = true,
            onBack = { sheet = OverlaySheet.Workspace },
            onDismiss = { sheet = null },
            onOpenSession = { sessionId ->
                sheet = null
                actions.onOpenSession(sessionId)
            },
            onNewSession = {
                sheet = null
                actions.onNewSession()
            },
        )

        OverlaySheet.Session -> SessionSheet(
            state = state,
            sessions = state.sessions,
            title = stringResource(R.string.assistant_overlay_session),
            showBack = false,
            onBack = {},
            onDismiss = { sheet = null },
            onOpenSession = { sessionId ->
                sheet = null
                actions.onOpenSession(sessionId)
            },
            onNewSession = {
                sheet = null
                actions.onNewSession()
            },
        )

        OverlaySheet.Model -> ModelPickerSheet(
            providerId = state.providerId,
            modelId = state.modelId,
            providers = state.providers,
            models = state.models,
            thinkingLevel = state.thinkingLevel,
            thinkingLevels = state.thinkingLevels,
            onDismiss = { sheet = null },
            onProviderChange = { providerId ->
                sheet = null
                actions.onSelectProvider(providerId)
            },
            onModelChange = { modelId ->
                sheet = null
                actions.onSelectModel(modelId)
            },
            onThinkingLevelChange = { level ->
                sheet = null
                actions.onSelectThinking(level)
            },
            onOpenSettings = {
                sheet = null
                state.sessionId?.let(actions.onOpenInApp)
            },
        )

        OverlaySheet.Thinking -> ThinkingSheet(
            levels = state.thinkingLevels,
            selected = state.thinkingLevel,
            onDismiss = { sheet = null },
            onSelect = { level ->
                sheet = null
                actions.onSelectThinking(level)
            },
        )

        null -> Unit
    }
}

@Composable
private fun WorkspaceSheet(
    state: AssistantOverlayState,
    onDismiss: () -> Unit,
    onSelectWorkspace: (String) -> Unit,
) {
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = stringResource(R.string.workspace_section), onClose = onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            state.workspaces.forEachIndexed { index, workspace ->
                DdListRow(
                    title = workspace.name,
                    subtitle = workspace.cwd,
                    onClick = { onSelectWorkspace(workspace.id) },
                    trailing = {
                        if (workspace.id == state.workspaceId) {
                            DdIcon(icon = DdIcons.Check, tint = deepDeepColors().accent, size = 18.dp)
                        }
                    },
                    modifier = Modifier.testTag("assistant_overlay_workspace_${workspace.id}"),
                )
                if (index != state.workspaces.lastIndex) DdHairline()
            }
        }
    }
}

@Composable
private fun SessionSheet(
    state: AssistantOverlayState,
    sessions: List<ConversationSummary>,
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = title, onClose = onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            if (showBack) {
                DdListRow(
                    title = stringResource(R.string.assistant_overlay_back),
                    icon = DdIcons.ArrowLeft,
                    onClick = onBack,
                    modifier = Modifier.testTag("assistant_overlay_sheet_back"),
                )
                DdHairline()
            }
            DdListRow(
                title = stringResource(R.string.drawer_workspace_new_session),
                icon = DdIcons.Plus,
                onClick = onNewSession,
                modifier = Modifier.testTag("assistant_overlay_sheet_new_session"),
            )
            DdHairline()
            sessions.forEachIndexed { index, session ->
                DdListRow(
                    title = session.title.ifBlank { stringResource(R.string.assistant_overlay_session) },
                    onClick = { onOpenSession(session.id) },
                    trailing = {
                        if (session.id == state.sessionId) {
                            DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                        }
                    },
                    modifier = Modifier.testTag("assistant_overlay_session_${session.id}"),
                )
                if (index != sessions.lastIndex) DdHairline()
            }
        }
    }
}

@Composable
private fun ThinkingSheet(
    levels: List<ThinkingLevel>,
    selected: ThinkingLevel,
    onDismiss: () -> Unit,
    onSelect: (ThinkingLevel) -> Unit,
) {
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = stringResource(R.string.assistant_thinking_sheet), onClose = onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            levels.forEach { level ->
                DdListRow(
                    title = thinkingLabel(level),
                    onClick = { onSelect(level) },
                    trailing = {
                        if (level == selected) {
                            DdIcon(icon = DdIcons.Check, tint = deepDeepColors().accent, size = 18.dp)
                        }
                    },
                )
            }
        }
    }
}
