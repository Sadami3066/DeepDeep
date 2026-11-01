package io.github.whywood.deepdeep.plugin.gui

class GuiUnavailableException(
    message: String = "无障碍服务未开启",
) : IllegalStateException(message)

data class GuiTextResult(
    val text: String,
    val verified: Boolean,
)

data class GuiWaitOutcome(
    val matched: Boolean,
    val packageName: String? = null,
    val windowTitle: String? = null,
)

interface GuiActions {
    fun invalidateSnapshots()

    fun bounds(displayId: Int, selector: GuiSelector): GuiBounds

    val connected: Boolean

    suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot

    suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int)

    suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult

    suspend fun waitFor(displayId: Int, packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome
}

enum class GuiScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}
