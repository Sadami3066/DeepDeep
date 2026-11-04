package io.github.whywood.deepdeep.plugin.gui

class GuiUnavailableException(
    message: String = "Accessibility service is not enabled",
) : IllegalStateException(message)

data class GuiTextResult(
    val text: String,
    val verified: Boolean,
)

data class GuiScreenshot(val png: ByteArray, val width: Int, val height: Int)

data class GuiWaitOutcome(
    val matched: Boolean,
    val packageName: String? = null,
    val windowTitle: String? = null,
)

interface GuiActions {
    fun invalidateSnapshots()

    fun bounds(displayId: Int, selector: GuiSelector): GuiBounds

    val connected: Boolean

    suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int, expectedPackage: String? = null): GuiSnapshot

    suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int)

    suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult

    suspend fun waitFor(displayId: Int, packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome

    suspend fun swipeMain(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        throw GuiUnavailableException("Main screen gestures are unavailable")
    }

    suspend fun keyMain(name: String) {
        throw GuiUnavailableException("Main screen keys are unavailable")
    }

    suspend fun screenshotMain(): GuiScreenshot {
        throw GuiUnavailableException("Main screen screenshots are unavailable")
    }
}

enum class GuiScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}
