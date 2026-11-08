package io.github.whywood.deepdeep.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

private val DdSelectionRowMinHeight = DdSpacing.Huge + DdSpacing.Lg
private val DdSelectionRingSize = DdSpacing.Xl
private val DdSelectionDotSize = DdSpacing.Md - DdSpacing.Xxs
private val DdSelectionHairline = DdSpacing.Xxs / 2

@Composable
fun DdSelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = deepDeepColors()
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.inkTertiary.copy(alpha = DdAlpha.Muted),
        animationSpec = tween(
            durationMillis = DdMotion.FAST,
            easing = DdMotion.EaseOut,
        ),
        label = "dd-selection-ring",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = DdMotion.SwitchSpring,
        label = "dd-selection-dot",
    )
    DdPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        background = if (selected) colors.accentSoft else Color.Transparent,
        enabled = enabled,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DdSelectionRowMinHeight)
                .padding(
                    horizontal = maxOf(LocalDdRowHorizontalPadding.current, DdSpacing.Md),
                    vertical = DdSpacing.Md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DdText(
                    text = title,
                    style = DdType.body,
                    color = colors.ink.copy(alpha = if (enabled) 1f else DdAlpha.Disabled),
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    DdText(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary.copy(alpha = if (enabled) 1f else DdAlpha.Disabled),
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(DdSpacing.Md))
            Box(
                modifier = Modifier
                    .size(DdSelectionRingSize)
                    .clip(CircleShape)
                    .border(
                        width = DdSelectionHairline,
                        color = ringColor,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(DdSelectionDotSize)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}
