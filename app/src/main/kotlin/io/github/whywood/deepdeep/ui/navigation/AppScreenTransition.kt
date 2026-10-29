package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.whywood.deepdeep.design.DdMotion
import kotlinx.coroutines.delay

private const val UNFREEZE_GRACE_MILLIS = 160L

object AppScreenTransition {
    fun direction(initial: AppDestination, target: AppDestination): Int = when {
        target.depth > initial.depth -> 1
        target.depth < initial.depth -> -1
        else -> 0
    }
}

@Composable
fun AnimatedScreenHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    base: @Composable (frozen: Boolean) -> Unit,
    content: @Composable (AppDestination) -> Unit,
) {
    val saveableState = rememberSaveableStateHolder()
    val covered = navigator.current != AppDestination.Chat
    var baseFrozen by remember { mutableStateOf(false) }
    var baseDrawSuspended by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) {
            baseFrozen = true
            delay(DdMotion.SCREEN.toLong())
            baseDrawSuspended = true
        } else {
            baseDrawSuspended = false
            delay(DdMotion.SCREEN.toLong() + UNFREEZE_GRACE_MILLIS)
            baseFrozen = false
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val directionSign = if (rtl) -1f else 1f
    val progress = remember { Animatable(1f) }
    val visited = remember { mutableSetOf<String>() }
    var front by remember { mutableStateOf(navigator.current) }
    var back by remember { mutableStateOf<AppDestination?>(null) }
    var direction by remember { mutableIntStateOf(0) }

    LaunchedEffect(navigator.current) {
        val target = navigator.current
        if (target == front) return@LaunchedEffect
        if (back != null) {
            back = null
            progress.snapTo(1f)
        }
        val outgoing = front
        direction = AppScreenTransition.direction(outgoing, target)
        back = outgoing.takeIf { it != AppDestination.Chat }
        front = target
        if (back == null && target == AppDestination.Chat) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(DdMotion.SCREEN, easing = DdMotion.EaseOut))
        back = null
        withFrameNanos { }
        val retained = navigator.stack.map { it.routeKey }.toSet()
        visited.retainAll { key ->
            val keep = key in retained
            if (!keep) saveableState.removeState(key)
            keep
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { if (!covered || !baseDrawSuspended) drawContent() }
                .graphicsLayer(),
        ) {
            base(baseFrozen)
        }
        val outgoing = back
        val incoming = front
        val pages = buildList {
            if (direction < 0) {
                if (incoming != AppDestination.Chat) add(incoming)
                if (outgoing != null && outgoing != AppDestination.Chat) add(outgoing)
            } else {
                if (outgoing != null && outgoing != AppDestination.Chat) add(outgoing)
                if (incoming != AppDestination.Chat) add(incoming)
            }
        }
        pages.forEach { page ->
            key(page.routeKey) {
                ScreenPage(
                    destination = page,
                    progress = progress,
                    incoming = page == incoming,
                    direction = direction,
                    directionSign = directionSign,
                    saveableState = saveableState,
                    visited = visited,
                    content = content,
                )
            }
        }
        if (back != null) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
}

@Composable
private fun ScreenPage(
    destination: AppDestination,
    progress: Animatable<Float, AnimationVector1D>,
    incoming: Boolean,
    direction: Int,
    directionSign: Float,
    saveableState: SaveableStateHolder,
    visited: MutableSet<String>,
    content: @Composable (AppDestination) -> Unit,
) {
    SideEffect { visited.add(destination.routeKey) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = progress.value
                if (direction == 0) {
                    alpha = if (incoming) fraction else 1f - fraction
                    return@graphicsLayer
                }
                val travelled = when {
                    incoming && direction > 0 -> 1f - fraction
                    !incoming && direction < 0 -> fraction
                    else -> 0f
                }
                translationX = travelled * size.width * directionSign
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            },
    ) {
        saveableState.SaveableStateProvider(destination.routeKey) {
            content(destination)
        }
    }
}
