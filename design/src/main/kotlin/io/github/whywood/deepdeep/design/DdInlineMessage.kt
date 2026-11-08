package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 行内消息：单行说明/校验提示，可带语义图标与尾部操作，用于表单字段下方或列表行内。
 */
@Composable
fun DdInlineMessage(
    text: String,
    modifier: Modifier = Modifier,
    tone: DdTone = DdTone.Neutral,
    icon: DdIcon? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val tint = tone.tint(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DdSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            DdIcon(icon = icon, tint = tint, size = DdSpacing.Lg)
            Spacer(Modifier.width(DdSpacing.Xs))
        }
        DdText(
            text = text,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = if (tone == DdTone.Neutral) colors.inkTertiary else tint,
        )
        if (action != null) {
            Spacer(Modifier.width(DdSpacing.Sm))
            action()
        }
    }
}
