package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DdIconBox(
    icon: DdIcon,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(DdRadius.Medium),
    containerColor: Color = deepDeepColors().wash,
    tint: Color = deepDeepColors().inkSecondary,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        DdIcon(
            icon = icon,
            tint = tint,
            size = iconSize,
            contentDescription = contentDescription,
        )
    }
}
