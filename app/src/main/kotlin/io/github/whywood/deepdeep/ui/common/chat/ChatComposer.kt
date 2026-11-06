package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.common.ComposerVoice
import io.github.whywood.deepdeep.ui.common.VoiceRecordingBar
import io.github.whywood.deepdeep.ui.common.VoiceTranscriptPreview
import io.github.whywood.deepdeep.ui.common.rememberComposerVoice
import io.github.whywood.deepdeep.ui.common.voiceFailureText
import io.github.whywood.deepdeep.ui.navigation.PluginComposerActionRow
import java.io.File



private enum class ComposerSurface { Collapsed, Expanded, Recording }

data class ChatComposerState(
    val value: String,
    val ready: Boolean,
    val sending: Boolean,
    val configured: Boolean,
    val modelLabel: String,
    val modelId: String,
    val models: List<ModelOption>,
    val providerId: String,
    val providers: List<ProviderSummary>,
    val thinkingLevel: ThinkingLevel,
    val thinkingLevels: List<ThinkingLevel>,
    val quote: QuotedMessage?,
    val attachments: List<AttachmentRef>,
    val attachmentRoot: File,
    val queuedInputs: List<QueuedInput>,
    val importingAttachment: Boolean,
    val modelsOpen: Boolean,
)

data class ChatComposerActions(
    val onValueChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onModelChange: (String) -> Unit,
    val onProviderChange: (String) -> Unit,
    val onThinkingLevelChange: (ThinkingLevel) -> Unit,
    val onClearQuote: () -> Unit,
    val onPickAttachment: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onOpenAttachment: (AttachmentRef) -> Unit,
    val onManageProviders: () -> Unit,
    val onFollowUp: () -> Unit,
    val onClearQueue: () -> Unit,
    val onModelsOpenChange: (Boolean) -> Unit,
    val onOpenPluginPage: (String, String) -> Unit,
)

@Composable
internal fun ChatComposer(
    state: ChatComposerState,
    actions: ChatComposerActions,
    voiceLanguage: AppLanguage = AppLanguage.SYSTEM,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val value = state.value
    val label = state.modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val placeholder = when {
        !state.configured -> stringResource(R.string.assistant_configure_input)
        else -> stringResource(R.string.assistant_hint)
    }
    val canSend = state.ready && state.configured && value.isNotBlank() && !state.importingAttachment
    val stops = state.sending && value.isBlank()
    val sendDescription = when {
        stops -> stringResource(R.string.assistant_stop)
        state.sending -> stringResource(R.string.assistant_send_steer)
        else -> stringResource(R.string.assistant_send)
    }
    var focused by remember { mutableStateOf(false) }
    val focusScale = remember { Animatable(1f) }
    var pendingExpand by remember { mutableStateOf(false) }
    var committedVoice by remember { mutableStateOf("") }

    val voice = rememberComposerVoice(
        language = voiceLanguage,
        onCommit = { text ->
            val next = if (value.isBlank()) text else value.trimEnd() + " " + text
            committedVoice = next
            actions.onValueChange(next)
        },
        onSendRequested = {
            val next = committedVoice.ifBlank { value }
            if (next.isNotBlank()) {
                actions.onValueChange(next)
                actions.onSend()
            }
        },
    )
    val recording = voice.listening || voice.stopping
    val expanded = pendingExpand ||
        focused ||
        value.isNotBlank() ||
        state.quote != null ||
        state.attachments.isNotEmpty() ||
        state.queuedInputs.isNotEmpty() ||
        state.importingAttachment ||
        state.sending ||
        voice.failure != null
    val surface = when {
        recording -> ComposerSurface.Recording
        expanded -> ComposerSurface.Expanded
        else -> ComposerSurface.Collapsed
    }
    val startVoice: () -> Unit = {
        committedVoice = ""
        voice.start()
    }
    val onInputFocusChanged: (Boolean) -> Unit = { isFocused ->
        focused = isFocused
        if (isFocused) pendingExpand = false
    }

    LaunchedEffect(state.quote) {
        if (state.quote != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(pendingExpand) {
        if (!pendingExpand) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
        if (focused) pendingExpand = false
    }

    LaunchedEffect(recording) {
        if (recording) {
            focused = false
            keyboard?.hide()
        }
    }

    LaunchedEffect(focused) {
        if (!focused || !chatAnimationsEnabled()) {
            focusScale.snapTo(1f)
            return@LaunchedEffect
        }
        focusScale.animateTo(1.03f, tween(durationMillis = 120, easing = DdMotion.EaseOut))
        focusScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val collapsedInset =
        ComposerMetrics.CollapsedHorizontalPadding - ComposerMetrics.ExpandedHorizontalPadding
    val horizontalInset by animateDpAsState(
        targetValue = if (surface == ComposerSurface.Collapsed) collapsedInset else 0.dp,
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "composer-inset",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (surface == ComposerSurface.Collapsed) {
            ComposerMetrics.CollapsedShapeRadius
        } else {
            ComposerMetrics.ShapeRadius
        },
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "composer-corner",
    )
    val shape = RoundedCornerShape(cornerRadius)

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset),
            ) {
                VoiceTranscriptPreview(
                    buffer = voice.buffer,
                    partial = voice.partial,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                AnimatedContent(
                    targetState = surface,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(durationMillis = 160, easing = DdMotion.EaseOut)) togetherWith
                            fadeOut(tween(durationMillis = 120, easing = DdMotion.EaseOut)))
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ ->
                                        tween(
                                            durationMillis = DdMotion.BASE,
                                            easing = DdMotion.EaseOut,
                                        )
                                    },
                                ),
                            )
                    },
                    label = "composer-surface",
                ) { current ->
                when (current) {
                    ComposerSurface.Recording -> VoiceRecordingBar(
                        volume = voice.volume,
                        stopping = voice.stopping,
                        canSend = voice.buffer.isNotBlank() || value.isNotBlank(),
                        onCancel = voice.cancel,
                        onStop = voice.stop,
                        onSend = voice.send,
                    )

                    ComposerSurface.Expanded -> ComposerCard(
                        shape = shape,
                        focused = focused,
                        scale = focusScale.value,
                    ) {
                        ChatComposerExpandedContent(
                            state = state,
                            actions = actions,
                            voice = voice,
                            label = label,
                            placeholder = placeholder,
                            canSend = canSend,
                            stops = stops,
                            sendDescription = sendDescription,
                            focusRequester = focusRequester,
                            onStartVoice = startVoice,
                            onInputFocusChanged = onInputFocusChanged,
                        )
                    }

                    ComposerSurface.Collapsed -> ComposerCard(shape) {
                        ChatComposerCollapsedContent(
                            placeholder = placeholder,
                            canSend = canSend,
                            sendDescription = sendDescription,
                            onExpand = {
                                if (state.configured || state.providers.isNotEmpty()) {
                                    pendingExpand = true
                                } else {
                                    actions.onManageProviders()
                                }
                            },
                            onPickAttachment = actions.onPickAttachment,
                            onStartVoice = startVoice,
                            onSend = actions.onSend,
                        )
                    }
                }
                }
            }
        }
        if (state.providers.isEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.assistant_configure_hint),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.weight(1f))
                    DdTextAction(
                        text = stringResource(R.string.assistant_open_settings),
                        onClick = actions.onManageProviders,
                    )
                }
            }
        }
    }

    if (state.modelsOpen) {
        ModelPickerSheet(
            providerId = state.providerId,
            modelId = state.modelId,
            configured = state.configured,
            providers = state.providers,
            models = state.models,
            thinkingLevel = state.thinkingLevel,
            thinkingLevels = state.thinkingLevels,
            onDismiss = { actions.onModelsOpenChange(false) },
            onProviderChange = actions.onProviderChange,
            onModelChange = actions.onModelChange,
            onThinkingLevelChange = actions.onThinkingLevelChange,
            onManageProviders = actions.onManageProviders,
        )
    }
}

@Composable
private fun ComposerCard(
    shape: RoundedCornerShape,
    focused: Boolean = false,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val elevation by animateDpAsState(
        targetValue = if (focused) 15.dp else 10.dp,
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "composer-elevation",
    )
    val spotAlpha by animateFloatAsState(
        targetValue = if (focused) 0.34f else 0.24f,
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "composer-spot",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF0B1220),
                spotColor = Color(0xFF0B1220).copy(alpha = spotAlpha),
            )
            .clip(shape)
            .background(colors.composer)
            .then(
                if (colors.paper.luminance() < 0.5f) {
                    Modifier.border(1.dp, colors.ink.copy(alpha = 0.08f), shape)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

@Composable
private fun ChatComposerCollapsedContent(
    placeholder: String,
    canSend: Boolean,
    sendDescription: String,
    onExpand: () -> Unit,
    onPickAttachment: () -> Unit,
    onStartVoice: () -> Unit,
    onSend: () -> Unit,
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
            .testTag("assistant_collapsed"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIconButton(
            icon = DdIcons.Plus,
            onClick = onPickAttachment,
            contentDescription = stringResource(R.string.assistant_attach_add),
            modifier = Modifier.testTag("assistant_attach"),
            size = 34.dp,
            iconSize = 17.dp,
            tint = colors.inkTertiary,
        )
        Text(
            text = placeholder,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("assistant_collapsed_hint"),
            style = DdType.body,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ComposerMicButton(onClick = onStartVoice)
        Spacer(Modifier.width(6.dp))
        ComposerSendButton(
            canSend = canSend,
            contentDescription = sendDescription,
            onClick = onSend,
        )
    }
}

@Composable
private fun ChatComposerExpandedContent(
    state: ChatComposerState,
    actions: ChatComposerActions,
    voice: ComposerVoice,
    label: String,
    placeholder: String,
    canSend: Boolean,
    stops: Boolean,
    sendDescription: String,
    focusRequester: FocusRequester,
    onStartVoice: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val quote = state.quote
    var lastQuote by remember { mutableStateOf<QuotedMessage?>(null) }

    LaunchedEffect(quote) {
        if (quote != null) lastQuote = quote
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Column {
            if (state.queuedInputs.isNotEmpty()) {
                QueueStrip(
                    queuedInputs = state.queuedInputs,
                    onClear = actions.onClearQueue,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            if (state.importingAttachment) {
                Text(
                    text = stringResource(R.string.assistant_importing_image),
                    modifier = Modifier.padding(bottom = 8.dp).testTag("assistant_importing"),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                )
            }
            AnimatedVisibility(
                visible = quote != null,
                enter = fadeIn(tween(DdMotion.FAST)) +
                    expandVertically(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(tween(DdMotion.FAST)) +
                    shrinkVertically(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                        shrinkTowards = Alignment.Top,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("assistant_quote_preview"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.hairline),
                        )
                        Text(
                            text = (quote ?: lastQuote)?.text.orEmpty(),
                            modifier = Modifier.padding(start = 10.dp),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DdIconButton(
                        icon = DdIcons.Close,
                        onClick = actions.onClearQuote,
                        contentDescription = stringResource(R.string.assistant_quote_remove),
                        modifier = Modifier.testTag("assistant_quote_clear"),
                        size = 30.dp,
                        iconSize = 14.dp,
                        tint = colors.inkTertiary,
                    )
                }
            }
            if (state.attachments.isNotEmpty()) {
                AttachmentChips(
                    attachments = state.attachments,
                    root = state.attachmentRoot,
                    onRemove = actions.onRemoveAttachment,
                    onOpen = actions.onOpenAttachment,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            ChatInputField(
                value = state.value,
                onValueChange = { next ->
                    voice.clearFailure()
                    actions.onValueChange(next)
                },
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onInputFocusChanged(it.isFocused) },
                enabled = state.configured,
                maxLines = 8,
                visualTransformation = SlashCommandColor(colors.accent),
                focusRequester = focusRequester,
                testTag = "assistant_input",
            )
            val failure = voice.failure
            AnimatedVisibility(
                visible = failure != null,
                enter = fadeIn(tween(DdMotion.FAST)) +
                    expandVertically(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(tween(DdMotion.FAST)) +
                    shrinkVertically(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                        shrinkTowards = Alignment.Top,
                    ),
            ) {
                if (failure != null) {
                    Text(
                        text = voiceFailureText(failure),
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 2.dp)
                            .testTag("assistant_voice_status"),
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = colors.danger,
                    )
                }
            }
        }
    }
    PluginComposerActionRow(
        onOpenPluginPage = actions.onOpenPluginPage,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp),
    )
    AnimatedVisibility(
        visible = state.sending,
        enter = fadeIn(tween(DdMotion.FAST)),
        exit = fadeOut(tween(DdMotion.FAST)),
    ) {
        Text(
            text = stringResource(R.string.assistant_ux_running_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                .testTag("assistant_running_hint"),
            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.inkTertiary,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIconButton(
            icon = DdIcons.Plus,
            onClick = actions.onPickAttachment,
            contentDescription = stringResource(R.string.assistant_attach_add),
            modifier = Modifier.testTag("assistant_attach"),
            size = 34.dp,
            iconSize = 17.dp,
            tint = colors.inkTertiary,
        )
        ComposerChip(
            text = label,
            onClick = {
                if (state.configured || state.providers.isNotEmpty()) {
                    actions.onModelsOpenChange(true)
                } else {
                    actions.onManageProviders()
                }
            },
            enabled = !state.sending,
            contentDescription = stringResource(R.string.assistant_model_chip, label),
            testTag = "assistant_model",
            maxWidth = 150.dp,
        )
        Spacer(Modifier.weight(1f))
        ComposerMicButton(onClick = onStartVoice)
        if (state.sending) {
            Spacer(Modifier.width(6.dp))
            ComposerMoreButton(
                showQueue = !stops && !state.importingAttachment,
                onFollowUp = actions.onFollowUp,
                onStop = actions.onStop,
            )
        }
        Spacer(Modifier.width(6.dp))
        ComposerSendButton(
            canSend = canSend,
            contentDescription = sendDescription,
            onClick = actions.onSend,
            stops = stops,
            onStop = actions.onStop,
        )
    }
}

@Composable
private fun ComposerMicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.size(ComposerMetrics.ActionButtonSize).testTag("assistant_voice"),
        contentDescription = stringResource(R.string.voice_open),
    ) {
        DdIcon(
            icon = DdIcons.Mic,
            tint = colors.inkSecondary,
            size = ComposerMetrics.ActionIconSize,
        )
    }
}

@Composable
private fun ComposerMoreButton(
    showQueue: Boolean,
    onFollowUp: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = deepDeepColors()
    var open by remember { mutableStateOf(false) }
    Box {
        DdIconButton(
            icon = DdIcons.DotsVertical,
            onClick = { open = true },
            contentDescription = stringResource(R.string.assistant_ux_more),
            modifier = Modifier.testTag("assistant_more"),
            size = 34.dp,
            iconSize = 17.dp,
            tint = colors.inkTertiary,
        )
        DdMenu(
            expanded = open,
            onDismissRequest = { open = false },
            menuWidth = 180.dp,
        ) {
            if (showQueue) {
                DdMenuItem(
                    text = stringResource(R.string.assistant_ux_queue_send),
                    onClick = {
                        open = false
                        onFollowUp()
                    },
                    testTag = "assistant_menu_queue",
                )
            }
            DdMenuItem(
                text = stringResource(R.string.assistant_stop),
                onClick = {
                    open = false
                    onStop()
                },
                testTag = "assistant_menu_stop",
                destructive = true,
            )
        }
    }
}

@Composable
private fun ComposerSendButton(
    canSend: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stops: Boolean = false,
    onStop: () -> Unit = {},
) {
    val colors = deepDeepColors()
    val enabled = canSend || stops
    Box(
        modifier = modifier
            .size(ComposerMetrics.ActionButtonSize)
            .clip(CircleShape)
            .background(
                when {
                    stops -> colors.danger.copy(alpha = 0.14f)
                    canSend -> colors.accent
                    else -> colors.wash
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = { if (stops) onStop() else onClick() },
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag("assistant_send"),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stops,
            transitionSpec = {
                fadeIn(tween(DdMotion.FAST)) togetherWith fadeOut(tween(DdMotion.FAST))
            },
            label = "composer-primary-icon",
        ) { stopping ->
            DdIcon(
                icon = if (stopping) DdIcons.Stop else DdIcons.ArrowUp,
                tint = when {
                    stopping -> colors.danger
                    canSend -> colors.onAccent
                    else -> colors.inkTertiary
                },
                size = ComposerMetrics.ActionIconSize,
            )
        }
    }
}

@Composable
internal fun QueueStrip(
    queuedInputs: List<QueuedInput>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            queuedInputs.forEach { input ->
                val label = stringResource(
                    if (input.kind == InputQueueKind.STEER) R.string.assistant_queue_steer
                    else R.string.assistant_queue_follow_up,
                )
                Box(
                    modifier = Modifier
                        .testTag("assistant_queue_chip")
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.wash)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = label + " · " + input.text.replace(Regex("\\s+"), " ").trim().let { text ->
                            if (text.length <= 40) text else text.take(40).trimEnd() + "…"
                        },
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onClear,
            contentDescription = stringResource(R.string.assistant_queue_clear),
            modifier = Modifier.testTag("assistant_queue_clear"),
            size = 28.dp,
            iconSize = 13.dp,
            tint = colors.inkTertiary,
        )
    }
}

@Composable
internal fun ComposerChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    maxWidth: androidx.compose.ui.unit.Dp,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.height(32.dp).testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
        contentDescription = contentDescription,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = maxWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = text,
                style = DdType.chip,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 12.dp)
        }
    }
}

@Composable
internal fun thinkingLabel(level: ThinkingLevel): String = stringResource(
    when (level) {
        ThinkingLevel.OFF -> R.string.assistant_thinking_off
        ThinkingLevel.LOW -> R.string.assistant_thinking_low
        ThinkingLevel.MEDIUM -> R.string.assistant_thinking_medium
        ThinkingLevel.HIGH -> R.string.assistant_thinking_high
        ThinkingLevel.MAX -> R.string.assistant_thinking_max
    },
)

private class SlashCommandColor(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val match = SLASH_COMMAND.find(text.text) ?: return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private companion object {
        val SLASH_COMMAND = Regex("^/[A-Za-z0-9:_-]+")
    }
}
