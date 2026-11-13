package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalDdHapticsEnabled = staticCompositionLocalOf { true }

data class DdSwipeAction(
    val icon: DdIcon,
    val label: String,
    val tint: Color,
    val background: Color,
    val onClick: () -> Unit,
)

private val SwipeActionWidth = 72.dp
private val SwipeOpenVelocity = 800.dp

private enum class DdSwipeValue {
    Closed,
    Open,
}

@Suppress("DEPRECATION")
@Composable
fun DdSwipeRow(
    key: Any,
    actions: List<DdSwipeAction>,
    openKey: Any?,
    onOpenChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = deepDeepColors()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hapticsEnabled = LocalDdHapticsEnabled.current
    val haptic = LocalHapticFeedback.current
    val currentOpenKey = rememberUpdatedState(openKey)
    val currentOnOpenChange = rememberUpdatedState(onOpenChange)
    val actionWidth = SwipeActionWidth * actions.size
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val velocityThresholdPx = with(density) { SwipeOpenVelocity.toPx() }
    val state = remember(key) {
        AnchoredDraggableState(
            initialValue = DdSwipeValue.Closed,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = DdMotion.SheetSpring,
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { value ->
                val updated = when (value) {
                    DdSwipeValue.Open -> key
                    DdSwipeValue.Closed -> currentOpenKey.value.takeIf { it != key }
                }
                currentOnOpenChange.value(updated)
                true
            },
        )
    }
    val anchors = remember(actionWidthPx) {
        DraggableAnchors {
            DdSwipeValue.Closed at 0f
            DdSwipeValue.Open at -actionWidthPx
        }
    }
    SideEffect { state.updateAnchors(anchors) }
    LaunchedEffect(openKey, actionWidthPx) {
        val target = if (openKey == key) DdSwipeValue.Open else DdSwipeValue.Closed
        if (state.settledValue != target && state.targetValue != target) {
            state.animateTo(target, DdMotion.SheetSpring)
        }
    }
    var armed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(state, hapticsEnabled, actionWidthPx) {
        snapshotFlow { state.offset }.collect { offset ->
            if (offset.isNaN() || actionWidthPx <= 0f) return@collect
            val progress = (-offset / actionWidthPx).coerceIn(0f, 1f)
            if (progress >= 0.5f) {
                if (!armed) {
                    armed = true
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            } else {
                armed = false
            }
        }
    }
    val close: () -> Unit = {
        scope.launch { state.animateTo(DdSwipeValue.Closed, DdMotion.SheetSpring) }
    }
    Box(modifier.fillMaxWidth().clipToBounds()) {
        if (actions.isNotEmpty()) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
            ) {
                actions.forEach { action ->
                    Column(
                        modifier = Modifier
                            .width(SwipeActionWidth)
                            .fillMaxHeight()
                            .background(action.background)
                            .clickable(enabled = enabled) {
                                action.onClick()
                                close()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DdIcon(icon = action.icon, tint = action.tint, size = 20.dp)
                        Spacer(Modifier.height(DdSpacing.Xs))
                        Text(
                            text = action.label,
                            style = DdType.chip,
                            color = action.tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset {
                    val current = state.offset
                    IntOffset(if (current.isNaN()) 0 else current.roundToInt(), 0)
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && actions.isNotEmpty(),
                )
                .background(colors.paper)
                .semantics {
                    if (actions.isNotEmpty()) {
                        customActions = actions.map { action ->
                            CustomAccessibilityAction(action.label) {
                                action.onClick()
                                close()
                                true
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}
