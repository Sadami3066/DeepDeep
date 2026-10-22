package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Composable
fun DdMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    menuWidth: Dp = 160.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = colors.raised,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.width(menuWidth).padding(horizontal = 6.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun DdMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(10.dp),
        contentDescription = text,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = text,
                modifier = Modifier.align(Alignment.Center),
                style = DdType.bodySmall,
                color = if (selected) colors.accent else colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (trailing != null) {
                Row(Modifier.align(Alignment.CenterEnd)) { trailing() }
            }
        }
    }
}
