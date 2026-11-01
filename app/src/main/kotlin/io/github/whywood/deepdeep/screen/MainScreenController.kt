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
        check(expectedPackage == null || pruned.packageName == expectedPackage) { "主屏当前窗口不属于 $expectedPackage；请显式 screen_launch(target=main) 后重新观察" }
        return ScreenObserveResult(BACKEND_ACCESSIBILITY, pruned, snapshot.nodes.size - pruned.nodes.size)
    }

    override suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult {
        require(selectors.isNotEmpty()) { "需要至少一个查找条件" }
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
        return "已点击主屏 ${selector.describeSelector()}"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String {
        invalidSwipeCoordinates(x1, y1, x2, y2, bounds())?.let { error(it) }
        if (actions.connected) actions.swipeMain(x1, y1, x2, y2, durationMs)
        else requireShell().execute("input -d 0 swipe $x1 $y1 $x2 $y2 $durationMs")
        return "已在主屏滑动"
    }

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, selector: GuiSelector?): String {
        val region = if (selector == null) bounds() else { requireNodes(); actions.bounds(0, selector) }
        val path = clampedSwipePath(direction, region, bounds()) ?: error("滚动区域不可见，请重新观察主屏")
        repeat(amount.coerceIn(1, 10)) { swipe(path[0], path[1], path[2], path[3], 300) }
        return "已在主屏滚动 $amount 次"
    }

    override suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult {
        require(text.isNotEmpty()) { "text 不能为空" }
        if (selector != null) {
            requireNodes()
            val result = actions.setText(0, selector, text)
            return ScreenTextResult(BACKEND_ACCESSIBILITY, result.text, result.verified)
        }
        require(isAsciiText(text)) { "主屏中文输入需要先 screen_observe(target=main)，再指定输入框 ref 或 fieldText" }
        requireShell().execute("input -d 0 text ${mainShellQuote(text.replace(" ", "%s"))}")
        return ScreenTextResult("system", text, false)
    }

    override suspend fun key(name: String): String {
        require(name in MAIN_SCREEN_KEYS) { "不支持的主屏按键：$name" }
        if (shell?.available == true) {
            val code = mapOf("back" to 4, "home" to 3, "recents" to 187, "notifications" to 83, "volume_up" to 24, "volume_down" to 25,
                "enter" to 66, "delete" to 67, "tab" to 61, "escape" to 111, "space" to 62, "arrow_up" to 19, "arrow_down" to 20, "arrow_left" to 21, "arrow_right" to 22).getValue(name)
            shell.execute("input -d 0 keyevent $code")
        } else { requireNodes(); actions.keyMain(name) }
        return "已向主屏发送按键 $name"
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
            check(before.none { it != 0 }) { "$packageName 正在另一块屏幕运行，未移动该应用；请继续使用对应 target" }
            val app = apps.app(packageName)
            shell.execute("am start -W --display 0 -n ${mainShellQuote("${app.packageName}/${app.component}")}")
            val after = mainTaskDisplays(shell.execute("dumpsys activity activities ${mainShellQuote(packageName)}"), packageName)
            return ScreenLaunchReport(0 in after, if (0 in after) "已在主屏启动 $packageName" else "未能确认应用落在主屏，请重新观察", packageName, 0, if (0 in after) 0 else -1)
        }
        check(virtualStatus().virtualState == "offline") { "虚拟屏当前未确认停止，主屏启动需要 ADB 或 Root 检查应用位置，避免移动虚拟屏应用" }
        requireNodes()
        apps.launchOnMain(packageName)
        val reached = actions.waitFor(0, packageName, null, null, 3000).matched
        return ScreenLaunchReport(reached, if (reached) "已在主屏启动 $packageName" else "未确认主屏启动成功；请观察当前页面", packageName, 0, if (reached) 0 else -1)
    }

    override suspend fun listApps(): List<ScreenApp> = apps.launchableApps()

    private fun bounds(): GuiBounds = displaySize().let { (width, height) ->
        check(width > 0 && height > 0) { "无法确定主屏尺寸，未执行操作" }
        GuiBounds(0, 0, width, height)
    }
    private fun requireNodes() { check(actions.connected) { "主屏节点、中文输入与截图需要连接无障碍服务；请到权限设置开启或重新连接" } }
    private fun requireShell(): MainScreenShell = shell?.takeIf { it.available } ?: error("此主屏操作需要可用的 ADB 或 Root")
}
