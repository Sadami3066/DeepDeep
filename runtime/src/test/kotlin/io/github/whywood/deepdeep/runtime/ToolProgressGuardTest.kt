package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProgressGuardTest {
    @Test
    fun thirdEquivalentActionIsBlockedWithoutStateChange() = runTest {
        val guard = ToolProgressGuard()
        repeat(2) { index ->
            val context = call("call-$index", "screen_tap", buildJsonObject { put("x", 10); put("y", 20) })
            assertEquals(ToolCallDecision.Continue, guard.beforeToolCall(context))
            guard.afterToolResult(result(context, "(no output)"))
        }

        val decision = guard.beforeToolCall(
            call("call-3", "screen_tap", buildJsonObject { put("x", 10); put("y", 20) }),
        )

        assertTrue(decision is ToolCallDecision.Block)
    }

    @Test
    fun changedObservationResetsActionBudget() = runTest {
        val guard = ToolProgressGuard()
        observe(guard, "initial", page("A"))
        act(guard, "tap-1", 10)
        observe(guard, "same", page("A"))
        act(guard, "tap-2", 20)
        observe(guard, "changed", page("B"))

        val decision = guard.beforeToolCall(
            call("tap-3", "screen_tap", buildJsonObject { put("x", 30); put("y", 20) }),
        )

        assertEquals(ToolCallDecision.Continue, decision)
    }

    @Test
    fun thirdCoordinateStrategyIsBlockedAfterTwoUnchangedCycles() = runTest {
        val guard = ToolProgressGuard()
        observe(guard, "initial", page("A"))
        act(guard, "tap-1", 10)
        observe(guard, "same-1", page("A"))
        act(guard, "tap-2", 20)
        val warning = observe(guard, "same-2", page("A"))

        val decision = guard.beforeToolCall(
            call("tap-3", "screen_tap", buildJsonObject { put("x", 30); put("y", 20) }),
        )

        assertTrue(warning is ToolResultDecision.Replace)
        assertTrue(decision is ToolCallDecision.Block)
    }

    @Test
    fun pollingWithoutActionsDoesNotConsumeProgressBudget() = runTest {
        val guard = ToolProgressGuard()
        repeat(4) { index ->
            val decision = observe(guard, "observe-$index", page("A"))
            assertEquals(ToolResultDecision.Keep, decision)
        }
    }

    @Test
    fun duplicateImageIsReplacedWithCompactNotice() = runTest {
        val guard = ToolProgressGuard()
        val first = call("read-1", "read", buildJsonObject { put("path", "/tmp/one.png") })
        guard.beforeToolCall(first)
        val firstDecision = guard.afterToolResult(
            ToolResultContext(first.toolName, first.callId, first.args, AgentToolResult(content = listOf(ImageContent("pixels", "image/png")))),
        )
        val second = call("read-2", "read", buildJsonObject { put("path", "/tmp/two.png") })
        guard.beforeToolCall(second)
        val secondDecision = guard.afterToolResult(
            ToolResultContext(second.toolName, second.callId, second.args, AgentToolResult(content = listOf(ImageContent("pixels", "image/png")))),
        )

        assertEquals(ToolResultDecision.Keep, firstDecision)
        assertTrue(secondDecision is ToolResultDecision.Replace)
        val replaced = (secondDecision as ToolResultDecision.Replace).result
        assertTrue(replaced.content.any { it is TextContent && it.text.contains("Repeated image omitted") })
        assertFalse(replaced.content.any { it is ImageContent })
    }

    @Test
    fun structuredLocatorUsesSeparateStrategyFromCoordinates() = runTest {
        val guard = ToolProgressGuard()
        observe(guard, "initial", page("A"))
        act(guard, "tap-1", 10)
        observe(guard, "same-1", page("A"))
        act(guard, "tap-2", 20)
        observe(guard, "same-2", page("A"))

        val decision = guard.beforeToolCall(
            call("locator", "screen_tap", buildJsonObject { put("text", "Continue") }),
        )

        assertEquals(ToolCallDecision.Continue, decision)
    }

    @Test
    fun thirdTimedOutWaitIsBlockedEvenWhenElapsedTimeDiffers() = runTest {
        val guard = ToolProgressGuard()
        repeat(2) { index ->
            val context = call(
                "wait-$index",
                "screen_wait",
                buildJsonObject { put("forTextGone", "广告") },
            )
            assertEquals(ToolCallDecision.Continue, guard.beforeToolCall(context))
            guard.afterToolResult(
                result(context, "{\"ok\":false,\"elapsedMs\":${5000 + index},\"reason\":\"timeout\"}"),
            )
        }

        val decision = guard.beforeToolCall(
            call("wait-3", "screen_wait", buildJsonObject { put("forTextGone", "广告") }),
        )

        assertTrue(decision is ToolCallDecision.Block)
    }

    @Test
    fun repeatingTwoStateCycleBlocksRepeatedStrategy() = runTest {
        val guard = ToolProgressGuard()
        observe(guard, "a-1", page("A"))
        act(guard, "tap-1", 10)
        observe(guard, "b-1", page("B"))
        act(guard, "tap-2", 20)
        observe(guard, "a-2", page("A"))
        act(guard, "tap-3", 30)
        observe(guard, "b-2", page("B"))

        val decision = guard.beforeToolCall(
            call("tap-4", "screen_tap", buildJsonObject { put("x", 40); put("y", 20) }),
        )

        assertTrue(decision is ToolCallDecision.Block)
    }

    @Test
    fun originalReadBudgetTerminatesRunAndResetsForNextTask() = runTest {
        val guard = ToolProgressGuard()
        var result: ToolResultDecision = ToolResultDecision.Keep
        repeat(13) { index ->
            result = observe(guard, "read-$index", page("A"))
        }

        val terminal = (result as ToolResultDecision.Replace).result
        assertEquals(true, terminal.terminate)
        val error = runCatching { guard.checkRunCompleted() }.exceptionOrNull()
        assertTrue(error is ScreenGuardStopped)
        assertTrue(error?.message.orEmpty().contains("read_loop"))
        guard.beginRun()
        assertEquals(ToolResultDecision.Keep, observe(guard, "new-run", page("A")))
        guard.checkRunCompleted()
    }

    private suspend fun act(guard: ToolProgressGuard, id: String, x: Int) {
        val context = call(id, "screen_tap", buildJsonObject { put("x", x); put("y", 20) })
        assertEquals(ToolCallDecision.Continue, guard.beforeToolCall(context))
        guard.afterToolResult(result(context, "(no output)"))
    }

    private suspend fun observe(guard: ToolProgressGuard, id: String, text: String): ToolResultDecision {
        val context = call(id, "screen_observe", buildJsonObject { put("target", "virtual") })
        assertEquals(ToolCallDecision.Continue, guard.beforeToolCall(context))
        return guard.afterToolResult(result(context, text))
    }

    private fun call(id: String, name: String, args: JsonObject): ToolCallContext =
        ToolCallContext(name, id, args)

    private fun result(context: ToolCallContext, text: String): ToolResultContext =
        ToolResultContext(
            context.toolName,
            context.callId,
            context.args,
            AgentToolResult(content = listOf(TextContent(text))),
        )

    private fun page(value: String): String =
        "{\"nodes\":[{\"ref\":\"7:volatile-$value:1\",\"text\":\"$value\"}]}"
}
