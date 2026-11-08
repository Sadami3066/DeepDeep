package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 带前置图标、副标题与操作区的 sheet 标题栏；须显式传 leadingIcon（可为 null）以与旧签名区分。
 */
@Composable
fun DdSheetHeader(
    title: String,
    onClose: () -> Unit,
    leadingIcon: DdIcon?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable () -> Unit)? = null,
    closeIconSize: Dp = 20.dp,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = DdSpacing.ScreenHorizontal, end = DdSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            DdIcon(icon = leadingIcon, tint = colors.inkSecondary, size = 22.dp)
            Spacer(Modifier.width(DdSpacing.Md))
        }
        Column(Modifier.weight(1f)) {
            DdText(
                text = title,
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
            )
            if (subtitle != null) {
                DdText(
                    text = subtitle,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
        if (actions != null) {
            actions()
            Spacer(Modifier.width(DdSpacing.Xs))
        }
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onClose,
            contentDescription = stringResource(R.string.dd_close),
            tint = colors.inkSecondary,
            iconSize = closeIconSize,
        )
    }
}
