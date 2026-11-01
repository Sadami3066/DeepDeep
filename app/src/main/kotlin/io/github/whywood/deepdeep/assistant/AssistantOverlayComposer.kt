package io.github.whywood.deepdeep.assistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
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
    glow: OverlayGlow,
    modifier: Modifier = Modifier,
    onMicPressStart: () -> Unit = {},
    onMicPressEnd: (heldLong: Boolean) -> Unit = { _ -> },
    onMicCancel: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val colors = deepDeepColors()
    val busy = state.conversation.sending
    val canSend = value.isNotBlank() && state.conversation.loaded
    var showModelSheet by remember { mutableStateOf(false) }
    val modelLabel = state.modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val micDescription = stringResource(R.string.assistant_overlay_mic)
    val attachDescription = stringResource(R.string.assistant_attach_add)
    val pressStart by rememberUpdatedState(onMicPressStart)
    val pressEnd by rememberUpdatedState(onMicPressEnd)
    val pressCancel by rememberUpdatedState(onMicCancel)
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
                glow = glow,
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
                        if (state.attachments.isNotEmpty()) {
                            AssistantOverlayAttachments(
                                attachments = state.attachments,
                                onOpen = actions.onOpenPreview,
                                onRemove = actions.onRemoveAttachment,
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
                    if (state.importingAttachment) {
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            DdSpinner(size = 14.dp, color = colors.inkTertiary)
                        }
                    } else {
                        DdIconButton(
                            icon = DdIcons.Plus,
                            onClick = actions.onPickAttachment,
                            contentDescription = attachDescription,
                            modifier = Modifier.testTag("assistant_overlay_attach"),
                            size = 34.dp,
                            iconSize = 17.dp,
                            tint = colors.inkTertiary,
                        )
                    }
                    ComposerChip(
                        text = modelLabel,
                        onClick = { showModelSheet = true },
                        enabled = !busy,
                        contentDescription = stringResource(R.string.assistant_model_sheet),
                        testTag = "assistant_overlay_model_chip",
                        maxWidth = 150.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (listening) colors.accentSoft else colors.wash)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    pressStart()
                                    val early = withTimeoutOrNull(280) {
                                        waitForUpOrCancellation() != null
                                    }
                                    when (early) {
                                        null -> {
                                            if (waitForUpOrCancellation() != null) {
                                                pressEnd(true)
                                            } else {
                                                pressCancel()
                                            }
                                        }

                                        true -> pressEnd(false)
                                        false -> pressCancel()
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = micDescription
                                role = Role.Button
                                onClick {
                                    onToggleMic()
                                    true
                                }
                            }
                            .testTag("assistant_overlay_mic"),
                        contentAlignment = Alignment.Center,
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

@Composable
private fun AssistantOverlayAttachments(
    attachments: List<AttachmentRef>,
    onOpen: (AttachmentRef) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.wash)
                    .clickable { onOpen(attachment) }
                    .padding(start = 8.dp, end = 4.dp)
                    .testTag("assistant_overlay_attachment_${attachment.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdIcon(
                    icon = if (attachment.kind == AttachmentKind.IMAGE) DdIcons.Image else DdIcons.File,
                    tint = colors.inkSecondary,
                    size = 14.dp,
                )
                Text(
                    text = attachment.name,
                    modifier = Modifier.padding(horizontal = 6.dp).widthIn(max = 140.dp),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(attachment.id) }
                        .testTag("assistant_overlay_attachment_remove_${attachment.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    DdIcon(icon = DdIcons.Close, tint = colors.inkTertiary, size = 12.dp)
                }
            }
        }
    }
}
