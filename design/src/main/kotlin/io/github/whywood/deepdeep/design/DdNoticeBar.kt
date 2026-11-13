package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

public enum class DdNoticeTone {
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
}

private const val NoticeBackgroundAlpha = 0.10f

@Composable
public fun DdNoticeBar(
    text: String,
    modifier: Modifier = Modifier,
    tone: DdNoticeTone = DdNoticeTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val toneColor = tone.noticeColor(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DdShapes.Medium)
            .background(toneColor.copy(alpha = NoticeBackgroundAlpha))
            .padding(start = DdSpacing.Md, end = DdSpacing.Sm, top = DdSpacing.Sm, bottom = DdSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(toneColor),
        )
        Spacer(Modifier.width(DdSpacing.Sm))
        DdText(
            text = text,
            modifier = Modifier.weight(1f).padding(vertical = DdSpacing.Xs),
            style = DdType.bodySmall,
            color = colors.ink,
        )
        if (actionLabel != null && onAction != null) {
            DdTextAction(
                text = actionLabel,
                onClick = onAction,
                color = toneColor,
            )
        }
    }
}

private fun DdNoticeTone.noticeColor(colors: DeepDeepColors): Color = when (this) {
    DdNoticeTone.INFO -> colors.accent
    DdNoticeTone.SUCCESS -> colors.success
    DdNoticeTone.WARNING -> colors.warning
    DdNoticeTone.DANGER -> colors.danger
}
