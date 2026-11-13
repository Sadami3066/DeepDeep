package io.github.whywood.deepdeep.design

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SheetMaxWidth = 640.dp
private val SheetTopGap = 8.dp
private val SheetDismissVelocity = 900.dp
private const val SheetDismissFraction = 0.25f

internal fun sheetDragOffset(offset: Float, delta: Float, sheetHeight: Float): Float =
    (offset + delta).coerceIn(0f, sheetHeight.coerceAtLeast(0f))

internal fun sheetDismissTarget(
    offset: Float,
    sheetHeight: Float,
    velocity: Float,
    dismissVelocity: Float,
): Float {
    if (sheetHeight <= 0f) return 0f
    val byDistance = offset > sheetHeight * SheetDismissFraction
    val byVelocity = velocity > dismissVelocity
    return if (byDistance || byVelocity) sheetHeight else 0f
}

@Composable
internal fun DdSheetContent(
    onDismiss: () -> Unit,
    fullBleed: Boolean = false,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val motionEnabled = LocalDdMotionEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelColor = containerColor ?: if (fullBleed) Color.Black else colors.raised
    val contentColor = if (fullBleed) Color.White else colors.ink
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf(false) }
    var motionJob by remember { mutableStateOf<Job?>(null) }
    val enter = remember { Animatable(if (motionEnabled) 0f else 1f) }

    fun animateOffset(
        target: Float,
        initialVelocity: Float,
        finish: (() -> Unit)? = null,
    ) {
        motionJob?.cancel()
        motionJob = scope.launch {
            animate(
                initialValue = dragOffset,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = tween(DdMotion.SHEET, easing = DdMotion.EaseInOut),
            ) { value, _ -> dragOffset = value }
            finish?.invoke()
        }
    }

    fun slideOut(velocity: Float) {
        if (dismissing) return
        dismissing = true
        if (!motionEnabled || panelHeightPx <= 0) {
            onDismiss()
            return
        }
        animateOffset(panelHeightPx.toFloat(), velocity, finish = onDismiss)
    }

    fun requestDismiss() {
        if (dismissing) return
        if (!motionEnabled || panelHeightPx <= 0) {
            dismissing = true
            onDismiss()
            return
        }
        slideOut(0f)
    }

    fun settle(velocity: Float) {
        if (dismissing) return
        val target = sheetDismissTarget(
            offset = dragOffset,
            sheetHeight = panelHeightPx.toFloat(),
            velocity = velocity,
            dismissVelocity = with(density) { SheetDismissVelocity.toPx() },
        )
        if (target > 0f) {
            slideOut(velocity)
            return
        }
        if (!motionEnabled) {
            dragOffset = 0f
            return
        }
        motionJob?.cancel()
        motionJob = scope.launch {
            animate(
                initialValue = dragOffset,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = DdMotion.SheetSpring,
            ) { value, _ -> dragOffset = value }
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val height = panelHeightPx.toFloat()
                val delta = available.y
                if (height <= 0f || delta == 0f || dragOffset <= 0f) return Offset.Zero
                motionJob?.cancel()
                val next = sheetDragOffset(dragOffset, delta, height)
                val consumed = next - dragOffset
                dragOffset = next
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val height = panelHeightPx.toFloat()
                val delta = available.y
                if (height <= 0f || delta <= 0f) return Offset.Zero
                motionJob?.cancel()
                val next = sheetDragOffset(dragOffset, delta, height)
                val consumedDelta = next - dragOffset
                dragOffset = next
                return Offset(0f, consumedDelta)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragOffset > 0f) settle(available.y)
                return Velocity.Zero
            }
        }
    }
    val dragState = rememberDraggableState { delta ->
        val height = panelHeightPx.toFloat()
        if (height > 0f) {
            dragOffset = sheetDragOffset(dragOffset, delta, height)
        }
    }
    val scrimInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        if (motionEnabled) {
            enter.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = DdMotion.SHEET, easing = DdMotion.EaseOut),
            )
        }
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        @Suppress("DEPRECATION")
        val softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        LaunchedEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            window.setDimAmount(0f)
            window.setSoftInputMode(softInputMode)
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val maxPanelHeight = (maxHeight - statusBarTop - SheetTopGap).coerceAtLeast(0.dp)
                val closeLabel = stringResource(R.string.dd_close)
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                val fraction = if (panelHeightPx > 0) {
                                    (dragOffset / panelHeightPx).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                alpha = enter.value * (1f - fraction)
                            }
                            .background(colors.scrim)
                            .semantics { contentDescription = closeLabel }
                            .clickable(
                                interactionSource = scrimInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { requestDismiss() },
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = SheetMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = maxPanelHeight)
                            .onSizeChanged { panelHeightPx = it.height }
                            .graphicsLayer {
                                translationY = dragOffset + (1f - enter.value) * size.height
                            }
                            .nestedScroll(nestedScroll)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                enabled = !dismissing,
                                interactionSource = scrimInteraction,
                                onDragStarted = { motionJob?.cancel() },
                                onDragStopped = { velocity -> settle(velocity) },
                            )
                            .clip(SheetTopShape)
                            .background(panelColor),
                    ) {
                        if (!fullBleed) {
                            Box(
                                Modifier
                                    .padding(top = 10.dp, bottom = 8.dp)
                                    .size(width = 32.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(colors.hairline)
                                    .align(Alignment.CenterHorizontally),
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
                                content = content,
                            )
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }
}
