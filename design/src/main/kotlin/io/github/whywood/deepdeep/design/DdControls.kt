package io.github.whywood.deepdeep.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DdSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = DdMotion.SwitchSpring,
        label = "switch-thumb-offset",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = DdMotion.SwitchSpring,
        label = "switch-thumb-scale",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            colors.accent
        } else {
            colors.ink.copy(alpha = if (colors.dark) 0.22f else 0.10f)
        },
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.onAccent else if (colors.dark) colors.ink else Color.White,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "switch-thumb-color",
    )
    val stateText = stateDescription
    val descriptionModifier = if (stateText == null) {
        Modifier
    } else {
        Modifier.semantics { this.stateDescription = stateText.orEmpty() }
    }
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(descriptionModifier)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                interactionSource = interaction,
                indication = null,
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (4f + 20f * thumbProgress).dp)
                .size(24.dp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun <T> DdSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (T) -> String,
) {
    if (options.isEmpty()) return
    val colors = deepDeepColors()
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = DdMotion.SegmentedSpring,
        label = "segment-index",
    )
    val indicatorColor = if (colors.dark) colors.ink.copy(alpha = 0.16f) else colors.surface
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(colors.wash),
    ) {
        val segmentWidth = maxWidth / options.size.toFloat()
        Box(
            Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(indicatorColor),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = DdType.chip,
                        color = if (isSelected) colors.ink else colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
