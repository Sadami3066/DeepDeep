package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiActions
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult
import io.github.whywood.deepdeep.plugin.gui.GuiWaitOutcome

class AccessibilityScreenBackend(private val actions: GuiActions) : ScreenAccessibilityBackend {
    override val connected: Boolean get() = actions.connected
    override fun invalidateSnapshots() = actions.invalidateSnapshots()
    override fun bounds(displayId: Int, selector: GuiSelector): GuiBounds = actions.bounds(displayId, selector)
    override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot = actions.observe(displayId, maxDepth, maxNodes)
    override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) = actions.tap(displayId, selector, durationMs)
    override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult = actions.setText(displayId, selector, text)
    override suspend fun waitFor(displayId: Int, packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome =
        actions.waitFor(displayId, packageName, text, textGone, timeoutMs)
}
