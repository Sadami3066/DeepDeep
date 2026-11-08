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
 * 指标行：左侧标签（可带说明），右侧数值，用于用量、设置摘要等只读统计。
 */
@Composable
fun DdStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DdSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DdText(
                text = label,
                style = DdType.bodySmall,
                color = colors.inkSecondary,
                maxLines = 1,
            )
            if (supporting != null) {
                Spacer(Modifier.height(DdSpacing.Xxs))
                DdText(
                    text = supporting,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(DdSpacing.Md))
        DdText(
            text = value,
            style = DdType.numeric,
            color = colors.ink,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(Modifier.width(DdSpacing.Sm))
            trailing()
        }
    }
}
