package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MainScreenControllerTest {
    private val actions = Actions()
    private val shell = Shell()
    private var virtualState = "offline"
    private val apps = object : ScreenAppCatalog {
        var launched = 0
        override fun launchableApps() = listOf(ScreenApp("com.example", "Example", "com.example.Main"))
        override fun launchOnMain(packageName: String): ScreenApp { launched++; return app(packageName) }
    }
    private fun controller() = MainScreenController(actions, apps, { 1080 to 2400 }, {
        ScreenStatusSnapshot(actions.connected, actions.connected, virtualState)
    }, shell)

    @Test
    fun statusSeparatesMainCapabilitiesFromVirtualLifecycle(): Unit = runBlocking {
        virtualState = "offline"
        val online = controller().status()
        assertEquals(0, online.main?.displayId)
        assertEquals("offline", online.virtualState)
        assertTrue(online.mainInputAvailable)
        assertTrue(online.mainScreenshotAvailable)
        assertTrue("home" in online.mainKeys)
        actions.connected = false
        shell.available = true
        val systemOnly = controller().status()
        assertTrue(systemOnly.mainInputAvailable)
        assertFalse(systemOnly.mainScreenshotAvailable)
        assertEquals(MAIN_SCREEN_KEYS.toList(), systemOnly.mainKeys)
        shell.available = false
        val unavailable = controller().status()
        assertFalse(unavailable.mainInputAvailable)
        assertTrue(unavailable.mainKeys.isEmpty())
    }

    @Test
    fun mainNodesUseDisplayZeroAndRejectRefsFromAnotherScreen(): Unit = runBlocking {
        val screen = controller()
        val main = screen.observe(20, 400, "com.example")
        val ref = main.snapshot.nodes.single().ref
        assertTrue(ref.startsWith("0:"))
        assertEquals(0, actions.observed)
        screen.tap(GuiSelector.Ref(ref), 60)
        assertEquals(0, actions.tapped)
        val other = actions.cache.store(7, snapshot()).nodes.single().ref
        assertThrows(IllegalArgumentException::class.java) { runBlocking { screen.tap(GuiSelector.Ref(other), 60) } }
        screen.observe(20, 400, null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { screen.tap(GuiSelector.Ref(ref), 60) } }
    }

    @Test
    fun mainChineseInputIsVerifiedAndNeverUsesShellText(): Unit = runBlocking {
        val screen = controller()
        val ref = screen.observe(20, 400, null).snapshot.nodes.single().ref
        val result = screen.typeText(GuiSelector.Ref(ref), "中性中文")
        assertEquals(0, actions.edited)
        assertEquals("中性中文", result.text)
        assertTrue(result.verified)
        assertTrue(shell.commands.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { screen.typeText(null, "中文") } }
    }

    @Test
    fun mainSwipeAndKeysStayOnDisplayZero(): Unit = runBlocking {
        val screen = controller()
        screen.swipe(1, 2, 10, 20, 300)
        assertEquals(listOf(1, 2, 10, 20), actions.swiped)
        shell.available = true
        screen.key("home")
        screen.key("enter")
        assertEquals(listOf("input -d 0 keyevent 3", "input -d 0 keyevent 66"), shell.commands)
        assertThrows(IllegalStateException::class.java) { runBlocking { screen.tap(GuiSelector.Point(1080, 20), 60) } }
    }

    @Test
    fun mainPointInputWorksWithoutAccessibilityAndNeverRetriesFailure(): Unit = runBlocking {
        actions.connected = false
        shell.available = true
        val screen = controller()
        screen.tap(GuiSelector.Point(1, 2), 600)
        assertEquals("input -d 0 swipe 1 2 1 2 600", shell.commands.single())
        shell.failure = IllegalStateException("failed")
        assertThrows(IllegalStateException::class.java) { runBlocking { screen.swipe(1, 2, 20, 30, 300) } }
        assertEquals(2, shell.commands.size)
        assertNull(actions.swiped)
        assertThrows(IllegalStateException::class.java) { runBlocking { screen.observe(20, 400, null) } }
    }

    @Test
    fun mainShellTextQuotesUntrustedInput(): Unit = runBlocking {
        shell.available = true
        controller().typeText(null, "x'; echo injected")
        assertEquals("input -d 0 text 'x'\\'';%secho%sinjected'", shell.commands.single())
    }

    @Test
    fun mainLaunchRefusesToMoveVirtualTask(): Unit = runBlocking {
        shell.available = true
        shell.outputs += tasks(7)
        assertThrows(IllegalStateException::class.java) { runBlocking { controller().launch("com.example") } }
        assertEquals(1, shell.commands.size)
        assertFalse(shell.commands.any { it.startsWith("am start") })
    }

    @Test
    fun mainLaunchChecksActualLandingAndDoesNotRepeat(): Unit = runBlocking {
        shell.available = true
        shell.outputs.addAll(listOf("ACTIVITY MANAGER ACTIVITIES\n", "Status: ok", tasks(0)))
        val result = controller().launch("com.example")
        assertTrue(result.ok)
        assertTrue(result.landedOnExpectedDisplay)
        assertEquals(0, result.taskDisplayId)
        assertEquals("am start -W --display 0 -n 'com.example/com.example.Main'", shell.commands[1])
        assertEquals(3, shell.commands.size)
    }

    @Test
    fun unreadableTaskStateFailsBeforeLaunch(): Unit = runBlocking {
        shell.available = true
        shell.outputs += "permission denied"
        assertThrows(IllegalStateException::class.java) { runBlocking { controller().launch("com.example") } }
        assertEquals(1, shell.commands.size)
    }

    @Test
    fun mainLaunchWithoutShellRequiresVirtualToBeStopped(): Unit = runBlocking {
        virtualState = "online"
        assertThrows(IllegalStateException::class.java) { runBlocking { controller().launch("com.example") } }
        assertEquals(0, apps.launched)
        virtualState = "offline"
        assertTrue(controller().launch("com.example").ok)
        assertEquals(1, apps.launched)
    }

    @Test
    fun mainScreenshotAndWaitDoNotDependOnVirtualScreenBeingOnline(): Unit = runBlocking {
        val screen = controller()
        assertTrue(screen.waitFor("com.example", "ready", null, 100).matched)
        assertEquals(0, actions.waited)
        assertArrayEquals(byteArrayOf(1), screen.screenshot().png)
        assertEquals(1, actions.screenshots)
        actions.connected = false
        assertThrows(IllegalStateException::class.java) { runBlocking { screen.screenshot() } }
        assertEquals(1, actions.screenshots)
    }

    @Test
    fun cancelledMainOperationIsPropagatedWithoutRetry(): Unit = runBlocking {
        shell.available = true
        shell.failure = CancellationException("stop")
        assertThrows(CancellationException::class.java) { runBlocking { controller().key("enter") } }
        assertEquals(1, shell.commands.size)
    }

    @Test
    fun taskParserRejectsUnknownLocationAndDoesNotMatchPackagePrefix() {
        assertEquals(setOf(0, 7), mainTaskDisplays(tasks(0) + tasks(7), "com.example"))
        assertTrue(mainTaskDisplays(tasks(7).replace("com.example/", "com.example.other/"), "com.example").isEmpty())
        assertThrows(IllegalStateException::class.java) { mainTaskDisplays("ACTIVITY MANAGER ACTIVITIES\nActivityRecord{abc u0 com.example/.Main}", "com.example") }
    }

    private class Shell : MainScreenShell {
        override var available = false
        val commands = mutableListOf<String>()
        val outputs = ArrayDeque<String>()
        var failure: Exception? = null
        override suspend fun execute(command: String, timeoutMs: Long): String {
            commands += command
            failure?.let { throw it }
            return outputs.removeFirstOrNull().orEmpty()
        }
    }

    private class Actions : GuiActions {
        override var connected = true
        val cache = GuiSnapshotCache()
        var observed = -1
        var tapped = -1
        var edited = -1
        var waited = -1
        var screenshots = 0
        var swiped: List<Int>? = null
        override fun invalidateSnapshots() = cache.clear()
        override fun bounds(displayId: Int, selector: GuiSelector) = cache.find(displayId, selector).bounds
        override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int, expectedPackage: String?): GuiSnapshot {
            observed = displayId
            return cache.store(displayId, snapshot())
        }
        override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) { cache.find(displayId, selector); tapped = displayId }
        override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult {
            cache.find(displayId, selector); edited = displayId
            return GuiTextResult(text, true)
        }
        override suspend fun waitFor(displayId: Int, packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome {
            waited = displayId
            return GuiWaitOutcome(true, "com.example", "ready")
        }
        override suspend fun swipeMain(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) { swiped = listOf(x1, y1, x2, y2) }
        override suspend fun screenshotMain(): GuiScreenshot { screenshots++; return GuiScreenshot(byteArrayOf(1), 1080, 2400) }
    }

    companion object {
        private fun snapshot() = GuiNodeTree.buildSnapshot(GuiNode("EditText", GuiBounds(0, 0, 100, 100), text = "ready", editable = true), "com.example")
        private fun tasks(display: Int) = "ACTIVITY MANAGER ACTIVITIES\nDisplay #$display (activities from top to bottom):\n * Hist #0: ActivityRecord{abc u0 com.example/.Main} t1}\n"
    }
}
