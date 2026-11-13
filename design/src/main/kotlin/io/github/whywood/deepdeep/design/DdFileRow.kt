package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DdFileRow(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DdIcon = DdIcons.File,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val alpha = if (enabled) 1f else DdAlpha.Disabled
    DdPressable(
        onClick = onClick ?: {},
        modifier = modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        enabled = onClick != null && enabled,
        contentDescription = name,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = DdSpacing.Lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = icon,
                tint = colors.inkSecondary.copy(alpha = alpha),
                size = 20.dp,
            )
            Spacer(Modifier.width(DdSpacing.Md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    style = DdType.body,
                    color = colors.ink.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(DdSpacing.Sm))
                trailing()
            }
        }
    }
}
