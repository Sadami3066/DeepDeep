package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
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
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.SessionUsage
import java.io.File
import io.github.whywood.deepdeep.runtime.ThinkingLevel

@OptIn(ExperimentalFoundationApi::class)
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
    val onOpenSettings: () -> Unit,
    val onFollowUp: () -> Unit,
    val onClearQueue: () -> Unit,
    val onVoiceClick: (() -> Unit)?,
    val onModelsOpenChange: (Boolean) -> Unit,
)

@Composable
internal fun ChatComposer(
    state: ChatComposerState,
    actions: ChatComposerActions,
    usage: SessionUsage?,
    jobCount: Int,
    onOpenUsage: () -> Unit,
    onOpenJobs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (
        value, ready, sending, configured, modelLabel, modelId, models, providerId, providers,
        thinkingLevel, thinkingLevels, quote, attachments, attachmentRoot, queuedInputs,
        importingAttachment, modelsOpen,
    ) = state
    val (
        onValueChange, onSend, onStop, onModelChange, onProviderChange, onThinkingLevelChange,
        onClearQuote, onPickAttachment, onRemoveAttachment, onOpenAttachment, onOpenSettings,
        onFollowUp, onClearQueue, onVoiceClick, onModelsOpenChange,
    ) = actions
    val colors = deepDeepColors()
    val canSend = ready && configured && value.isNotBlank() && !importingAttachment
    val shape = RoundedCornerShape(28.dp)
    val label = modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val placeholder = when {
        !configured -> stringResource(R.string.assistant_configure_input)
        else -> stringResource(R.string.assistant_hint)
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var lastQuote by remember { mutableStateOf<QuotedMessage?>(null) }

    LaunchedEffect(quote) {
        if (quote != null) {
            lastQuote = quote
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0xFF0B1220),
                        spotColor = Color(0x3D0B1220),
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
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                ) {
                    Column {
                        if (queuedInputs.isNotEmpty()) {
                            QueueStrip(
                                queuedInputs = queuedInputs,
                                onClear = onClearQueue,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        if (importingAttachment) {
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
                                    onClick = onClearQuote,
                                    contentDescription = stringResource(R.string.assistant_quote_remove),
                                    modifier = Modifier.testTag("assistant_quote_clear"),
                                    size = 30.dp,
                                    iconSize = 14.dp,
                                    tint = colors.inkTertiary,
                                )
                            }
                        }
                        if (attachments.isNotEmpty()) {
                            AttachmentChips(
                                attachments = attachments,
                                root = attachmentRoot,
                                onRemove = onRemoveAttachment,
                                onOpen = onOpenAttachment,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ChatInputField(
                                value = value,
                                onValueChange = onValueChange,
                                placeholder = placeholder,
                                modifier = Modifier.weight(1f),
                                enabled = configured,
                                maxLines = 8,
                                visualTransformation = SlashCommandColor(colors.accent),
                                onSend = { if (canSend) onSend() },
                                focusRequester = focusRequester,
                                testTag = "assistant_input",
                            )
                            Row(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                JobsMeter(
                                    count = jobCount,
                                    onClick = onOpenJobs,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                if (usage != null && usage.contextWindow > 0) {
                                    ContextMeter(
                                        usedTokens = usage.contextTokens,
                                        windowTokens = usage.contextWindow,
                                        onClick = onOpenUsage,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
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
                    ComposerChip(
                        text = label,
                        onClick = {
                            if (configured) onModelsOpenChange(true) else onOpenSettings()
                        },
                        enabled = !sending,
                        contentDescription = stringResource(R.string.assistant_model_chip, label),
                        testTag = "assistant_model",
                        maxWidth = 150.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    val voiceClick = onVoiceClick
                    if (voiceClick != null && !sending) {
                        DdPressable(
                            onClick = voiceClick,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("assistant_voice"),
                            shape = CircleShape,
                            background = colors.wash,
                            contentDescription = stringResource(R.string.voice_open),
                        ) {
                            DdIcon(icon = DdIcons.Mic, tint = colors.inkSecondary, size = 18.dp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (sending) {
                        DdPressable(
                            onClick = onStop,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("assistant_stop"),
                            shape = CircleShape,
                            background = colors.wash,
                            contentDescription = stringResource(R.string.assistant_stop),
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
                    val sendDescription = stringResource(
                        if (sending) R.string.assistant_send_steer else R.string.assistant_send,
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colors.accent else colors.wash)
                            .combinedClickable(
                                enabled = canSend,
                                role = Role.Button,
                                onClick = onSend,
                                onLongClick = { if (sending && canSend) onFollowUp() },
                            )
                            .semantics { contentDescription = sendDescription }
                            .testTag("assistant_send"),
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
        if (!configured) {
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
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }

    if (modelsOpen) {
        ModelPickerSheet(
            providerId = providerId,
            modelId = modelId,
            providers = providers,
            models = models,
            thinkingLevel = thinkingLevel,
            thinkingLevels = thinkingLevels,
            onDismiss = { onModelsOpenChange(false) },
            onProviderChange = onProviderChange,
            onModelChange = onModelChange,
            onThinkingLevelChange = onThinkingLevelChange,
            onOpenSettings = onOpenSettings,
        )
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
