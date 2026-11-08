package io.github.whywood.deepdeep.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val indicatorColor by animateColorAsState(
        targetValue = if (colors.dark) colors.ink.copy(alpha = DdAlpha.IndicatorDark) else colors.surface,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "segmented-indicator",
    )
    DdSegmentedCore(
        options = options,
        selected = options[index],
        onSelect = { option -> onSelect(options.indexOf(option)) },
        modifier = modifier,
        enabled = true,
        height = 44.dp,
        shape = RoundedCornerShape(DdRadius.Segmented),
        indicatorShape = RoundedCornerShape(DdRadius.Indicator),
        indicatorPadding = 3.dp,
        indicatorColor = indicatorColor,
        testTags = testTags,
    ) { option, isSelected ->
        Text(
            text = option,
            style = DdType.chip,
            color = if (isSelected) colors.ink else colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
