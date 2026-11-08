package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/**
 * 状态徽标：胶囊小标签，用于「推荐」「已启用」「失败」等状态，支持语义色调与前置图标。
 */
@Composable
fun DdBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: DdTone = DdTone.Neutral,
    icon: DdIcon? = null,
) {
    val colors = deepDeepColors()
    val tint = tone.tint(colors)
    Row(
        modifier = modifier
            .clip(DdShapes.Pill)
            .background(tint.copy(alpha = DdAlpha.TrackLight))
            .padding(horizontal = DdSpacing.Sm, vertical = DdSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DdSpacing.Xxs),
    ) {
        if (icon != null) {
            DdIcon(icon = icon, tint = tint, size = DdSpacing.Md)
        }
        DdText(
            text = text,
            style = DdType.chip,
            color = if (tone == DdTone.Neutral) colors.inkSecondary else tint,
            maxLines = 1,
        )
    }
}
