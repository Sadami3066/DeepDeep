package io.github.whywood.deepdeep.assistant

import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import io.github.whywood.deepdeep.ui.chat.testing.HOME_ID
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantOverlayControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val strings = ChatUiStrings.fallback()
    private val providers = FakeProviders()

    private fun session(id: String = "session-1", updatedAt: Long = 10L) = ConversationSummary(
        id = id,
        title = "Title",
        updatedAt = updatedAt,
        filePath = "$id.jsonl",
        cwd = HOME_CWD,
    )

    private fun controller(
        runtime: FakeAgentRuntime,
        active: String?,
        scope: CoroutineScope,
        requested: String? = null,
        attachmentStore: AttachmentStore = FakeAttachmentStore(),
        normalizeImage: suspend (File, String) -> NormalizedImage? = { _, _ -> null },
        onSelected: (String) -> Unit = {},
    ) = AssistantOverlayController(
        runtime = runtime,
        activeSessionId = { active },
        homeWorkspaceId = HOME_ID,
        scope = scope,
        providers = providers,
        attachmentStore = attachmentStore,
        homeDir = File(HOME_CWD),
        configuredProviders = { setOf("deepseek") },
        defaultModel = { ModelConfig("deepseek", "model", ThinkingLevel.OFF) },
        onSessionSelected = onSelected,
        initialSessionId = { requested },
        ioDispatcher = dispatcher,
        strings = { strings },
        normalizeImage = normalizeImage,
    )

    private fun ref(
        id: String = "a1",
        name: String = "photo.png",
        mime: String = "image/png",
        kind: AttachmentKind = AttachmentKind.IMAGE,
    ) = AttachmentRef(
        id = id,
        name = name,
        mime = mime,
        size = 10L,
        path = "attachments/$id",
        kind = kind,
    )

    @Test
    fun startOpensActiveSessionAndSeedsHistory() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, "hello"),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertTrue(controller.state.value.conversation.loaded)
        assertEquals(2, controller.state.value.conversation.messages.size)
        assertEquals("hello", controller.state.value.conversation.messages[1].text)
        assertTrue(runtime.openCalls.isEmpty())
        controller.reset()
    }

    @Test
    fun startPrefersSessionWithActiveRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-2" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "running"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, ""),
                ),
            )
            activeRuns.value = mapOf(
                "session-2" to ActiveRunSnapshot("run-9", 5L),
                "session-1" to ActiveRunSnapshot("run-8", 1L),
            )
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, "session-1", this) { selected += it }

        controller.start()
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(listOf("session-2"), selected)
        assertTrue(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun startPrefersExplicitRequestOverActiveRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(
                session("session-1", updatedAt = 10L),
                session("session-2", updatedAt = 20L),
                session("session-3", updatedAt = 30L),
            )
            histories = mapOf(
                "session-1" to listOf(AssistantMessage(0, AssistantRole.USER, "requested")),
            )
            activeRuns.value = mapOf(
                "session-2" to ActiveRunSnapshot("run-9", 50L),
                "session-3" to ActiveRunSnapshot("run-8", 60L),
            )
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, "session-2", this, requested = "session-1") { selected += it }

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertEquals("requested", controller.state.value.conversation.messages.single().text)
        assertEquals(listOf("session-1"), selected)
        assertFalse(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun startPrefersExplicitRequestOverActiveAppSession() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-1" to listOf(AssistantMessage(0, AssistantRole.USER, "requested")),
                "session-2" to listOf(AssistantMessage(0, AssistantRole.USER, "active")),
            )
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, "session-2", this, requested = "session-1") { selected += it }

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertEquals(listOf("session-1"), selected)
        controller.reset()
    }

    @Test
    fun startFallsBackWhenRequestedSessionIsGone() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            activeRuns.value = mapOf("session-2" to ActiveRunSnapshot("run-9", 5L))
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, null, this, requested = "session-gone") { selected += it }

        controller.start()
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(listOf("session-2"), selected)
        controller.reset()
    }

    @Test
    fun initialSessionCandidatesKeepRequestedFirstThenActiveRunThenAppSession() {
        assertEquals(
            listOf("requested", "run", "active"),
            initialSessionCandidates("requested", "run", "active"),
        )
        assertEquals(listOf("run", "active"), initialSessionCandidates(null, "run", "active"))
        assertEquals(listOf("run", "active"), initialSessionCandidates("  ", "run", "active"))
        assertEquals(listOf("run"), initialSessionCandidates(null, "run", "run"))
        assertTrue(initialSessionCandidates(null, null, null).isEmpty())
    }

    @Test
    fun startAttachesLatestSessionOnColdStart() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-2" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "old"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, "answer"),
                ),
            )
        }
        val controller = controller(runtime, null, this)

        controller.start()
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(listOf("session-2.jsonl"), runtime.openCalls)
        assertEquals(listOf("session-2"), runtime.historyCalls.keys.toList())
        assertEquals(2, controller.state.value.conversation.messages.size)
        controller.reset()
    }

    @Test
    fun startCreatesSessionWhenNoneExists() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, null, this)

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertTrue(controller.state.value.conversation.loaded)
        assertTrue(runtime.openCalls.isEmpty())
        controller.reset()
    }

    @Test
    fun startSeedsQueuedInputsFromRuntime() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            queued = listOf(QueuedInput(InputQueueKind.STEER, "queued"))
        }
        val controller = controller(runtime, "session-1", this)

        controller.start()
        runCurrent()

        assertEquals(
            listOf(QueuedInput(InputQueueKind.STEER, "queued")),
            controller.state.value.conversation.queuedInputs,
        )
        controller.reset()
    }

    @Test
    fun sendBeginsTurnThenSendsOptimistically() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply { onSend = { _, _ -> } }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.send("  hello  "))
        runCurrent()

        assertEquals(listOf("hello"), runtime.sent)
        val messages = controller.state.value.conversation.messages
        assertEquals(2, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals("hello", messages[0].text)
        assertTrue(messages[1].streaming)
        assertTrue(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun sendStreamsToDone() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ ->
                emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "hello"))
                emitFor(sessionId, RuntimeEvent.ReasoningDelta("run-1", "think"))
                emitFor(sessionId, RuntimeEvent.TextDelta("run-1", "world"))
                emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "world", RunStatus.DONE, 200L))
            }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.send("hello"))
        runCurrent()

        assertEquals(listOf("hello"), runtime.sent)
        val tail = controller.state.value.conversation.messages.last()
        assertEquals("world", tail.text)
        assertEquals(MessageStatus.DONE, tail.status)
        assertFalse(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun sendWhileBusySteersWithoutOptimisticMessage() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ -> emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "one")) }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.send("one"))
        runCurrent()
        val messageCount = controller.state.value.conversation.messages.size

        assertTrue(controller.send("two"))
        runCurrent()

        assertEquals(listOf("one"), runtime.sent)
        assertEquals(listOf("two"), runtime.steered)
        assertTrue(runtime.followUps.isEmpty())
        assertEquals(messageCount, controller.state.value.conversation.messages.size)
        assertNull(controller.state.value.error)
        controller.reset()
    }

    @Test
    fun sendWhileBusyLongPressQueues() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ -> emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "one")) }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()
        controller.send("one")
        runCurrent()

        controller.send("later", followUp = true)
        runCurrent()

        assertEquals(listOf("later"), runtime.followUps)
        assertTrue(runtime.steered.isEmpty())
        controller.reset()
    }

    @Test
    fun sendSteersWhenRuntimeRunsSessionExternally() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        runtime.activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-9", 0L))

        assertTrue(controller.send("hi"))
        runCurrent()

        assertTrue(runtime.sent.isEmpty())
        assertEquals(listOf("hi"), runtime.steered)
        controller.reset()
    }

    @Test
    fun externalTurnEventsAppearInConversation() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunStarted("run-7", 1_000L, prompt = "from main"))
        runCurrent()

        val seeded = controller.state.value.conversation.messages
        assertEquals(2, seeded.size)
        assertEquals(AssistantRole.USER, seeded[0].role)
        assertEquals("from main", seeded[0].text)
        assertTrue(seeded[1].streaming)

        runtime.emitFor("session-1", RuntimeEvent.ReasoningDelta("run-7", "plan"))
        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolStarted("run-7", "call-1", "bash", mapOf("command" to "date"), 1_100L),
        )
        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolFinished("run-7", "call-1", "bash", "output", isError = false, durationMs = 20L),
        )
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-7", "working"))
        runCurrent()

        val messages = controller.state.value.conversation.messages
        val tail = messages.last()
        assertTrue(tail.streaming)
        val reasoning = tail.steps.filterIsInstance<AssistantStep.Reasoning>().first()
        assertEquals("plan", reasoning.text)
        val tool = tail.steps.filterIsInstance<AssistantStep.Tool>().first()
        assertEquals("bash", tool.trace.name)
        assertEquals(ToolActivityStatus.DONE, tool.trace.status)
        controller.reset()
    }

    @Test
    fun stopForwardsToRuntime() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.stop()

        assertEquals(listOf("session-1"), runtime.stopped)
        controller.reset()
    }

    @Test
    fun retryOnlyAcceptsFailedTail() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "partial",
                        status = MessageStatus.FAILED,
                        rawError = "failed",
                        startedAt = 1L,
                        finishedAt = 2L,
                    ),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.retry(0L)
        controller.retry(99L)
        assertEquals(0, runtime.retried)

        controller.retry(1L)
        runCurrent()

        assertEquals(1, runtime.retried)
        assertTrue(controller.state.value.conversation.messages.last().retrying)
        controller.reset()
    }

    @Test
    fun retryIgnoresCompletedTail() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(id = 1, role = AssistantRole.ASSISTANT, text = "ok"),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.retry(1L)
        runCurrent()

        assertEquals(0, runtime.retried)
        controller.reset()
    }

    @Test
    fun sendFailureMarksTailFailed() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { _, _ -> error("boom") }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.send("hello"))
        runCurrent()

        val tail = controller.state.value.conversation.messages.last()
        assertEquals(MessageStatus.FAILED, tail.status)
        assertEquals(strings.runError("boom"), tail.error)
        assertFalse(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun toggleStepExpandsReasoning() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "answer",
                        steps = listOf(AssistantStep.Reasoning(0L, "reasoning")),
                    ),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.toggleStep(1L, 0L)
        runCurrent()

        val step = controller.state.value.conversation.messages.first()
            .steps.filterIsInstance<AssistantStep.Reasoning>().first()
        assertTrue(step.expanded)
        controller.reset()
    }

    @Test
    fun openSessionSwitchesProjectionAndSelection() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-1" to emptyList(),
                "session-2" to listOf(AssistantMessage(0, AssistantRole.USER, "second")),
            )
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, "session-1", this) { selected += it }
        controller.start()
        runCurrent()

        controller.openSession("session-2")
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals("second", controller.state.value.conversation.messages.single().text)
        assertEquals("session-2", selected.last())
        assertTrue(runtime.openCalls.contains("session-2.jsonl"))
        controller.reset()
    }

    @Test
    fun startExposesAllSessionsForSwitcher() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertEquals(
            listOf("session-1", "session-2"),
            controller.state.value.allSessions.map { it.id },
        )
        controller.reset()
    }

    @Test
    fun newSessionUsesRequestedWorkspace() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, null, this)
        controller.start()
        runCurrent()

        controller.newSession("workspace-x")
        runCurrent()

        assertEquals("workspace-x", runtime.newSessionCalls.last())
        controller.reset()
    }

    @Test
    fun newSessionCreatesAndOpensIt() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val selected = mutableListOf<String>()
        val controller = controller(runtime, null, this) { selected += it }
        controller.start()
        runCurrent()

        controller.newSession(HOME_ID)
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals("session-2", selected.last())
        assertEquals(HOME_ID, runtime.newSessionCalls.last())
        controller.reset()
    }

    @Test
    fun selectModelCallsRuntimeAndReflectsTruth() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessionModels["session-1"] = SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF)
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertEquals("model", controller.state.value.modelId)
        assertEquals(listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH), controller.state.value.thinkingLevels)

        controller.selectModel("model-2")
        runCurrent()

        assertEquals("model-2", controller.state.value.modelId)
        assertEquals("DeepSeek 2", controller.state.value.modelLabel)
        assertEquals(
            SessionModelSnapshot("deepseek", "model-2", ThinkingLevel.OFF),
            runtime.sessionModels["session-1"],
        )
        controller.reset()
    }

    @Test
    fun selectThinkingCallsRuntimeAndReflectsTruth() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessionModels["session-1"] = SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF)
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.selectThinking(ThinkingLevel.HIGH)
        runCurrent()

        assertEquals(ThinkingLevel.HIGH, controller.state.value.thinkingLevel)
        assertEquals(
            SessionModelSnapshot("deepseek", "model", ThinkingLevel.HIGH),
            runtime.sessionModels["session-1"],
        )
        controller.reset()
    }

    @Test
    fun modelSwitchIgnoredWhileBusy() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessionModels["session-1"] = SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF)
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        runtime.activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-9", 0L))
        controller.selectModel("model-2")
        runCurrent()

        assertEquals("model", controller.state.value.modelId)
        assertEquals(
            SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF),
            runtime.sessionModels["session-1"],
        )
        controller.reset()
    }

    @Test
    fun clearQueueReturnsDroppedTexts() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            queued = listOf(
                QueuedInput(InputQueueKind.STEER, "s1"),
                QueuedInput(InputQueueKind.FOLLOW_UP, "f1"),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertEquals(listOf("s1", "f1"), controller.clearQueue())
        controller.reset()
    }

    @Test
    fun sendBeforeStartIsIgnored() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, null, this)

        assertFalse(controller.send("hi"))
        assertTrue(runtime.sent.isEmpty())
        controller.reset()
    }

    @Test
    fun resetClearsStateWithoutStoppingRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()
        controller.send("hi")
        runCurrent()

        controller.reset()

        assertEquals(AssistantOverlayState(), controller.state.value)
        assertTrue(runtime.stopped.isEmpty())
    }

    @Test
    fun importedAttachmentAppendsAndClearsImportingFlag() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        val pending = CompletableDeferred<AttachmentImport>()
        controller.importAttachment("session-1", File(HOME_CWD)) { pending.await() }
        runCurrent()
        assertTrue(controller.state.value.importingAttachment)

        pending.complete(AttachmentImport.Imported(ref()))
        runCurrent()

        assertFalse(controller.state.value.importingAttachment)
        assertEquals(listOf(ref()), controller.state.value.attachments)
        assertNull(controller.state.value.error)
        controller.reset()
    }

    @Test
    fun importStopsAtTwentyAttachments() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        repeat(20) { index ->
            controller.importAttachment("session-1", File(HOME_CWD)) {
                AttachmentImport.Imported(ref(id = "a$index", name = "file$index.txt", mime = "text/plain", kind = AttachmentKind.TEXT))
            }
        }
        runCurrent()
        assertEquals(20, controller.state.value.attachments.size)

        controller.importAttachment("session-1", File(HOME_CWD)) {
            AttachmentImport.Imported(ref(id = "over"))
        }
        runCurrent()

        assertEquals(20, controller.state.value.attachments.size)
        assertEquals(AssistantOverlayError.AttachmentLimit, controller.state.value.error)
        controller.reset()
    }

    @Test
    fun tooLargeAttachmentReportsTooLarge() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.TooLarge }
        runCurrent()

        assertEquals(AssistantOverlayError.AttachmentTooLarge, controller.state.value.error)
        assertTrue(controller.state.value.attachments.isEmpty())
        controller.reset()
    }

    @Test
    fun failedAttachmentReportsFailed() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.UnsupportedImage }
        runCurrent()

        assertEquals(AssistantOverlayError.AttachmentFailed, controller.state.value.error)
        assertTrue(controller.state.value.attachments.isEmpty())
        controller.reset()
    }

    @Test
    fun removeAttachmentDropsIt() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()

        controller.removeAttachment("a1")

        assertTrue(controller.state.value.attachments.isEmpty())
        controller.reset()
    }

    @Test
    fun sendIncludesAttachmentBlockAndImages() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply { onSend = { _, _ -> } }
        val controller = controller(
            runtime = runtime,
            active = "session-1",
            scope = this,
            normalizeImage = { _, _ -> NormalizedImage("BASE64", "image/jpeg", 1, 1) },
        )
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()

        assertTrue(controller.send("hello"))
        runCurrent()

        val prompt = runtime.sent.single()
        assertTrue(prompt.startsWith("<attachments>"))
        assertTrue(prompt.contains("- $HOME_CWD/attachments/a1 |"))
        assertTrue(prompt.endsWith("hello"))
        assertEquals(listOf(ImagePart("BASE64", "image/jpeg", path = "$HOME_CWD/attachments/a1")), runtime.sentImages.single())
        assertTrue(controller.state.value.attachments.isEmpty())
        controller.reset()
    }

    @Test
    fun sendWhileBusySteersWithAttachments() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ -> emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "one")) }
        }
        val controller = controller(
            runtime = runtime,
            active = "session-1",
            scope = this,
            normalizeImage = { _, _ -> NormalizedImage("BASE64", "image/jpeg", 1, 1) },
        )
        controller.start()
        runCurrent()
        controller.send("one")
        runCurrent()
        assertTrue(controller.state.value.conversation.sending)
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()

        assertTrue(controller.send("two"))
        runCurrent()

        val steer = runtime.steered.single()
        assertTrue(steer.startsWith("<attachments>"))
        assertTrue(steer.endsWith("two"))
        assertEquals(listOf(ImagePart("BASE64", "image/jpeg", path = "$HOME_CWD/attachments/a1")), runtime.sentImages.last())
        controller.reset()
    }

    @Test
    fun switchingSessionDuringImagePreparationKeepsOriginalRecipient() = runTest(dispatcher) {
        val recipients = mutableListOf<String>()
        val runtime = FakeAgentRuntime().apply { onSend = { sessionId, _ -> recipients += sessionId } }
        val image = CompletableDeferred<NormalizedImage?>()
        val controller = controller(runtime, "session-1", this, normalizeImage = { _, _ -> image.await() })
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()
        assertTrue(controller.send("photo"))
        runCurrent()
        assertTrue(controller.state.value.conversation.sending)
        controller.openSession("session-2")
        runCurrent()
        image.complete(NormalizedImage("BASE64", "image/jpeg", 1, 1))
        runCurrent()

        assertEquals(listOf("session-1"), recipients)
        assertEquals("session-2", controller.state.value.sessionId)
        assertTrue(controller.state.value.conversation.messages.isEmpty())
        controller.reset()
    }

    @Test
    fun stopDuringImagePreparationPreventsLateSend() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val image = CompletableDeferred<NormalizedImage?>()
        val controller = controller(runtime, "session-1", this, normalizeImage = { _, _ -> image.await() })
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()
        assertTrue(controller.send("photo"))
        runCurrent()
        controller.stop()
        image.complete(NormalizedImage("BASE64", "image/jpeg", 1, 1))
        runCurrent()

        assertTrue(runtime.sent.isEmpty())
        assertFalse(controller.state.value.conversation.sending)
        assertEquals(MessageStatus.STOPPED, controller.state.value.conversation.messages.last().status)
        assertEquals(listOf("session-1"), runtime.stopped)
        controller.reset()
    }

    @Test
    fun stopDuringQueuedImagePreparationPreventsLateSteer() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-live", 1L))
        }
        val image = CompletableDeferred<NormalizedImage?>()
        val controller = controller(runtime, "session-1", this, normalizeImage = { _, _ -> image.await() })
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()
        assertTrue(controller.send("photo"))
        runCurrent()
        controller.stop()
        image.complete(NormalizedImage("BASE64", "image/jpeg", 1, 1))
        runCurrent()

        assertTrue(runtime.steered.isEmpty())
        assertTrue(runtime.followUps.isEmpty())
        assertEquals(listOf("session-1"), runtime.stopped)
        controller.reset()
    }

    @Test
    fun switchingSessionClearsAttachments() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()
        controller.importAttachment("session-1", File(HOME_CWD)) { AttachmentImport.Imported(ref()) }
        runCurrent()
        assertEquals(1, controller.state.value.attachments.size)

        controller.openSession("session-2")
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertTrue(controller.state.value.attachments.isEmpty())
        assertFalse(controller.state.value.importingAttachment)
        controller.reset()
    }

    @Test
    fun switchingSessionDiscardsLateImport() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val store = FakeAttachmentStore()
        val controller = controller(runtime, "session-1", this, attachmentStore = store)
        controller.start()
        runCurrent()
        val pending = CompletableDeferred<AttachmentImport>()
        controller.importAttachment("session-1", File(HOME_CWD)) { pending.await() }
        runCurrent()

        controller.openSession("session-2")
        runCurrent()
        pending.complete(AttachmentImport.Imported(ref()))
        runCurrent()

        assertTrue(controller.state.value.attachments.isEmpty())
        assertEquals(listOf("attachments/a1"), store.deletedPaths)
        controller.reset()
    }

    private class FakeAttachmentStore : AttachmentStore {
        val deletedPaths = mutableListOf<String>()

        override suspend fun import(uri: Uri, root: File): AttachmentImport = AttachmentImport.Failed

        override fun file(ref: AttachmentRef, root: File): File =
            object : File(root, ref.path) {
                override fun delete(): Boolean {
                    deletedPaths += ref.path
                    return true
                }
            }

        override fun shareIntent(ref: AttachmentRef, root: File): Intent? = null

        override fun resolve(raw: String, root: File): AttachmentRef? = null
    }

    private class FakeProviders(
        private val options: List<ModelOption> = listOf(
            ModelOption("deepseek", "model", "DeepSeek", listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH)),
            ModelOption("deepseek", "model-2", "DeepSeek 2", listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH)),
        ),
    ) : ProviderDirectory {
        override val summaries: List<ProviderSummary> = options
            .map { it.providerId }
            .distinct()
            .map { providerId ->
                ProviderSummary(
                    id = providerId,
                    name = providerId,
                    modelCount = options.count { it.providerId == providerId },
                    baseUrl = null,
                    hasOAuth = false,
                    supportsApiKey = true,
                )
            }

        override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }

        override fun models(providerId: String): List<ModelOption> =
            options.filter { it.providerId == providerId }

        override fun resolveConfig(config: ModelConfig): ModelConfig {
            val models = models(config.providerId)
            val model = models.firstOrNull { it.id == config.modelId } ?: models.firstOrNull() ?: return config
            val thinking = config.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
            return ModelConfig(model.providerId, model.id, thinking)
        }

        override fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model? = null

        override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
    }
}
