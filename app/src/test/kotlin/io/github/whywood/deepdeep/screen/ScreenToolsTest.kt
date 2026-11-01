package io.github.whywood.deepdeep.screen

import dev.pi.ai.TextContent
import dev.pi.ai.RunSignal
import dev.pi.ai.RequestAbortedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_DEPTH
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNode
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    fun exposesElevenScreenToolsWithoutLegacyAliases() {
        val names = screenTools(controller, screenshotDir).map { it.name }
        assertEquals(screenToolNames(), names)
        assertEquals(
            listOf(
                "screen_status",
                "screen_observe",
                "screen_screenshot",
                "screen_tap",
                "screen_swipe",
                "screen_scroll",
                "screen_type",
                "screen_key",
                "screen_wait",
                "screen_list_apps",
            "screen_launch",
            ),
            names,
        )
        names.forEach { name ->
            assertTrue(name, name.startsWith("screen_"))
            assertTrue(name, !name.startsWith("gui_"))
            assertTrue(name, !name.startsWith("virtual_screen_"))
        }
        val legacy = setOf(
            "screen_find",
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
    fun toolTextFollowsTaskLanguagePolicy() {
        val tools = screenTools(controller, screenshotDir)
        assertEquals(11, tools.size)
        tools.forEach { tool ->
            assertNull("${tool.name} 不应再带 promptSnippet", tool.promptSnippet)
            assertNull("${tool.name} 不应再带 promptGuidelines", tool.promptGuidelines)
            assertTaskText("${tool.name} description", tool.description)
            val parameterDescriptions = mutableListOf<String>()
            collectDescriptions(tool.parameters, parameterDescriptions)
            parameterDescriptions.forEachIndexed { index, text ->
                assertTaskText("${tool.name} 参数 $index", text)
            }
        }
    }

    @Test
    fun statusToolReportsCapabilities() = runTest {
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"accessibility\":\"connected\""))
        assertTrue(!text.contains("\"main\""))
        assertTrue(text.contains("\"virtual\""))
        assertTrue(text.contains("\"state\":\"online\""))
        assertTrue(text.contains("\"readNodes\":true"))
        assertTrue(text.contains("\"tap\":\"node\""))
        assertTrue(text.contains("\"swipe\":true"))
        assertTrue(text.contains("\"typeText\":\"unicode-with-field\""))
        assertTrue(text.contains("\"keys\":[\"back\""))
        assertTrue(text.contains("\"screenshot\":true"))
        assertTrue(text.contains("\"width\":700"))
        assertTrue(text.contains("\"height\":1200"))
        assertTrue(!text.contains("\"adb\""))
        assertTrue(!text.contains("\"displayId\""))
        assertTrue(!text.contains("\"nodeBackend\""))
        assertTrue(!text.contains("\"inputBackend\""))
        assertTrue(!text.contains("\"uid\""))
        assertTrue(!text.contains("\"backend\""))
    }

    @Test
    fun statusToolReportsOfflineCapabilities() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(accessibilityConnected = false, accessibilityEnabled = false, virtualState = "offline", virtual = null)
        val result = run("screen_status", buildJsonObject {})
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"state\":\"offline\""))
        assertTrue(text.contains("\"tap\":\"none\""))
        assertTrue(text.contains("\"screenshot\":false"))
        assertTrue(!text.contains("\"main\""))
    }

    @Test
    fun statusToolMarksMainNodesUnavailableWhileVirtualScreenIsOnline() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(
            accessibilityConnected = false,
            accessibilityEnabled = false,
        )
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"accessibility\":\"disabled\""))
        assertTrue(text.contains("\"readNodes\":false"))
        assertTrue(text.contains("\"tap\":\"point\""))
        assertTrue(text.contains("\"virtual\""))
        assertTrue(text.contains("\"state\":\"online\""))
        assertTrue(text.contains("\"readNodes\":false"))
        assertTrue(text.contains("\"typeText\":\"ascii\""))
    }

    @Test
    fun statusToolReportsRebindingAccessibility() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(
            accessibilityConnected = false,
            accessibilityEnabled = true,
        )
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"accessibility\":\"disconnected\""))
        assertTrue(text.contains("service is not connected"))
        assertTrue(text.contains("\"readNodes\":false"))
        assertTrue(text.contains("\"tap\":\"point\""))
        assertTrue(text.contains("\"screenshot\":true"))
    }

    @Test
    fun statusToolReportsVirtualFailureAsTaskCategories() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(
            virtualState = "failed",
            virtual = null,
            virtualFailure = ScreenVirtualFailure(
                category = VIRTUAL_FAILURE_TRANSPORT,
                message = "虚拟屏连接已断开（连接被对端关闭），自动重连失败",
                recoverable = true,
                reason = "eof",
            ),
            virtualTransportNotice = "虚拟屏已重建，旧屏上的应用已丢失",
        )
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"category\":\"connection\""))
        assertTrue(text.contains("\"message\""))
        assertTrue(text.contains("virtual_screen(action=start)"))
        assertTrue(text.contains("\"notice\""))
        assertTrue(!text.contains("\"recoverable\""))
        assertTrue(!text.contains("\"reason\""))
        assertTrue(!text.contains("transport"))
    }

    @Test
    fun statusToolOmitsVirtualFailureUnlessFailed() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(
            virtualState = "online",
            virtualFailure = ScreenVirtualFailure(
                category = VIRTUAL_FAILURE_TRANSPORT,
                message = "旧错误",
                recoverable = true,
            ),
        )
        val result = run("screen_status", buildJsonObject { })
        assertNull(result.isError)
        assertTrue(!result.text().contains("\"failure\""))
    }

    @Test
    fun virtualFailureCategoriesMapToTaskLanguage() {
        assertEquals("connection", virtualFailureJsonCategory(VIRTUAL_FAILURE_TRANSPORT))
        assertEquals("privilege", virtualFailureJsonCategory(VIRTUAL_FAILURE_BACKEND))
        assertEquals("start", virtualFailureJsonCategory(VIRTUAL_FAILURE_START))
        assertTrue(virtualFailureHint(VIRTUAL_FAILURE_TRANSPORT).contains("virtual_screen(action=start)"))
        assertTrue(virtualFailureHint(VIRTUAL_FAILURE_BACKEND).contains("Root"))
        assertTrue(virtualFailureHint(VIRTUAL_FAILURE_START).contains("failed to start"))
    }

    @Test
    fun observeToolReturnsWholeTreeWithoutFilters() = runTest {
        val result = run("screen_observe", buildJsonObject {  })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":3"))
        assertTrue(text.contains("\"total\":3"))
        assertTrue(text.contains("\"scanned\":3"))
        assertTrue(text.contains("\"truncated\":false"))
        assertTrue(text.contains("\"filtered\":0"))
        assertTrue(text.contains("\"ref\":\"0\""))
    }

    @Test
    fun observeToolDefaultsToFullDepth() = runTest {
        val result = run("screen_observe", buildJsonObject { })
        assertNull(result.isError)
        assertEquals(GUI_MAX_DEPTH, controller.lastObserveMaxDepth)
    }

    @Test
    fun observeToolKeepsExplicitDepth() = runTest {
        val result = run("screen_observe", buildJsonObject { put("maxDepth", 6) })
        assertNull(result.isError)
        assertEquals(6, controller.lastObserveMaxDepth)
    }

    @Test
    fun observeToolReportsFilteredNodes() = runTest {
        controller.filteredNodes = 3
        val result = run("screen_observe", buildJsonObject { })
        assertNull(result.isError)
        assertTrue(result.text().contains("\"filtered\":3"))
        assertTrue(result.text().contains("\"scanned\":3"))
    }

    @Test
    fun observeToolReportsTraversalCountAndTruncationHint() = runTest {
        controller.observeSnapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "root",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = listOf(
                    GuiNode(
                        className = "a",
                        bounds = GuiBounds(0, 0, 100, 100),
                        children = listOf(GuiNode(className = "a1", bounds = GuiBounds(0, 0, 50, 50))),
                    ),
                    GuiNode(className = "b", bounds = GuiBounds(0, 100, 100, 200)),
                ),
            ),
            maxDepth = 1,
            maxNodes = 10,
        )
        val result = run("screen_observe", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":3"))
        assertTrue(text.contains("\"scanned\":3"))
        assertTrue(text.contains("\"truncated\":true"))
        assertTrue(text.contains("not exhaustive"))
    }

    @Test
    fun observeToolFiltersByText() = runTest {
        val result = run("screen_observe", buildJsonObject { put("text", "OK") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":2"))
        assertTrue(text.contains("\"total\":2"))
        assertTrue(text.contains("\"scanned\":3"))
        assertTrue(text.contains("\"ref\":\"1\""))
        assertTrue(text.contains("\"ref\":\"2\""))
        assertTrue(!text.contains("\"ref\":\"0\""))
    }

    @Test
    fun observeToolAppliesFilterLimit() = runTest {
        val result = run("screen_observe", buildJsonObject {
            put("text", "OK")
            put("limit", 1)
        })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":1"))
        assertTrue(text.contains("\"total\":2"))
    }

    @Test
    fun observeToolAppliesLimitWithoutFilters() = runTest {
        val result = run("screen_observe", buildJsonObject { put("limit", 2) })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":2"))
        assertTrue(text.contains("\"total\":3"))
        assertTrue(text.contains("\"ref\":\"0\""))
        assertTrue(text.contains("\"ref\":\"1\""))
        assertTrue(!text.contains("\"ref\":\"2\""))
    }

    @Test
    fun observeToolReturnsAllMatchesOverTwentyWithoutLimit() = runTest {
        controller.observeSnapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "root",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = (0 until 25).map { index ->
                    GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(0, index * 10, 100, index * 10 + 10),
                        text = "OK",
                    )
                },
            ),
            packageName = "com.example",
        )
        val result = run("screen_observe", buildJsonObject { put("text", "OK") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":25"))
        assertTrue(text.contains("\"total\":25"))
        assertEquals(25, Regex("\"ref\":\"").findAll(text).count())
    }

    @Test
    fun observeToolHintsWhenFilterMatchesNothing() = runTest {
        val result = run("screen_observe", buildJsonObject { put("text", "不存在的文字") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":0"))
        assertTrue(text.contains("\"total\":0"))
        assertTrue(text.contains("\"scanned\":3"))
        assertTrue(text.contains("\"hint\""))
    }

    @Test
    fun observeToolCombinesFiltersWithAnd() = runTest {
        val result = run("screen_observe", buildJsonObject { put("text", "OK"); put("class", "Button") })
        assertNull(result.isError)
        assertTrue(result.text().contains("\"count\":2"))
        val noMatch = run("screen_observe", buildJsonObject { put("text", "OK"); put("class", "TextView") })
        assertNull(noMatch.isError)
        assertTrue(noMatch.text().contains("\"count\":0"))
    }

    @Test
    fun tapToolRoutesTargetAndDuration() = runTest {
        val result = run(
            "screen_tap",
            buildJsonObject {

                put("x", 10)
                put("y", 20)
                put("durationMs", 120)
            },
        )
        assertNull(result.isError)
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
        val text = result.text()
        assertTrue(text.contains("\"verified\":true"))
        assertTrue(!text.contains("\"backend\""))
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
        assertTrue(result.text().contains("Unsupported key"))
    }

    @Test
    fun appToolListsLaunchableApps() = runTest {
        val result = run("screen_list_apps", buildJsonObject {})
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"target\":\"virtual\""))
        assertTrue(text.contains("\"count\":1"))
        assertTrue(text.contains("\"package\":\"com.example\""))
        assertTrue(text.contains("\"label\":\"Example\""))
        assertTrue(text.contains("\"component\":\"com.example/.Main\""))
    }

    @Test
    fun appListWorksWhileVirtualScreenIsOffline() = runTest {
        controller.statusSnapshot = controller.statusSnapshot.copy(virtualState = "offline", virtual = null)
        val result = run("screen_list_apps", buildJsonObject {})
        assertNull(result.isError)
        assertTrue(result.text().contains("com.example"))
    }

    @Test
    fun appToolLaunchesOnVirtualScreen() = runTest {
        val result = run(
            "screen_launch",
            buildJsonObject {

                put("packageName", "com.android.settings")
            },
        )
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"ok\":true"))
        assertTrue(text.contains("\"package\":\"com.android.settings\""))
        assertTrue(text.contains("\"message\""))
        assertTrue(text.contains("\"onExpectedScreen\":true"))
        assertTrue(!text.contains("\"displayId\""))
        assertTrue(!text.contains("\"taskDisplayId\""))
        assertTrue(!text.contains("\"landedOnMain\""))
        assertTrue(!text.contains("\"backend\""))
    }

    @Test
    fun appToolReportsOffScreenLaunchAsHint() = runTest {
        controller.launchReport = ScreenLaunchReport(
            ok = true,
            message = "已在虚拟屏启动",
            packageName = "com.example",
            displayId = 9,
            taskDisplayId = 0,
            landedOnMain = true,
        )
        val result = run(
            "screen_launch",
            buildJsonObject {

                put("packageName", "com.example")
            },
        )
        assertEquals(true, result.isError)
        val text = result.text()
        assertTrue(text.contains("\"onExpectedScreen\":false"))
        assertTrue(text.contains("\"hint\""))
    }

    @Test
    fun appToolReportsLaunchFailureAsError() = runTest {
        controller.launchReport = ScreenLaunchReport(
            ok = false,
            message = "任务落屏异常",
            packageName = "com.example",
            displayId = 9,
            taskDisplayId = -1,
        )
        val result = run(
            "screen_launch",
            buildJsonObject {

                put("packageName", "com.example")
            },
        )
        assertTrue(result.isError == true)
        val text = result.text()
        assertTrue(text.contains("任务落屏异常"))
        assertTrue(text.contains("\"ok\":false"))
        assertTrue(text.contains("Check screen_status"))
    }

    @Test
    fun appToolReportsGenericHintOnlyWithoutFailureMessage() = runTest {
        controller.launchReport = ScreenLaunchReport(
            ok = false,
            message = "",
            packageName = "com.example",
            displayId = 9,
            taskDisplayId = -1,
        )
        val result = run(
            "screen_launch",
            buildJsonObject {

                put("packageName", "com.example")
            },
        )
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("Check screen_status"))
    }

    @Test
    fun appToolRequiresValidPackageName() = runTest {
        val result = run(
            "screen_launch",
            buildJsonObject {
                put("packageName", "settings")
            },
        )
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("valid Android package name"))
    }

    @Test
    fun newAppEntriesRejectOldAction() = runTest {
        val missing = run("screen_launch", buildJsonObject {})
        assertEquals(true, missing.isError)
        assertTrue(missing.text().contains("packageName"))
        val unknown = run("screen_list_apps", buildJsonObject { put("action", "list") })
        assertEquals(true, unknown.isError)
        assertTrue(unknown.text().contains("arguments"))
    }

    @Test
    fun screenshotToolWritesPngWithTaskFields() = runTest {
        controller.capture = ScreenCapture(byteArrayOf(1, 2, 3), 1080, 2400, BACKEND_ACCESSIBILITY)
        val result = run("screen_screenshot", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"target\":\"virtual\""))
        assertTrue(text.contains("\"path\":\"${screenshotDir.absolutePath}/screen-"))
        assertTrue(!text.contains("\"bashPath\""))
        assertTrue(!text.contains("\"hostPath\""))
        assertTrue(text.contains("\"width\":1080"))
        assertTrue(text.contains("\"height\":2400"))
        assertTrue(!text.contains("\"backend\""))
        val file = screenshotDir.listFiles()!!.single()
        assertTrue(file.name.startsWith("screen-"))
        assertEquals(byteArrayOf(1, 2, 3).toList(), file.readBytes().toList())
    }

    @Test
    fun waitToolReportsTimeoutWithCurrentWindow() = runTest {
        controller.waitOutcome = ScreenWaitOutcome(matched = false, packageName = "com.example", windowTitle = "设置")
        val result = run("screen_wait", buildJsonObject { put("forText", "OK") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"ok\":false"))
        assertTrue(text.contains("\"reason\":\"timeout\""))
        assertTrue(text.contains("\"timeoutMs\":5000"))
        assertTrue(text.contains("\"current\":{\"package\":\"com.example\",\"title\":\"设置\"}"))
    }

    @Test
    fun waitToolReportsMatchedWindow() = runTest {
        controller.waitOutcome = ScreenWaitOutcome(matched = true, packageName = "com.android.settings", windowTitle = "设置")
        val result = run("screen_wait", buildJsonObject { put("forPackage", "com.android.settings") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"ok\":true"))
        assertTrue(text.contains("\"current\":{\"package\":\"com.android.settings\",\"title\":\"设置\"}"))
        assertTrue(!text.contains("\"reason\""))
    }

    @Test
    fun defaultVirtualAndExplicitMainRouteIndependently() = runTest {
        val main = FakeController()
        val tools = screenTools(controller, screenshotDir, main).associateBy { it.name }
        tools.getValue("screen_tap").execute("virtual", buildJsonObject { put("x", 10); put("y", 20) }, null, null)
        assertEquals(GuiSelector.Point(10, 20), controller.lastTapSelector)
        assertNull(main.lastTapSelector)
        tools.getValue("screen_tap").execute("main", buildJsonObject { put("target", "main"); put("x", 30); put("y", 40) }, null, null)
        assertEquals(GuiSelector.Point(30, 40), main.lastTapSelector)
        assertEquals(GuiSelector.Point(10, 20), controller.lastTapSelector)
        val observed = tools.getValue("screen_observe").execute("main", buildJsonObject { put("target", "main") }, null, null)
        assertTrue(observed.text().contains("\"target\":\"main\""))
    }

    @Test
    fun virtualFailureNeverFallsBackToMain() = runTest {
        val virtual = object : ScreenController by controller {
            override suspend fun tap(selector: GuiSelector, durationMs: Int): String = error("virtual offline")
        }
        val main = FakeController()
        val tool = screenTools(virtual, screenshotDir, main).first { it.name == "screen_tap" }
        val result = tool.execute("virtual", buildJsonObject { put("x", 1); put("y", 2) }, null, null)
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("virtual offline"))
        assertNull(main.lastTapSelector)
    }

    @Test
    fun midOperationAbortCancelsSelectedScreenWithoutRetry() = runTest {
        val started = CompletableDeferred<Unit>()
        var calls = 0
        var cleaned = false
        val main = object : ScreenController by controller {
            override suspend fun screenshot(): ScreenCapture {
                calls++
                started.complete(Unit)
                try { awaitCancellation() } finally { cleaned = true }
            }
        }
        val tool = screenTools(controller, screenshotDir, main).first { it.name == "screen_screenshot" }
        val signal = RunSignal()
        val result = async { runCatching { tool.execute("main", buildJsonObject { put("target", "main") }, signal, null) } }
        runCurrent()
        started.await()
        signal.abort("stop")
        assertTrue(result.await().exceptionOrNull() is RequestAbortedException)
        assertTrue(cleaned)
        assertEquals(1, calls)
        assertTrue(!screenshotDir.exists())
    }

    @Test
    fun everyScreenToolDeclaresTargetsAndRejectsUnknownScreenBeforeOperation() = runTest {
        for (tool in screenTools(controller, screenshotDir)) {
            for (target in listOf("auto", "unknown")) {
                val result = tool.execute("legacy", buildJsonObject { put("target", target) }, null, null)
                assertEquals(tool.name, true, result.isError)
                assertTrue(result.text().contains("target must be virtual or main"))
            }
            assertTrue(tool.parameters.toString().contains("\"target\""))
        }
    }

    private suspend fun run(name: String, args: JsonObject): dev.pi.agent.AgentToolResult {
        val tool = screenTools(controller, screenshotDir).first { it.name == name }
        return tool.execute("call", args, null, null)
    }

    private fun dev.pi.agent.AgentToolResult.text(): String =
        (content.single() as TextContent).text

    private fun assertTaskText(label: String, text: String) {
        assertTrue("$label 不能为空", text.isNotBlank())
        assertTrue("$label 需要英文: $text", text.all { it.code < 128 })
    }

    private fun collectDescriptions(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key == "description" && value is JsonPrimitive) {
                    out += value.content
                } else {
                    collectDescriptions(value, out)
                }
            }

            is JsonArray -> element.forEach { collectDescriptions(it, out) }
            else -> Unit
        }
    }

    private class FakeController : ScreenController {
        var lastObserveMaxDepth = -1
        var filteredNodes = 0
        var lastTapSelector: GuiSelector? = null
        var lastTapDuration = -1
        var lastScrollDirection: GuiScrollDirection? = null
        var lastScrollAmount = -1
        var lastTypedText: String? = null
        var lastTextSelector: GuiSelector? = null
        var capture = ScreenCapture(byteArrayOf(9), 1, 1, BACKEND_ACCESSIBILITY)
        var waitOutcome = ScreenWaitOutcome(matched = true)
        var observeSnapshot: GuiSnapshot? = null
        var listAppsFailure: Throwable? = null
        var statusSnapshot = ScreenStatusSnapshot(
            accessibilityConnected = true,
            accessibilityEnabled = true,
            virtualState = "online",
            virtual = ScreenDisplayInfo(9, 700, 1200, BACKEND_HELPER),
            virtualBackend = "adb",
            virtualUid = 2000,
        )
        var launchReport = ScreenLaunchReport(
            ok = true,
            message = "已在虚拟屏启动",
            packageName = "com.android.settings",
            displayId = 9,
            taskDisplayId = 9,
        )

        override suspend fun status(): ScreenStatusSnapshot = statusSnapshot

        override suspend fun observe(
            maxDepth: Int,
            maxNodes: Int,
            expectedPackage: String?,
        ): ScreenObserveResult {
            lastObserveMaxDepth = maxDepth
            return ScreenObserveResult(BACKEND_ACCESSIBILITY, observeSnapshot ?: snapshot(), filteredNodes)
        }

        override suspend fun find(
            selectors: List<GuiSelector>,
            limit: Int,
        ): ScreenFindResult {
            val snapshot = snapshot()
            return ScreenFindResult(BACKEND_ACCESSIBILITY, snapshot, GuiNodeTree.findAll(snapshot, selectors, limit))
        }

        override suspend fun tap(selector: GuiSelector, durationMs: Int): String {
            lastTapSelector = selector
            lastTapDuration = durationMs
            return "已点击"
        }

        override suspend fun swipe(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            durationMs: Long,
        ): String = "已滑动"

        override suspend fun scroll(
            direction: GuiScrollDirection,
            amount: Int,
            selector: GuiSelector?,
        ): String {
            lastScrollDirection = direction
            lastScrollAmount = amount
            return "已滚动"
        }

        override suspend fun typeText(
            selector: GuiSelector?,
            text: String,
        ): ScreenTextResult {
            lastTypedText = text
            lastTextSelector = selector
            return ScreenTextResult(BACKEND_ACCESSIBILITY, text, true)
        }

        override suspend fun key(name: String): String = "已按键"

        override suspend fun waitFor(
            packageName: String?,
            text: String?,
            textGone: String?,
            timeoutMs: Long,
        ): ScreenWaitOutcome = waitOutcome

        override suspend fun screenshot(): ScreenCapture = capture

        override suspend fun launch(packageName: String): ScreenLaunchReport {
            return launchReport
        }

        override suspend fun listApps(): List<ScreenApp> {
            listAppsFailure?.let { throw it }
            return listOf(ScreenApp("com.example", "Example", "com.example/.Main"))
        }

        private fun snapshot(): GuiSnapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "android.widget.FrameLayout",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = listOf(
                    GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(100, 50, 300, 150),
                        text = "OK",
                    ),
                    GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(100, 200, 300, 300),
                        text = "OK again",
                    ),
                ),
            ),
            packageName = "com.example",
        )
    }

}
