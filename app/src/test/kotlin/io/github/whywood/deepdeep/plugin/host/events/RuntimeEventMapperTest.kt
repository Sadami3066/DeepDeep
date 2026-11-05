package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentEvent
import dev.pi.coding.mcp.McpServerState
import dev.pi.coding.mcp.McpServerStatus as SdkMcpStatus
import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus as PluginMcpStatus
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.TurnNotice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
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

    private fun mapper(sink: Sink, includeRuntimeFallbacks: Boolean = true): RuntimeEventMapper =
        RuntimeEventMapper(sink::emit, includeRuntimeFallbacks)

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
    fun mapsToolExecutionPhases() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch(
            "session-1",
            RuntimeEvent.ToolStarted("run-1", "call-1", "bash", mapOf("command" to "ls"), 1_000L),
        )
        mapper.dispatch("session-1", RuntimeEvent.ToolFinished("run-1", "call-1", "bash", "ok", false, 10L))
        mapper.dispatch("session-1", RuntimeEvent.ToolFinished("run-1", "call-2", "bash", "bad", true, 20L))

        assertEquals(
            listOf(
                PluginEvent.ToolExecutionUpdate("session-1", "call-1", "bash", PluginEvent.ToolPhase.STARTED),
                PluginEvent.ToolExecutionUpdate("session-1", "call-1", "bash", PluginEvent.ToolPhase.FINISHED),
                PluginEvent.ToolExecutionUpdate("session-1", "call-2", "bash", PluginEvent.ToolPhase.FAILED),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsDeltasToRunScopedMessageDelta() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.TextDelta("run-1", "hello"))
        mapper.dispatch("session-1", RuntimeEvent.ReasoningDelta("run-1", "thinking"))

        assertEquals(
            listOf(
                PluginEvent.MessageDelta("session-1", "run-1:assistant", "hello"),
                PluginEvent.MessageDelta("session-1", "run-1:assistant", "thinking"),
            ),
            sink.events,
        )
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
    fun mapsCompactionFallbacksFromActivity() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Compacting))
        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.CompactionFailed))
        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Thinking))
        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.RunningTool("bash")))
        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", null))

        assertEquals(
            listOf(
                PluginEvent.CompactionStarted("session-1"),
                PluginEvent.CompactionFailed("session-1", ""),
            ),
            sink.events,
        )
    }

    @Test
    fun mapsRetryFallbackFromActivity() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Retrying(2)))

        assertEquals(
            listOf(PluginEvent.RetryScheduled("session-1", 2, 0L, null)),
            sink.events,
        )
    }

    @Test
    fun runtimeFallbacksCanBeDisabled() = runTest {
        val sink = Sink()
        val mapper = mapper(sink, includeRuntimeFallbacks = false)

        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Compacting))
        mapper.dispatch("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Retrying(1)))
        mapper.dispatch("session-1", NoticeEvent("run-1", TurnNotice.Compacted(120L, 80L)))

        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun mapsCompactionFinishedFromNoticeEvent() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", NoticeEvent("run-1", TurnNotice.Compacted(120L, 80L)))
        mapper.dispatch("session-1", NoticeEvent("run-1", TurnNotice.Compacted(120L, null)))
        mapper.dispatch("session-1", NoticeEvent("run-1", TurnNotice.GoalFinished(true)))
        mapper.dispatch("session-1", NoticeEvent("run-1", TurnNotice.JobFinished("job", false)))

        assertEquals(
            listOf(
                PluginEvent.CompactionFinished("session-1", 120L, 80L),
                PluginEvent.CompactionFinished("session-1", 120L, 0L),
            ),
            sink.events,
        )
    }

    @Test
    fun ignoresSessionLevelAndQueueRuntimeEvents() = runTest {
        val sink = Sink()
        val mapper = mapper(sink)

        mapper.dispatch("session-1", RuntimeEvent.QueueChanged("run-1", listOf("a"), listOf("b")))
        mapper.dispatch("session-1", RuntimeEvent.QueuedInputsDropped(listOf("a")))
        mapper.dispatch("session-1", RuntimeEvent.SessionRemoved())
        mapper.dispatch("session-1", RuntimeEvent.SessionArchived(archived = true))
        mapper.dispatch("session-1", RuntimeEvent.SessionListChanged())
        mapper.dispatch("session-1", io.github.whywood.deepdeep.runtime.BackgroundTurnStarted("run-1"))

        assertTrue(sink.events.isEmpty())
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

        mapper.dispatchSessionEvent("session-1", SessionEvent.AgentEventReceived(AgentEvent.TurnStart))
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
        mapper.dispatch(
            "session-1",
            RuntimeEvent.ToolStarted("run-1", "call-1", "bash", emptyMap(), 2_000L),
        )

        assertEquals(
            listOf(
                PluginEvent.RunStarted("session-1", "run-1"),
                PluginEvent.ToolExecutionUpdate("session-1", "call-1", "bash", PluginEvent.ToolPhase.STARTED),
            ),
            hooks.events,
        )
        assertEquals(listOf("runStarted", "toolExecutionUpdate"), hooks.calls)
    }
}
