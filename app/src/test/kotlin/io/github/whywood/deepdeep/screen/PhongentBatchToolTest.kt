package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.tool.Until
import com.wangzan101.mika.core.tool.UntilOutcome
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhongentBatchToolTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun laterRefTranslatedThroughStabilizer() = runTest {
        val before = listOf(button("搜索框", 0), button("确定", 500))
        val after = listOf(button("提示", 300), button("搜索框", 0), button("确定", 500))
        val display = Screen(before, after)
        val actions = screenTools(display, folder.root).associateBy { it.name }
        val seen = actions.getValue("screen_observe").execute("before", obj("""{"interface":true}"""), null, null).payload()
        val base = seen.string("snapshotId")
        val elements = seen.getValue("elements").jsonArray.map { it.jsonObject }
        val searchRef = elements.single { it.string("name") == "搜索框" }.string("ref")
        val okayRef = elements.single { it.string("name") == "确定" }.string("ref")
        val out = phongentBatchTool({ display }, { actions }).execute("batch", obj(
            """{"snapshotId":"$base","steps":[{"tool":"screen_tap","args":{"ref":"$searchRef"}},{"tool":"screen_tap","args":{"ref":"$okayRef"}}]}""",
        ), null, null).payload()

        assertEquals("true", out.string("ok"))
        assertEquals(listOf("搜索框", "确定"), display.taps)
        assertTrue(out.getValue("steps").jsonArray.all { it.jsonObject["effectDetail"] != null })
        assertNotNull(out["screen"])
    }

    @Test fun treeBlindResultStopsBeforeTheNextStepUnlessItsAppearanceConditionPasses() = runTest {
        val display = Screen(listOf(button("就绪", 30)))
        var calls = 0
        val tap = CodingTool(name = "screen_tap", description = "tap", parameters = obj("{}"), label = "tap", execute = { _, _, _, _ ->
            calls++
            textResult("""{"ok":true,"changed":false,"effect":{"kind":"no_effect","treeBlind":true}}""")
        })
        val batch = phongentBatchTool({ display }, { mapOf(tap.name to tap) })
        val noCondition = batch.execute("batch", obj(
            """{"steps":[{"tool":"screen_tap","args":{"x":0,"y":0}},{"tool":"screen_tap","args":{"x":1,"y":1}}]}""",
        ), null, null).payload()
        assertEquals("screen_needs_review", noCondition.string("reason"))
        assertEquals(1, calls)
        val expected = batch.execute("batch2", obj(
            """{"steps":[{"tool":"screen_tap","args":{"x":0,"y":0},"expect":"就绪"},{"tool":"screen_tap","args":{"x":1,"y":1}}]}""",
        ), null, null).payload()
        assertEquals("true", expected.string("ok"))
        assertEquals(3, calls)
    }

    @Test fun truncatedTreeIsNotEvidenceOfDisappearance() = runTest {
        val display = Screen(listOf(button("搜索", 30)), truncated = true)
        val result = waitPhongentScreen(display, Until(gone = "加载中", timeoutMs = 1_000), clock = { testScheduler.currentTime })
        assertEquals(UntilOutcome(false, UntilOutcome.TIMEOUT, 1_000), result)
    }

    @Test fun accessibilityEventWakesWaitingBeforeThePollingInterval() = runTest {
        val display = Screen(listOf(button("加载", 30)))
        val eventPort = object : ScreenController by display {
            override suspend fun awaitScreenChange(timeoutMs: Long): Boolean {
                delay(20)
                return true
            }

            override suspend fun observe(maxDepth: Int, maxNodes: Int, expectedPackage: String?): ScreenObserveResult {
                return if (testScheduler.currentTime < 20L) display.observe(maxDepth, maxNodes, expectedPackage)
                else Screen(listOf(button("完成", 30))).observe(maxDepth, maxNodes, expectedPackage)
            }
        }
        val result = waitPhongentScreen(eventPort, Until(appears = "完成", timeoutMs = 1_000), clock = { testScheduler.currentTime })
        assertEquals(UntilOutcome(true, null, 220), result)
    }

    @Test fun rejectsMixedTargetsBeforeDispatchingAnyStep() = runTest {
        val display = Screen(listOf(button("继续", 100)))
        val tools = screenTools(display, folder.root).associateBy { it.name }
        val result = phongentBatchTool({ display }, { tools }).execute(
            "batch",
            obj("""{"steps":[{"tool":"screen_tap","args":{"x":1,"y":1}},{"tool":"screen_tap","args":{"target":"main","x":2,"y":2}}]}"""),
            null,
            null,
        )
        assertEquals(true, result.isError)
        assertEquals("mixed_surface", result.payload().string("error"))
        assertEquals(emptyList<String>(), display.taps)
    }

    private class Screen(
        private val first: List<GuiNode>,
        private val next: List<GuiNode> = first,
        private val truncated: Boolean = false,
    ) : ScreenController {
        val taps = mutableListOf<String>()
        private val observedRefs = mutableMapOf<String, String>()
        private var observations = 0
        override suspend fun status() = ScreenStatusSnapshot(true, true, "online")
        override suspend fun effectDisplayInfo() = ScreenDisplayInfo(0, 1080, 2400, BACKEND_ACCESSIBILITY)
        override suspend fun settleForEffect(timeoutMs: Long) = true
        override suspend fun observe(maxDepth: Int, maxNodes: Int, expectedPackage: String?): ScreenObserveResult {
            val nodes = if (taps.isEmpty()) first else next
            val root = GuiNode("Root", GuiBounds(0, 0, 1080, 2400), children = nodes)
            val snapshot = GuiNodeTree.buildSnapshot(root, "com.example", null, maxDepth, maxNodes)
            val named = snapshot.nodes.mapIndexed { index, node ->
                node.copy(ref = "${++observations}:$index").also {
                    it.text?.let { text -> observedRefs[it.ref] = text }
                }
            }
            return ScreenObserveResult(BACKEND_ACCESSIBILITY, snapshot.copy(nodes = named, truncated = truncated), scope = "fixture")
        }
        override suspend fun tap(selector: GuiSelector, durationMs: Int): String {
            taps += when (selector) {
                is GuiSelector.Ref -> observedRefs.getValue(selector.ref)
                else -> selector.toString()
            }
            return "tapped"
        }
        override suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult = error("unused")
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String = error("unused")
        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, selector: GuiSelector?): String = error("unused")
        override suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult = error("unused")
        override suspend fun key(name: String): String = error("unused")
        override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): ScreenWaitOutcome = error("unused")
        override suspend fun screenshot(): ScreenCapture = error("unused")
        override suspend fun launch(packageName: String): ScreenLaunchReport = error("unused")
        override suspend fun listApps(): List<ScreenApp> = error("unused")
    }

    private fun button(name: String, y: Int) = GuiNode("Button", GuiBounds(100, y, 500, y + 60), text = name, clickable = true)
    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun AgentToolResult.payload() = structuredContent as? JsonObject ?: obj((content.single() as TextContent).text)
}
