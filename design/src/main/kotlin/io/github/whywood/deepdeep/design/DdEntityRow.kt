package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color

private val DdEntityRowMinHeight = DdSpacing.Huge + DdSpacing.Lg

@Composable
fun DdEntityRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: DdIcon? = null,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    showChevron: Boolean = false,
) {
    val colors = deepDeepColors()
    val contentAlpha = if (enabled) 1f else DdAlpha.Disabled
    val background = if (selected) colors.wash else Color.Transparent
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DdEntityRowMinHeight)
                .padding(
                    horizontal = maxOf(LocalDdRowHorizontalPadding.current, DdSpacing.Md),
                    vertical = DdSpacing.Md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                DdIcon(
                    icon = leadingIcon,
                    tint = colors.inkSecondary.copy(alpha = DdAlpha.Secondary),
                    size = DdSpacing.Xl,
                )
                Spacer(Modifier.width(DdSpacing.Md))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdText(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = DdType.body,
                        color = colors.ink.copy(alpha = contentAlpha),
                        maxLines = 1,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(DdSpacing.Sm))
                        badge()
                    }
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    DdText(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary.copy(alpha = contentAlpha),
                        maxLines = 1,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(DdSpacing.Sm))
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(DdSpacing.Sm))
                DdIcon(
                    icon = DdIcons.ChevronRight,
                    tint = colors.inkTertiary.copy(alpha = DdAlpha.Muted),
                    size = DdSpacing.Lg,
                )
            }
        }
    }
    if (onClick != null) {
        DdPressable(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = DdShapes.Row,
            background = background,
            enabled = enabled,
            contentDescription = title,
        ) {
            row()
        }
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxWidth()
                .background(color = background, shape = DdShapes.Row),
        ) {
            row()
        }
    }
}
