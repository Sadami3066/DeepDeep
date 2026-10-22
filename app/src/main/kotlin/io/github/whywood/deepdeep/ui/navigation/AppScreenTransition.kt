package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import io.github.whywood.deepdeep.design.DdMotion

object AppScreenTransition {
    const val FORWARD_ENTER_FRACTION = 0.18f
    const val FORWARD_EXIT_FRACTION = 0.06f

    fun direction(initial: AppDestination, target: AppDestination): Int = when {
        target.depth > initial.depth -> 1
        target.depth < initial.depth -> -1
        else -> 0
    }

    fun of(scope: AnimatedContentTransitionScope<AppDestination>): ContentTransform = with(scope) {
        val fade = DdMotion.spec<Float>(DdMotion.SCREEN)
        val slide = DdMotion.spec<IntOffset>(DdMotion.SCREEN)
        val depthDelta = direction(initialState, targetState)
        val enterFraction = when (depthDelta) {
            1 -> FORWARD_ENTER_FRACTION
            -1 -> -FORWARD_EXIT_FRACTION
            else -> 0f
        }
        val exitFraction = when (depthDelta) {
            1 -> -FORWARD_EXIT_FRACTION
            -1 -> FORWARD_ENTER_FRACTION
            else -> 0f
        }
        val enter = fadeIn(fade) + slideInHorizontally(slide) { (it * enterFraction).toInt() }
        val exit = fadeOut(fade) + slideOutHorizontally(slide) { (it * exitFraction).toInt() }
        (enter togetherWith exit).using(SizeTransform(clip = false))
    }
}

@Composable
fun AnimatedScreenHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    content: @Composable (AppDestination) -> Unit,
) {
    AnimatedContent(
        targetState = navigator.current,
        modifier = modifier,
        transitionSpec = { AppScreenTransition.of(this) },
        contentKey = { it.routeKey },
        label = "app-screen",
    ) { destination ->
        content(destination)
    }
}
