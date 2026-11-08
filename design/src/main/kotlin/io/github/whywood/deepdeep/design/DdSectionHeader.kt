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

/**
 * 分组标题行：左侧标题/副标题，右侧可选操作，用于设置、市场等分组上方。
 */
@Composable
fun DdSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DdSpacing.Xl, bottom = DdSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DdText(
                text = title,
                style = DdType.label,
                color = colors.inkSecondary,
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
        if (action != null) {
            Spacer(Modifier.width(DdSpacing.Sm))
            action()
        }
    }
}
