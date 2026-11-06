package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal class SelectionCoordinator {
    var activeMessageId by mutableStateOf<Long?>(null)
        private set

    var resetMessageId by mutableStateOf<Long?>(null)
        private set

    var resetToken by mutableIntStateOf(0)
        private set

    private var selectionBounds: Rect? = null
    private var dismissMenu: (() -> Unit)? = null

    fun activate(messageId: Long, bounds: Rect?) {
        activeMessageId = messageId
        selectionBounds = bounds
    }

    fun updateBounds(messageId: Long, bounds: Rect) {
        if (activeMessageId == messageId) selectionBounds = bounds
    }

    fun updateMenuDismisser(messageId: Long, dismiss: (() -> Unit)?) {
        if (activeMessageId == messageId) dismissMenu = dismiss
    }

    fun deactivate(messageId: Long) {
        if (activeMessageId != messageId) return
        reset()
    }

    fun clear() {
        val active = activeMessageId ?: return
        val dismiss = dismissMenu
        reset()
        dismiss?.invoke()
        resetMessageId = active
        resetToken += 1
    }

    fun isInsideSelectionArea(positionInWindow: Offset, marginPx: Float): Boolean {
        val bounds = selectionBounds ?: return false
        val inflated = Rect(
            left = bounds.left - marginPx,
            top = bounds.top - marginPx,
            right = bounds.right + marginPx,
            bottom = bounds.bottom + marginPx,
        )
        return inflated.contains(positionInWindow)
    }

    private fun reset() {
        activeMessageId = null
        selectionBounds = null
        dismissMenu = null
    }

    fun tokenFor(messageId: Long): Int = if (resetMessageId == messageId) resetToken else 0
}

internal val LocalSelectionCoordinator = staticCompositionLocalOf<SelectionCoordinator?> { null }
