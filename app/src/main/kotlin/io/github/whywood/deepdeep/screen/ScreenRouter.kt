package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiGlobalAction
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiWaitOutcome
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import kotlinx.coroutines.delay

class ScreenRouter(
    private val accessibility: ScreenAccessibilityBackend,
    private val adb: ScreenAdbBackend,
    private val virtual: ScreenVirtualBackend,
    private val apps: ScreenAppCatalog,
    private val mainDisplaySize: () -> Pair<Int, Int>,
    private val accessibilityEnabled: () -> Boolean = { accessibility.connected },
) : ScreenController {
    override suspend fun status(): ScreenStatusSnapshot {
        val accessibilityConnected = runCatching { accessibility.connected }.getOrDefault(false)
        val accessibilityOn = runCatching { accessibilityEnabled() }.getOrDefault(false)
        val virtualState = runCatching { virtual.state() }.getOrElse { VirtualScreenState.Offline }
        val online = virtualState as? VirtualScreenState.Online
        val adbAvailable = runCatching { adb.available() }.getOrDefault(false)
        val size = mainDisplaySize()
        val mainNodeBackend = when {
            accessibilityConnected -> BACKEND_ACCESSIBILITY
            adbAvailable && online == null -> BACKEND_ADB
            else -> BACKEND_NONE
        }
        val mainInputBackend = when {
            adbAvailable -> BACKEND_ADB
            accessibilityConnected -> BACKEND_ACCESSIBILITY
            else -> BACKEND_NONE
        }
        return ScreenStatusSnapshot(
            accessibilityConnected = accessibilityConnected,
            accessibilityEnabled = accessibilityOn,
            adbAvailable = adbAvailable,
            main = ScreenDisplayInfo(
                target = ScreenTarget.MAIN,
                displayId = MAIN_DISPLAY_ID,
                width = size.first,
                height = size.second,
                backend = mainNodeBackend,
                nodeBackend = mainNodeBackend,
                inputBackend = mainInputBackend,
            ),
            virtualState = virtualStateName(virtualState),
            virtual = online?.let {
                ScreenDisplayInfo(
                    target = ScreenTarget.VIRTUAL,
                    displayId = it.displayId,
                    width = it.width,
                    height = it.height,
                    backend = BACKEND_HELPER,
                    nodeBackend = if (accessibilityConnected) BACKEND_ACCESSIBILITY else BACKEND_NONE,
                    inputBackend = BACKEND_HELPER,
                )
            },
            virtualBackend = online?.backend,
            virtualUid = runCatching { virtual.helperUid() }.getOrNull(),
            virtualFailure = runCatching { virtual.lastFailure() }.getOrNull(),
            virtualTransportNotice = runCatching { virtual.lastTransportNotice() }.getOrNull(),
        )
    }

    override suspend fun observe(
        target: ScreenTarget,
        maxDepth: Int,
        maxNodes: Int,
        expectedPackage: String?,
    ): ScreenObserveResult {
        val observed = observeRaw(target, maxDepth, maxNodes)
        val pruned = observed.snapshot.usableOn(displayBounds(target))
        val result = ScreenObserveResult(
            backend = observed.backend,
            snapshot = pruned,
            filteredNodes = observed.snapshot.nodes.size - pruned.nodes.size,
        )
        val currentPackage = result.snapshot.packageName
        if (expectedPackage != null && currentPackage != null && currentPackage != expectedPackage) {
            throw IllegalStateException(
                "当前窗口属于 $currentPackage，不是 $expectedPackage；请先切换到目标应用或省略 packageName",
            )
        }
        return result
    }

    private suspend fun observeRaw(target: ScreenTarget, maxDepth: Int, maxNodes: Int): ScreenObserveResult = when (target) {
        ScreenTarget.MAIN -> if (accessibility.connected) {
            ScreenObserveResult(BACKEND_ACCESSIBILITY, accessibility.observe(MAIN_DISPLAY_ID, maxDepth, maxNodes))
        } else {
            ensureAdbMainObservationAllowed()
            ScreenObserveResult(BACKEND_ADB, adb.observeMain(maxDepth, maxNodes))
        }
        ScreenTarget.VIRTUAL -> {
            val online = requireVirtualOnline()
            if (!accessibility.connected) throw virtualAccessibilityError()
            ScreenObserveResult(BACKEND_ACCESSIBILITY, accessibility.observe(online.displayId, maxDepth, maxNodes))
        }
    }

    private suspend fun displayBounds(target: ScreenTarget): GuiBounds = when (target) {
        ScreenTarget.MAIN -> mainBounds()
        ScreenTarget.VIRTUAL -> {
            val online = requireVirtualOnline()
            GuiBounds(0, 0, online.width, online.height)
        }
    }

    override suspend fun find(
        target: ScreenTarget,
        selectors: List<GuiSelector>,
        limit: Int,
    ): ScreenFindResult {
        if (selectors.isEmpty()) throw IllegalArgumentException("需要提供至少一个查找条件")
        val observed = observe(target, SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        val matches = GuiNodeTree.findAll(observed.snapshot, selectors, limit)
        return ScreenFindResult(observed.backend, observed.snapshot, matches)
    }

    override suspend fun tap(target: ScreenTarget, selector: GuiSelector, durationMs: Int): String {
        if (selector is GuiSelector.Point) {
            val display = displayBounds(target)
            invalidTapCoordinate(selector.x, selector.y, display)?.let { throw IllegalArgumentException(it) }
        }
        return when (target) {
            ScreenTarget.MAIN -> tapMain(selector, durationMs)
            ScreenTarget.VIRTUAL -> tapVirtual(selector, durationMs)
        }
    }

    override suspend fun swipe(
        target: ScreenTarget,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long,
    ): String {
        val display = displayBounds(target)
        invalidSwipeCoordinates(x1, y1, x2, y2, display)?.let { throw IllegalArgumentException(it) }
        return when (target) {
            ScreenTarget.MAIN -> {
                if (!adb.available()) {
                    throw IllegalStateException("主屏滑动需要无线调试（ADB）连接：请到权限设置完成配对；滚动请改用 screen_scroll")
                }
                adb.swipe(x1, y1, x2, y2, durationMs)
                "已通过 ADB 在主屏滑动 ($x1, $y1) → ($x2, $y2)"
            }
            ScreenTarget.VIRTUAL -> {
                requireVirtualOnline()
                virtual.swipe(x1, y1, x2, y2, durationMs).requireOk()
                "已在虚拟屏滑动 ($x1, $y1) → ($x2, $y2)"
            }
        }
    }

    override suspend fun scroll(
        target: ScreenTarget,
        direction: GuiScrollDirection,
        amount: Int,
        selector: GuiSelector?,
    ): String {
        val steps = amount.coerceIn(1, MAX_SCROLL_AMOUNT)
        val bounds = when {
            selector != null -> resolveBounds(target, selector)
                ?: throw IllegalArgumentException(
                    "找不到滚动目标节点：ref 可能已失效、节点未布局或已移出屏幕；请重新 screen_observe 后重试",
                )
            target == ScreenTarget.MAIN -> mainBounds()
            else -> {
                val online = requireVirtualOnline()
                GuiBounds(0, 0, online.width, online.height)
            }
        }
        return when (target) {
            ScreenTarget.MAIN -> {
                if (adb.available()) {
                    swipeWithin(target, direction, steps, bounds)
                } else if (accessibility.connected) {
                    accessibility.scroll(direction, steps, selector)
                    "已通过无障碍向主屏 ${direction.name.lowercase()} 滚动 $steps 次"
                } else {
                    throw mainAccessError()
                }
            }
            ScreenTarget.VIRTUAL -> {
                requireVirtualOnline()
                swipeWithin(target, direction, steps, bounds)
            }
        }
    }

    override suspend fun typeText(
        target: ScreenTarget,
        selector: GuiSelector?,
        text: String,
    ): ScreenTextResult {
        if (text.isEmpty()) throw IllegalArgumentException("text 不能为空")
        return when (target) {
            ScreenTarget.MAIN -> typeMain(selector, text)
            ScreenTarget.VIRTUAL -> typeVirtual(selector, text)
        }
    }

    override suspend fun key(target: ScreenTarget, name: String): String = when (target) {
        ScreenTarget.MAIN -> keyMain(name)
        ScreenTarget.VIRTUAL -> {
            requireVirtualOnline()
            val normalized = name.trim().lowercase()
            if (normalized.isEmpty()) throw IllegalArgumentException("缺少按键名 key")
            virtual.key(normalized).requireOk()
            "已向虚拟屏发送按键 $normalized"
        }
    }

    override suspend fun waitFor(
        target: ScreenTarget,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): ScreenWaitOutcome = when (target) {
        ScreenTarget.MAIN -> waitMain(packageName, text, textGone, timeoutMs)
        ScreenTarget.VIRTUAL -> {
            val online = requireVirtualOnline()
            if (!accessibility.connected) throw virtualAccessibilityError()
            accessibility.waitFor(online.displayId, packageName, text, textGone, timeoutMs).toScreenWaitOutcome()
        }
    }

    override suspend fun screenshot(target: ScreenTarget): ScreenCapture = when (target) {
        ScreenTarget.MAIN -> {
            if (!accessibility.connected) {
                throw IllegalStateException(
                    if (accessibilityRebinding()) {
                        "主屏截图需要无障碍服务（Android 11+）：无障碍服务正在重新绑定，请等待几秒后重试"
                    } else {
                        "主屏截图需要无障碍服务（Android 11+）：请到系统设置开启 DeepDeep 无障碍"
                    },
                )
            }
            val shot = accessibility.screenshotMain()
            ScreenCapture(shot.png, shot.width, shot.height, BACKEND_ACCESSIBILITY)
        }
        ScreenTarget.VIRTUAL -> {
            val online = requireVirtualOnline()
            val png = virtual.screenshot()
                ?: throw IllegalStateException("虚拟屏暂无可用画面：请先启动应用并等待首帧")
            ScreenCapture(png, online.width, online.height, BACKEND_HELPER)
        }
    }

    override suspend fun launch(target: ScreenTarget, packageName: String): ScreenLaunchReport {
        val name = packageName.trim()
        if (name.isEmpty()) throw IllegalArgumentException("缺少包名 packageName")
        return when (target) {
            ScreenTarget.MAIN -> {
                val app = apps.launchOnMain(name)
                ScreenLaunchReport(
                    ok = true,
                    message = "已在主屏启动 ${app.label}（$name）",
                    target = ScreenTarget.MAIN,
                    packageName = name,
                    displayId = MAIN_DISPLAY_ID,
                    taskDisplayId = MAIN_DISPLAY_ID,
                    landedOnMain = true,
                    backend = "package_manager",
                )
            }
            ScreenTarget.VIRTUAL -> {
                val online = requireVirtualOnline()
                val report = virtual.launch(name)
                ScreenLaunchReport(
                    ok = report.ok,
                    message = report.message,
                    target = ScreenTarget.VIRTUAL,
                    packageName = name,
                    displayId = report.displayId.takeIf { it >= 0 } ?: online.displayId,
                    taskDisplayId = report.taskDisplayId,
                    landedOnMain = report.landedOnMain,
                    backend = BACKEND_HELPER,
                )
            }
        }
    }

    override suspend fun listApps(target: ScreenTarget): List<ScreenApp> = when (target) {
        ScreenTarget.MAIN -> apps.launchableApps()
        ScreenTarget.VIRTUAL -> {
            requireVirtualOnline()
            apps.launchableApps()
        }
    }

    private suspend fun tapMain(selector: GuiSelector, durationMs: Int): String {
        if (selector is GuiSelector.Point) {
            return if (adb.available()) {
                adb.tap(selector.x, selector.y)
                "已通过 ADB 点击主屏 (${selector.x}, ${selector.y})"
            } else {
                accessibility.tap(MAIN_DISPLAY_ID, selector, durationMs)
                "已通过无障碍点击主屏 (${selector.x}, ${selector.y})"
            }
        }
        if (accessibility.connected) {
            accessibility.tap(MAIN_DISPLAY_ID, selector, durationMs)
            return "已通过无障碍点击主屏 ${selector.describeSelector()}"
        }
        if (!adb.available()) throw mainAccessError()
        ensureAdbMainObservationAllowed()
        val display = mainBounds()
        val snapshot = adb.observeMain(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES).usableOn(display)
        val node = findScreenNode(snapshot, selector)
            ?: throw IllegalArgumentException("界面上找不到匹配的节点，请先 screen_observe 后重试")
        val (x, y) = node.bounds.centerWithin(display)
        adb.tap(x, y)
        return "已通过 ADB 点击主屏 ${selector.describeSelector()}（${node.bounds.format()}）"
    }

    private suspend fun tapVirtual(selector: GuiSelector, durationMs: Int): String {
        val online = requireVirtualOnline()
        if (selector is GuiSelector.Point) {
            virtual.tap(selector.x, selector.y).requireOk()
            return "已在虚拟屏点击 (${selector.x}, ${selector.y})"
        }
        if (!accessibility.connected) throw virtualAccessibilityError()
        accessibility.tap(online.displayId, selector, durationMs)
        return "已通过无障碍点击虚拟屏 ${selector.describeSelector()}"
    }

    private suspend fun typeMain(selector: GuiSelector?, text: String): ScreenTextResult {
        if (selector != null) {
            if (!accessibility.connected) {
                throw IllegalStateException(
                    if (accessibilityRebinding()) {
                        "主屏按节点输入需要无障碍服务与输入框 ref：无障碍服务正在重新绑定，请等待几秒后重试"
                    } else {
                        "主屏按节点输入需要无障碍服务与输入框 ref：请开启无障碍后重试；不能把文本盲注入当前焦点"
                    },
                )
            }
            val outcome = accessibility.setText(MAIN_DISPLAY_ID, selector, text)
            return ScreenTextResult(BACKEND_ACCESSIBILITY, outcome.text, outcome.verified)
        }
        if (!isAsciiText(text)) {
            throw IllegalStateException(
                "主屏输入非 ASCII 文本需要输入框 ref 或 fieldText（经无障碍输入）：请先 screen_observe 获取输入框 ref 后重试",
            )
        }
        if (!adb.available()) {
            throw IllegalStateException(
                if (accessibility.connected) {
                    "直接注入文本需要无线调试（ADB）；请提供输入框 ref 或 fieldText 以通过无障碍输入"
                } else {
                    "直接注入文本需要无线调试（ADB）；请到权限设置完成配对，或提供输入框 ref 或 fieldText 并使用无障碍输入"
                },
            )
        }
        adb.typeText(text)
        return ScreenTextResult(BACKEND_ADB, text, false)
    }

    private suspend fun typeVirtual(selector: GuiSelector?, text: String): ScreenTextResult {
        val online = requireVirtualOnline()
        if (selector != null) {
            if (!accessibility.connected) throw virtualAccessibilityError()
            val outcome = accessibility.setText(online.displayId, selector, text)
            return ScreenTextResult(BACKEND_ACCESSIBILITY, outcome.text, outcome.verified)
        }
        if (!isAsciiText(text)) {
            throw IllegalStateException("虚拟屏输入非 ASCII 文本需要无障碍服务与输入框 ref：当前只能注入 ASCII 按键")
        }
        virtual.text(text).requireOk()
        return ScreenTextResult(BACKEND_HELPER, text, false)
    }

    private suspend fun keyMain(name: String): String {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) throw IllegalArgumentException("缺少按键名 key")
        val global = MAIN_GLOBAL_ACTIONS[normalized]
        if (global != null && accessibility.connected) {
            accessibility.global(global)
            return "已通过无障碍在主屏执行 $normalized"
        }
        val adbAvailable = adb.available()
        val keyCode = MAIN_KEY_CODES[normalized]
        if (keyCode != null && adbAvailable) {
            adb.keyEvent(keyCode)
            return "已通过 ADB 在主屏发送按键 $normalized"
        }
        if (normalized == KEY_NOTIFICATIONS && adbAvailable) {
            adb.shell("cmd statusbar expand-notifications")
            return "已通过 ADB 打开通知栏"
        }
        throw IllegalStateException(
            "主屏按键 $name 需要无线调试（ADB）连接：请到权限设置完成配对" +
                if (global != null) "，或开启无障碍服务后用同一按键重试" else "",
        )
    }

    private suspend fun waitMain(
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): ScreenWaitOutcome {
        if (accessibility.connected) {
            return accessibility.waitFor(MAIN_DISPLAY_ID, packageName, text, textGone, timeoutMs).toScreenWaitOutcome()
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        ensureAdbMainObservationAllowed()
        var current = ScreenWaitOutcome(matched = false)
        while (true) {
            val snapshot = runCatching { adb.observeMain(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES) }.getOrNull()
            if (snapshot != null) {
                current = ScreenWaitOutcome(false, snapshot.packageName, snapshot.windowTitle)
                if (snapshotMatches(snapshot, packageName, text, textGone)) return current.copy(matched = true)
            }
            if (System.currentTimeMillis() >= deadline) return current
            delay(WAIT_POLL_MS)
        }
    }

    private suspend fun resolveBounds(target: ScreenTarget, selector: GuiSelector): GuiBounds? = runCatching {
        val snapshot: GuiSnapshot = when (target) {
            ScreenTarget.MAIN -> if (accessibility.connected) {
                accessibility.observe(MAIN_DISPLAY_ID, SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES)
            } else {
                ensureAdbMainObservationAllowed()
                adb.observeMain(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES)
            }
            ScreenTarget.VIRTUAL -> {
                val online = requireVirtualOnline()
                if (!accessibility.connected) return null
                accessibility.observe(online.displayId, SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES)
            }
        }
        findScreenNode(snapshot.usableOn(displayBounds(target)), selector)?.bounds
    }.getOrNull()

    private suspend fun swipeWithin(
        target: ScreenTarget,
        direction: GuiScrollDirection,
        steps: Int,
        bounds: GuiBounds,
    ): String {
        val path = clampedSwipePath(direction, bounds, displayBounds(target))
            ?: throw IllegalStateException(
                "滚动目标的坐标超出屏幕或可滑区域太小，无法生成有效滑动：" +
                    "请重新 screen_observe 确认节点，或改用 screen_swipe 按坐标滑动",
            )
        repeat(steps) {
            when (target) {
                ScreenTarget.MAIN -> adb.swipe(path[0], path[1], path[2], path[3], SCROLL_DURATION_MS)
                ScreenTarget.VIRTUAL -> virtual.swipe(path[0], path[1], path[2], path[3], SCROLL_DURATION_MS)
                    .requireOk()
            }
        }
        return "已在${screenLabel(target)}向 ${direction.name.lowercase()} 滚动 $steps 次"
    }

    private fun screenLabel(target: ScreenTarget): String = when (target) {
        ScreenTarget.MAIN -> "主屏"
        ScreenTarget.VIRTUAL -> "虚拟屏"
    }

    private fun mainBounds(): GuiBounds {
        val size = mainDisplaySize()
        return GuiBounds(0, 0, size.first, size.second)
    }

    private suspend fun requireVirtualOnline(): VirtualScreenState.Online =
        when (val state = runCatching { virtual.state() }.getOrElse { VirtualScreenState.Offline }) {
            is VirtualScreenState.Online -> state
            VirtualScreenState.Starting -> throw IllegalStateException("虚拟屏正在启动，请稍后重试")
            is VirtualScreenState.Failed -> throw IllegalStateException(virtualFailureMessage(state))
            VirtualScreenState.Offline -> throw IllegalStateException("虚拟屏未启动：请先调用 virtual_screen(action=start)")
        }

    private fun virtualFailureMessage(state: VirtualScreenState.Failed): String {
        val failure = runCatching { virtual.lastFailure() }.getOrNull()
            ?: return "虚拟屏启动失败：${state.message}"
        return "${virtualFailureHint(failure.category)}：${failure.message}"
    }

    private fun accessibilityRebinding(): Boolean = runCatching { accessibilityEnabled() }.getOrDefault(false)

    private fun virtualAccessibilityError(): IllegalStateException = if (accessibilityRebinding()) {
        IllegalStateException(
            "无障碍服务正在重新绑定（系统设置里已开启）：请等待几秒后重试；" +
                "期间虚拟屏只能坐标点击、滑动、按键与截图",
        )
    } else {
        IllegalStateException(
            "虚拟屏的节点读取与文本输入需要无障碍服务：请到系统设置开启 DeepDeep 无障碍；" +
                "无障碍关闭时只能用坐标点击、滑动、按键与截图",
        )
    }

    private fun mainAccessError(): IllegalStateException = if (accessibilityRebinding()) {
        IllegalStateException(
            "主屏操作的无障碍服务正在重新绑定（系统设置里已开启）：请等待几秒后重试；" +
                "也可以到权限设置完成 ADB 配对",
        )
    } else {
        IllegalStateException("主屏操作需要无障碍服务或无线调试（ADB）连接：请到权限设置完成配对，或开启无障碍服务")
    }

    private suspend fun ensureAdbMainObservationAllowed() {
        val state = runCatching { virtual.state() }.getOrElse { VirtualScreenState.Offline }
        if (state is VirtualScreenState.Online) {
            throw IllegalStateException(
                if (accessibilityRebinding()) {
                    "主屏节点读取需要无障碍：无障碍服务正在重新绑定，请等待几秒后重试"
                } else {
                    "主屏节点读取需要无障碍：虚拟屏在线时无法通过无线调试（ADB）读取主屏节点；请开启无障碍后重试"
                },
            )
        }
    }

    private fun virtualStateName(state: VirtualScreenState): String = when (state) {
        VirtualScreenState.Offline -> "offline"
        VirtualScreenState.Starting -> "starting"
        is VirtualScreenState.Failed -> "failed"
        is VirtualScreenState.Online -> "online"
    }

    private companion object {
        const val MAX_SCROLL_AMOUNT = 10
        const val SCROLL_DURATION_MS = 300L
        const val WAIT_POLL_MS = 500L
        const val KEY_NOTIFICATIONS = "notifications"

        val MAIN_GLOBAL_ACTIONS: Map<String, GuiGlobalAction> = mapOf(
            "back" to GuiGlobalAction.BACK,
            "home" to GuiGlobalAction.HOME,
            "recents" to GuiGlobalAction.RECENTS,
            "notifications" to GuiGlobalAction.NOTIFICATIONS,
        )

        val MAIN_KEY_CODES: Map<String, Int> = mapOf(
            "back" to 4,
            "home" to 3,
            "recents" to 187,
            "enter" to 66,
            "delete" to 67,
            "tab" to 61,
            "escape" to 111,
            "space" to 62,
            "arrow_up" to 19,
            "arrow_down" to 20,
            "arrow_left" to 21,
            "arrow_right" to 22,
            "volume_up" to 24,
            "volume_down" to 25,
        )
    }
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
    if (!ok) throw IllegalStateException(message.ifBlank { "虚拟屏操作失败" })
}
