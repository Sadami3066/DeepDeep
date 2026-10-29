package io.github.whywood.deepdeep.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UNFREEZE_GRACE_MILLIS = 160L

object AppScreenTransition {
    const val SHARED_AXIS_FRACTION = 0.25f

    fun direction(initial: AppDestination, target: AppDestination): Int = when {
        target.depth > initial.depth -> 1
        target.depth < initial.depth -> -1
        else -> 0
    }

    fun popTarget(stack: List<AppDestination>): AppDestination? = stack.getOrNull(stack.size - 2)

    fun translationFraction(settled: Float, incoming: Boolean, direction: Int): Float = when {
        direction == 0 -> 0f
        incoming -> (1f - settled) * direction
        else -> -settled * direction
    }

    fun alpha(settled: Float, incoming: Boolean): Float = if (incoming) settled else 1f - settled
}

@Composable
fun AnimatedScreenHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    predictiveBackEnabled: Boolean = true,
    base: @Composable (frozen: Boolean) -> Unit,
    content: @Composable (AppDestination) -> Unit,
) {
    val saveableState = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
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
    val previewFraction = remember { mutableFloatStateOf(0f) }
    val visited = remember { mutableSetOf<String>() }
    var front by remember { mutableStateOf(navigator.current) }
    var back by remember { mutableStateOf<AppDestination?>(null) }
    var direction by remember { mutableIntStateOf(0) }
    var previewTarget by remember { mutableStateOf<AppDestination?>(null) }
    var previewDirection by remember { mutableIntStateOf(0) }
    var skipNextTransition by remember { mutableStateOf(false) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }

    PredictiveBackHandler(
        enabled = predictiveBackEnabled && navigator.canPop && back == null,
    ) { events ->
        val target = AppScreenTransition.popTarget(navigator.stack) ?: return@PredictiveBackHandler
        reboundJob?.cancel()
        previewDirection = AppScreenTransition.direction(front, target)
        previewFraction.floatValue = 0f
        previewTarget = target
        var predictive = false
        try {
            events.collect { event ->
                predictive = true
                previewFraction.floatValue = event.progress
            }
        } catch (cancellation: CancellationException) {
            if (previewTarget == target) {
                if (predictive) {
                    reboundJob = scope.launch {
                        animate(
                            initialValue = previewFraction.floatValue,
                            targetValue = 0f,
                            animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                        ) { value, _ -> previewFraction.floatValue = value }
                        previewTarget = null
                    }
                } else {
                    previewTarget = null
                }
            }
            return@PredictiveBackHandler
        }
        if (previewTarget != target || navigator.current != front) {
            previewTarget = null
            return@PredictiveBackHandler
        }
        if (predictive) {
            previewFraction.floatValue = 1f
            skipNextTransition = true
            if (!navigator.pop()) {
                skipNextTransition = false
                previewTarget = null
            }
        } else {
            previewTarget = null
            navigator.pop()
        }
    }

    LaunchedEffect(navigator.current) {
        val target = navigator.current
        reboundJob?.cancel()
        if (skipNextTransition) {
            skipNextTransition = false
            previewTarget = null
            if (target == front) return@LaunchedEffect
            back = null
            front = target
            withFrameNanos { }
            pruneVisitedStates(navigator, saveableState, visited)
            return@LaunchedEffect
        }
        if (target == front) {
            previewTarget = null
            return@LaunchedEffect
        }
        previewTarget = null
        if (back != null) {
            back = null
            progress.snapTo(1f)
        }
        val outgoing = front
        direction = AppScreenTransition.direction(outgoing, target)
        back = outgoing.takeIf { it != AppDestination.Chat }
        front = target
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(DdMotion.SCREEN, easing = DdMotion.EaseOut))
        back = null
        withFrameNanos { }
        pruneVisitedStates(navigator, saveableState, visited)
    }

    Box(modifier) {
        val preview = previewTarget.takeIf { back == null }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (!covered || !baseDrawSuspended || preview == AppDestination.Chat) drawContent()
                }
                .graphicsLayer(),
        ) {
            base(baseFrozen)
        }
        val incoming = preview ?: front
        val outgoing = if (preview != null) front.takeIf { it != preview } else back
        val activeDirection = if (preview != null) previewDirection else direction
        val pages = buildList {
            if (activeDirection < 0) {
                if (incoming != AppDestination.Chat) add(incoming)
                if (outgoing != null && outgoing != AppDestination.Chat) add(outgoing)
            } else {
                if (outgoing != null && outgoing != AppDestination.Chat) add(outgoing)
                if (incoming != AppDestination.Chat) add(incoming)
            }
        }
        val fraction: () -> Float = if (preview != null) {
            { previewFraction.floatValue }
        } else {
            { progress.value }
        }
        pages.forEach { page ->
            key(page.routeKey) {
                ScreenPage(
                    destination = page,
                    fraction = fraction,
                    incoming = page == incoming,
                    direction = activeDirection,
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

private fun pruneVisitedStates(
    navigator: AppNavigator,
    saveableState: SaveableStateHolder,
    visited: MutableSet<String>,
) {
    val retained = navigator.stack.map { it.routeKey }.toSet()
    visited.retainAll { key ->
        val keep = key in retained
        if (!keep) saveableState.removeState(key)
        keep
    }
}

@Composable
private fun ScreenPage(
    destination: AppDestination,
    fraction: () -> Float,
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
                val settled = fraction()
                translationX = AppScreenTransition.translationFraction(settled, incoming, direction) *
                    AppScreenTransition.SHARED_AXIS_FRACTION * directionSign * size.width
                alpha = AppScreenTransition.alpha(settled, incoming)
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
