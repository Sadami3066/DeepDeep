package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentEvent
import dev.pi.agent.AgentMessage
import dev.pi.agent.AgentToolResult
import dev.pi.agent.StandardAgentMessage
import dev.pi.ai.AssistantMessage as PiAssistantMessage
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.ai.ToolCall
import dev.pi.ai.Usage
import dev.pi.coding.mcp.McpServerState
import dev.pi.coding.mcp.McpServerStatus as SdkMcpStatus
import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus as PluginMcpStatus
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEventMapperTest {
    private class Sink {
        val events = mutableListOf<PluginEvent>()
        var failures: List<PluginHookFailure> = emptyList()

        suspend fun emit(event: PluginEvent): List<PluginHookFailure> {
            events += event
            return failures
        }
    }

    private fun mapper(sink: Sink): RuntimeEventMapper = RuntimeEventMapper(sink::emit)

    private fun assistant(
        content: List<dev.pi.ai.Content> = emptyList(),
        timestamp: Long = 10L,
        stopReason: dev.pi.ai.StopReason = dev.pi.ai.StopReason.Stop,
    ): AgentMessage = StandardAgentMessage(
        PiAssistantMessage(
            content = content.toMutableList(),
            api = "test",
            provider = "test",
            model = "test",
            usage = Usage.empty(),
            stopReason = stopReason,
            timestamp = timestamp,
        ),
    )

    private fun toolPartial(text: String): AgentToolResult =
        AgentToolResult(content = listOf(TextContent(text)))

    @Test
    fun mapsRunLifecycle() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.RunStarted("run-1", 1_000L, "hello"))
        mapper.dispatch("session-1", RuntimeEvent.RunFinished("run-1", "done", RunStatus.DONE, 2_000L))
        mapper.dispatch("session-1", RuntimeEvent.RunFinished("run-1", "stopped", RunStatus.STOPPED, 3_000L))
        mapper.dispatch("session-1", RuntimeEvent.RunFailed("run-2", "boom"))

        assertEquals(
            listOf(
                PluginEvent.RunStarted("session-1", "run-1"),
                PluginEvent.RunFinished("session-1", "run-1"),
                PluginEvent.RunFinished("session-1", "run-1"),
                PluginEvent.RunFinished("session-1", "run-2", "boom"),
            ),
            sink.events,
        )
    }

    @Test
    fun ignoresRuntimeToolAndDeltaEventsToAvoidDuplicates() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch(
            "session-1",
            RuntimeEvent.ToolStarted("run-1", "call-1", "bash", mapOf("command" to "ls"), 1_000L),
        )
        mapper.dispatch("session-1", RuntimeEvent.ToolFinished("run-1", "call-1", "bash", "ok", false, 10L))
        mapper.dispatch("session-1", RuntimeEvent.TextDelta("run-1", "hello"))
        mapper.dispatch("session-1", RuntimeEvent.ReasoningDelta("run-1", "thinking"))

        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun mapsUserAndSystemMessages() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.UserInputAdmitted("run-1", "hi"))
        mapper.dispatch("session-1", RuntimeEvent.TurnInjected("run-1", "reminder body", "闹钟"))

        assertEquals(
            listOf(
                PluginEvent.MessageStarted("session-1", "run-1:user", PluginEvent.MessageRole.USER),
                PluginEvent.MessageFinished("session-1", "run-1:user", "hi"),
                PluginEvent.MessageStarted("session-1", "run-1:system", PluginEvent.MessageRole.SYSTEM),
                PluginEvent.MessageFinished("session-1", "run-1:system", "reminder body"),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsAgentTurnAndMessageLifecycle() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)
        val partial = assistant(content = listOf(TextContent("")), timestamp = 10L)

        mapper.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.TurnStart))
        mapper.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.MessageStart(partial)))
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.MessageUpdate(partial, TextDeltaEvent(0, "hello", (partial as StandardAgentMessage).message as PiAssistantMessage)),
            ),
        )
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.MessageEnd(assistant(content = listOf(TextContent("hello")), timestamp = 10L)),
            ),
        )
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(AgentEvent.TurnEnd(assistant(timestamp = 10L), emptyList())),
        )

        assertEquals(
            listOf(
                PluginEvent.TurnStarted("session-1", "session-1:turn:1"),
                PluginEvent.MessageStarted("session-1", "session-1:assistant:10", PluginEvent.MessageRole.ASSISTANT),
                PluginEvent.MessageDelta("session-1", "session-1:assistant:10", "hello"),
                PluginEvent.MessageFinished("session-1", "session-1:assistant:10", "hello"),
                PluginEvent.TurnFinished("session-1", "session-1:turn:1", false),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsAgentThinkingDeltaAsMessageDelta() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)
        val partial = assistant(timestamp = 11L)

        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.MessageUpdate(
                    partial,
                    ThinkingDeltaEvent(0, "hmm", (partial as StandardAgentMessage).message as PiAssistantMessage),
                ),
            ),
        )

        assertEquals(listOf(PluginEvent.MessageDelta("session-1", "session-1:assistant:11", "hmm")), sink.events)
    }

    @Test
    fun mapsAgentToolExecutionPhasesWithPartialAndResult() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)
        val arguments = buildJsonObject { put("command", "ls") }

        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(AgentEvent.ToolExecutionStart("call-1", "bash", arguments)),
        )
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.ToolExecutionUpdate("call-1", "bash", arguments, toolPartial("partial")),
            ),
        )
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.ToolExecutionEnd("call-1", "bash", toolPartial("ok"), isError = false, durationMs = 10L),
            ),
        )
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.ToolExecutionEnd("call-2", "bash", toolPartial("bad"), isError = true, durationMs = 20L),
            ),
        )

        val events = sink.events.filterIsInstance<PluginEvent.ToolExecutionUpdate>()
        assertEquals(
            listOf(
                PluginEvent.ToolPhase.STARTED,
                PluginEvent.ToolPhase.PARTIAL,
                PluginEvent.ToolPhase.FINISHED,
                PluginEvent.ToolPhase.FAILED,
            ),
            events.map { it.phase },
        )
        assertEquals(PluginToolResult.Text("partial"), events[1].partial)
        assertEquals(PluginToolResult.Text("ok"), events[2].partial)
        assertEquals(PluginToolResult.Text("bad", isError = true), events[3].partial)
    }

    @Test
    fun marksFailedTurnEnd() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.TurnStart))
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(
                AgentEvent.TurnEnd(assistant(stopReason = dev.pi.ai.StopReason.Error), emptyList()),
            ),
        )

        assertEquals(
            listOf(
                PluginEvent.TurnStarted("session-1", "session-1:turn:1"),
                PluginEvent.TurnFinished("session-1", "session-1:turn:1", true),
            ),
            sink.events,
        )
    }

    @Test
    fun ignoresAgentStartAndAgentEnd() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.AgentStart))
        mapper.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(AgentEvent.AgentEnd(emptyList())),
        )

        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun turnIdsStayConsistentAcrossMappersSharingAdapterIds() = runTest {
        val sink = Sink()
        val ids = PluginEventIds()
        val first = RuntimeEventMapper(sink::emit, ids)
        val second = RuntimeEventMapper(sink::emit, ids)

        first.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.TurnStart))
        second.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(AgentEvent.TurnEnd(assistant(), emptyList())),
        )
        second.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.TurnStart))
        first.dispatchSessionEvent(
            "session-1",
            SessionEvent.AgentEventReceived(AgentEvent.TurnEnd(assistant(), emptyList())),
        )

        assertEquals(
            listOf(
                PluginEvent.TurnStarted("session-1", "session-1:turn:1"),
                PluginEvent.TurnFinished("session-1", "session-1:turn:1", false),
                PluginEvent.TurnStarted("session-1", "session-1:turn:2"),
                PluginEvent.TurnFinished("session-1", "session-1:turn:2", false),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsSdkModelAndCompactionEvents() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatchSessionEvent("session-1", SessionEvent.ModelChanged("deepseek", "deepseek-chat", "high"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.CompactionStarted(120L))
        mapper.dispatchSessionEvent("session-1", SessionEvent.CompactionFinished(120L, 80L))
        mapper.dispatchSessionEvent("session-1", SessionEvent.CompactionFinished(120L, null))
        mapper.dispatchSessionEvent("session-1", SessionEvent.CompactionFailed("boom"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.CompactionFailed(null))

        assertEquals(
            listOf(
                PluginEvent.ModelChanged("session-1", "deepseek/deepseek-chat"),
                PluginEvent.ThinkingLevelChanged("session-1", "high"),
                PluginEvent.CompactionStarted("session-1"),
                PluginEvent.CompactionFinished("session-1", 120L, 80L),
                PluginEvent.CompactionFinished("session-1", 120L, 0L),
                PluginEvent.CompactionFailed("session-1", "boom"),
                PluginEvent.CompactionFailed("session-1", ""),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsSdkRetryEvents() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatchSessionEvent("session-1", SessionEvent.RetryScheduled(2, 500L, "rate limited"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.RetrySucceeded(2))
        mapper.dispatchSessionEvent("session-1", SessionEvent.RetryExhausted(3, "boom"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.RetryExhausted(3, null))

        assertEquals(
            listOf(
                PluginEvent.RetryScheduled("session-1", 2, 500L, "rate limited"),
                PluginEvent.RetrySucceeded("session-1", 2),
                PluginEvent.RetryExhausted("session-1", 3, "boom"),
                PluginEvent.RetryExhausted("session-1", 3, ""),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsSdkMcpEvents() = runTest {
        val states = listOf(
            McpServerState.Connected to PluginMcpStatus.OK,
            McpServerState.AuthRequired to PluginMcpStatus.AUTH_REQUIRED,
            McpServerState.Failed to PluginMcpStatus.FAILED,
            McpServerState.Disabled to PluginMcpStatus.UNKNOWN,
            McpServerState.Connecting to PluginMcpStatus.UNKNOWN,
            McpServerState.Closed to PluginMcpStatus.UNKNOWN,
            McpServerState.Unsupported to PluginMcpStatus.UNKNOWN,
        )
        for ((state, expected) in states) {
            val sink = Sink()
            mapper(sink).dispatchSessionEvent(
                "session-1",
                SessionEvent.McpServerStatusChanged("server", SdkMcpStatus("server", state)),
            )
            assertEquals(listOf(PluginEvent.McpStatusChanged("server", expected)), sink.events)
        }

        val sink = Sink()
        mapper(sink).dispatchSessionEvent(
            "session-1",
            SessionEvent.McpToolsChanged("server", listOf("added-tool"), listOf("removed-tool")),
        )
        assertEquals(listOf(PluginEvent.McpToolsChanged("server", listOf("added-tool"))), sink.events)
    }

    @Test
    fun mapsSdkCustomMessageToHostBusEvent() = runTest {
        val sink = Sink()
        mapper(sink).dispatchSessionEvent("session-1", SessionEvent.CustomMessageAppended("note", "entry-1"))

        val event = sink.events.single() as PluginEvent.BusEvent
        assertEquals(RUNTIME_CUSTOM_MESSAGE_TOPIC, event.event.topic)
        assertEquals(HOST_BUS_PUBLISHER, event.event.publisher)
        assertEquals("session-1", event.event.payload["sessionId"]?.jsonPrimitive?.content)
        assertEquals("note", event.event.payload["customType"]?.jsonPrimitive?.content)
        assertEquals("entry-1", event.event.payload["entryId"]?.jsonPrimitive?.content)
    }

    @Test
    fun ignoresUnmappedSdkSessionEvents() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatchSessionEvent("session-1", SessionEvent.Settled)
        mapper.dispatchSessionEvent("session-1", SessionEvent.SessionError("boom"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.InputHandled("text"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.McpLog("server", "info", "message"))
        mapper.dispatchSessionEvent("session-1", SessionEvent.McpSignInRequired("server", "https://example.com"))

        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun aggregatesSinkFailuresAndKeepsDispatching() = runTest {
        val sink = Sink()
        sink.failures = listOf(PluginHookFailure("plugin-a", PluginHookStage.OBSERVE, "boom"))
        val mapper = mapper(sink)

        val failures = mapper.dispatch("session-1", RuntimeEvent.UserInputAdmitted("run-1", "hi"))

        assertEquals(2, sink.events.size)
        assertEquals(2, failures.size)
        assertEquals("plugin-a", failures.first().pluginId)
    }

    @Test
    fun dispatchesSessionRuntimeEventUsingWrapperSession() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch(
            PluginHookSession("wrapped-session", "/workspace"),
            SessionRuntimeEvent("other-session", RuntimeEvent.RunStarted("run-1", 1_000L)),
        )

        assertEquals(listOf(PluginEvent.RunStarted("wrapped-session", "run-1")), sink.events)
    }

    @Test
    fun routesMappedEventsThroughHooks() = runTest {
        val hooks = EventRecordingHooks()
        val adapter = PluginRuntimeHooksAdapter(
            PluginHookSession("session-1", "/workspace"),
            TestHookSource(listOf(hookEntry("probe", hooks))),
            RecordingReporter(),
        )
        val mapper = RuntimeEventMapper(adapter)

        mapper.dispatch("session-1", RuntimeEvent.RunStarted("run-1", 1_000L, "hi"))
        mapper.dispatch("session-1", RuntimeEvent.TurnInjected("run-1", "reminder", null))

        assertEquals(
            listOf(
                PluginEvent.RunStarted("session-1", "run-1"),
                PluginEvent.MessageStarted("session-1", "run-1:system", PluginEvent.MessageRole.SYSTEM),
                PluginEvent.MessageFinished("session-1", "run-1:system", "reminder"),
            ),
            hooks.events,
        )
        assertEquals(listOf("runStarted", "messageStarted", "messageFinished"), hooks.calls)
    }
}
