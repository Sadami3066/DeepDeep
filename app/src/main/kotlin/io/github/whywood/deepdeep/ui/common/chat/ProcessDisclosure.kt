package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.LocalDdMotionEnabled

@Composable
internal fun ProcessDisclosure(
    expanded: Boolean,
    streaming: Boolean,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val motionEnabled = LocalDdMotionEnabled.current
    val layoutSpec: FiniteAnimationSpec<IntSize> =
        if (streaming || !motionEnabled) snap() else tween(DdMotion.BASE, easing = DdMotion.EaseOut)
    val fadeSpec: FiniteAnimationSpec<Float> =
        if (streaming || !motionEnabled) snap() else tween(DdMotion.FAST)
    Column(Modifier.fillMaxWidth()) {
        header()
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = layoutSpec, expandFrom = Alignment.Top) + fadeIn(fadeSpec),
            exit = shrinkVertically(animationSpec = layoutSpec, shrinkTowards = Alignment.Top) + fadeOut(fadeSpec),
        ) {
            content()
        }
    }
}
