package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

@Composable
fun DdExpandToggle(
    expanded: Boolean,
    collapsedLabel: String,
    expandedLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onToggle,
        modifier = modifier.heightIn(min = 44.dp),
        shape = DdShapes.Small,
        contentDescription = if (expanded) expandedLabel else collapsedLabel,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DdSpacing.Xs),
        ) {
            DdText(
                text = if (expanded) expandedLabel else collapsedLabel,
                style = DdType.label,
                color = colors.inkSecondary,
            )
            DdIcon(
                icon = DdIcons.ChevronDown,
                tint = colors.inkTertiary,
                size = 14.dp,
                modifier = if (expanded) Modifier.rotate(180f) else Modifier,
            )
        }
    }
}
