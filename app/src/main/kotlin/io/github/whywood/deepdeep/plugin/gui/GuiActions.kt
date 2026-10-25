package io.github.whywood.deepdeep.plugin.gui

class GuiUnavailableException(
    message: String = "无障碍服务未开启",
) : IllegalStateException(message)

data class GuiScreenshot(
    val png: ByteArray,
    val width: Int,
    val height: Int,
)

data class GuiTextResult(
    val text: String,
    val verified: Boolean,
)

interface GuiActions {
    val connected: Boolean

    suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot

    suspend fun tap(selector: GuiSelector, durationMs: Int)

    suspend fun setText(selector: GuiSelector, text: String): GuiTextResult

    suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?)

    suspend fun global(action: GuiGlobalAction)

    suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): Boolean

    suspend fun screenshot(): GuiScreenshot
}

enum class GuiScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class GuiGlobalAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
}
