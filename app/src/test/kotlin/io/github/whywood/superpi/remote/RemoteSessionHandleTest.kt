package io.github.whywood.superpi.remote

import io.github.whywood.superpi.runtime.AssistantRole
import io.github.whywood.superpi.runtime.AssistantStep
import io.github.whywood.superpi.runtime.ModelConfig
import io.github.whywood.superpi.data.relay.RemoteCallError
import io.github.whywood.superpi.runtime.SessionHandle
import io.github.whywood.superpi.sdk.agent.AgentEvent
import io.github.whywood.superpi.sdk.agent.asLlmMessage
import io.github.whywood.superpi.sdk.ai.AssistantMessage
import io.github.whywood.superpi.sdk.ai.BlockContent
import io.github.whywood.superpi.sdk.ai.MessageContent
import io.github.whywood.superpi.sdk.ai.PlainTextContent
import io.github.whywood.superpi.sdk.ai.StopReason
import io.github.whywood.superpi.sdk.ai.TextContent
import io.github.whywood.superpi.sdk.ai.TextDeltaEvent
import io.github.whywood.superpi.sdk.ai.ToolCall
import io.github.whywood.superpi.sdk.ai.ToolResultMessage
import io.github.whywood.superpi.sdk.ai.UserMessage
import io.github.whywood.superpi.sdk.coding.session.QueueBehavior
import io.github.whywood.superpi.sdk.coding.session.SessionEvent
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSessionHandleTest {
    private lateinit var root: File
    private lateinit var cwd: File
    private lateinit var agentDir: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("remote-session").toFile()
        cwd = File(root, "remote/device-1/pi")
        agentDir = File(root, "agent")
        cwd.mkdirs()
        agentDir.mkdirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun promptMirrorsStreamingTurnAndSettles() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("hello") }
        runCurrent()
        assertEquals(1, transport.calls.size)
        val call = transport.calls.single()
        assertEquals("bot.prompt", call.method)
        assertEquals(FakeRemoteTransport.DEVICE_ID, call.to)
        assertEquals("pi", call.params.getValue("botId").jsonPrimitive.content)
        assertEquals("hello", call.params.getValue("text").jsonPrimitive.content)
        assertNull(call.params["sessionId"])
        assertTrue(handle.isBusy)

        transport.emit(call.routeId, 0, FakeEvents.agentStart())
        transport.emit(call.routeId, 1, FakeEvents.textDelta("Hel"))
        transport.emit(call.routeId, 2, FakeEvents.textDelta("lo"))
        transport.emit(call.routeId, 3, FakeEvents.toolCall("call-1", "shell"))
        runCurrent()
        assertTrue(handle.isBusy)

        transport.emit(call.routeId, 4, FakeEvents.toolResult("call-1", "a.txt"))
        transport.emit(call.routeId, 5, FakeEvents.agentEnd("done"))
        transport.emit(call.routeId, 6, FakeEvents.settled())
        runCurrent()
        assertTrue(job.isCompleted)
        assertFalse(handle.isBusy)

        val agentEvents = received.filterIsInstance<SessionEvent.AgentEventReceived>().map { it.event }
        val deltas = agentEvents.filterIsInstance<AgentEvent.MessageUpdate>()
            .mapNotNull { (it.assistantMessageEvent as? TextDeltaEvent)?.delta }
        assertEquals(listOf("Hel", "lo"), deltas)
        assertTrue(agentEvents.any { it is AgentEvent.ToolExecutionStart })
        assertTrue(agentEvents.any { it is AgentEvent.ToolExecutionEnd })
        assertTrue(agentEvents.any { it is AgentEvent.AgentStart })
        assertTrue(agentEvents.any { it is AgentEvent.AgentEnd })

        assertEquals("Hello", handle.lastAssistantText())
        val messages = handle.messages.mapNotNull { it.asLlmMessage() }
        assertEquals(3, messages.size)
        assertTrue(messages[0] is UserMessage)
        val assistant = messages[1] as AssistantMessage
        assertEquals(StopReason.Stop, assistant.stopReason)
        assertEquals("Hello", assistant.content.filterIsInstance<TextContent>().joinToString("") { it.text })
        val toolCall = assistant.content.filterIsInstance<ToolCall>().single()
        assertEquals("call-1", toolCall.id)
        assertEquals("shell", toolCall.name)
        val toolResult = messages[2] as ToolResultMessage
        assertEquals("call-1", toolResult.toolCallId)
        assertEquals("a.txt", toolResult.content.filterIsInstance<TextContent>().joinToString("") { it.text })
        assertFalse(toolResult.isError)

        val history = handle.history()
        assertEquals(AssistantRole.USER, history.first().role)
        val assistantHistory = history.first { it.role == AssistantRole.ASSISTANT }
        val historyText = assistantHistory.text + assistantHistory.steps.filterIsInstance<AssistantStep.Text>()
            .joinToString("") { step -> step.text }
        assertTrue(historyText.contains("Hello"))
        assertTrue(assistantHistory.steps.filterIsInstance<AssistantStep.Tool>().any { step -> step.trace.name == "shell" })

        val file = File(handle.sessionFile!!)
        assertTrue(file.isFile)
        assertTrue(file.readLines().any { it.contains("superpi.remote") && it.contains(FakeRemoteTransport.PEER_SESSION_ID) })

        val next = launch { handle.prompt("again") }
        runCurrent()
        val second = transport.calls[1]
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, second.params.getValue("sessionId").jsonPrimitive.content)
        transport.emit(second.routeId, 0, FakeEvents.textDelta("ok"))
        transport.emit(second.routeId, 1, FakeEvents.settled())
        runCurrent()
        assertTrue(next.isCompleted)
    }

    @Test
    fun steerAndFollowUpCallRemoteMethods() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }
        finishPrompt(scope = this, transport = transport, handle = handle, text = "hello", reply = "hi")

        val turn = launch { handle.prompt("working") }
        runCurrent()
        val promptCall = transport.calls.last()
        assertEquals("bot.prompt", promptCall.method)

        handle.prompt("stop that", behavior = QueueBehavior.Steer)
        handle.prompt("then this", behavior = QueueBehavior.FollowUp)
        runCurrent()

        val steer = transport.calls.first { it.method == "bot.steer" }
        assertEquals("pi", steer.params.getValue("botId").jsonPrimitive.content)
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, steer.params.getValue("sessionId").jsonPrimitive.content)
        assertEquals("stop that", steer.params.getValue("text").jsonPrimitive.content)

        val followUp = transport.calls.first { it.method == "bot.follow_up" }
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, followUp.params.getValue("sessionId").jsonPrimitive.content)
        assertEquals("then this", followUp.params.getValue("text").jsonPrimitive.content)

        val steered = received.filterIsInstance<SessionEvent.AgentEventReceived>()
            .map { it.event }
            .filterIsInstance<AgentEvent.MessageStart>()
            .mapNotNull { (it.message.asLlmMessage() as? UserMessage)?.content?.text() }
        assertEquals(listOf("stop that", "then this"), steered)

        transport.emit(promptCall.routeId, 0, FakeEvents.textDelta("done"))
        transport.emit(promptCall.routeId, 1, FakeEvents.settled())
        runCurrent()
        assertTrue(turn.isCompleted)
        val userTexts = handle.messages.mapNotNull { it.asLlmMessage() as? UserMessage }
            .map { message -> message.content.text() }
        assertEquals(listOf("hello", "working", "stop that", "then this"), userTexts)
        assertTrue(handle.messages.last().asLlmMessage() is AssistantMessage)
    }

    @Test
    fun abortSendsRemoteAbortAndStopsTurn() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("long turn") }
        runCurrent()
        val call = transport.calls.single()
        transport.emit(call.routeId, 0, FakeEvents.textDelta("partial"))
        runCurrent()

        handle.abort()
        runCurrent()

        assertTrue(job.isCompleted)
        assertFalse(handle.isBusy)
        val abort = transport.calls.first { it.method == "bot.abort" }
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, abort.params.getValue("sessionId").jsonPrimitive.content)

        val assistant = handle.messages.mapNotNull { it.asLlmMessage() }.filterIsInstance<AssistantMessage>().last()
        assertEquals(StopReason.Aborted, assistant.stopReason)
        assertEquals("partial", assistant.content.filterIsInstance<TextContent>().joinToString("") { it.text })
        assertTrue(received.filterIsInstance<SessionEvent.SessionError>().isEmpty())
    }

    @Test
    fun offlinePromptEmitsSessionError() = runTest {
        val transport = FakeRemoteTransport(online = false)
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        handle.prompt("hello")

        assertTrue(transport.calls.isEmpty())
        assertFalse(handle.isBusy)
        val error = received.filterIsInstance<SessionEvent.SessionError>().single()
        assertTrue(error.message.contains("offline"))
    }

    @Test
    fun failureEventFailsTurnAndMirrorsPartial() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("hello") }
        runCurrent()
        val call = transport.calls.single()
        transport.emit(call.routeId, 0, FakeEvents.textDelta("partial"))
        transport.emit(call.routeId, 1, FakeEvents.failure("model exploded"))
        runCurrent()

        assertTrue(job.isCompleted)
        assertEquals("model exploded", received.filterIsInstance<SessionEvent.SessionError>().single().message)
        val assistant = handle.messages.mapNotNull { it.asLlmMessage() }
            .filterIsInstance<AssistantMessage>().last()
        assertEquals(StopReason.Error, assistant.stopReason)
        assertEquals("model exploded", assistant.errorMessage)
        assertEquals("partial", assistant.content.filterIsInstance<TextContent>().joinToString("") { it.text })
    }

    @Test
    fun duplicateAndForeignRouteEventsAreDeliveredOnce() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("hello") }
        runCurrent()
        val call = transport.calls.single()

        val delta = FakeEvents.textDelta("x")
        transport.emit(call.routeId, 0, delta)
        transport.emit(call.routeId, 0, delta)
        transport.emit("another-route", 0, FakeEvents.textDelta("foreign"))
        runCurrent()

        val deltas = received.filterIsInstance<SessionEvent.AgentEventReceived>()
            .map { it.event }
            .filterIsInstance<AgentEvent.MessageUpdate>()
            .mapNotNull { (it.assistantMessageEvent as? TextDeltaEvent)?.delta }
        assertEquals(listOf("x"), deltas)

        transport.emit(call.routeId, 1, FakeEvents.settled())
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun turnTimesOutWithSessionError() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("hello") }
        runCurrent()
        advanceTimeBy(10 * 60 * 1000L + 1)
        runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(
            "The remote bot did not finish the turn.",
            received.filterIsInstance<SessionEvent.SessionError>().single().message,
        )
        assertFalse(handle.isBusy)
    }

    @Test
    fun reopenedMirrorRestoresHistoryAndPeerSession() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val first = engine.create(null, ModelConfig())
        finishPrompt(scope = this, transport = transport, handle = first, text = "hello", reply = "hi there")
        first.appendTitle("Remote chat")
        val file = first.sessionFile!!
        first.close()

        val reopened = engine.create(file, ModelConfig())
        assertEquals(first.sessionId, reopened.sessionId)
        assertEquals("Remote chat", reopened.sessionName())
        assertEquals("hi there", reopened.lastAssistantText())
        assertEquals(2, reopened.messages.size)
        val history = reopened.history()
        assertEquals(AssistantRole.USER, history.first().role)
        assertTrue(history.any { it.role == AssistantRole.ASSISTANT && it.text.contains("hi there") })

        val job = launch { reopened.prompt("next") }
        runCurrent()
        val call = transport.calls.last()
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, call.params.getValue("sessionId").jsonPrimitive.content)
        transport.emit(call.routeId, 0, FakeEvents.settled())
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun connectionLossEndsTurnWithSessionError() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }

        val job = launch { handle.prompt("hello") }
        runCurrent()
        val call = transport.calls.single()
        transport.emit(call.routeId, 0, FakeEvents.textDelta("partial"))
        runCurrent()
        assertTrue(handle.isBusy)

        transport.setOnline(false)
        runCurrent()

        assertTrue(job.isCompleted)
        assertFalse(handle.isBusy)
        val error = received.filterIsInstance<SessionEvent.SessionError>().single()
        assertTrue(error.message.contains("Lost connection"))
        assertTrue(error.message.contains("Desktop"))
        val assistant = handle.messages.mapNotNull { it.asLlmMessage() }.filterIsInstance<AssistantMessage>().last()
        assertEquals(StopReason.Error, assistant.stopReason)
        assertEquals("partial", assistant.content.filterIsInstance<TextContent>().joinToString("") { it.text })
    }

    @Test
    fun sessionNotFoundClearsMappingAndRetriesOnce() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val first = engine.create(null, ModelConfig())
        finishPrompt(scope = this, transport = transport, handle = first, text = "hello", reply = "hi")
        val file = first.sessionFile!!
        first.close()
        transport.calls.clear()

        val reopened = engine.create(file, ModelConfig())
        transport.result = { buildJsonObject { put("sessionId", "peer-2") } }
        transport.failNext = RemoteCallError("session_not_found", "the session is gone")

        val job = launch { reopened.prompt("next") }
        runCurrent()

        val prompts = transport.calls.filter { it.method == "bot.prompt" }
        assertEquals(2, prompts.size)
        assertEquals(FakeRemoteTransport.PEER_SESSION_ID, prompts[0].params.getValue("sessionId").jsonPrimitive.content)
        assertNull(prompts[1].params["sessionId"])

        val retry = prompts[1]
        transport.emit(retry.routeId, 0, FakeEvents.textDelta("fresh"))
        transport.emit(retry.routeId, 1, FakeEvents.settled())
        runCurrent()
        assertTrue(job.isCompleted)
        assertFalse(reopened.isBusy)

        val lines = File(reopened.sessionFile!!).readLines()
        assertTrue(lines.any { it.contains("\"peerSessionId\":null") })
        assertTrue(lines.any { it.contains("\"peerSessionId\":\"peer-2\"") })

        val next = launch { reopened.prompt("again") }
        runCurrent()
        val nextPrompt = transport.calls.last()
        assertEquals("peer-2", nextPrompt.params.getValue("sessionId").jsonPrimitive.content)
        transport.emit(nextPrompt.routeId, 0, FakeEvents.settled())
        runCurrent()
        assertTrue(next.isCompleted)
    }

    @Test
    fun staleAbortDoesNotLeaveHandleBusy() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        finishPrompt(scope = this, transport = transport, handle = handle, text = "hello", reply = "hi")

        val turn = launch { handle.prompt("working") }
        runCurrent()
        transport.failNext = RemoteCallError("session_not_found", "gone")
        handle.abort()
        runCurrent()

        assertTrue(turn.isCompleted)
        assertFalse(handle.isBusy)
        assertTrue(transport.calls.any { it.method == "bot.abort" })

        transport.result = { buildJsonObject { put("sessionId", "peer-2") } }
        val next = launch { handle.prompt("fresh") }
        runCurrent()
        val fresh = transport.calls.last()
        assertNull(fresh.params["sessionId"])
        transport.emit(fresh.routeId, 0, FakeEvents.settled())
        runCurrent()
        assertTrue(next.isCompleted)
    }

    @Test
    fun staleSteerEndsActiveTurnWithSessionError() = runTest {
        val transport = FakeRemoteTransport()
        val engine = engine(transport, backgroundScope)
        val handle = engine.create(null, ModelConfig())
        val received = mutableListOf<SessionEvent>()
        handle.subscribe { event -> received += event }
        finishPrompt(scope = this, transport = transport, handle = handle, text = "hello", reply = "hi")

        val turn = launch { handle.prompt("working") }
        runCurrent()
        transport.failNext = RemoteCallError("session_not_found", "gone")
        handle.prompt("adjust", behavior = QueueBehavior.Steer)
        runCurrent()

        assertTrue(turn.isCompleted)
        assertFalse(handle.isBusy)
        val error = received.filterIsInstance<SessionEvent.SessionError>().single()
        assertTrue(error.message.contains("restarted"))

        transport.result = { buildJsonObject { put("sessionId", "peer-2") } }
        val next = launch { handle.prompt("fresh") }
        runCurrent()
        assertNull(transport.calls.last().params["sessionId"])
        transport.emit(transport.calls.last().routeId, 0, FakeEvents.settled())
        runCurrent()
        assertTrue(next.isCompleted)
    }

    private fun engine(transport: FakeRemoteTransport, scope: CoroutineScope): RemoteSessionEngine =
        RemoteSessionEngine(cwd, agentDir, target(transport), transport, scope)

    private fun target(transport: FakeRemoteTransport): RemoteSessionTarget = RemoteSessionTarget(
        deviceId = FakeRemoteTransport.DEVICE_ID,
        deviceName = "Desktop",
        botId = "pi",
        botName = "PI",
        isOnline = { transport.onlineDevices.value.contains(FakeRemoteTransport.DEVICE_ID) },
    )

    private suspend fun finishPrompt(
        scope: TestScope,
        transport: FakeRemoteTransport,
        handle: SessionHandle,
        text: String,
        reply: String,
    ) {
        val job = scope.launch { handle.prompt(text) }
        scope.runCurrent()
        val call = transport.calls.last()
        transport.emit(call.routeId, 0, FakeEvents.textDelta(reply))
        transport.emit(call.routeId, 1, FakeEvents.settled())
        scope.runCurrent()
        assertTrue(job.isCompleted)
    }

    private fun MessageContent.text(): String = when (this) {
        is PlainTextContent -> text
        is BlockContent -> blocks.filterIsInstance<io.github.whywood.superpi.sdk.ai.TextContent>().joinToString("") { it.text }
    }
}
