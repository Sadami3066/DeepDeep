package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

@Composable
fun DdDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val density = LocalDensity.current
    var anchorWidth by remember { mutableIntStateOf(0) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (expanded && shown) 1f else 0f,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "dd-dropdown-progress",
    )
    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged {
                anchorWidth = it.width
                anchorHeight = it.height
            },
    ) {
        anchor()
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorHeight + with(density) { DROPDOWN_GAP.roundToPx() }),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .graphicsLayer {
                            alpha = progress
                            translationY = (1f - progress) * -6.dp.toPx()
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.raised)
                        .border(1.dp, colors.hairline.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    content = content,
                )
            }
        }
    }
}

private val DROPDOWN_GAP = 6.dp
