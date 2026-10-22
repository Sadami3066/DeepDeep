package io.github.whywood.deepdeep.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            DdSectionLabel(label, Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
internal fun InitialBadge(name: String, size: Dp = 36.dp) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = DdType.label,
            color = colors.accent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    val colors = deepDeepColors()
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) colors.accentSoft else colors.wash)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = DdType.chip,
        color = if (active) colors.accent else colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDone: () -> Unit = {},
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.hairline,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "settings-field-border",
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.wash)
            .border(1.dp, border, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = DdType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = DdType.body, color = colors.inkTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun PlaceholderRow(
    icon: DdIcon,
    @StringRes title: Int,
    @StringRes subtitle: Int?,
    onPlaceholder: (String) -> Unit,
) {
    val titleText = stringResource(title)
    DdListRow(
        icon = icon,
        title = titleText,
        subtitle = subtitle?.let { stringResource(it) },
        value = stringResource(R.string.settings_placeholder_value),
        onClick = { onPlaceholder(titleText) },
        showChevron = true,
    )
}

@Composable
internal fun PlaceholderSheet(title: String, onClose: () -> Unit) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(title, onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_placeholder_body),
                modifier = Modifier.fillMaxWidth(),
                style = DdType.heading,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_placeholder_hint),
                modifier = Modifier.fillMaxWidth(),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            DdPrimaryButton(
                text = stringResource(R.string.settings_placeholder_dismiss),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
