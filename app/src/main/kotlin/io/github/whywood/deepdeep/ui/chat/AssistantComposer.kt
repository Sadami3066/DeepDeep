package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ThinkingLevel

@Composable
fun AssistantComposer(
    value: String,
    configured: Boolean,
    modelLabel: String,
    thinkingLevel: ThinkingLevel,
    thinkingLevels: List<ThinkingLevel>,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModel: () -> Unit,
    onThinking: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(30.dp)
    val canSend = configured && value.isNotBlank() && !sending
    val sendBackground by animateColorAsState(
        targetValue = if (sending || canSend) colors.accent else colors.wash,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "send-background",
    )
    val placeholder = when {
        !configured -> stringResource(R.string.assistant_live_unconfigured_hint)
        value.isEmpty() -> stringResource(R.string.assistant_live_hint)
        else -> ""
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.ink.copy(alpha = 0.06f),
                        spotColor = colors.ink.copy(alpha = 0.12f),
                    )
                    .clip(shape)
                    .background(colors.composer),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 4.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("assistant_input"),
                        enabled = configured,
                        textStyle = DdType.body.copy(
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            color = if (configured) colors.ink else colors.inkTertiary,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend) onSend() },
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        style = DdType.body.copy(
                                            fontSize = 17.sp,
                                            lineHeight = 26.sp,
                                        ),
                                        color = colors.inkTertiary,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerText(
                        text = modelLabel,
                        color = colors.ink,
                        contentDescription = stringResource(R.string.assistant_model_chip, modelLabel),
                        onClick = onModel,
                        enabled = !sending,
                        testTag = "assistant_model",
                        maxWidth = 140.dp,
                    )
                    if (thinkingLevels.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        ComposerText(
                            text = thinkingLevelLabel(thinkingLevel),
                            color = colors.ink,
                            contentDescription = stringResource(
                                R.string.assistant_thinking_description,
                                thinkingLevelLabel(thinkingLevel),
                            ),
                            onClick = onThinking,
                            enabled = !sending,
                            testTag = "assistant_thinking",
                            maxWidth = 40.dp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DdPressable(
                        onClick = { if (sending) onStop() else onSend() },
                        modifier = Modifier.size(38.dp).testTag(if (sending) "assistant_stop" else "assistant_send"),
                        shape = CircleShape,
                        background = sendBackground,
                        enabled = sending || canSend,
                        contentDescription = stringResource(
                            if (sending) R.string.assistant_stop else R.string.assistant_send,
                        ),
                    ) {
                        DdIcon(
                            icon = if (sending) DdIcons.Stop else DdIcons.ArrowUp,
                            tint = if (sending || canSend) colors.onAccent else colors.inkTertiary,
                            size = 18.dp,
                        )
                    }
                }
            }
        }

        if (!configured) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(top = 6.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assistant_live_not_configured),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.weight(1f))
                DdTextAction(
                    text = stringResource(R.string.assistant_live_open_settings),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ComposerText(
    text: String,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    maxWidth: Dp,
) {
    DdPressable(
        onClick = onClick,
        modifier = Modifier.height(36.dp).testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        enabled = enabled,
        contentDescription = contentDescription,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = maxWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = DdType.label,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun thinkingLevelLabel(level: ThinkingLevel): String = stringResource(
    when (level) {
        ThinkingLevel.OFF -> R.string.assistant_thinking_off
        ThinkingLevel.LOW -> R.string.assistant_thinking_low
        ThinkingLevel.MEDIUM -> R.string.assistant_thinking_medium
        ThinkingLevel.HIGH -> R.string.assistant_thinking_high
    },
)

internal fun modelDisplayName(
    options: List<ModelOption>,
    model: String?,
    fallback: String,
): String {
    if (model.isNullOrBlank()) return fallback
    val option = options.firstOrNull { it.id == model }
    if (option != null) {
        return option.displayName
            .removePrefix("DeepSeek ")
            .removePrefix("Claude ")
            .removePrefix("Gemini ")
    }
    return model.substringAfterLast('/')
}
