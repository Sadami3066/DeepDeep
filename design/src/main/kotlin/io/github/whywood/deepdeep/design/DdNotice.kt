package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip

/**
 * 块级提示：浅色底 + 语义图标 + 正文，可选标题与右侧操作，用于页面内提示/说明。
 */
@Composable
fun DdNotice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: DdTone = DdTone.Accent,
    icon: DdIcon? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val tint = tone.tint(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DdShapes.Medium)
            .background(tint.copy(alpha = DdAlpha.TrackLight))
            .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            DdIcon(icon = icon, tint = tint, size = DdSpacing.Xl)
            Spacer(Modifier.width(DdSpacing.Md))
        }
        Column(Modifier.weight(1f)) {
            if (title != null) {
                DdText(
                    text = title,
                    style = DdType.label,
                    color = colors.ink,
                    maxLines = 2,
                )
                Spacer(Modifier.height(DdSpacing.Xxs))
            }
            DdText(
                text = text,
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        }
        if (action != null) {
            Spacer(Modifier.width(DdSpacing.Md))
            action()
        }
    }
}
