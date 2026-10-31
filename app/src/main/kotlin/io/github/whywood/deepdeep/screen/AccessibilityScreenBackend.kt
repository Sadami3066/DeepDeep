package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiActions
import io.github.whywood.deepdeep.plugin.gui.GuiDisplayActions
import io.github.whywood.deepdeep.plugin.gui.GuiGlobalAction
import io.github.whywood.deepdeep.plugin.gui.GuiScreenshot
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult

class AccessibilityScreenBackend(
    private val mainActions: GuiActions,
    private val displayActions: GuiDisplayActions? = mainActions as? GuiDisplayActions,
) : ScreenAccessibilityBackend {
    override val connected: Boolean
        get() = mainActions.connected

    override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot =
        if (displayId == MAIN_DISPLAY_ID) {
            mainActions.observe(maxDepth, maxNodes)
        } else {
            requireDisplayActions().observe(displayId, maxDepth, maxNodes)
        }

    override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) {
        if (displayId == MAIN_DISPLAY_ID) {
            mainActions.tap(selector, durationMs)
        } else {
            requireDisplayActions().tap(displayId, selector, durationMs)
        }
    }

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {
        mainActions.scroll(direction, amount, target)
    }

    override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult =
        if (displayId == MAIN_DISPLAY_ID) {
            mainActions.setText(selector, text)
        } else {
            requireDisplayActions().setText(displayId, selector, text)
        }

    override suspend fun waitFor(
        displayId: Int,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): Boolean = if (displayId == MAIN_DISPLAY_ID) {
        mainActions.waitFor(packageName, text, textGone, timeoutMs)
    } else {
        requireDisplayActions().waitFor(displayId, packageName, text, textGone, timeoutMs)
    }

    override suspend fun screenshotMain(): GuiScreenshot = mainActions.screenshot()

    override suspend fun global(action: GuiGlobalAction) {
        mainActions.global(action)
    }

    private fun requireDisplayActions(): GuiDisplayActions = displayActions
        ?: throw IllegalStateException("无障碍服务不支持按屏读取：请到系统设置关闭再重新开启 DeepDeep 无障碍服务")
}
