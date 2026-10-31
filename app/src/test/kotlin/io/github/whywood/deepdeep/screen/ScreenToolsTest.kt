package io.github.whywood.deepdeep.screen

import dev.pi.ai.TextContent
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScreenToolsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val controller = FakeController()
    private val screenshotDir: File get() = File(temporaryFolder.root, "gui/screenshots")

    @Test
    fun exposesUnifiedScreenToolsWithoutLegacyAliases() {
        val names = screenTools(controller, screenshotDir).map { it.name }
        assertEquals(screenToolNames(), names)
        assertEquals(12, names.size)
        names.forEach { name ->
            assertTrue(name, name.startsWith("screen_"))
            assertTrue(name, !name.startsWith("gui_"))
            assertTrue(name, !name.startsWith("virtual_screen_"))
        }
        val legacy = setOf(
            "gui_observe",
            "gui_find",
            "gui_tap",
            "gui_set_text",
            "gui_scroll",
            "gui_global",
            "gui_wait",
            "gui_screenshot",
            "virtual_screen_launch",
            "virtual_screen_tap",
            "virtual_screen_swipe",
            "virtual_screen_key",
            "virtual_screen_text",
            "virtual_screen_screenshot",
        )
        assertTrue(legacy.intersect(names.toSet()).isEmpty())
    }

    @Test
    fun statusToolReportsBackends() = runTest {
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"accessibility\""))
        assertTrue(text.contains("\"adb\""))
        assertTrue(text.contains("\"main\""))
        assertTrue(text.contains("\"virtual\""))
        assertTrue(text.contains("\"state\":\"online\""))
    }

    @Test
    fun observeToolIncludesBackendAndNodes() = runTest {
        val result = run("screen_observe", buildJsonObject { put("target", "virtual") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"backend\":\"accessibility\""))
        assertTrue(text.contains("\"count\":2"))
        assertTrue(text.contains("\"ref\":\"0\""))
        assertEquals(ScreenTarget.VIRTUAL, controller.lastObserveTarget)
    }

    @Test
    fun findToolRejectsMissingSelectors() = runTest {
        val result = run("screen_find", buildJsonObject { put("target", "main") })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("至少一个条件"))
    }

    @Test
    fun findToolReturnsMatches() = runTest {
        val result = run("screen_find", buildJsonObject { put("text", "OK") })
        assertNull(result.isError)
        assertTrue(result.text().contains("\"count\":1"))
        assertTrue(result.text().contains("\"total\":1"))
    }

    @Test
    fun tapToolRoutesTargetAndDuration() = runTest {
        val result = run(
            "screen_tap",
            buildJsonObject {
                put("target", "virtual")
                put("x", 10)
                put("y", 20)
                put("durationMs", 120)
            },
        )
        assertNull(result.isError)
        assertEquals(ScreenTarget.VIRTUAL, controller.lastTapTarget)
        assertEquals(GuiSelector.Point(10, 20), controller.lastTapSelector)
        assertEquals(120, controller.lastTapDuration)
    }

    @Test
    fun tapToolRejectsConflictingSelectors() = runTest {
        val result = run(
            "screen_tap",
            buildJsonObject {
                put("ref", "1")
                put("x", 1)
                put("y", 2)
            },
        )
        assertTrue(result.isError == true)
    }

    @Test
    fun scrollToolParsesDirectionAndAmount() = runTest {
        val result = run(
            "screen_scroll",
            buildJsonObject {
                put("direction", "down")
                put("amount", 2)
            },
        )
        assertNull(result.isError)
        assertEquals(GuiScrollDirection.DOWN, controller.lastScrollDirection)
        assertEquals(2, controller.lastScrollAmount)
    }

    @Test
    fun typeToolParsesTextAndFieldText() = runTest {
        val result = run(
            "screen_type",
            buildJsonObject {
                put("text", "你好")
                put("fieldText", "搜索")
            },
        )
        assertNull(result.isError)
        assertEquals("你好", controller.lastTypedText)
        assertEquals(GuiSelector.Text("搜索"), controller.lastTextSelector)
        assertTrue(result.text().contains("\"verified\":true"))
    }

    @Test
    fun typeToolRequiresText() = runTest {
        val result = run("screen_type", buildJsonObject { })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("text"))
    }

    @Test
    fun keyToolRejectsUnknownKey() = runTest {
        val result = run("screen_key", buildJsonObject { put("key", "power") })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("不支持的 key"))
    }

    @Test
    fun launchVirtualReturnsTaskDisplayDiagnostics() = runTest {
        val result = run(
            "screen_launch",
            buildJsonObject {
                put("target", "virtual")
                put("packageName", "com.android.settings")
            },
        )
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"target\":\"virtual\""))
        assertTrue(text.contains("\"displayId\":9"))
        assertTrue(text.contains("\"taskDisplayId\":0"))
        assertTrue(text.contains("\"landedOnMain\":true"))
        assertTrue(text.contains("\"landedOnExpectedDisplay\":false"))
        assertTrue(text.contains("\"hint\""))
    }

    @Test
    fun launchVirtualFailureIsErrorButKeepsDiagnostics() = runTest {
        controller.launchReport = ScreenLaunchReport(
            ok = false,
            message = "任务落屏异常",
            target = ScreenTarget.VIRTUAL,
            packageName = "com.example",
            displayId = 9,
            taskDisplayId = -1,
        )
        val result = run(
            "screen_launch",
            buildJsonObject {
                put("target", "virtual")
                put("packageName", "com.example")
            },
        )
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("任务落屏异常"))
    }

    @Test
    fun launchRequiresValidPackageName() = runTest {
        val result = run("screen_launch", buildJsonObject { put("packageName", "settings") })
        assertTrue(result.isError == true)
    }

    @Test
    fun listAppsReturnsCatalog() = runTest {
        val result = run("screen_list_apps", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"package\":\"com.example\""))
        assertTrue(text.contains("\"label\":\"Example\""))
    }

    @Test
    fun screenshotToolWritesPngWithTargetAndBackend() = runTest {
        controller.capture = ScreenCapture(byteArrayOf(1, 2, 3), 1080, 2400, BACKEND_ACCESSIBILITY)
        val result = run("screen_screenshot", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"target\":\"main\""))
        assertTrue(text.contains("\"backend\":\"accessibility\""))
        assertTrue(text.contains("\"path\":\"/gui/screenshots/screen-"))
        val file = screenshotDir.listFiles()!!.single()
        assertTrue(file.name.startsWith("screen-"))
        assertEquals(byteArrayOf(1, 2, 3).toList(), file.readBytes().toList())
    }

    @Test
    fun waitToolReportsTimeoutWithoutError() = runTest {
        controller.waitResult = false
        val result = run("screen_wait", buildJsonObject { put("forText", "OK") })
        assertNull(result.isError)
        assertTrue(result.text().contains("\"ok\":false"))
        assertTrue(result.text().contains("\"reason\":\"timeout\""))
    }

    private suspend fun run(name: String, args: kotlinx.serialization.json.JsonObject): dev.pi.agent.AgentToolResult {
        val tool = screenTools(controller, screenshotDir).first { it.name == name }
        return tool.execute("call", args, null, null)
    }

    private fun dev.pi.agent.AgentToolResult.text(): String =
        (content.single() as TextContent).text

    private class FakeController : ScreenController {
        var lastObserveTarget: ScreenTarget? = null
        var lastTapTarget: ScreenTarget? = null
        var lastTapSelector: GuiSelector? = null
        var lastTapDuration = -1
        var lastScrollDirection: GuiScrollDirection? = null
        var lastScrollAmount = -1
        var lastTypedText: String? = null
        var lastTextSelector: GuiSelector? = null
        var lastLaunchTarget: ScreenTarget? = null
        var capture = ScreenCapture(byteArrayOf(9), 1, 1, BACKEND_ACCESSIBILITY)
        var waitResult = true
        var launchReport = ScreenLaunchReport(
            ok = true,
            message = "已在虚拟屏启动",
            target = ScreenTarget.VIRTUAL,
            packageName = "com.android.settings",
            displayId = 9,
            taskDisplayId = 0,
            landedOnMain = true,
            backend = BACKEND_HELPER,
        )

        override suspend fun status(): ScreenStatusSnapshot = ScreenStatusSnapshot(
            accessibilityConnected = true,
            accessibilityEnabled = true,
            adbAvailable = false,
            main = ScreenDisplayInfo(ScreenTarget.MAIN, MAIN_DISPLAY_ID, 1080, 2400, BACKEND_ACCESSIBILITY),
            virtualState = "online",
            virtual = ScreenDisplayInfo(ScreenTarget.VIRTUAL, 9, 1080, 2400, BACKEND_HELPER),
            virtualBackend = "adb",
            virtualUid = 2000,
        )

        override suspend fun observe(
            target: ScreenTarget,
            maxDepth: Int,
            maxNodes: Int,
            expectedPackage: String?,
        ): ScreenObserveResult {
            lastObserveTarget = target
            return ScreenObserveResult(BACKEND_ACCESSIBILITY, snapshot())
        }

        override suspend fun find(
            target: ScreenTarget,
            selectors: List<GuiSelector>,
            limit: Int,
        ): ScreenFindResult {
            val snapshot = snapshot()
            return ScreenFindResult(BACKEND_ACCESSIBILITY, snapshot, GuiNodeTree.findAll(snapshot, selectors, limit))
        }

        override suspend fun tap(target: ScreenTarget, selector: GuiSelector, durationMs: Int): String {
            lastTapTarget = target
            lastTapSelector = selector
            lastTapDuration = durationMs
            return "已点击"
        }

        override suspend fun swipe(
            target: ScreenTarget,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            durationMs: Long,
        ): String = "已滑动"

        override suspend fun scroll(
            target: ScreenTarget,
            direction: GuiScrollDirection,
            amount: Int,
            selector: GuiSelector?,
        ): String {
            lastScrollDirection = direction
            lastScrollAmount = amount
            return "已滚动"
        }

        override suspend fun typeText(
            target: ScreenTarget,
            selector: GuiSelector?,
            text: String,
        ): ScreenTextResult {
            lastTypedText = text
            lastTextSelector = selector
            return ScreenTextResult(BACKEND_ACCESSIBILITY, text, true)
        }

        override suspend fun key(target: ScreenTarget, name: String): String = "已按键"

        override suspend fun waitFor(
            target: ScreenTarget,
            packageName: String?,
            text: String?,
            textGone: String?,
            timeoutMs: Long,
        ): Boolean = waitResult

        override suspend fun screenshot(target: ScreenTarget): ScreenCapture = capture

        override suspend fun launch(target: ScreenTarget, packageName: String): ScreenLaunchReport {
            lastLaunchTarget = target
            return launchReport
        }

        override fun listApps(): List<ScreenApp> = listOf(
            ScreenApp("com.example", "Example", "com.example/.Main"),
        )

        private fun snapshot(): GuiSnapshot = GuiNodeTree.buildSnapshot(
            root = io.github.whywood.deepdeep.plugin.gui.GuiNode(
                className = "android.widget.FrameLayout",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = listOf(
                    io.github.whywood.deepdeep.plugin.gui.GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(100, 50, 300, 150),
                        text = "OK",
                    ),
                ),
            ),
            packageName = "com.example",
        )
    }
}
