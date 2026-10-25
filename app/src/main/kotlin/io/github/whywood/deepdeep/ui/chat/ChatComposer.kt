package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderSummary
import java.io.File
import io.github.whywood.deepdeep.runtime.ThinkingLevel

@Composable
internal fun ChatComposer(
    value: String,
    ready: Boolean,
    sending: Boolean,
    configured: Boolean,
    modelLabel: String,
    modelId: String,
    models: List<ModelOption>,
    providerId: String,
    providers: List<ProviderSummary>,
    thinkingLevel: ThinkingLevel,
    thinkingLevels: List<ThinkingLevel>,
    quote: QuotedMessage?,
    attachments: List<AttachmentRef>,
    attachmentRoot: File,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onClearQuote: () -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    onOpenSettings: () -> Unit,
    onVoiceClick: (() -> Unit)? = null,
    modelsOpen: Boolean = false,
    onModelsOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showProviders by remember { mutableStateOf(false) }
    val canSend = ready && configured && value.isNotBlank() && !sending
    val shape = RoundedCornerShape(28.dp)
    val label = modelLabel.ifBlank { stringResource(R.string.assistant_model_unconfigured) }
    val placeholder = when {
        !configured -> stringResource(R.string.assistant_configure_input)
        else -> stringResource(R.string.assistant_hint)
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var lastQuote by remember { mutableStateOf<QuotedMessage?>(null) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    LaunchedEffect(quote) {
        if (quote != null) {
            lastQuote = quote
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.composer),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                ) {
                    Column {
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
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { next ->
                                textFieldValue = next
                                onValueChange(next.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .focusRequester(focusRequester)
                                .testTag("assistant_input"),
                            enabled = configured,
                            textStyle = DdType.body.copy(
                                fontSize = 16.sp,
                                lineHeight = 25.sp,
                                color = if (configured) colors.ink else colors.inkTertiary,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            visualTransformation = SlashCommandColor(colors.accent),
                            maxLines = 8,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                            decorationBox = { inner ->
                                Box {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = placeholder,
                                            style = DdType.body.copy(fontSize = 16.sp, lineHeight = 25.sp),
                                            color = colors.inkTertiary,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIconButton(
                        icon = DdIcons.Paperclip,
                        onClick = onPickAttachment,
                        contentDescription = stringResource(R.string.assistant_attach_add),
                        modifier = Modifier.testTag("assistant_attach"),
                        size = 34.dp,
                        iconSize = 17.dp,
                        tint = colors.inkTertiary,
                        enabled = !sending,
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
                    DdPressable(
                        onClick = { if (sending) onStop() else onSend() },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag(if (sending) "assistant_stop" else "assistant_send"),
                        shape = CircleShape,
                        background = if (canSend) colors.accent else colors.wash,
                        enabled = sending || canSend,
                        contentDescription = stringResource(
                            if (sending) R.string.assistant_stop else R.string.assistant_send,
                        ),
                    ) {
                        if (sending) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(colors.danger),
                            )
                        } else {
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
        DdSheet(onDismiss = { onModelsOpenChange(false) }) {
            DdSheetHeader(
                title = stringResource(R.string.assistant_model_sheet),
                onClose = { onModelsOpenChange(false) },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (providers.isNotEmpty()) {
                    val providerName = providers.firstOrNull { it.id == providerId }?.name ?: providerId
                    DdDropdown(
                        expanded = showProviders,
                        onDismissRequest = { showProviders = false },
                        anchor = {
                            DdListRow(
                                title = stringResource(R.string.assistant_model_provider),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = providerName,
                                            style = DdType.bodySmall,
                                            color = colors.inkTertiary,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                                    }
                                },
                                onClick = { showProviders = true },
                                modifier = Modifier.testTag("assistant_provider_row"),
                            )
                        },
                    ) {
                        providers.forEach { provider ->
                            DdMenuItem(
                                text = provider.name,
                                onClick = {
                                    showProviders = false
                                    onProviderChange(provider.id)
                                },
                                selected = provider.id == providerId,
                                trailing = if (provider.id == providerId) {
                                    {
                                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                                    }
                                } else {
                                    null
                                },
                                testTag = "assistant_provider_${provider.id}",
                            )
                        }
                    }
                    DdHairline()
                }
                if (models.isEmpty()) {
                    DdListRow(
                        title = stringResource(R.string.assistant_model_unconfigured),
                        onClick = null,
                        enabled = false,
                    )
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.assistant_open_settings),
                        onClick = {
                            onModelsOpenChange(false)
                            onOpenSettings()
                        },
                        showChevron = true,
                    )
                } else {
                    models.forEachIndexed { index, option ->
                        DdListRow(
                            title = option.displayName,
                            onClick = {
                                onModelsOpenChange(false)
                                onModelChange(option.id)
                            },
                            trailing = {
                                if (option.id == modelId) {
                                    DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
                                }
                            },
                        )
                        if (index != models.lastIndex) DdHairline()
                    }
                }
                if (thinkingLevels.any { it != ThinkingLevel.OFF }) {
                    Spacer(Modifier.height(20.dp))
                    DdSectionLabel(stringResource(R.string.assistant_thinking_sheet))
                    Spacer(Modifier.height(4.dp))
                    thinkingLevels.forEachIndexed { index, level ->
                        DdListRow(
                            title = thinkingLabel(level),
                            onClick = {
                                onModelsOpenChange(false)
                                onThinkingLevelChange(level)
                            },
                            trailing = {
                                if (level == thinkingLevel) {
                                    DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
                                }
                            },
                        )
                        if (index != thinkingLevels.lastIndex) DdHairline()
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ComposerChip(
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
