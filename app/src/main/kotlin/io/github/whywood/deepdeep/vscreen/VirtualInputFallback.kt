package io.github.whywood.deepdeep.vscreen

import android.view.KeyEvent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

internal const val INPUT_DENIED_BEFORE_DISPATCH = "input_denied_before_dispatch"
internal const val DEFAULT_TAP_MILLIS = 60L
internal const val MAX_TAP_MILLIS = 3000L
internal const val DEFAULT_SWIPE_MILLIS = 300L
internal const val MAX_SWIPE_MILLIS = 5000L

internal val virtualInputKeyCodes: Map<String, Int> = mapOf(
    "back" to KeyEvent.KEYCODE_BACK,
    "enter" to KeyEvent.KEYCODE_ENTER,
    "delete" to KeyEvent.KEYCODE_DEL,
    "tab" to KeyEvent.KEYCODE_TAB,
    "escape" to KeyEvent.KEYCODE_ESCAPE,
    "space" to KeyEvent.KEYCODE_SPACE,
    "arrow_up" to KeyEvent.KEYCODE_DPAD_UP,
    "arrow_down" to KeyEvent.KEYCODE_DPAD_DOWN,
    "arrow_left" to KeyEvent.KEYCODE_DPAD_LEFT,
    "arrow_right" to KeyEvent.KEYCODE_DPAD_RIGHT,
)

internal class VirtualCommandRejected(message: String, val code: String?) : IllegalStateException(message)

internal class VirtualFallbackInput private constructor(val line: String) {
    companion object {
        fun fromCommand(display: VirtualScreenState.Online, command: String, params: JsonObject): VirtualFallbackInput? {
            if (display.displayId <= 0) return null
            val line = when (command) {
                VirtualScreenCommands.TAP -> {
                    val x = params.intField("x") ?: return null
                    val y = params.intField("y") ?: return null
                    if (outOfBoundsReason("tap", x, y, display.width, display.height) != null) return null
                    val duration = (params.longField("durationMs") ?: DEFAULT_TAP_MILLIS).coerceIn(0L, MAX_TAP_MILLIS)
                    if (duration == DEFAULT_TAP_MILLIS || duration == 0L) "tap $x $y"
                    else "swipe $x $y $x $y $duration"
                }
                VirtualScreenCommands.SWIPE -> {
                    val x1 = params.intField("x1") ?: return null
                    val y1 = params.intField("y1") ?: return null
                    val x2 = params.intField("x2") ?: return null
                    val y2 = params.intField("y2") ?: return null
                    if (outOfBoundsReason("swipe start", x1, y1, display.width, display.height) != null ||
                        outOfBoundsReason("swipe end", x2, y2, display.width, display.height) != null
                    ) return null
                    val duration = (params.longField("durationMs") ?: DEFAULT_SWIPE_MILLIS).coerceIn(50L, MAX_SWIPE_MILLIS)
                    "swipe $x1 $y1 $x2 $y2 $duration"
                }
                VirtualScreenCommands.KEY -> {
                    val key = params.stringField("key")?.trim()?.lowercase() ?: return null
                    val code = virtualInputKeyCodes[key] ?: return null
                    "keyevent $code"
                }
                else -> return null
            }
            return VirtualFallbackInput("/system/bin/input -d ${display.displayId} $line")
        }
    }
}

internal fun sameVirtualInputDisplay(expected: VirtualScreenState.Online, current: VirtualScreenState): Boolean =
    current is VirtualScreenState.Online && expected.displayId > 0 &&
        current.displayId == expected.displayId && current.instanceId == expected.instanceId

internal fun virtualInputFallback(
    expected: VirtualScreenState.Online,
    current: VirtualScreenState,
    command: String,
    params: JsonObject,
    errorCode: String?,
): VirtualFallbackInput? {
    if (errorCode != INPUT_DENIED_BEFORE_DISPATCH || !sameVirtualInputDisplay(expected, current)) return null
    return VirtualFallbackInput.fromCommand(expected, command, params)
}

internal suspend fun applyVirtualFallbackInput(send: suspend () -> Unit, acknowledge: suspend () -> Unit) {
    send()
    try {
        acknowledge()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException("System input was sent, but the virtual screen could not verify it. Observe before retrying.", error)
    }
}
