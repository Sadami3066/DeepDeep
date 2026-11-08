package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.withTimeoutOrNull

private val DdTooltipMaxWidth = DdSpacing.Huge * 6

@Composable
fun DdTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = deepDeepColors()
    var visible by remember { mutableStateOf(false) }
    val spacingPx = with(LocalDensity.current) { DdSpacing.Sm.roundToPx() }
    val positionProvider = remember(spacingPx) { DdTooltipPositionProvider(spacingPx) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.pointerInput(text) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                        true
                    } ?: false
                    if (!released) {
                        visible = true
                        waitForUpOrCancellation()
                        visible = false
                    }
                }
            },
        ) {
            content()
        }
        if (visible) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { visible = false },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false,
                ),
            ) {
                DdText(
                    text = text,
                    modifier = Modifier
                        .widthIn(max = DdTooltipMaxWidth)
                        .shadow(
                            elevation = DdElevation.Menu,
                            shape = DdShapes.Small,
                            clip = false,
                        )
                        .clip(DdShapes.Small)
                        .background(colors.raised)
                        .padding(
                            horizontal = DdSpacing.Md,
                            vertical = DdSpacing.Sm,
                        ),
                    style = DdType.bodySmall,
                    color = colors.ink,
                )
            }
        }
    }
}

private class DdTooltipPositionProvider(
    private val spacingPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - spacingPx
        val y = if (above < 0) {
            anchorBounds.bottom + spacingPx
        } else {
            above
        }
        return IntOffset(x, y)
    }
}
