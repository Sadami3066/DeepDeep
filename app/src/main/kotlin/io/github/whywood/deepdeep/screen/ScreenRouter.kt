package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiWaitOutcome
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

class ScreenRouter(
    private val accessibility: ScreenAccessibilityBackend,
    private val virtual: ScreenVirtualBackend,
    private val apps: ScreenAppCatalog,
    private val accessibilityEnabled: () -> Boolean = { accessibility.connected },
) : ScreenController {
    private var screenInstance: String? = null

    override suspend fun status(): ScreenStatusSnapshot {
        val state = virtual.state()
        val online = state as? VirtualScreenState.Online
        val connected = accessibility.connected
        return ScreenStatusSnapshot(
            accessibilityConnected = connected,
            accessibilityEnabled = accessibilityEnabled(),
            virtualState = when (state) {
                VirtualScreenState.Offline -> "offline"
                VirtualScreenState.Starting -> "starting"
                is VirtualScreenState.Failed -> "failed"
                is VirtualScreenState.Online -> "online"
            },
            virtual = online?.let {
                ScreenDisplayInfo(it.displayId, it.width, it.height, BACKEND_HELPER,
                    if (connected) BACKEND_ACCESSIBILITY else BACKEND_NONE, BACKEND_HELPER)
            },
            virtualBackend = online?.backend,
            virtualUid = virtual.helperUid(),
            virtualFailure = virtual.lastFailure(),
            virtualTransportNotice = virtual.lastTransportNotice(),
            reconnecting = virtual.reconnecting(),
        )
    }

    override suspend fun observe(maxDepth: Int, maxNodes: Int, expectedPackage: String?): ScreenObserveResult {
        val online = requireVirtualOnline()
        requireAccessibility()
        val snapshot = accessibility.observe(online.displayId, maxDepth, maxNodes)
        val pruned = snapshot.usableOn(online.bounds())
        if (expectedPackage != null && pruned.packageName != expectedPackage) {
            throw IllegalStateException("Current window belongs to ${pruned.packageName ?: "an unknown app"}, not $expectedPackage; call screen_launch to start the target app")
        }
        return ScreenObserveResult(BACKEND_ACCESSIBILITY, pruned, snapshot.nodes.size - pruned.nodes.size)
    }

    override suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult {
        require(selectors.isNotEmpty()) { "Provide at least one filter" }
        val observed = observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        return ScreenFindResult(observed.backend, observed.snapshot, GuiNodeTree.findAll(observed.snapshot, selectors, limit))
    }

    override suspend fun tap(selector: GuiSelector, durationMs: Int): String {
        val online = requireVirtualOnline()
        if (selector is GuiSelector.Point) {
            invalidTapCoordinate(selector.x, selector.y, online.bounds())?.let { throw IllegalArgumentException(it) }
            virtual.tap(selector.x, selector.y, durationMs).requireOk()
        } else {
            requireAccessibility()
            accessibility.tap(online.displayId, selector, durationMs)
        }
        return "Tapped virtual screen ${selector.describeSelector()}"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String {
        val online = requireVirtualOnline()
        invalidSwipeCoordinates(x1, y1, x2, y2, online.bounds())?.let { throw IllegalArgumentException(it) }
        virtual.swipe(x1, y1, x2, y2, durationMs).requireOk()
        return "Swiped on virtual screen ($x1, $y1) -> ($x2, $y2)"
    }

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, selector: GuiSelector?): String {
        val online = requireVirtualOnline()
        val bounds = if (selector == null) online.bounds() else {
            requireAccessibility()
            accessibility.bounds(online.displayId, selector)
        }
        val path = clampedSwipePath(direction, bounds, online.bounds())
            ?: throw IllegalArgumentException("Scroll target is not visible or too small to scroll; call screen_observe again")
        val steps = amount.coerceIn(1, 10)
        repeat(steps) {
            requireVirtualOnline()
            virtual.swipe(path[0], path[1], path[2], path[3], 300).requireOk()
        }
        return "Scrolled virtual screen ${direction.name.lowercase()} $steps times"
    }

    override suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult {
        val online = requireVirtualOnline()
        require(text.isNotEmpty()) { "text must not be empty" }
        if (selector != null) {
            requireAccessibility()
            val result = accessibility.setText(online.displayId, selector, text)
            return ScreenTextResult(BACKEND_ACCESSIBILITY, result.text, result.verified)
        }
        require(isAsciiText(text)) { "Non-ASCII text needs accessibility plus a field ref or fieldText; call screen_observe first" }
        virtual.text(text).requireOk()
        return ScreenTextResult(BACKEND_HELPER, text, false)
    }

    override suspend fun key(name: String): String {
        requireVirtualOnline()
        require(name in SCREEN_KEYS) { "Unsupported virtual screen key: $name" }
        virtual.key(name).requireOk()
        return "Sent virtual screen key $name"
    }

    override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): ScreenWaitOutcome {
        val online = requireVirtualOnline()
        requireAccessibility()
        return accessibility.waitFor(online.displayId, packageName, text, textGone, timeoutMs).toScreenWaitOutcome()
    }

    override suspend fun screenshot(): ScreenCapture {
        val online = requireVirtualOnline()
        val png = virtual.screenshot() ?: throw IllegalStateException("Virtual screen has no frame yet: call screen_launch and wait for the first frame")
        return ScreenCapture(png, online.width, online.height, BACKEND_HELPER)
    }

    override suspend fun launch(packageName: String): ScreenLaunchReport {
        val online = requireVirtualOnline()
        require(packageName.isNotBlank()) { "Missing required parameter: packageName" }
        val report = virtual.launch(packageName)
        return ScreenLaunchReport(
            ok = report.ok && report.taskDisplayId == online.displayId,
            message = report.message,
            packageName = packageName,
            displayId = online.displayId,
            taskDisplayId = report.taskDisplayId,
            landedOnMain = report.landedOnMain,
            backend = BACKEND_HELPER,
        )
    }

    override suspend fun listApps(): List<ScreenApp> = apps.launchableApps()

    private suspend fun requireVirtualOnline(): VirtualScreenState.Online {
        val state = virtual.state()
        if (state !is VirtualScreenState.Online) {
            accessibility.invalidateSnapshots()
            screenInstance = null
            throw IllegalStateException(when {
                virtual.reconnecting() -> "Virtual screen is reconnecting; check screen_status shortly"
                state is VirtualScreenState.Starting -> "Virtual screen is starting; retry shortly"
                state is VirtualScreenState.Failed -> "Virtual screen connection or start failed: ${state.message}; check privileges and call virtual_screen(action=start)"
                else -> "Virtual screen is not started: call virtual_screen(action=start) first"
            })
        }
        check(state.displayId > 0) { "Invalid virtual screen display id; refusing to operate the main screen" }
        if (screenInstance != state.instanceId) {
            accessibility.invalidateSnapshots()
            screenInstance = state.instanceId
        }
        return state
    }

    private fun requireAccessibility() {
        check(accessibility.connected) {
            if (accessibilityEnabled()) "Accessibility is enabled but the service is not connected; wait briefly and retry, or toggle DeepDeep accessibility off and on in system settings if it stays disconnected"
            else "Virtual screen node reading and Unicode input need accessibility; enable DeepDeep accessibility in system settings. Coordinate taps, swipes, keys and screenshots still work"
        }
    }

    private fun VirtualScreenState.Online.bounds() = GuiBounds(0, 0, width, height)
}

internal fun swipePathFor(direction: GuiScrollDirection, bounds: GuiBounds): IntArray {
    val centerX = bounds.left + bounds.width / 2
    val centerY = bounds.top + bounds.height / 2
    return when (direction) {
        GuiScrollDirection.DOWN -> intArrayOf(
            centerX,
            bounds.top + (bounds.height * 0.75f).toInt(),
            centerX,
            bounds.top + (bounds.height * 0.25f).toInt(),
        )
        GuiScrollDirection.UP -> intArrayOf(
            centerX,
            bounds.top + (bounds.height * 0.25f).toInt(),
            centerX,
            bounds.top + (bounds.height * 0.75f).toInt(),
        )
        GuiScrollDirection.LEFT -> intArrayOf(
            bounds.left + (bounds.width * 0.8f).toInt(),
            centerY,
            bounds.left + (bounds.width * 0.2f).toInt(),
            centerY,
        )
        GuiScrollDirection.RIGHT -> intArrayOf(
            bounds.left + (bounds.width * 0.2f).toInt(),
            centerY,
            bounds.left + (bounds.width * 0.8f).toInt(),
            centerY,
        )
    }
}

internal fun snapshotMatches(
    snapshot: GuiSnapshot,
    packageName: String?,
    text: String?,
    textGone: String?,
): Boolean {
    val packageMatches = packageName == null || snapshot.packageName == packageName
    val contains = { value: String ->
        snapshot.windowTitle?.contains(value, ignoreCase = true) == true ||
            snapshot.nodes.any { node ->
                node.text?.contains(value, ignoreCase = true) == true ||
                    node.desc?.contains(value, ignoreCase = true) == true
            }
    }
    val textMatches = text == null || contains(text)
    val textGoneMatches = textGone == null || !contains(textGone)
    return packageMatches && textMatches && textGoneMatches
}

private fun GuiWaitOutcome.toScreenWaitOutcome(): ScreenWaitOutcome =
    ScreenWaitOutcome(matched = matched, packageName = packageName, windowTitle = windowTitle)

private fun VirtualScreenResult.requireOk() {
    if (!ok) throw IllegalStateException(message.ifBlank { "Virtual screen operation failed" })
}
