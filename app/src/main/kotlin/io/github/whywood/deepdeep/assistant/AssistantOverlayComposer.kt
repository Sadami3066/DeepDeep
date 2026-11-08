package io.github.whywood.deepdeep.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.ComposerVoice
import io.github.whywood.deepdeep.ui.common.VoiceRecordingBar
import io.github.whywood.deepdeep.ui.common.VoiceTranscriptPreview
import io.github.whywood.deepdeep.ui.common.chat.ChatInputField
import io.github.whywood.deepdeep.ui.common.chat.ComposerChip
import io.github.whywood.deepdeep.ui.common.chat.ComposerMetrics
import io.github.whywood.deepdeep.ui.common.chat.ModelPickerSheet
import io.github.whywood.deepdeep.ui.common.chat.QueuedInputBuffer
import io.github.whywood.deepdeep.ui.common.chat.state.mergeDroppedInputText



private enum class OverlayComposerSurface { Collapsed, Expanded, Recording }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AssistantOverlayComposer(
    state: AssistantOverlayState,
    value: String,
    onValueChange: (String) -> Unit,
    voice: ComposerVoice,
    onSubmit: (Boolean) -> Unit,
    actions: AssistantOverlayActions,
    glow: OverlayGlow,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = deepDeepColors()
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = state.conversation.sending
    val canSend = value.isNotBlank() && state.conversation.loaded
    var showModelSheet by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var pendingExpand by remember { mutableStateOf(false) }
    val recording = voice.listening || voice.stopping
    val expanded = pendingExpand ||
        focused ||
        value.isNotBlank() ||
        recording ||
        state.attachments.isNotEmpty() ||
        state.conversation.queuedInputs.isNotEmpty() ||
        state.importingAttachment ||
        busy ||
        state.error != null
    val surface = when {
        recording -> OverlayComposerSurface.Recording
        expanded -> OverlayComposerSurface.Expanded
        else -> OverlayComposerSurface.Collapsed
    }
    val micDescription = stringResource(R.string.assistant_overlay_mic)
    val attachDescription = stringResource(R.string.assistant_attach_add)
    val sendDescription = stringResource(
        if (busy) R.string.assistant_send_steer else R.string.assistant_overlay_send,
    )
    val collapsedInset =
        ComposerMetrics.CollapsedHorizontalPadding - ComposerMetrics.ExpandedHorizontalPadding
    val horizontalInset by animateDpAsState(
        targetValue = if (surface == OverlayComposerSurface.Collapsed) collapsedInset else 0.dp,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "overlay-composer-inset",
    )

    LaunchedEffect(pendingExpand) {
        if (!pendingExpand) return@LaunchedEffect
        focusRequester?.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset.coerceAtLeast(0.dp)),
            ) {
                VoiceTranscriptPreview(
                    buffer = voice.buffer,
                    partial = voice.partial,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                AnimatedContent(
                    targetState = recording,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(DdMotion.FAST)) togetherWith fadeOut(tween(DdMotion.FAST)))
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ ->
                                        tween(DdMotion.BASE, easing = DdMotion.EaseOut)
                                    },
                                ),
                            )
                    },
                    label = "overlay-composer-surface",
                ) { isRecording ->
                if (isRecording) {
                    VoiceRecordingBar(
                        volume = voice.volume,
                        stopping = voice.stopping,
                        canSend = voice.buffer.isNotBlank() || value.isNotBlank(),
                        onCancel = voice.cancel,
                        onStop = voice.stop,
                        onSend = voice.send,
                    )
                } else {
                    OverlayComposerCard(
                        glow = glow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = {
                                (
                                    fadeIn(tween(DdMotion.FAST)) togetherWith
                                        fadeOut(tween(DdMotion.FAST))
                                    ).using(
                                    SizeTransform(
                                        clip = false,
                                        sizeAnimationSpec = { _, _ ->
                                            tween(DdMotion.BASE, easing = DdMotion.EaseOut)
                                        },
                                    ),
                                )
                            },
                            contentAlignment = Alignment.BottomCenter,
                            label = "overlay-composer-content",
                        ) { isExpanded ->
                            if (isExpanded) {
                                AssistantOverlayExpandedContent(
                                    state = state,
                                    value = value,
                                    onValueChange = onValueChange,
                                    busy = busy,
                                    canSend = canSend,
                                    micDescription = micDescription,
                                    attachDescription = attachDescription,
                                    sendDescription = sendDescription,
                                    onMicClick = voice.start,
                                    onSubmit = onSubmit,
                                    actions = actions,
                                    onFocusChanged = { isFocused ->
                                        focused = isFocused
                                        if (isFocused) pendingExpand = false
                                    },
                                    onOpenModels = { showModelSheet = true },
                                    focusRequester = focusRequester,
                                )
                            } else {
                                AssistantOverlayCollapsedBar(
                                    state = state,
                                    canSend = canSend,
                                    micDescription = micDescription,
                                    attachDescription = attachDescription,
                                    sendDescription = sendDescription,
                                    onMicClick = voice.start,
                                    onSubmit = onSubmit,
                                    actions = actions,
                                    onExpand = { pendingExpand = true },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providerId = state.providerId,
            modelId = state.modelId,
            configured = state.providers.any { it.id == state.providerId },
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
            onManageProviders = {
                showModelSheet = false
                state.sessionId?.let(actions.onOpenInApp)
            },
        )
    }
}

@Composable
private fun AssistantOverlayExpandedContent(
    state: AssistantOverlayState,
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    canSend: Boolean,
    micDescription: String,
    attachDescription: String,
    sendDescription: String,
    onMicClick: () -> Unit,
    onSubmit: (Boolean) -> Unit,
    actions: AssistantOverlayActions,
    onFocusChanged: (Boolean) -> Unit,
    onOpenModels: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val colors = deepDeepColors()
    val modelLabel = state.modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        ) {
            Column {
                val queued = state.conversation.queuedInputs
                if (queued.isNotEmpty()) {
                    QueuedInputBuffer(
                        queuedInputs = queued,
                        onRemove = actions.onRemoveQueued,
                        onSend = actions.onSendQueued,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChanged(it.isFocused) },
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
                onClick = onOpenModels,
                contentDescription = stringResource(R.string.assistant_model_sheet),
                testTag = "assistant_overlay_model_chip",
                maxWidth = 150.dp,
            )
            Spacer(Modifier.weight(1f))
            OverlayMicButton(
                contentDescription = micDescription,
                onClick = onMicClick,
                modifier = Modifier.testTag("assistant_overlay_mic"),
            )
            if (busy) {
                Spacer(Modifier.width(6.dp))
                OverlayStopButton(onClick = actions.onStop)
            }
            Spacer(Modifier.width(6.dp))
            OverlaySendButton(
                canSend = canSend,
                busy = busy,
                contentDescription = sendDescription,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun AssistantOverlayCollapsedBar(
    state: AssistantOverlayState,
    canSend: Boolean,
    micDescription: String,
    attachDescription: String,
    sendDescription: String,
    onMicClick: () -> Unit,
    onSubmit: (Boolean) -> Unit,
    actions: AssistantOverlayActions,
    onExpand: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerMetrics.CollapsedHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand,
            )
            .padding(start = 8.dp, end = 8.dp)
            .testTag("assistant_overlay_collapsed"),
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
        Text(
            text = stringResource(R.string.assistant_overlay_input_hint),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("assistant_overlay_collapsed_hint"),
            style = DdType.body,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OverlayMicButton(
            contentDescription = micDescription,
            onClick = onMicClick,
            modifier = Modifier.testTag("assistant_overlay_mic"),
        )
        Spacer(Modifier.width(6.dp))
        OverlaySendButton(
            canSend = canSend,
            busy = false,
            contentDescription = sendDescription,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun OverlayMicButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.size(ComposerMetrics.ActionButtonSize),
        contentDescription = contentDescription,
    ) {
        DdIcon(
            icon = DdIcons.Mic,
            tint = colors.inkSecondary,
            size = ComposerMetrics.ActionIconSize,
        )
    }
}

@Composable
private fun OverlayStopButton(onClick: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.size(34.dp).testTag("assistant_overlay_stop"),
        shape = CircleShape,
        background = colors.accent,
        contentDescription = stringResource(R.string.assistant_overlay_stop),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.onAccent),
        )
    }
}

@Composable
private fun OverlaySendButton(
    canSend: Boolean,
    busy: Boolean,
    contentDescription: String,
    onSubmit: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    Box(
        modifier = Modifier
            .size(ComposerMetrics.ActionButtonSize)
            .clip(CircleShape)
            .background(if (canSend) colors.accent else colors.wash)
            .combinedClickable(
                enabled = canSend,
                role = Role.Button,
                onClick = { onSubmit(false) },
                onLongClick = { if (busy) onSubmit(true) },
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag("assistant_overlay_send"),
        contentAlignment = Alignment.Center,
    ) {
        DdIcon(
            icon = DdIcons.ArrowUp,
            tint = if (canSend) colors.onAccent else colors.inkTertiary,
            size = ComposerMetrics.ActionIconSize,
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
