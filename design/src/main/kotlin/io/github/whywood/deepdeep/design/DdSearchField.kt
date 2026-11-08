package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DdSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    clearContentDescription: String,
    onEscape: (() -> Unit)? = null,
    fieldTestTag: String? = null,
    clearTestTag: String? = null,
) {
    DdSearchFieldCore(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        modifier = modifier,
        focusRequester = focusRequester,
        clearContentDescription = clearContentDescription,
        onEscape = onEscape,
        fieldTestTag = fieldTestTag,
        clearTestTag = clearTestTag,
        onSearch = null,
        trailing = null,
    )
}

@Composable
fun DdSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    clearContentDescription: String,
    onEscape: (() -> Unit)? = null,
    fieldTestTag: String? = null,
    clearTestTag: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    DdSearchFieldCore(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        modifier = modifier,
        focusRequester = focusRequester,
        clearContentDescription = clearContentDescription,
        onEscape = onEscape,
        fieldTestTag = fieldTestTag,
        clearTestTag = clearTestTag,
        onSearch = onSearch,
        trailing = trailing,
    )
}

@Composable
private fun DdSearchFieldCore(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    focusRequester: FocusRequester?,
    clearContentDescription: String,
    onEscape: (() -> Unit)?,
    fieldTestTag: String?,
    clearTestTag: String?,
    onSearch: (() -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)?,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(14.dp)
    val focus = focusRequester
    val escape = onEscape
    val fieldTag = fieldTestTag
    val clearTag = clearTestTag
    val search = onSearch
    val textStyle = DdType.bodySmall.copy(fontSize = 15.sp, lineHeight = 22.sp, color = colors.ink)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, colors.hairline.copy(alpha = DdAlpha.Border), shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.Search, tint = colors.inkTertiary, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .then(if (focus == null) Modifier else Modifier.focusRequester(focus))
                .then(
                    if (escape == null) {
                        Modifier
                    } else {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                escape.invoke()
                                true
                            } else {
                                false
                            }
                        }
                    },
                )
                .then(if (fieldTag == null) Modifier else Modifier.testTag(fieldTag)),
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search?.invoke() }),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = DdType.bodySmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = colors.inkTertiary,
                        )
                    }
                    inner()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing.invoke(this)
        }
        if (query.isNotEmpty()) {
            DdIconButton(
                icon = DdIcons.Close,
                onClick = { onQueryChange("") },
                contentDescription = clearContentDescription,
                modifier = if (clearTag == null) Modifier else Modifier.testTag(clearTag),
                size = 32.dp,
                iconSize = 15.dp,
                tint = colors.inkTertiary,
            )
        }
    }
}
