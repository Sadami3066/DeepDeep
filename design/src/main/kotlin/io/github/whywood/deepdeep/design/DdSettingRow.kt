package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置行：左侧标题/副标题，右侧控件（开关、值、按钮），可选整行点击与 chevron。
 */
@Composable
fun DdSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
) {
    val colors = deepDeepColors()
    val content: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f)) {
            DdText(
                text = title,
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(DdSpacing.Xxs))
                DdText(
                    text = subtitle,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
        if (control != null) {
            Spacer(Modifier.width(DdSpacing.Md))
            control()
        }
        if (showChevron) {
            Spacer(Modifier.width(DdSpacing.Sm))
            DdIcon(
                icon = DdIcons.ChevronRight,
                tint = colors.inkTertiary.copy(alpha = DdAlpha.Muted),
                size = 15.dp,
            )
        }
    }
    if (onClick != null) {
        DdPressable(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = DdShapes.Row,
            enabled = enabled,
            contentDescription = title,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(
                        horizontal = LocalDdRowHorizontalPadding.current,
                        vertical = DdSpacing.Md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(
                    horizontal = LocalDdRowHorizontalPadding.current,
                    vertical = DdSpacing.Md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
