package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

private val DdComposerHairline = DdSpacing.Xxs / 2

@Composable
fun DdComposerSurface(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val elevation by animateDpAsState(
        targetValue = if (focused) DdElevation.ComposerFocused else DdElevation.Composer,
        animationSpec = tween(
            durationMillis = DdMotion.BASE,
            easing = DdMotion.EaseOut,
        ),
        label = "dd-composer-elevation",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = DdShapes.Card,
                clip = false,
            )
            .clip(DdShapes.Card)
            .background(colors.composer)
            .border(
                width = DdComposerHairline,
                color = colors.hairline.copy(alpha = DdAlpha.Border),
                shape = DdShapes.Card,
            )
            .padding(
                horizontal = DdSpacing.Md,
                vertical = DdSpacing.Sm,
            ),
        content = content,
    )
}
