package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiActions
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import kotlinx.coroutines.CancellationException

class MainScreenController(
    private val actions: GuiActions,
    private val apps: ScreenAppCatalog,
    private val displaySize: () -> Pair<Int, Int>,
    private val virtualStatus: suspend () -> ScreenStatusSnapshot,
    private val shell: MainScreenShell? = null,
    private val screenshotSupported: () -> Boolean = { true },
) : ScreenController {
    override suspend fun status(): ScreenStatusSnapshot {
        val base = try { virtualStatus() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { ScreenStatusSnapshot(actions.connected, actions.connected, "unavailable") }
        val (width, height) = displaySize()
        val keys = when {
            shell?.available == true -> MAIN_SCREEN_KEYS.toList()
            !actions.connected -> emptyList()
            screenshotSupported() -> listOf("back", "home", "recents", "notifications", "enter")
            else -> listOf("back", "home", "recents", "notifications")
        }
        return base.copy(
            accessibilityConnected = actions.connected,
            main = ScreenDisplayInfo(0, width, height, if (actions.connected) BACKEND_ACCESSIBILITY else BACKEND_NONE),
            mainInputAvailable = actions.connected || shell?.available == true,
            mainScreenshotAvailable = actions.connected && screenshotSupported(),
            mainKeys = keys,
        )
    }

    override suspend fun observe(maxDepth: Int, maxNodes: Int, expectedPackage: String?): ScreenObserveResult {
        requireNodes()
        val snapshot = actions.observe(0, maxDepth, maxNodes)
        val pruned = snapshot.usableOn(bounds())
        check(expectedPackage == null || pruned.packageName == expectedPackage) { "Main screen current window is not $expectedPackage; call screen_launch(target=main) explicitly and observe again" }
        return ScreenObserveResult(BACKEND_ACCESSIBILITY, pruned, snapshot.nodes.size - pruned.nodes.size)
    }

    override suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult {
        require(selectors.isNotEmpty()) { "Provide at least one filter" }
        val observed = observe(SCREEN_DEFAULT_MAX_DEPTH, 400, null)
        return ScreenFindResult(observed.backend, observed.snapshot, GuiNodeTree.findAll(observed.snapshot, selectors, limit))
    }

    override suspend fun tap(selector: GuiSelector, durationMs: Int): String {
        if (selector is GuiSelector.Point) {
            invalidTapCoordinate(selector.x, selector.y, bounds())?.let { error(it) }
            if (actions.connected) actions.tap(0, selector, durationMs)
            else {
                val command = if (durationMs >= 400) "swipe ${selector.x} ${selector.y} ${selector.x} ${selector.y} $durationMs" else "tap ${selector.x} ${selector.y}"
                requireShell().execute("input -d 0 $command")
            }
        } else {
            requireNodes()
            actions.tap(0, selector, durationMs)
        }
        return "Tapped main screen ${selector.describeSelector()}"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String {
        invalidSwipeCoordinates(x1, y1, x2, y2, bounds())?.let { error(it) }
        if (actions.connected) actions.swipeMain(x1, y1, x2, y2, durationMs)
        else requireShell().execute("input -d 0 swipe $x1 $y1 $x2 $y2 $durationMs")
        return "Swiped on main screen"
    }

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, selector: GuiSelector?): String {
        val region = if (selector == null) bounds() else { requireNodes(); actions.bounds(0, selector) }
        val path = clampedSwipePath(direction, region, bounds()) ?: error("Scroll area is not visible; observe the main screen again")
        repeat(amount.coerceIn(1, 10)) { swipe(path[0], path[1], path[2], path[3], 300) }
        return "Scrolled main screen $amount times"
    }

    override suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult {
        require(text.isNotEmpty()) { "text must not be empty" }
        if (selector != null) {
            requireNodes()
            val result = actions.setText(0, selector, text)
            return ScreenTextResult(BACKEND_ACCESSIBILITY, result.text, result.verified)
        }
        require(isAsciiText(text)) { "Main screen Unicode input needs screen_observe(target=main) first, then a field ref or fieldText" }
        requireShell().execute("input -d 0 text ${mainShellQuote(text.replace(" ", "%s"))}")
        return ScreenTextResult("system", text, false)
    }

    override suspend fun key(name: String): String {
        require(name in MAIN_SCREEN_KEYS) { "Unsupported main screen key: $name" }
        if (shell?.available == true) {
            val code = mapOf("back" to 4, "home" to 3, "recents" to 187, "notifications" to 83, "volume_up" to 24, "volume_down" to 25,
                "enter" to 66, "delete" to 67, "tab" to 61, "escape" to 111, "space" to 62, "arrow_up" to 19, "arrow_down" to 20, "arrow_left" to 21, "arrow_right" to 22).getValue(name)
            shell.execute("input -d 0 keyevent $code")
        } else { requireNodes(); actions.keyMain(name) }
        return "Sent main screen key $name"
    }

    override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): ScreenWaitOutcome {
        requireNodes()
        val result = actions.waitFor(0, packageName, text, textGone, timeoutMs)
        return ScreenWaitOutcome(result.matched, result.packageName, result.windowTitle)
    }

    override suspend fun screenshot(): ScreenCapture {
        requireNodes()
        val capture = actions.screenshotMain()
        return ScreenCapture(capture.png, capture.width, capture.height, BACKEND_ACCESSIBILITY)
    }

    override suspend fun launch(packageName: String): ScreenLaunchReport {
        if (shell?.available == true) {
            val before = mainTaskDisplays(shell.execute("dumpsys activity activities ${mainShellQuote(packageName)}"), packageName)
            check(before.none { it != 0 }) { "$packageName is running on another screen; the app was not moved, keep using the matching target" }
            val app = apps.app(packageName)
            shell.execute("am start -W --display 0 -n ${mainShellQuote("${app.packageName}/${app.component}")}")
            val after = mainTaskDisplays(shell.execute("dumpsys activity activities ${mainShellQuote(packageName)}"), packageName)
            return ScreenLaunchReport(0 in after, if (0 in after) "Launched $packageName on the main screen" else "Could not confirm the app landed on the main screen; observe again", packageName, 0, if (0 in after) 0 else -1)
        }
        check(virtualStatus().virtualState == "offline") { "The virtual screen is not confirmed stopped; launching on the main screen needs ADB or Root to check the app location and avoid moving virtual screen apps" }
        requireNodes()
        apps.launchOnMain(packageName)
        val reached = actions.waitFor(0, packageName, null, null, 3000).matched
        return ScreenLaunchReport(reached, if (reached) "Launched $packageName on the main screen" else "Main screen launch was not confirmed; observe the current screen", packageName, 0, if (reached) 0 else -1)
    }

    override suspend fun listApps(): List<ScreenApp> = apps.launchableApps()

    private fun bounds(): GuiBounds = displaySize().let { (width, height) ->
        check(width > 0 && height > 0) { "Cannot determine the main screen size; no operation was performed" }
        GuiBounds(0, 0, width, height)
    }
    private fun requireNodes() { check(actions.connected) { "Main screen node reads, Unicode input and screenshots need the accessibility service; enable or reconnect it in settings" } }
    private fun requireShell(): MainScreenShell = shell?.takeIf { it.available } ?: error("This main screen operation needs ADB or Root")
}
