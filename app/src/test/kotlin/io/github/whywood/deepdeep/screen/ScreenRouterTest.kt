package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiGlobalAction
import io.github.whywood.deepdeep.plugin.gui.GuiNode
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScreenshot
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenLaunchReport
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRouterTest {
    private val accessibility = FakeAccessibility()
    private val adb = FakeAdb()
    private val virtual = FakeVirtual()
    private val apps = FakeApps()

    private fun router(
        accessibilityEnabled: () -> Boolean = { accessibility.connected },
    ): ScreenRouter = ScreenRouter(
        accessibility = accessibility,
        adb = adb,
        virtual = virtual,
        apps = apps,
        mainDisplaySize = { 1080 to 2400 },
        accessibilityEnabled = accessibilityEnabled,
    )

    @Test
    fun mainObserveUsesAccessibilityWhenConnected() = runBlocking {
        accessibility.connected = true
        val observed = router().observe(ScreenTarget.MAIN, 8, 120, null)
        assertEquals(BACKEND_ACCESSIBILITY, observed.backend)
        assertEquals(1, accessibility.observeCalls)
        assertEquals(0, adb.observeCalls)
        assertEquals(MAIN_DISPLAY_ID, accessibility.lastObserveDisplayId)
    }

    @Test
    fun mainObserveFallsBackToAdbWhenAccessibilityOff() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Offline
        adb.availableState = true
        val observed = router().observe(ScreenTarget.MAIN, 8, 120, null)
        assertEquals(BACKEND_ADB, observed.backend)
        assertEquals(1, adb.observeCalls)
        assertEquals(0, accessibility.observeCalls)
    }

    @Test
    fun mainAdbObservationRefusesWhileVirtualScreenIsOnline() = runBlocking {
        accessibility.connected = false
        adb.availableState = true
        val error = expectFailure { router().observe(ScreenTarget.MAIN, 8, 120, null) }
        assertTrue(error.message.orEmpty().contains("虚拟屏在线"))
    }

    @Test
    fun mainObserveFailsWhenNoBackendIsAvailable() = runBlocking {
        accessibility.connected = false
        adb.availableState = false
        adb.observeFailure = IllegalStateException("无线调试（ADB）未就绪")
        expectFailure { router().observe(ScreenTarget.MAIN, 8, 120, null) }
        Unit
    }

    @Test
    fun mainObserveRejectsUnexpectedPackage() = runBlocking {
        accessibility.connected = true
        expectFailure { router().observe(ScreenTarget.MAIN, 8, 120, "com.other") }
        Unit
    }

    @Test
    fun virtualCoordinateTapUsesHelperEvenWithoutAccessibility() = runBlocking {
        accessibility.connected = false
        val message = router().tap(ScreenTarget.VIRTUAL, GuiSelector.Point(10, 20), 60)
        assertEquals(10 to 20, virtual.tapped)
        assertNull(accessibility.lastTapSelector)
        assertTrue(message.contains("虚拟屏"))
    }

    @Test
    fun virtualNodeTapUsesAccessibilityDisplay() = runBlocking {
        accessibility.connected = true
        router().tap(ScreenTarget.VIRTUAL, GuiSelector.Text("OK"), 60)
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastTapDisplayId)
        assertEquals(GuiSelector.Text("OK"), accessibility.lastTapSelector)
        assertNull(virtual.tapped)
    }

    @Test
    fun virtualNodeTapWithoutAccessibilityFailsReadable() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().tap(ScreenTarget.VIRTUAL, GuiSelector.Text("OK"), 60) }
        assertTrue(error.message.orEmpty().contains("无障碍"))
    }

    @Test
    fun virtualObserveRequiresAccessibility() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().observe(ScreenTarget.VIRTUAL, 8, 120, null) }
        assertTrue(error.message.orEmpty().contains("无障碍"))
    }

    @Test
    fun offlineVirtualScreenFailsReadable() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        val error = expectFailure { router().observe(ScreenTarget.VIRTUAL, 8, 120, null) }
        assertTrue(error.message.orEmpty().contains("虚拟屏未启动"))
    }

    @Test
    fun mainPointTapPrefersAdbWhenAvailable() = runBlocking {
        accessibility.connected = true
        adb.availableState = true
        router().tap(ScreenTarget.MAIN, GuiSelector.Point(5, 6), 60)
        assertEquals(5 to 6, adb.tapped)
        assertNull(accessibility.lastTapSelector)
    }

    @Test
    fun mainNamedTapWithoutAccessibilityResolvesThroughAdbSnapshot() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Offline
        adb.availableState = true
        router().tap(ScreenTarget.MAIN, GuiSelector.Text("OK"), 60)
        assertEquals(200 to 100, adb.tapped)
    }

    @Test
    fun mainNamedTapUsesAccessibilityWhenConnected() = runBlocking {
        accessibility.connected = true
        router().tap(ScreenTarget.MAIN, GuiSelector.Text("OK"), 60)
        assertEquals(MAIN_DISPLAY_ID, accessibility.lastTapDisplayId)
        assertNull(adb.tapped)
    }

    @Test
    fun mainSwipeUsesAdbAndFailsWithoutIt() = runBlocking {
        adb.availableState = true
        router().swipe(ScreenTarget.MAIN, 1, 2, 3, 4, 300)
        assertEquals(listOf(1, 2, 3, 4, 300), adb.swiped)
        adb.availableState = false
        expectFailure { router().swipe(ScreenTarget.MAIN, 1, 2, 3, 4, 300) }
        Unit
    }

    @Test
    fun virtualSwipeUsesHelper() = runBlocking {
        router().swipe(ScreenTarget.VIRTUAL, 1, 2, 3, 4, 300)
        assertEquals(listOf(1L, 2L, 3L, 4L, 300L), virtual.swiped)
    }

    @Test
    fun scrollOnVirtualScreenSwipesHelper() = runBlocking {
        router().scroll(ScreenTarget.VIRTUAL, GuiScrollDirection.DOWN, 2, null)
        assertEquals(2, virtual.swipeCount)
    }

    @Test
    fun scrollOnMainScreenFallsBackToAccessibilityWithoutAdb() = runBlocking {
        accessibility.connected = true
        adb.availableState = false
        router().scroll(ScreenTarget.MAIN, GuiScrollDirection.DOWN, 1, null)
        assertEquals(GuiScrollDirection.DOWN, accessibility.lastScrollDirection)
    }

    @Test
    fun mainScreenshotRequiresAccessibility() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().screenshot(ScreenTarget.MAIN) }
        assertTrue(error.message.orEmpty().contains("无障碍"))
    }

    @Test
    fun mainScreenshotUsesAccessibility() = runBlocking {
        accessibility.connected = true
        val capture = router().screenshot(ScreenTarget.MAIN)
        assertEquals(BACKEND_ACCESSIBILITY, capture.backend)
        assertEquals(1080, capture.width)
        assertEquals(2400, capture.height)
    }

    @Test
    fun virtualScreenshotUsesHelper() = runBlocking {
        val capture = router().screenshot(ScreenTarget.VIRTUAL)
        assertEquals(BACKEND_HELPER, capture.backend)
        assertEquals(VIRTUAL_DISPLAY_ID, capture.width / 100)
        assertEquals(byteArrayOf(1, 2, 3).toList(), capture.png.toList())
    }

    @Test
    fun virtualLaunchReturnsTaskDisplayDiagnostics() = runBlocking {
        virtual.launchReport = VirtualScreenLaunchReport(
            ok = true,
            message = "已在虚拟屏启动 com.example",
            displayId = VIRTUAL_DISPLAY_ID,
            taskDisplayId = 0,
            landedOnMain = true,
        )
        val report = router().launch(ScreenTarget.VIRTUAL, "com.example")
        assertEquals(ScreenTarget.VIRTUAL, report.target)
        assertEquals(VIRTUAL_DISPLAY_ID, report.displayId)
        assertEquals(0, report.taskDisplayId)
        assertTrue(report.landedOnMain)
        assertEquals(false, report.landedOnExpectedDisplay)
    }

    @Test
    fun mainLaunchUsesAppCatalog() = runBlocking {
        val report = router().launch(ScreenTarget.MAIN, "com.example")
        assertEquals("com.example", apps.launched)
        assertTrue(report.ok)
        assertEquals(MAIN_DISPLAY_ID, report.displayId)
        assertTrue(report.landedOnMain)
    }

    @Test
    fun typeAsciiOnVirtualScreenUsesHelper() = runBlocking {
        val result = router().typeText(ScreenTarget.VIRTUAL, null, "hello")
        assertEquals("hello", virtual.typed)
        assertEquals(BACKEND_HELPER, result.backend)
    }

    @Test
    fun typeUnicodeOnVirtualScreenWithoutAccessibilityFails() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().typeText(ScreenTarget.VIRTUAL, null, "你好") }
        assertTrue(error.message.orEmpty().contains("非 ASCII"))
    }

    @Test
    fun typeUnicodeOnVirtualScreenUsesAccessibilityWithRef() = runBlocking {
        accessibility.connected = true
        val result = router().typeText(ScreenTarget.VIRTUAL, GuiSelector.Ref("1"), "你好")
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastSetTextDisplayId)
        assertEquals("你好", accessibility.lastSetTextValue)
        assertEquals(BACKEND_ACCESSIBILITY, result.backend)
    }

    @Test
    fun typeAsciiOnMainScreenUsesAdb() = runBlocking {
        accessibility.connected = false
        adb.availableState = true
        val result = router().typeText(ScreenTarget.MAIN, null, "hello")
        assertEquals("hello", adb.typed)
        assertEquals(BACKEND_ADB, result.backend)
    }

    @Test
    fun typeUnicodeOnMainScreenWithoutAccessibilityFails() = runBlocking {
        accessibility.connected = false
        adb.availableState = true
        val error = expectFailure { router().typeText(ScreenTarget.MAIN, null, "你好") }
        assertTrue(error.message.orEmpty().contains("非 ASCII"))
    }

    @Test
    fun backKeyUsesAccessibilityGlobalWhenConnected() = runBlocking {
        accessibility.connected = true
        router().key(ScreenTarget.MAIN, "back")
        assertEquals(GuiGlobalAction.BACK, accessibility.lastGlobal)
        assertNull(adb.lastKeyEvent)
    }

    @Test
    fun backKeyFallsBackToAdbWithoutAccessibility() = runBlocking {
        accessibility.connected = false
        adb.availableState = true
        router().key(ScreenTarget.MAIN, "back")
        assertEquals(4, adb.lastKeyEvent)
    }

    @Test
    fun enterKeyUsesAdb() = runBlocking {
        adb.availableState = true
        router().key(ScreenTarget.MAIN, "enter")
        assertEquals(66, adb.lastKeyEvent)
    }

    @Test
    fun virtualKeyUsesHelper() = runBlocking {
        router().key(ScreenTarget.VIRTUAL, "back")
        assertEquals("back", virtual.lastKey)
    }

    @Test
    fun statusReportsBackendsAndVirtualState() = runBlocking {
        adb.availableState = true
        val status = router().status()
        assertTrue(status.accessibilityConnected)
        assertTrue(status.adbAvailable)
        assertEquals(MAIN_DISPLAY_ID, status.main.displayId)
        assertEquals(BACKEND_ACCESSIBILITY, status.main.backend)
        assertEquals("online", status.virtualState)
        assertEquals(VIRTUAL_DISPLAY_ID, status.virtual?.displayId)
        assertEquals(2000, status.virtualUid)
    }

    @Test
    fun waitOnVirtualScreenRequiresAccessibility() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().waitFor(ScreenTarget.VIRTUAL, null, "OK", null, 100) }
        assertTrue(error.message.orEmpty().contains("无障碍"))
    }

    @Test
    fun waitOnMainScreenFallsBackToAdbPolling() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Offline
        adb.availableState = true
        val matched = router().waitFor(ScreenTarget.MAIN, null, "OK", null, 1_000)
        assertTrue(matched)
    }

    @Test
    fun swipePathForDirectionUsesBounds() {
        val bounds = GuiBounds(0, 0, 1000, 2000)
        assertEquals(listOf(500, 1500), swipePathFor(GuiScrollDirection.DOWN, bounds).take(2))
        assertEquals(listOf(500, 500), swipePathFor(GuiScrollDirection.UP, bounds).take(2))
        assertEquals(listOf(800, 1000), swipePathFor(GuiScrollDirection.LEFT, bounds).take(2))
    }

    @Test
    fun snapshotMatchesChecksPackageAndText() {
        val snapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "root",
                bounds = GuiBounds(0, 0, 100, 100),
                children = listOf(
                    GuiNode(
                        className = "android.widget.TextView",
                        bounds = GuiBounds(0, 0, 50, 50),
                        text = "OK",
                    ),
                ),
            ),
            packageName = "com.example",
        )
        assertTrue(snapshotMatches(snapshot, "com.example", "ok", null))
        assertTrue(snapshotMatches(snapshot, null, null, "missing"))
        assertTrue(!snapshotMatches(snapshot, "com.other", null, null))
        assertTrue(!snapshotMatches(snapshot, null, null, "OK"))
    }

    @Test
    fun observeDropsUnusableBoundsAndReportsFilteredCount() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val observed = router().observe(ScreenTarget.MAIN, SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        assertEquals(listOf("0", "2"), observed.snapshot.nodes.map { it.ref })
        assertEquals(1, observed.filteredNodes)
    }

    @Test
    fun findSearchesFullDepthAndFindsDeepNode() = runBlocking {
        accessibility.connected = true
        accessibility.snapshotForDepth = { maxDepth, maxNodes ->
            GuiNodeTree.buildSnapshot(deepRoot(), maxDepth = maxDepth, maxNodes = maxNodes)
        }
        val found = router().find(ScreenTarget.MAIN, listOf(GuiSelector.Text("深层目标")), 20)
        assertEquals(1, found.matches.size)
        assertEquals(SCREEN_DEFAULT_MAX_DEPTH, accessibility.lastObserveMaxDepth)
    }

    @Test
    fun findUsesPrunedSnapshotAndReportsMatches() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val found = router().find(ScreenTarget.MAIN, listOf(GuiSelector.Text("OK")), 20)
        assertEquals(1, found.matches.size)
        assertEquals("2", found.matches.single().ref)
    }

    @Test
    fun scrollWithUnlaidOutRefFailsReadably() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val error = expectFailure {
            router().scroll(ScreenTarget.VIRTUAL, GuiScrollDirection.DOWN, 1, GuiSelector.Ref("1"))
        }
        assertTrue(error.message.orEmpty().contains("找不到滚动目标节点"))
    }

    @Test
    fun scrollPathIsClampedIntoVirtualDisplay() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = wideNodeSnapshot()
        router().scroll(ScreenTarget.VIRTUAL, GuiScrollDirection.RIGHT, 1, GuiSelector.Ref("1"))
        assertEquals(listOf(660L, 600L, 699L, 600L, 300L), virtual.swiped)
    }

    @Test
    fun swipeRejectsCoordinatesOutsideDisplay() = runBlocking {
        val virtualError = expectFailure {
            router().swipe(ScreenTarget.VIRTUAL, 0, 0, 700, 1200, 300)
        }
        assertTrue(virtualError.message.orEmpty().contains("滑动坐标超出屏幕范围"))
        assertTrue(virtualError.message.orEmpty().contains("700×1200"))
        val mainError = expectFailure {
            router().swipe(ScreenTarget.MAIN, 0, 0, 1080, 10, 300)
        }
        assertTrue(mainError.message.orEmpty().contains("1080×2400"))
        val samePoint = expectFailure {
            router().swipe(ScreenTarget.VIRTUAL, 10, 10, 10, 10, 300)
        }
        assertTrue(samePoint.message.orEmpty().contains("起点与终点相同"))
    }

    @Test
    fun tapByPointRejectsCoordinatesOutsideDisplay() = runBlocking {
        val mainError = expectFailure {
            router().tap(ScreenTarget.MAIN, GuiSelector.Point(1080, 10), 60)
        }
        assertTrue(mainError.message.orEmpty().contains("点击坐标超出屏幕范围"))
        val virtualError = expectFailure {
            router().tap(ScreenTarget.VIRTUAL, GuiSelector.Point(10, 1200), 60)
        }
        assertTrue(virtualError.message.orEmpty().contains("700×1200"))
    }

    @Test
    fun adbTapResolvesRefsAgainstPrunedSnapshot() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Offline
        adb.availableState = true
        adb.snapshot = viewPagerSnapshot()
        router().tap(ScreenTarget.MAIN, GuiSelector.Ref("2"), 60)
        assertEquals(200 to 100, adb.tapped)
    }

    @Test
    fun statusSeparatesEnabledFromConnectedAndReportsChannels() = runBlocking {
        accessibility.connected = false
        adb.availableState = true
        val status = router(accessibilityEnabled = { true }).status()
        assertEquals(false, status.accessibilityConnected)
        assertEquals(true, status.accessibilityEnabled)
        assertEquals(BACKEND_ADB, status.main.backend)
        assertEquals(BACKEND_ADB, status.main.nodeBackend)
        assertEquals(BACKEND_ADB, status.main.inputBackend)
        assertEquals(BACKEND_NONE, status.virtual?.nodeBackend)
        assertEquals(BACKEND_HELPER, status.virtual?.inputBackend)
    }

    @Test
    fun statusReportsAccessibilityChannelsWhenConnected() = runBlocking {
        accessibility.connected = true
        adb.availableState = true
        val status = router(accessibilityEnabled = { true }).status()
        assertEquals(BACKEND_ACCESSIBILITY, status.main.nodeBackend)
        assertEquals(BACKEND_ADB, status.main.inputBackend)
        assertEquals(BACKEND_ACCESSIBILITY, status.virtual?.nodeBackend)
        assertEquals(BACKEND_HELPER, status.virtual?.inputBackend)
    }

    @Test
    fun virtualNodeErrorsWhileRebindingAskToWait() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { true }).tap(ScreenTarget.VIRTUAL, GuiSelector.Text("OK"), 60)
        }
        assertTrue(error.message.orEmpty().contains("正在重新绑定"))
        assertTrue(error.message.orEmpty().contains("等待几秒"))
    }

    @Test
    fun virtualNodeErrorsWhenDisabledPointToSettings() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { false }).tap(ScreenTarget.VIRTUAL, GuiSelector.Text("OK"), 60)
        }
        assertTrue(error.message.orEmpty().contains("请到系统设置开启"))
    }

    @Test
    fun mainObserveWhileRebindingWithVirtualOnlineAsksToWait() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { true }).observe(ScreenTarget.MAIN, SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        }
        assertTrue(error.message.orEmpty().contains("正在重新绑定"))
    }

    @Test
    fun statusReportsVirtualTransportFailureAndRebuildNotice() = runBlocking {
        virtual.stateValue = VirtualScreenState.Failed("虚拟屏连接已断开")
        virtual.failure = ScreenVirtualFailure(
            category = VIRTUAL_FAILURE_TRANSPORT,
            message = "虚拟屏连接已断开（连接被对端关闭），自动重连失败",
            recoverable = true,
            reason = "eof",
        )
        virtual.transportNotice = "虚拟屏已重建，旧屏上的应用已丢失"
        val status = router().status()
        assertEquals("failed", status.virtualState)
        assertEquals(VIRTUAL_FAILURE_TRANSPORT, status.virtualFailure?.category)
        assertEquals(true, status.virtualFailure?.recoverable)
        assertEquals("eof", status.virtualFailure?.reason)
        assertEquals("虚拟屏已重建，旧屏上的应用已丢失", status.virtualTransportNotice)
    }

    @Test
    fun failedVirtualTransportErrorSuggestsRelaunch() = runBlocking {
        virtual.stateValue = VirtualScreenState.Failed("连接被对端关闭")
        virtual.failure = ScreenVirtualFailure(
            category = VIRTUAL_FAILURE_TRANSPORT,
            message = "连接被对端关闭",
            recoverable = true,
            reason = "eof",
        )
        val error = expectFailure {
            router().scroll(ScreenTarget.VIRTUAL, GuiScrollDirection.DOWN, 1, null)
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("自动重连已尝试"))
        assertTrue(message.contains("screen_launch"))
        assertTrue(message.contains("eof"))
        assertFalse(message.contains("启动失败"))
    }

    @Test
    fun failedVirtualBackendErrorPointsToPermissions() = runBlocking {
        virtual.stateValue = VirtualScreenState.Failed("没有可用后端")
        virtual.failure = ScreenVirtualFailure(
            category = VIRTUAL_FAILURE_BACKEND,
            message = "没有可用的无线调试（ADB）或 Root 权限",
            recoverable = false,
        )
        val error = expectFailure {
            router().scroll(ScreenTarget.VIRTUAL, GuiScrollDirection.DOWN, 1, null)
        }
        assertTrue(error.message.orEmpty().contains("无线调试/Root 不可用"))
    }

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (expected: Throwable) {
            return expected
        }
        throw AssertionError("expected an exception")
    }

    private class FakeAccessibility : ScreenAccessibilityBackend {
        override var connected = true
        var observeCalls = 0
        var lastObserveDisplayId = -1
        var lastObserveMaxDepth = -1
        var snapshot: GuiSnapshot = testSnapshot()
        var snapshotForDepth: ((Int, Int) -> GuiSnapshot)? = null
        var lastTapDisplayId = -1
        var lastTapSelector: GuiSelector? = null
        var lastSetTextDisplayId = -1
        var lastSetTextValue = ""
        var lastScrollDirection: GuiScrollDirection? = null
        var lastGlobal: GuiGlobalAction? = null
        var waitResult = true

        override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot {
            observeCalls++
            lastObserveDisplayId = displayId
            lastObserveMaxDepth = maxDepth
            return snapshotForDepth?.invoke(maxDepth, maxNodes) ?: snapshot
        }

        override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) {
            lastTapDisplayId = displayId
            lastTapSelector = selector
        }

        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {
            lastScrollDirection = direction
        }

        override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult {
            lastSetTextDisplayId = displayId
            lastSetTextValue = text
            return GuiTextResult(text, true)
        }

        override suspend fun waitFor(
            displayId: Int,
            packageName: String?,
            text: String?,
            textGone: String?,
            timeoutMs: Long,
        ): Boolean = waitResult

        override suspend fun screenshotMain(): GuiScreenshot =
            GuiScreenshot(byteArrayOf(9, 9), 1080, 2400)

        override suspend fun global(action: GuiGlobalAction) {
            lastGlobal = action
        }
    }

    private class FakeAdb : ScreenAdbBackend {
        var availableState = true
        var observeCalls = 0
        var observeFailure: Throwable? = null
        var snapshot: GuiSnapshot = testSnapshot()
        var tapped: Pair<Int, Int>? = null
        var swiped: List<Int>? = null
        var lastKeyEvent: Int? = null
        var typed: String? = null

        override suspend fun available(): Boolean = availableState

        override suspend fun observeMain(maxDepth: Int, maxNodes: Int): GuiSnapshot {
            observeCalls++
            observeFailure?.let { throw it }
            return snapshot
        }

        override suspend fun tap(x: Int, y: Int) {
            tapped = x to y
        }

        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
            swiped = listOf(x1, y1, x2, y2, durationMs.toInt())
        }

        override suspend fun keyEvent(keyCode: Int) {
            lastKeyEvent = keyCode
        }

        override suspend fun typeText(text: String) {
            typed = text
        }

        override suspend fun shell(command: String): String = ""
    }

    private class FakeVirtual : ScreenVirtualBackend {
        var stateValue: VirtualScreenState = VirtualScreenState.Online(
            displayId = VIRTUAL_DISPLAY_ID,
            width = 700,
            height = 1200,
            backend = "adb",
        )
        var failure: ScreenVirtualFailure? = null
        var transportNotice: String? = null
        var tapped: Pair<Int, Int>? = null
        var swiped: List<Long>? = null
        var swipeCount = 0
        var lastKey: String? = null
        var typed: String? = null
        var launchReport = VirtualScreenLaunchReport(
            ok = true,
            message = "ok",
            displayId = VIRTUAL_DISPLAY_ID,
            taskDisplayId = VIRTUAL_DISPLAY_ID,
        )

        override suspend fun state(): VirtualScreenState = stateValue

        override suspend fun tap(x: Int, y: Int): VirtualScreenResult {
            tapped = x to y
            return VirtualScreenResult(true, "已点击")
        }

        override suspend fun swipe(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            durationMs: Long,
        ): VirtualScreenResult {
            swipeCount++
            swiped = listOf(x1.toLong(), y1.toLong(), x2.toLong(), y2.toLong(), durationMs)
            return VirtualScreenResult(true, "已滑动")
        }

        override suspend fun key(name: String): VirtualScreenResult {
            lastKey = name
            return VirtualScreenResult(true, "已按键")
        }

        override suspend fun text(value: String): VirtualScreenResult {
            typed = value
            return VirtualScreenResult(true, "已输入")
        }

        override suspend fun screenshot(): ByteArray? = byteArrayOf(1, 2, 3)

        override suspend fun launch(packageName: String): VirtualScreenLaunchReport = launchReport

        override fun helperUid(): Int? = 2000

        override fun lastFailure(): ScreenVirtualFailure? = failure

        override fun lastTransportNotice(): String? = transportNotice
    }

    private class FakeApps : ScreenAppCatalog {
        var launched: String? = null

        override fun launchableApps(): List<ScreenApp> = listOf(
            ScreenApp("com.example", "Example", "com.example/.Main"),
        )

        override fun launchOnMain(packageName: String): ScreenApp {
            launched = packageName
            return launchableApps().first()
        }
    }

    private companion object {
        const val VIRTUAL_DISPLAY_ID = 7
    }
}

private fun testSnapshot(): GuiSnapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "android.widget.FrameLayout",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = listOf(
                    GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(100, 50, 300, 150),
                        text = "OK",
                        clickable = true,
                    ),
                ),
            ),
    packageName = "com.example",
)

private fun viewPagerSnapshot(): GuiSnapshot = GuiNodeTree.buildSnapshot(
    root = GuiNode(
        className = "android.widget.FrameLayout",
        bounds = GuiBounds(0, 0, 1080, 2400),
        children = listOf(
            GuiNode(
                className = "androidx.viewpager.widget.ViewPager",
                bounds = GuiBounds(79, 2433, 202, 2400),
                text = "未布局页",
            ),
            GuiNode(
                className = "android.widget.Button",
                bounds = GuiBounds(100, 50, 300, 150),
                text = "OK",
                clickable = true,
            ),
        ),
    ),
    maxDepth = SCREEN_DEFAULT_MAX_DEPTH,
    maxNodes = GUI_MAX_NODES,
    packageName = "com.example",
)

private fun deepRoot(): GuiNode {
    var node = GuiNode(
        className = "android.widget.TextView",
        bounds = GuiBounds(10, 10, 100, 50),
        text = "深层目标",
    )
    repeat(19) {
        node = GuiNode(
            className = "android.widget.FrameLayout",
            bounds = GuiBounds(0, 0, 1080, 2400),
            children = listOf(node),
        )
    }
    return node
}

private fun wideNodeSnapshot(): GuiSnapshot = GuiNodeTree.buildSnapshot(
    root = GuiNode(
        className = "android.widget.FrameLayout",
        bounds = GuiBounds(0, 0, 1080, 2400),
        children = listOf(
            GuiNode(
                className = "android.widget.ListView",
                bounds = GuiBounds(600, 100, 900, 1100),
                scrollable = true,
            ),
        ),
    ),
    maxDepth = SCREEN_DEFAULT_MAX_DEPTH,
    maxNodes = GUI_MAX_NODES,
    packageName = "com.example",
)
