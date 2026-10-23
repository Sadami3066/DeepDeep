package io.github.whywood.deepdeep.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun DdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDone: () -> Unit = {},
    testTag: String? = null,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.hairline.copy(alpha = 0.6f),
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "dd-field-border",
    )
    val shape = RoundedCornerShape(14.dp)
    val focus = focusRequester
    val tag = testTag
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focus == null) Modifier else Modifier.focusRequester(focus))
                .then(if (tag == null) Modifier else Modifier.testTag(tag)),
            textStyle = DdType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = DdType.body,
                            color = colors.inkTertiary,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
