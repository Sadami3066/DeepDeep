package io.github.whywood.deepdeep.assistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.chat.ChatInputField
import io.github.whywood.deepdeep.ui.chat.ComposerChip
import io.github.whywood.deepdeep.ui.chat.ModelPickerSheet
import io.github.whywood.deepdeep.ui.chat.QueueStrip
import io.github.whywood.deepdeep.ui.chat.state.mergeDroppedInputText

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
    val busy = state.conversation.sending
    val canSend = value.isNotBlank() && state.conversation.loaded
    var showModelSheet by remember { mutableStateOf(false) }
    val modelLabel = state.modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val micDescription = stringResource(R.string.assistant_overlay_mic)
    val sendDescription = stringResource(
        if (busy) R.string.assistant_send_steer else R.string.assistant_overlay_send,
    )

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            OverlayComposerCard(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                ) {
                    Column {
                        val queued = state.conversation.queuedInputs
                        if (queued.isNotEmpty()) {
                            QueueStrip(
                                queuedInputs = queued,
                                onClear = {
                                    val dropped = actions.onClearQueue()
                                    if (dropped.isNotEmpty()) {
                                        onValueChange(mergeDroppedInputText(value, dropped))
                                    }
                                },
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
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
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerChip(
                        text = modelLabel,
                        onClick = { showModelSheet = true },
                        enabled = !busy,
                        contentDescription = stringResource(R.string.assistant_model_sheet),
                        testTag = "assistant_overlay_model_chip",
                        maxWidth = 150.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    DdPressable(
                        onClick = onToggleMic,
                        modifier = Modifier.size(38.dp).testTag("assistant_overlay_mic"),
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
                    if (busy) {
                        Spacer(Modifier.width(6.dp))
                        DdPressable(
                            onClick = { actions.onStop() },
                            modifier = Modifier.size(34.dp).testTag("assistant_overlay_stop"),
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
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
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
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providerId = state.providerId,
            modelId = state.modelId,
            providers = state.providers,
            models = state.models,
            thinkingLevel = state.thinkingLevel,
            thinkingLevels = state.thinkingLevels,
            onDismiss = { showModelSheet = false },
            onProviderChange = { providerId ->
                showModelSheet = false
                actions.onSelectProvider(providerId)
            },
            onModelChange = { modelId ->
                showModelSheet = false
                actions.onSelectModel(modelId)
            },
            onThinkingLevelChange = { level ->
                showModelSheet = false
                actions.onSelectThinking(level)
            },
            onOpenSettings = {
                showModelSheet = false
                state.sessionId?.let(actions.onOpenInApp)
            },
        )
    }
}
