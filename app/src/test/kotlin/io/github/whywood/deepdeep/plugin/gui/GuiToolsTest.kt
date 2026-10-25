package io.github.whywood.deepdeep.plugin.gui

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuiToolsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val actions = FakeGuiActions()
    private val screenshotDir: File get() = File(temporaryFolder.root, "gui/screenshots")
    private val tools by lazy { guiTools(actions, screenshotDir) }

    @Test
    fun exposesEightTools() {
        assertEquals(
            listOf(
                "gui_observe",
                "gui_find",
                "gui_tap",
                "gui_set_text",
                "gui_scroll",
                "gui_global",
                "gui_wait",
                "gui_screenshot",
            ),
            tools.map { it.name },
        )
    }

    @Test
    fun observeReturnsPackageAndNodeJson() = runTest {
        val result = run("gui_observe", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"package\":\"com.example\""))
        assertTrue(text.contains("\"title\":\"Main\""))
        assertTrue(text.contains("\"count\":2"))
        assertTrue(text.contains("\"ref\":\"1\""))
        assertTrue(text.contains("\"bounds\":\"[100,200][300,280]\""))
        assertTrue(text.contains("\"clickable\":true"))
        assertEquals(GUI_DEFAULT_MAX_DEPTH to GUI_DEFAULT_MAX_NODES, actions.lastObserve)
    }

    @Test
    fun observePassesExplicitLimits() = runTest {
        run(
            "gui_observe",
            buildJsonObject {
                put("maxDepth", 3)
                put("maxNodes", 50)
            },
        )
        assertEquals(3 to 50, actions.lastObserve)
    }

    @Test
    fun observeValidatesLimits() = runTest {
        assertTrue(run("gui_observe", buildJsonObject { put("maxDepth", 0) }).isError == true)
        assertTrue(run("gui_observe", buildJsonObject { put("maxDepth", GUI_MAX_DEPTH + 1) }).isError == true)
        assertTrue(run("gui_observe", buildJsonObject { put("maxNodes", 0) }).isError == true)
        assertTrue(run("gui_observe", buildJsonObject { put("maxNodes", GUI_MAX_NODES + 1) }).isError == true)
    }

    @Test
    fun observeRejectsUnexpectedPackage() = runTest {
        val result = run("gui_observe", buildJsonObject { put("packageName", "com.other") })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("com.example"))
        assertTrue(result.text().contains("com.other"))
    }

    @Test
    fun findReturnsMatchesWithRefs() = runTest {
        val result = run("gui_find", buildJsonObject { put("text", "确定") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"count\":1"))
        assertTrue(text.contains("\"total\":1"))
        assertTrue(text.contains("\"hasMore\":false"))
        assertTrue(text.contains("\"ref\":\"1\""))
        assertEquals(GUI_DEFAULT_MAX_DEPTH to GUI_MAX_NODES, actions.lastObserve)
    }

    @Test
    fun findSupportsDescResourceIdAndClass() = runTest {
        val byDesc = run("gui_find", buildJsonObject { put("desc", "提交") })
        assertTrue(byDesc.text().contains("\"ref\":\"1\""))

        val byId = run("gui_find", buildJsonObject { put("resourceId", "ok") })
        assertTrue(byId.text().contains("\"ref\":\"1\""))

        val byClass = run("gui_find", buildJsonObject { put("class", "Button") })
        assertTrue(byClass.text().contains("\"ref\":\"1\""))
    }

    @Test
    fun findRequiresAllCriteriaToMatch() = runTest {
        val matched = run(
            "gui_find",
            buildJsonObject {
                put("text", "确定")
                put("class", "Button")
            },
        )
        assertTrue(matched.text().contains("\"count\":1"))

        val missed = run(
            "gui_find",
            buildJsonObject {
                put("text", "确定")
                put("class", "EditText")
            },
        )
        assertTrue(missed.text().contains("\"count\":0"))
    }

    @Test
    fun findRequiresAtLeastOneCriterion() = runTest {
        val result = run("gui_find", buildJsonObject { })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("text、desc、resourceId、class"))
    }

    @Test
    fun findValidatesLimit() = runTest {
        assertTrue(run("gui_find", buildJsonObject { put("text", "OK"); put("limit", 0) }).isError == true)
        assertTrue(run("gui_find", buildJsonObject { put("text", "OK"); put("limit", 51) }).isError == true)
    }

    @Test
    fun findHonorsLimitAndReportsHasMore() = runTest {
        actions.snapshot = GuiSnapshot(
            packageName = "com.example",
            windowTitle = "Main",
            nodes = listOf(
                snapshotNode(ref = "0", text = "OK"),
                snapshotNode(ref = "1", text = "OK"),
                snapshotNode(ref = "2", text = "OK"),
            ),
            truncated = false,
        )
        val result = run(
            "gui_find",
            buildJsonObject {
                put("text", "OK")
                put("limit", 2)
            },
        )
        val text = result.text()
        assertTrue(text.contains("\"count\":2"))
        assertTrue(text.contains("\"total\":3"))
        assertTrue(text.contains("\"hasMore\":true"))
    }

    @Test
    fun observeThenTapResolvesRefFlow() = runTest {
        assertNull(run("gui_observe", buildJsonObject { }).isError)
        val tap = run(
            "gui_tap",
            buildJsonObject {
                put("ref", "1")
                put("durationMs", 120)
            },
        )
        assertNull(tap.isError)
        assertEquals(GuiSelector.Ref("1") to 120, actions.lastTap)
        assertTrue(tap.text().contains("已点击"))
        assertTrue(tap.text().contains("ref=1"))
    }

    @Test
    fun tapRejectsMissingOrConflictingSelectors() = runTest {
        assertTrue(run("gui_tap", buildJsonObject { }).isError == true)
        assertTrue(
            run(
                "gui_tap",
                buildJsonObject {
                    put("ref", "1")
                    put("text", "OK")
                },
            ).isError == true,
        )
        assertTrue(run("gui_tap", buildJsonObject { put("x", 10) }).isError == true)
        assertTrue(
            run(
                "gui_tap",
                buildJsonObject {
                    put("x", 10)
                    put("y", 20)
                    put("text", "OK")
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "gui_tap",
                buildJsonObject {
                    put("x", -1)
                    put("y", 20)
                },
            ).isError == true,
        )
    }

    @Test
    fun tapSupportsCoordinatesAndValidatesDuration() = runTest {
        val result = run(
            "gui_tap",
            buildJsonObject {
                put("x", 10)
                put("y", 20)
            },
        )
        assertNull(result.isError)
        assertEquals(GuiSelector.Point(10, 20) to 60, actions.lastTap)

        assertTrue(
            run(
                "gui_tap",
                buildJsonObject {
                    put("x", 10)
                    put("y", 20)
                    put("durationMs", -1)
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "gui_tap",
                buildJsonObject {
                    put("x", 10)
                    put("y", 20)
                    put("durationMs", 10_000)
                },
            ).isError == true,
        )
    }

    @Test
    fun setTextRequiresSelectorAndText() = runTest {
        assertTrue(run("gui_set_text", buildJsonObject { put("ref", "1") }).isError == true)
        assertTrue(run("gui_set_text", buildJsonObject { put("setText", "hello") }).isError == true)

        val result = run(
            "gui_set_text",
            buildJsonObject {
                put("text", "输入框")
                put("setText", "hello")
            },
        )
        assertNull(result.isError)
        assertEquals(GuiSelector.Text("输入框") to "hello", actions.lastSetText)
    }

    @Test
    fun setTextReportsVerifiedReadBack() = runTest {
        val result = run("gui_set_text", buildJsonObject { put("ref", "1"); put("setText", "hello") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"verified\":true"))
        assertTrue(text.contains("\"text\":\"hello\""))
    }

    @Test
    fun setTextReportsUnverifiedReadBack() = runTest {
        actions.setTextReadBack = "hello!"
        val result = run("gui_set_text", buildJsonObject { put("ref", "1"); put("setText", "hello") })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"verified\":false"))
        assertTrue(text.contains("\"text\":\"hello!\""))
        assertTrue(text.contains("hint"))
    }

    @Test
    fun scrollValidatesDirectionAmountAndTarget() = runTest {
        assertTrue(run("gui_scroll", buildJsonObject { put("direction", "sideways") }).isError == true)
        assertTrue(
            run(
                "gui_scroll",
                buildJsonObject {
                    put("direction", "down")
                    put("amount", 0)
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "gui_scroll",
                buildJsonObject {
                    put("direction", "down")
                    put("amount", 11)
                },
            ).isError == true,
        )

        val result = run(
            "gui_scroll",
            buildJsonObject {
                put("direction", "down")
                put("amount", 3)
                put("ref", "2")
            },
        )
        assertNull(result.isError)
        assertEquals(Triple(GuiScrollDirection.DOWN, 3, GuiSelector.Ref("2")), actions.lastScroll)
    }

    @Test
    fun globalValidatesAction() = runTest {
        assertTrue(run("gui_global", buildJsonObject { put("action", "power") }).isError == true)
        val result = run("gui_global", buildJsonObject { put("action", "recents") })
        assertNull(result.isError)
        assertEquals(GuiGlobalAction.RECENTS, actions.lastGlobal)
    }

    @Test
    fun waitRequiresConditionAndReportsTimeoutAsNonError() = runTest {
        assertTrue(run("gui_wait", buildJsonObject { }).isError == true)
        assertTrue(
            run(
                "gui_wait",
                buildJsonObject {
                    put("forText", "done")
                    put("timeoutMs", 0)
                },
            ).isError == true,
        )

        actions.waitResult = false
        val timeout = run(
            "gui_wait",
            buildJsonObject {
                put("forText", "done")
                put("timeoutMs", 300)
            },
        )
        assertNull(timeout.isError)
        assertTrue(timeout.text().contains("\"ok\":false"))
        assertTrue(timeout.text().contains("\"reason\":\"timeout\""))
        assertEquals(WaitCall(null, "done", null, 300L), actions.lastWait)
    }

    @Test
    fun waitSucceedsWhenConditionAppears() = runTest {
        actions.waitResult = true
        val result = run(
            "gui_wait",
            buildJsonObject {
                put("forPackage", "com.example")
                put("timeoutMs", 1000)
            },
        )
        assertNull(result.isError)
        assertTrue(result.text().contains("\"ok\":true"))
        assertEquals(WaitCall("com.example", null, null, 1000L), actions.lastWait)
    }

    @Test
    fun waitSupportsTextGoneAndCombinedConditions() = runTest {
        val result = run(
            "gui_wait",
            buildJsonObject {
                put("forPackage", "com.example")
                put("forText", "首页")
                put("forTextGone", "加载中")
                put("timeoutMs", 2000)
            },
        )
        assertNull(result.isError)
        assertTrue(result.text().contains("\"ok\":true"))
        assertEquals(WaitCall("com.example", "首页", "加载中", 2000L), actions.lastWait)
    }

    @Test
    fun waitTextGoneTimeoutIsNotError() = runTest {
        actions.waitResult = false
        val result = run(
            "gui_wait",
            buildJsonObject {
                put("forTextGone", "加载中")
                put("timeoutMs", 500)
            },
        )
        assertNull(result.isError)
        assertTrue(result.text().contains("\"ok\":false"))
        assertEquals(WaitCall(null, null, "加载中", 500L), actions.lastWait)
    }

    @Test
    fun screenshotWritesPngAndReturnsPaths() = runTest {
        val png = byteArrayOf(1, 2, 3, 4)
        actions.screenshotResult = GuiScreenshot(png, 1080, 2400)
        val result = run("gui_screenshot", buildJsonObject { })
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("\"path\":\"/gui/screenshots/gui-"))
        assertTrue(text.contains("\"width\":1080"))
        assertTrue(text.contains("\"height\":2400"))
        assertTrue(text.contains("\"sizeBytes\":4"))
        assertTrue(text.contains("\"hostPath\":\"${screenshotDir.absolutePath}/gui-"))

        val files = screenshotDir.listFiles()
        assertEquals(1, files?.size)
        val file = files!!.single()
        assertTrue(file.name.startsWith("gui-"))
        assertTrue(file.name.endsWith(".png"))
        assertArrayEquals(png, file.readBytes())
    }

    @Test
    fun screenshotUsesConfiguredLinuxDirectory() = runTest {
        val custom = guiTools(actions, screenshotDir, "/workspace/shots")
        val result = custom.first { it.name == "gui_screenshot" }.execute("call", buildJsonObject { }, null, null)
        assertNull(result.isError)
        assertEquals(true, result.text().contains("\"path\":\"/workspace/shots/gui-"))
    }

    @Test
    fun screenshotFailuresBecomeErrors() = runTest {
        actions.screenshotFailure = IllegalStateException("当前界面禁止截图（安全窗口或 FLAG_SECURE），请用户自行查看")
        val result = run("gui_screenshot", buildJsonObject { })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("禁止截图"))
    }

    @Test
    fun screenshotEmptyPayloadIsError() = runTest {
        actions.screenshotResult = GuiScreenshot(ByteArray(0), 1080, 2400)
        val result = run("gui_screenshot", buildJsonObject { })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("空"))
    }

    @Test
    fun disconnectedServiceFailsAllTools() = runTest {
        actions.disconnected = true
        mapOf(
            "gui_observe" to buildJsonObject { },
            "gui_find" to buildJsonObject { put("text", "确定") },
            "gui_tap" to buildJsonObject { put("ref", "1") },
            "gui_set_text" to buildJsonObject {
                put("ref", "1")
                put("setText", "hello")
            },
            "gui_scroll" to buildJsonObject { put("direction", "down") },
            "gui_global" to buildJsonObject { put("action", "back") },
            "gui_wait" to buildJsonObject { put("forText", "done") },
            "gui_screenshot" to buildJsonObject { },
        ).forEach { (name, args) ->
            val result = run(name, args)
            assertEquals(name, true, result.isError)
            assertTrue(name, result.text().contains("无障碍"))
        }
    }

    @Test
    fun handlerExceptionsBecomeErrorResults() = runTest {
        actions.failWith = IllegalStateException("窗口不可读")
        val result = run("gui_observe", buildJsonObject { })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("窗口不可读"))
    }

    private suspend fun run(name: String, args: JsonObject): AgentToolResult =
        tools.first { it.name == name }.execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun snapshotNode(ref: String, text: String? = null): GuiSnapshotNode = GuiSnapshotNode(
        ref = ref,
        className = "android.widget.TextView",
        bounds = GuiBounds(0, 0, 100, 100),
        text = text,
        desc = null,
        resourceId = null,
        clickable = false,
        scrollable = false,
        editable = false,
        enabled = true,
        depth = 0,
    )

    private class FakeGuiActions : GuiActions {
        var disconnected: Boolean = false
        var failWith: Exception? = null
        var waitResult: Boolean = true
        var setTextReadBack: String? = null
        var screenshotResult: GuiScreenshot = GuiScreenshot(byteArrayOf(1, 2, 3), 1080, 2400)
        var screenshotFailure: Exception? = null
        var lastObserve: Pair<Int, Int>? = null
        var lastTap: Pair<GuiSelector, Int>? = null
        var lastSetText: Pair<GuiSelector, String>? = null
        var lastScroll: Triple<GuiScrollDirection, Int, GuiSelector?>? = null
        var lastGlobal: GuiGlobalAction? = null
        var lastWait: WaitCall? = null

        var snapshot: GuiSnapshot = GuiSnapshot(
            packageName = "com.example",
            windowTitle = "Main",
            nodes = listOf(
                GuiSnapshotNode(
                    ref = "0",
                    className = "android.widget.FrameLayout",
                    bounds = GuiBounds(0, 0, 1080, 2400),
                    text = null,
                    desc = null,
                    resourceId = null,
                    clickable = false,
                    scrollable = false,
                    editable = false,
                    enabled = true,
                    depth = 0,
                ),
                GuiSnapshotNode(
                    ref = "1",
                    className = "android.widget.Button",
                    bounds = GuiBounds(100, 200, 300, 280),
                    text = "确定",
                    desc = "提交按钮",
                    resourceId = "com.example:id/ok",
                    clickable = true,
                    scrollable = false,
                    editable = false,
                    enabled = true,
                    depth = 1,
                ),
            ),
            truncated = false,
        )

        override val connected: Boolean
            get() = !disconnected

        override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot {
            failWith?.let { throw it }
            lastObserve = maxDepth to maxNodes
            return snapshot
        }

        override suspend fun tap(selector: GuiSelector, durationMs: Int) {
            failWith?.let { throw it }
            lastTap = selector to durationMs
        }

        override suspend fun setText(selector: GuiSelector, text: String): GuiTextResult {
            failWith?.let { throw it }
            lastSetText = selector to text
            val actual = setTextReadBack ?: text
            return GuiTextResult(actual, actual == text)
        }

        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {
            failWith?.let { throw it }
            lastScroll = Triple(direction, amount, target)
        }

        override suspend fun global(action: GuiGlobalAction) {
            failWith?.let { throw it }
            lastGlobal = action
        }

        override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): Boolean {
            failWith?.let { throw it }
            lastWait = WaitCall(packageName, text, textGone, timeoutMs)
            return waitResult
        }

        override suspend fun screenshot(): GuiScreenshot {
            screenshotFailure?.let { throw it }
            return screenshotResult
        }
    }
}

private data class WaitCall(
    val packageName: String?,
    val text: String?,
    val textGone: String?,
    val timeoutMs: Long,
)
