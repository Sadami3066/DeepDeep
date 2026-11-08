package io.github.whywood.deepdeep.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class DdIcon(@param:DrawableRes val res: Int)

@Composable
fun DdIcon(
    icon: DdIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon.res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
