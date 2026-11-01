package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNode
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult
import io.github.whywood.deepdeep.plugin.gui.GuiWaitOutcome
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
    private val virtual = FakeVirtual()
    private val apps = FakeApps()

    private fun router(
        accessibilityEnabled: () -> Boolean = { accessibility.connected },
    ): ScreenRouter = ScreenRouter(
        accessibility = accessibility,
        virtual = virtual,
        apps = apps,
        accessibilityEnabled = accessibilityEnabled,
    )

    @Test
    fun observeUsesVirtualAccessibilityDisplay() = runBlocking {
        accessibility.connected = true
        val observed = router().observe(8, 120, null)
        assertEquals(BACKEND_ACCESSIBILITY, observed.backend)
        assertEquals(1, accessibility.observeCalls)
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastObserveDisplayId)
    }

    @Test
    fun offlineObserveRejectsEvenWithAccessibility() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        assertTrue(expectFailure { router().observe(8, 120, null) }.message.orEmpty().contains("virtual_screen(action=start)"))
        assertEquals(0, accessibility.observeCalls)
    }

    @Test
    fun observeWithoutAccessibilityDoesNotFallBack() = runBlocking {
        accessibility.connected = false
        assertTrue(expectFailure { router().observe(8, 120, null) }.message.orEmpty().contains("accessibility"))
        assertEquals(0, accessibility.observeCalls)
    }

    @Test
    fun startingObserveReportsStarting() = runBlocking {
        virtual.stateValue = VirtualScreenState.Starting
        assertTrue(expectFailure { router().observe(8, 120, null) }.message.orEmpty().contains("starting"))
    }

    @Test
    fun observeRejectsUnexpectedPackage() = runBlocking {
        accessibility.connected = true
        expectFailure { router().observe(8, 120, "com.other") }
        Unit
    }

    @Test
    fun virtualCoordinateTapUsesHelperEvenWithoutAccessibility() = runBlocking {
        accessibility.connected = false
        val message = router().tap(GuiSelector.Point(10, 20), 60)
        assertEquals(10 to 20, virtual.tapped)
        assertNull(accessibility.lastTapSelector)
        assertTrue(message.contains("virtual screen"))
    }

    @Test
    fun virtualNodeTapUsesAccessibilityDisplay() = runBlocking {
        accessibility.connected = true
        router().tap(GuiSelector.Text("OK"), 60)
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastTapDisplayId)
        assertEquals(GuiSelector.Text("OK"), accessibility.lastTapSelector)
        assertNull(virtual.tapped)
    }

    @Test
    fun virtualNodeTapWithoutAccessibilityFailsReadable() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().tap(GuiSelector.Text("OK"), 60) }
        assertTrue(error.message.orEmpty().contains("accessibility"))
    }

    @Test
    fun virtualObserveRequiresAccessibility() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().observe(8, 120, null) }
        assertTrue(error.message.orEmpty().contains("accessibility"))
    }

    @Test
    fun offlineVirtualScreenFailsReadable() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        val error = expectFailure { router().observe(8, 120, null) }
        assertTrue(error.message.orEmpty().contains("is not started"))
    }

    @Test
    fun pointTapUsesHelper() = runBlocking {
        accessibility.connected = true
        router().tap(GuiSelector.Point(5, 6), 60)
        assertNull(accessibility.lastTapSelector)
    }

    @Test
    fun offlineNodeTapFailsBeforeAccessibility() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        expectFailure { router().tap(GuiSelector.Text("OK"), 60) }
        assertNull(accessibility.lastTapSelector)
    }

    @Test
    fun namedTapUsesVirtualDisplay() = runBlocking {
        accessibility.connected = true
        router().tap(GuiSelector.Text("OK"), 60)
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastTapDisplayId)
    }

    @Test
    fun offlineSwipeDoesNotInject() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        expectFailure { router().swipe(1, 2, 3, 4, 300) }
        assertNull(virtual.swiped)
    }

    @Test
    fun virtualSwipeUsesHelper() = runBlocking {
        router().swipe(1, 2, 3, 4, 300)
        assertEquals(listOf(1L, 2L, 3L, 4L, 300L), virtual.swiped)
    }

    @Test
    fun scrollOnVirtualScreenSwipesHelper() = runBlocking {
        val message = router().scroll(GuiScrollDirection.DOWN, 2, null)
        assertEquals(2, virtual.swipeCount)
        assertEquals("Scrolled virtual screen down 2 times", message)
    }

    @Test
    fun scrollWithRefUsesCachedBoundsWithoutObserving() = runBlocking {
        val router = router()
        router.observe(8, 120, null)
        router.scroll(GuiScrollDirection.DOWN, 1, GuiSelector.Ref("1"))
        assertEquals(1, accessibility.observeCalls)
        assertEquals(1, virtual.swipeCount)
    }

    @Test
    fun screenshotDoesNotRequireAccessibility() = runBlocking {
        accessibility.connected = false
        assertEquals(BACKEND_HELPER, router().screenshot().backend)
    }

    @Test
    fun offlineScreenshotFails() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        assertTrue(expectFailure { router().screenshot() }.message.orEmpty().contains("is not started"))
    }

    @Test
    fun virtualScreenshotUsesHelper() = runBlocking {
        val capture = router().screenshot()
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
        val report = router().launch("com.example")
        assertEquals(VIRTUAL_DISPLAY_ID, report.displayId)
        assertEquals(0, report.taskDisplayId)
        assertTrue(report.landedOnMain)
        assertEquals(false, report.landedOnExpectedDisplay)
    }

    @Test
    fun offlineLaunchDoesNotLaunchApp() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        expectFailure { router().launch("com.example") }
        assertEquals(0, virtual.launchCalls)
    }

    @Test
    fun typeAsciiOnVirtualScreenUsesHelper() = runBlocking {
        val result = router().typeText(null, "hello")
        assertEquals("hello", virtual.typed)
        assertEquals(BACKEND_HELPER, result.backend)
    }

    @Test
    fun typeUnicodeOnVirtualScreenWithoutAccessibilityFails() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().typeText(null, "你好") }
        assertTrue(error.message.orEmpty().contains("Non-ASCII"))
    }

    @Test
    fun typeUnicodeOnVirtualScreenUsesAccessibilityWithRef() = runBlocking {
        accessibility.connected = true
        val result = router().typeText(GuiSelector.Ref("1"), "你好")
        assertEquals(VIRTUAL_DISPLAY_ID, accessibility.lastSetTextDisplayId)
        assertEquals("你好", accessibility.lastSetTextValue)
        assertEquals(BACKEND_ACCESSIBILITY, result.backend)
    }

    @Test
    fun asciiEntryUsesHelper() = runBlocking {
        accessibility.connected = false
        val result = router().typeText(null, "hello")
        assertEquals(BACKEND_HELPER, result.backend)
    }

    @Test
    fun unicodeEntryWithoutFieldFails() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().typeText(null, "你好") }
        assertTrue(error.message.orEmpty().contains("Non-ASCII"))
    }

    @Test
    fun virtualTextEntryMatrixFollowsAvailableChannels() = runBlocking {
        for (connected in listOf(true, false)) {
            accessibility.connected = connected
            assertEquals(BACKEND_HELPER, router().typeText(null, "hello").backend)
            if (connected) assertEquals(BACKEND_ACCESSIBILITY, router().typeText(GuiSelector.Ref("1"), "你好").backend)
            else assertTrue(expectFailure { router().typeText(GuiSelector.Ref("1"), "你好") }.message.orEmpty().contains("accessibility"))
            assertTrue(expectFailure { router().typeText(null, "你好") }.message.orEmpty().contains("Non-ASCII"))
        }
    }

    @Test
    fun offlineAsciiEntryDoesNotInject() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        expectFailure { router().typeText(null, "hello") }
        assertNull(virtual.typed)
    }

    @Test
    fun backKeyUsesVirtualHelperWhenConnected() = runBlocking {
        router().key("back")
        assertEquals("back", virtual.lastKey)
    }

    @Test
    fun backKeyWorksWithoutAccessibility() = runBlocking {
        accessibility.connected = false
        router().key("back")
        assertEquals("back", virtual.lastKey)
    }

    @Test
    fun unsupportedKeysNeverReachHelper() = runBlocking {
        for (key in listOf("home", "recents", "notifications", "volume_up", "volume_down")) {
            expectFailure { router().key(key) }
            assertNull(virtual.lastKey)
        }
    }

    @Test
    fun virtualKeyUsesHelper() = runBlocking {
        router().key("back")
        assertEquals("back", virtual.lastKey)
    }

    @Test
    fun statusReportsBackendsAndVirtualState() = runBlocking {
        val status = router().status()
        assertTrue(status.accessibilityConnected)
                assertEquals("online", status.virtualState)
        assertEquals(VIRTUAL_DISPLAY_ID, status.virtual?.displayId)
        assertEquals(2000, status.virtualUid)
    }

    @Test
    fun waitOnVirtualScreenRequiresAccessibility() = runBlocking {
        accessibility.connected = false
        val error = expectFailure { router().waitFor(null, "OK", null, 100) }
        assertTrue(error.message.orEmpty().contains("accessibility"))
    }

    @Test
    fun offlineWaitDoesNotPollAnotherScreen() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        expectFailure { router().waitFor(null, "OK", null, 100) }
        assertEquals(0, accessibility.waitCalls)
    }

    @Test
    fun waitOnMainScreenReportsCurrentWindowFromAccessibility() = runBlocking {
        accessibility.connected = true
        accessibility.waitOutcome = GuiWaitOutcome(matched = false, packageName = "com.example", windowTitle = "设置")
        val outcome = router().waitFor("com.example", "文件管理", null, 10)
        assertEquals(false, outcome.matched)
        assertEquals("com.example", outcome.packageName)
        assertEquals("设置", outcome.windowTitle)
    }

    @Test
    fun virtualWaitUsesAccessibilityOutcome() = runBlocking {
        accessibility.connected = true
        accessibility.waitOutcome = GuiWaitOutcome(matched = true, packageName = "com.android.documentsui", windowTitle = "文件管理")
        val outcome = router().waitFor(null, "文件管理", null, 10)
        assertTrue(outcome.matched)
        assertEquals("文件管理", outcome.windowTitle)
    }

    @Test
    fun listsLaunchableApps() = runBlocking {
        assertEquals(listOf("com.example"), router().listApps().map { it.packageName })
    }

    @Test
    fun appListWorksWhileOfflineAndFailed() = runBlocking {
        virtual.stateValue = VirtualScreenState.Offline
        assertEquals(1, router().listApps().size)
        virtual.stateValue = VirtualScreenState.Failed("unavailable")
        assertEquals(1, router().listApps().size)
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
    fun snapshotMatchesWindowTitle() {
        val snapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(className = "android.widget.FrameLayout", bounds = GuiBounds(0, 0, 100, 100)),
            packageName = "com.android.documentsui",
            windowTitle = "文件管理",
        )
        assertTrue(snapshotMatches(snapshot, null, "文件管理", null))
        assertTrue(!snapshotMatches(snapshot, null, null, "文件管理"))
        assertTrue(snapshotMatches(snapshot, null, null, "设置"))
    }

    @Test
    fun observeDropsUnusableBoundsAndReportsFilteredCount() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val observed = router().observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        assertEquals(listOf("0", "2"), observed.snapshot.nodes.map { it.ref })
        assertEquals(1, observed.filteredNodes)
        assertEquals(3, observed.snapshot.scanned)
    }

    @Test
    fun findSearchesFullDepthAndFindsDeepNode() = runBlocking {
        accessibility.connected = true
        accessibility.snapshotForDepth = { maxDepth, maxNodes ->
            GuiNodeTree.buildSnapshot(deepRoot(), maxDepth = maxDepth, maxNodes = maxNodes)
        }
        val found = router().find(listOf(GuiSelector.Text("深层目标")), 20)
        assertEquals(1, found.matches.size)
        assertEquals(SCREEN_DEFAULT_MAX_DEPTH, accessibility.lastObserveMaxDepth)
    }

    @Test
    fun findUsesPrunedSnapshotAndReportsMatches() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val found = router().find(listOf(GuiSelector.Text("OK")), 20)
        assertEquals(1, found.matches.size)
        assertEquals("2", found.matches.single().ref)
    }

    @Test
    fun scrollWithUnlaidOutRefFailsReadably() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = viewPagerSnapshot()
        val error = expectFailure {
            router().scroll(GuiScrollDirection.DOWN, 1, GuiSelector.Ref("1"))
        }
        assertTrue(error.message.orEmpty().contains("not visible"))
    }

    @Test
    fun scrollPathIsClampedIntoVirtualDisplay() = runBlocking {
        accessibility.connected = true
        accessibility.snapshot = wideNodeSnapshot()
        router().scroll(GuiScrollDirection.RIGHT, 1, GuiSelector.Ref("1"))
        assertEquals(listOf(660L, 600L, 699L, 600L, 300L), virtual.swiped)
    }

    @Test
    fun swipeRejectsCoordinatesOutsideDisplay() = runBlocking {
        val virtualError = expectFailure {
            router().swipe(0, 0, 700, 1200, 300)
        }
        assertTrue(virtualError.message.orEmpty().contains("Swipe coordinates are outside the screen"))
        assertTrue(virtualError.message.orEmpty().contains("700x1200"))
        val mainError = expectFailure {
            router().swipe(0, 0, 1080, 10, 300)
        }
        assertTrue(mainError.message.orEmpty().contains("700x1200"))
        val samePoint = expectFailure {
            router().swipe(10, 10, 10, 10, 300)
        }
        assertTrue(samePoint.message.orEmpty().contains("start and end are the same"))
    }

    @Test
    fun tapByPointRejectsCoordinatesOutsideDisplay() = runBlocking {
        val mainError = expectFailure {
            router().tap(GuiSelector.Point(1080, 10), 60)
        }
        assertTrue(mainError.message.orEmpty().contains("Tap coordinate is outside the screen"))
        val virtualError = expectFailure {
            router().tap(GuiSelector.Point(10, 1200), 60)
        }
        assertTrue(virtualError.message.orEmpty().contains("700x1200"))
    }

    @Test
    fun screenInstanceChangeInvalidatesCachedRefs() = runBlocking {
        val router = router()
        router.observe(8, 120, null)
        val before = accessibility.invalidations
        virtual.stateValue = VirtualScreenState.Online(VIRTUAL_DISPLAY_ID, 700, 1200, "adb")
        router.tap(GuiSelector.Point(1, 2), 60)
        assertEquals(before + 1, accessibility.invalidations)
    }

    @Test
    fun statusSeparatesEnabledFromConnectedAndReportsChannels() = runBlocking {
        accessibility.connected = false
        val status = router(accessibilityEnabled = { true }).status()
        assertEquals(false, status.accessibilityConnected)
        assertEquals(true, status.accessibilityEnabled)
        assertEquals(BACKEND_NONE, status.virtual?.nodeBackend)
        assertEquals(BACKEND_HELPER, status.virtual?.inputBackend)
    }

    @Test
    fun statusReportsMainNodeChannelNoneWhileVirtualScreenIsOnline() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Online(
            displayId = VIRTUAL_DISPLAY_ID,
            width = 700,
            height = 1200,
            backend = "adb",
        )
        val status = router(accessibilityEnabled = { false }).status()
        assertEquals("online", status.virtualState)
    }

    @Test
    fun statusReportsMainNodeChannelAdbWhenVirtualScreenIsOffline() = runBlocking {
        accessibility.connected = false
        virtual.stateValue = VirtualScreenState.Offline
        val status = router(accessibilityEnabled = { false }).status()
        assertEquals("offline", status.virtualState)
        assertNull(status.virtual)
    }

    @Test
    fun statusReportsAccessibilityChannelsWhenConnected() = runBlocking {
        accessibility.connected = true
        val status = router(accessibilityEnabled = { true }).status()
        assertEquals(BACKEND_ACCESSIBILITY, status.virtual?.nodeBackend)
        assertEquals(BACKEND_HELPER, status.virtual?.inputBackend)
    }

    @Test
    fun virtualNodeErrorsWhileRebindingAskToWait() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { true }).tap(GuiSelector.Text("OK"), 60)
        }
        assertTrue(error.message.orEmpty().contains("service is not connected"))
        assertTrue(error.message.orEmpty().contains("wait briefly"))
    }

    @Test
    fun virtualNodeErrorsWhenDisabledPointToSettings() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { false }).tap(GuiSelector.Text("OK"), 60)
        }
        assertTrue(error.message.orEmpty().contains("in system settings"))
    }

    @Test
    fun observeWithEnabledButDisconnectedServiceExplainsRecovery() = runBlocking {
        accessibility.connected = false
        val error = expectFailure {
            router(accessibilityEnabled = { true }).observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        }
        assertTrue(error.message.orEmpty().contains("service is not connected"))
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
            router().scroll(GuiScrollDirection.DOWN, 1, null)
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("virtual_screen(action=start)"))
        assertTrue(message.contains("privileges"))
        assertTrue(message.contains("连接被对端关闭"))
        assertFalse(message.contains("main screen"))
        assertFalse(message.contains("transport"))
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
            router().scroll(GuiScrollDirection.DOWN, 1, null)
        }
        assertTrue(error.message.orEmpty().contains("privileges"))
        assertTrue(error.message.orEmpty().contains("privileges"))
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
        var invalidations = 0
        var waitCalls = 0
        override fun invalidateSnapshots() { invalidations++ }
        override fun bounds(displayId: Int, selector: GuiSelector): GuiBounds =
            GuiNodeTree.find(snapshot, selector)?.bounds ?: error("无匹配节点")
        var observeCalls = 0
        var lastObserveDisplayId = -1
        var lastObserveMaxDepth = -1
        var snapshot: GuiSnapshot = testSnapshot()
        var snapshotForDepth: ((Int, Int) -> GuiSnapshot)? = null
        var lastTapDisplayId = -1
        var lastTapSelector: GuiSelector? = null
        var lastSetTextDisplayId = -1
        var lastSetTextValue = ""
        var waitOutcome = GuiWaitOutcome(matched = true)

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
        ): GuiWaitOutcome { waitCalls++; return waitOutcome }

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

        override suspend fun tap(x: Int, y: Int, durationMs: Int): VirtualScreenResult {
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

        var launchCalls = 0
        override suspend fun launch(packageName: String): VirtualScreenLaunchReport { launchCalls++; return launchReport }

        override fun helperUid(): Int? = 2000

        override fun lastFailure(): ScreenVirtualFailure? = failure

        override fun lastTransportNotice(): String? = transportNotice
    }

    private class FakeApps : ScreenAppCatalog {
        var launched: String? = null

        override fun launchableApps(): List<ScreenApp> = listOf(
            ScreenApp("com.example", "Example", "com.example/.Main"),
        )

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
