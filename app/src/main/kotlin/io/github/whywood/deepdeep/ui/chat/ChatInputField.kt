package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    placeholderAlign: Alignment.Horizontal = Alignment.Start,
    enabled: Boolean = true,
    maxLines: Int = 8,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onSend: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    testTag: String = "assistant_input",
) {
    val colors = deepDeepColors()
    val textStyle = DdType.body.copy(
        fontSize = 16.sp,
        lineHeight = 25.sp,
        color = if (enabled) colors.ink else colors.inkTertiary,
    )
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    BasicTextField(
        value = textFieldValue,
        onValueChange = { next ->
            textFieldValue = next
            onValueChange(next.text)
        },
        modifier = modifier
            .heightIn(min = 42.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .testTag(testTag),
        enabled = enabled,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = visualTransformation,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { inner ->
            Box {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        modifier = Modifier.fillMaxWidth(),
                        style = textStyle,
                        color = colors.inkTertiary,
                        textAlign = when (placeholderAlign) {
                            Alignment.CenterHorizontally -> TextAlign.Center
                            Alignment.End -> TextAlign.End
                            else -> TextAlign.Start
                        },
                    )
                }
                inner()
            }
        },
    )
}
