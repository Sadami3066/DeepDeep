package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

public enum class DdStatusTone {
    NEUTRAL,
    ACCENT,
    SUCCESS,
    WARNING,
    DANGER,
}

@Composable
public fun DdStatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: DdStatusTone = DdStatusTone.NEUTRAL,
    busy: Boolean = false,
) {
    val colors = deepDeepColors()
    val toneColor = tone.statusColor(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = DdSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdText(
            text = label,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(DdSpacing.Md))
        if (busy) {
            DdSpinner(
                color = toneColor,
                size = 12.dp,
                strokeWidth = 1.5.dp,
            )
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(toneColor),
            )
        }
        Spacer(Modifier.width(DdSpacing.Sm))
        DdText(
            text = value,
            style = DdType.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun DdStatusTone.statusColor(colors: DeepDeepColors): Color = when (this) {
    DdStatusTone.NEUTRAL -> colors.inkTertiary
    DdStatusTone.ACCENT -> colors.accent
    DdStatusTone.SUCCESS -> colors.success
    DdStatusTone.WARNING -> colors.warning
    DdStatusTone.DANGER -> colors.danger
}
