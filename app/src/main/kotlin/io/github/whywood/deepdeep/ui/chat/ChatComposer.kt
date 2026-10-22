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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
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
    thinkingLevel: ThinkingLevel,
    thinkingLevels: List<ThinkingLevel>,
    quote: QuotedMessage?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelChange: (String) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onClearQuote: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showModels by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    val canSend = ready && configured && value.isNotBlank() && !sending
    val sendActive = sending || canSend
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
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
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
                            maxLines = 8,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                            decorationBox = { inner ->
                                Box {
                                    if (value.isEmpty()) {
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
                    ComposerChip(
                        text = label,
                        onClick = {
                            if (configured) showModels = true else onOpenSettings()
                        },
                        enabled = !sending,
                        contentDescription = stringResource(R.string.assistant_model_chip, label),
                        testTag = "assistant_model",
                        maxWidth = 150.dp,
                    )
                    if (thinkingLevels.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        ComposerChip(
                            text = stringResource(
                                R.string.assistant_thinking_chip,
                                thinkingLabel(thinkingLevel),
                            ),
                            onClick = { showThinking = true },
                            enabled = !sending,
                            contentDescription = stringResource(
                                R.string.assistant_thinking_chip,
                                thinkingLabel(thinkingLevel),
                            ),
                            testTag = "assistant_thinking",
                            maxWidth = 100.dp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DdPressable(
                        onClick = { if (sending) onStop() else onSend() },
                        modifier = Modifier.size(38.dp).testTag(if (sending) "assistant_stop" else "assistant_send"),
                        shape = CircleShape,
                        background = if (sendActive) colors.accent else colors.wash,
                        enabled = sendActive,
                        contentDescription = stringResource(
                            if (sending) R.string.assistant_stop else R.string.assistant_send,
                        ),
                    ) {
                        DdIcon(
                            icon = if (sending) DdIcons.Stop else DdIcons.ArrowUp,
                            tint = if (sendActive) colors.onAccent else colors.inkTertiary,
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

    if (showModels) {
        DdSheet(onDismiss = { showModels = false }) {
            DdSheetHeader(
                title = stringResource(R.string.assistant_model_sheet),
                onClose = { showModels = false },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(8.dp))
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
                            showModels = false
                            onOpenSettings()
                        },
                        showChevron = true,
                    )
                } else {
                    models.forEachIndexed { index, option ->
                        DdListRow(
                            title = option.displayName,
                            onClick = {
                                showModels = false
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
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showThinking) {
        DdSheet(onDismiss = { showThinking = false }) {
            DdSheetHeader(
                title = stringResource(R.string.assistant_thinking_sheet),
                onClose = { showThinking = false },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(8.dp))
                thinkingLevels.forEachIndexed { index, level ->
                    DdListRow(
                        title = thinkingLabel(level),
                        onClick = {
                            showThinking = false
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
    },
)
