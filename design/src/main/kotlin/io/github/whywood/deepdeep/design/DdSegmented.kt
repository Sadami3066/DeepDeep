package io.github.whywood.deepdeep.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DdSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTags: List<String>? = null,
) {
    if (options.isEmpty()) return
    val colors = deepDeepColors()
    val index = selectedIndex.coerceIn(0, options.size - 1)
    val animatedIndex by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = DdMotion.SegmentedSpring,
        label = "segmented-index",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (colors.dark) colors.ink.copy(alpha = 0.16f) else colors.surface,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "segmented-indicator",
    )
    val indicatorShape = RoundedCornerShape(11.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.wash),
    ) {
        val segmentWidth = maxWidth / options.size.toFloat()
        Box(
            Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .shadow(elevation = 2.dp, shape = indicatorShape, clip = false)
                .clip(indicatorShape)
                .background(indicatorColor),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .selectableGroup(),
        ) {
            options.forEachIndexed { optionIndex, option ->
                val isSelected = optionIndex == index
                val tag = testTags?.getOrNull(optionIndex)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (tag == null) Modifier else Modifier.testTag(tag))
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(optionIndex) },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
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
