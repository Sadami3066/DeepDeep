package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.Agent
import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.agent.AgentInitialState
import io.github.whywood.deepdeep.sdk.agent.AgentOptions
import io.github.whywood.deepdeep.sdk.agent.AgentTool
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.agent.AgentToolUpdateCallback
import io.github.whywood.deepdeep.sdk.agent.JsonArgs
import io.github.whywood.deepdeep.sdk.agent.StreamFn
import io.github.whywood.deepdeep.sdk.ai.AssistantMessage as PiAssistantMessage
import io.github.whywood.deepdeep.sdk.ai.Content
import io.github.whywood.deepdeep.sdk.ai.DoneEvent
import io.github.whywood.deepdeep.sdk.ai.Model
import io.github.whywood.deepdeep.sdk.ai.ModelCost
import io.github.whywood.deepdeep.sdk.ai.ModelInputModality
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.Usage
import io.github.whywood.deepdeep.sdk.ai.createAssistantMessageEventStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolExecutionHooksTest {
    private fun model(): Model = Model(
        id = "mock",
        name = "mock",
        api = "mock",
        provider = "mock",
        baseUrl = "http://127.0.0.1",
        input = listOf(ModelInputModality.Text),
        cost = ModelCost(input = 0.0, output = 0.0, cacheRead = 0.0, cacheWrite = 0.0),
        reasoning = false,
        contextWindow = 8_192,
        maxTokens = 2_048,
    )

    private fun assistant(
        content: List<Content>,
        stopReason: StopReason = StopReason.Stop,
    ): PiAssistantMessage = PiAssistantMessage(
        content = content.toMutableList(),
        api = "mock",
        provider = "mock",
        model = "mock",
        usage = Usage.empty(),
        stopReason = stopReason,
        timestamp = System.currentTimeMillis(),
    )

    private fun doneStream(message: PiAssistantMessage) = createAssistantMessageEventStream().also { stream ->
        stream.push(DoneEvent(message.stopReason, message))
    }

    private fun toolCallAssistant(id: String, name: String, arguments: JsonObject): PiAssistantMessage =
        assistant(listOf(ToolCall(id, name, arguments)))

    private fun textAssistant(text: String): PiAssistantMessage = assistant(listOf(TextContent(text)))

    private fun agentTool(
        name: String,
        execute: suspend (String, JsonArgs, RunSignal?, AgentToolUpdateCallback?) -> AgentToolResult,
    ): AgentTool = AgentTool(
        name = name,
        description = name,
        parameters = buildJsonObject { put("type", "object") },
        label = name,
        execute = execute,
    )

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun singleToolEnd(events: List<AgentEvent>): AgentEvent.ToolExecutionEnd =
        events.filterIsInstance<AgentEvent.ToolExecutionEnd>().single()

    @Test
    fun dynamicToolAddedAfterHookInstallRunsThroughBeforeAndAfter() = runTest {
        val calls = mutableListOf<String>()
        val hooks = object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision {
                calls += "before:${context.toolName}:${context.args["value"]?.jsonPrimitive?.content}"
                return ToolCallDecision.Rewrite(buildJsonObject { put("value", "rewritten") })
            }

            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                calls += "after:${context.result.content.filterIsInstance<TextContent>().joinToString("") { it.text }}"
                return ToolResultDecision.Replace(
                    AgentToolResult(content = listOf(TextContent("replaced-result"))),
                )
            }
        }
        var streamCall = 0
        val streamFn: StreamFn = { _, _, _ ->
            when (streamCall++) {
                0 -> doneStream(toolCallAssistant("c1", "dynamic_tool", buildJsonObject { put("value", "original") }))
                else -> doneStream(textAssistant("done"))
            }
        }
        val agent = Agent(
            AgentOptions(
                initialState = AgentInitialState(model = model(), tools = emptyList()),
                streamFn = streamFn,
            ),
        )
        agent.installToolExecutionHooks(hooks)
        var executedArgs: JsonArgs? = null
        agent.state.tools.add(
            agentTool("dynamic_tool") { _, args, _, _ ->
                executedArgs = args
                AgentToolResult(content = listOf(TextContent("raw-result")))
            },
        )
        val events = mutableListOf<AgentEvent>()
        agent.subscribe { event, _ -> events.add(event) }

        agent.prompt("go")

        assertEquals("rewritten", executedArgs?.get("value")?.jsonPrimitive?.content)
        assertEquals(listOf("before:dynamic_tool:original", "after:raw-result"), calls)
        val end = singleToolEnd(events)
        assertEquals("replaced-result", resultText(end.result))
        assertFalse(end.isError)
    }

    @Test
    fun throwingToolStillRunsAfterHook() = runTest {
        val calls = mutableListOf<String>()
        val hooks = object : ToolExecutionHooks {
            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                calls += "after:${context.result.content.filterIsInstance<TextContent>().joinToString("") { it.text }}"
                return ToolResultDecision.Keep
            }
        }
        var streamCall = 0
        val streamFn: StreamFn = { _, _, _ ->
            when (streamCall++) {
                0 -> doneStream(toolCallAssistant("c1", "exploding", buildJsonObject { }))
                else -> doneStream(textAssistant("done"))
            }
        }
        val agent = Agent(
            AgentOptions(
                initialState = AgentInitialState(model = model(), tools = emptyList()),
                streamFn = streamFn,
            ),
        )
        agent.installToolExecutionHooks(hooks)
        agent.state.tools.add(agentTool("exploding") { _, _, _, _ -> error("boom") })
        val events = mutableListOf<AgentEvent>()
        agent.subscribe { event, _ -> events.add(event) }

        agent.prompt("go")

        assertEquals(listOf("after:boom"), calls)
        assertTrue(singleToolEnd(events).isError)
        assertEquals("boom", resultText(singleToolEnd(events).result))
    }

    @Test
    fun blockedToolDoesNotExecuteAndReportsReason() = runTest {
        val calls = mutableListOf<String>()
        val hooks = object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                ToolCallDecision.Block("blocked by plugin")

            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                calls += "after"
                return ToolResultDecision.Keep
            }
        }
        var streamCall = 0
        val streamFn: StreamFn = { _, _, _ ->
            when (streamCall++) {
                0 -> doneStream(toolCallAssistant("c1", "guarded", buildJsonObject { }))
                else -> doneStream(textAssistant("done"))
            }
        }
        val agent = Agent(
            AgentOptions(
                initialState = AgentInitialState(model = model(), tools = emptyList()),
                streamFn = streamFn,
            ),
        )
        agent.installToolExecutionHooks(hooks)
        var executed = false
        agent.state.tools.add(
            agentTool("guarded") { _, _, _, _ ->
                executed = true
                AgentToolResult(content = listOf(TextContent("should not run")))
            },
        )
        val events = mutableListOf<AgentEvent>()
        agent.subscribe { event, _ -> events.add(event) }

        agent.prompt("go")

        assertFalse(executed)
        assertTrue(calls.isEmpty())
        val end = singleToolEnd(events)
        assertTrue(end.isError)
        assertEquals("blocked by plugin", resultText(end.result))
    }

    @Test
    fun hooksWithoutPluginWrappersLeaveAgentHooksNull() {
        val streamFn: StreamFn = { _, _, _ -> doneStream(textAssistant("unused")) }
        val agent = Agent(
            AgentOptions(
                initialState = AgentInitialState(model = model(), tools = emptyList()),
                streamFn = streamFn,
            ),
        )
        agent.installToolExecutionHooks(null)
        assertEquals(null, agent.beforeToolCall)
        assertEquals(null, agent.afterToolCall)
    }
}
