package io.github.whywood.deepdeep.ui.chat

import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillExpansion
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.TurnSource
import io.github.whywood.deepdeep.runtime.UserQuestion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        runtime: FakeRuntime,
        autoRetry: Boolean = false,
        skillExpander: SkillExpander? = null,
        settings: FakeSettings = FakeSettings(),
        attachmentStore: FakeAttachmentStore = FakeAttachmentStore(),
        interruptedQuestions: suspend (String) -> List<InterruptedQuestion> = { emptyList() },
        dismissInterruptedQuestions: suspend (String) -> Unit = {},
        interruptedRuns: suspend (String) -> List<InterruptedRun> = { emptyList() },
        dismissInterruptedRun: suspend (String) -> Unit = {},
    ) = ChatViewModel(
        runtime,
        FakeProviders(),
        settings,
        attachmentStore,
        prepareRuntime = {},
        autoRetry = kotlinx.coroutines.flow.flowOf(autoRetry),
        skillExpander = skillExpander,
        ioDispatcher = dispatcher,
        interruptedQuestions = interruptedQuestions,
        dismissInterruptedQuestions = dismissInterruptedQuestions,
        interruptedRuns = interruptedRuns,
        dismissInterruptedRun = dismissInterruptedRun,
    )

    private fun TurnNotice?.isInterruptedNoticeForTest(): Boolean =
        this is TurnNotice.InterruptedQuestions || this is TurnNotice.InterruptedRun

    private fun interruptedRun(sessionId: String) = InterruptedRun(
        sessionId = sessionId,
        runId = "run-$sessionId",
        startedAt = 2L,
        source = TurnSource.USER,
    )

    private fun interruptedQuestion(sessionId: String) = InterruptedQuestion(
        requestId = "req-$sessionId",
        sessionId = sessionId,
        toolCallId = "call-$sessionId",
        questions = listOf(UserQuestion(id = "q1", question = "Continue?")),
        createdAt = 1L,
    )

    @Test
    fun sendShowsWorkingStateImmediatelyAndFinishes() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.onInputChange("hello")
        viewModel.send()

        val sending = viewModel.state.value
        assertTrue(sending.sending)
        assertEquals(RuntimeActivity.Thinking, sending.activity)
        assertEquals(2, sending.messages.size)
        assertEquals(MessageStatus.RUNNING, sending.messages.last().status)
        assertTrue(sending.conversationTitle.isNotBlank())

        runtime.emitFor(viewModel.state.value.conversationId!!, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(viewModel.state.value.conversationId!!, RuntimeEvent.TextDelta(runtime.runId, "hi"))
        runtime.emitFor(viewModel.state.value.conversationId!!, RuntimeEvent.RunFinished(runtime.runId, "hi", RunStatus.DONE, 2000L))
        advanceUntilIdle()

        val finished = viewModel.state.value
        assertFalse(finished.sending)
        assertEquals("hi", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
    }

    @Test
    fun updateUiStringsLocalizesSubsequentEvents() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.updateUiStrings(ChatUiStrings.fallback().copy(requestFailed = "localized failure"))

        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "unexpected"))
        advanceUntilIdle()

        assertEquals("localized failure", viewModel.state.value.messages.last().error)
    }

    @Test
    fun ignoresStaleRunEvents() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-2", "stale"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-1", "fresh"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "fresh", RunStatus.DONE, 2000L))
        advanceUntilIdle()
        assertEquals("fresh", viewModel.state.value.messages.last().text)
    }

    @Test
    fun keepsTextWithToolsInProcessAndFinalTextAsAnswer() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("do it")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "starting"))
        runtime.emitFor(
            sessionId,
            RuntimeEvent.ToolStarted(
                runId = runtime.runId,
                callId = "call-1",
                name = "bash",
                arguments = mapOf("command" to "sleep 1"),
                startedAt = 1200L,
            ),
        )
        runtime.emitFor(
            sessionId,
            RuntimeEvent.ToolFinished(
                runId = runtime.runId,
                callId = "call-1",
                name = "bash",
                result = "ok",
                isError = false,
                durationMs = 1000L,
            ),
        )
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "done"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "done", RunStatus.DONE, 3000L))
        advanceUntilIdle()

        val message = viewModel.state.value.messages.last()
        assertEquals("done", message.text)
        assertEquals(2, message.steps.size)
        assertTrue(message.steps[0] is AssistantStep.Text)
        assertEquals("starting", (message.steps[0] as AssistantStep.Text).text)
        assertTrue(message.steps[1] is AssistantStep.Tool)
    }

    @Test
    fun switchingBackShowsCachedMessagesBeforeSessionOpens() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        assertEquals("from a", viewModel.state.value.messages.single().text)

        viewModel.openConversation("session-b")
        assertTrue(viewModel.state.value.switchingConversation)
        advanceUntilIdle()
        assertEquals("from b", viewModel.state.value.messages.single().text)

        viewModel.openConversation("session-a")
        assertFalse(viewModel.state.value.switchingConversation)
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("from a", viewModel.state.value.messages.single().text)
        advanceUntilIdle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("a.jsonl", runtime.openCalls.last())
    }

    @Test
    fun insertsInterruptedQuestionNoticeAfterOpeningSession() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val queries = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId ->
                queries += sessionId
                if (sessionId == "session-a") listOf(interruptedQuestion(sessionId)) else emptyList()
            },
        )
        advanceUntilIdle()

        assertEquals(listOf("session-a"), queries)
        val message = viewModel.state.value.messages.last()
        assertEquals(TurnNotice.InterruptedQuestions(1), message.notice)
        assertEquals(2, viewModel.state.value.messages.size)
    }

    @Test
    fun keepsInterruptedQuestionNoticeAcrossConversationSwitches() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val queries = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId ->
                queries += sessionId
                if (sessionId == "session-a") listOf(interruptedQuestion(sessionId)) else emptyList()
            },
        )
        advanceUntilIdle()

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        viewModel.openConversation("session-a")
        advanceUntilIdle()

        assertEquals(1, queries.count { it == "session-a" })
        assertEquals(1, viewModel.state.value.messages.count { message ->
            message.notice is TurnNotice.InterruptedQuestions
        })
    }

    @Test
    fun dismissesInterruptedQuestionsOnNextMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val dismissed = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId -> listOf(interruptedQuestion(sessionId)) },
            dismissInterruptedQuestions = { sessionId -> dismissed += sessionId },
        )
        advanceUntilIdle()

        viewModel.onInputChange("continue please")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("session-a"), dismissed)
        assertEquals("continue please", runtime.sent.single())
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedQuestions
        })
    }

    @Test
    fun insertsInterruptedRunNoticeAfterOpeningSession() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val queries = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedRuns = { sessionId ->
                queries += sessionId
                if (sessionId == "session-a") listOf(interruptedRun(sessionId)) else emptyList()
            },
        )
        advanceUntilIdle()

        assertEquals(listOf("session-a"), queries)
        val message = viewModel.state.value.messages.last()
        assertEquals(TurnNotice.InterruptedRun(1), message.notice)
        assertEquals(2, viewModel.state.value.messages.size)
    }

    @Test
    fun keepsInterruptedRunNoticeAcrossConversationSwitches() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val queries = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedRuns = { sessionId ->
                queries += sessionId
                if (sessionId == "session-a") listOf(interruptedRun(sessionId)) else emptyList()
            },
        )
        advanceUntilIdle()

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        viewModel.openConversation("session-a")
        advanceUntilIdle()

        assertEquals(1, queries.count { it == "session-a" })
        assertEquals(1, viewModel.state.value.messages.count { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun dismissesInterruptedRunOnNextMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val dismissed = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedRuns = { sessionId -> listOf(interruptedRun(sessionId)) },
            dismissInterruptedRun = { sessionId -> dismissed += sessionId },
        )
        advanceUntilIdle()

        viewModel.onInputChange("continue please")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("session-a"), dismissed)
        assertEquals("continue please", runtime.sent.single())
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun removesBothInterruptedNoticesAfterSendingMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val dismissedQuestions = mutableListOf<String>()
        val dismissedRuns = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId -> listOf(interruptedQuestion(sessionId)) },
            dismissInterruptedQuestions = { sessionId -> dismissedQuestions += sessionId },
            interruptedRuns = { sessionId -> listOf(interruptedRun(sessionId)) },
            dismissInterruptedRun = { sessionId -> dismissedRuns += sessionId },
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.messages.count { message -> message.notice.isInterruptedNoticeForTest() })

        viewModel.onInputChange("continue please")
        viewModel.send()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.none { message -> message.notice.isInterruptedNoticeForTest() })
        assertEquals(listOf("session-a"), dismissedQuestions)
        assertEquals(listOf("session-a"), dismissedRuns)
    }

    @Test
    fun doesNotRestoreInterruptedRunNoticeAfterSwitchingBack() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val interrupted = mutableListOf(interruptedRun("session-a"))
        val viewModel = viewModel(
            runtime,
            interruptedRuns = { sessionId -> interrupted.filter { run -> run.sessionId == sessionId } },
            dismissInterruptedRun = { sessionId -> interrupted.removeAll { run -> run.sessionId == sessionId } },
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.messages.size)

        viewModel.onInputChange("continue please")
        viewModel.send()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        viewModel.openConversation("session-a")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun insertsInterruptedQuestionAndRunNoticesTogether() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId -> listOf(interruptedQuestion(sessionId)) },
            interruptedRuns = { sessionId -> listOf(interruptedRun(sessionId)) },
        )
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals(3, messages.size)
        assertEquals(TurnNotice.InterruptedQuestions(1), messages[1].notice)
        assertEquals(TurnNotice.InterruptedRun(1), messages[2].notice)
    }

    @Test
    fun backgroundRunKeepsStreamingAfterSwitchingAway() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to emptyList(),
                "session-b" to listOf(AssistantMessage(1, AssistantRole.USER, "from b")),
            )
        }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        assertEquals("from b", viewModel.state.value.messages.single().text)

        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, " more"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "partial more", RunStatus.DONE, 2000L))
        advanceUntilIdle()
        runtime.histories = runtime.histories + (sessionId to listOf(
            AssistantMessage(1, AssistantRole.USER, "hello"),
            AssistantMessage(2, AssistantRole.ASSISTANT, "partial more"),
        ))

        viewModel.openConversation("session-a")
        advanceUntilIdle()
        val finished = viewModel.state.value
        assertEquals("partial more", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
        assertFalse(finished.sending)
    }

    @Test
    fun attachmentImportedAfterSwitchingSessionIsDiscarded() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val store = FakeAttachmentStore()
        val viewModel = viewModel(runtime, attachmentStore = store)
        advanceUntilIdle()
        val sessionA = viewModel.state.value.conversationId!!
        val root = java.nio.file.Files.createTempDirectory("deepdeep-attachment").toFile()
        val ref = AttachmentRef(
            id = "att-1",
            name = "notes.txt",
            mime = "text/plain",
            size = 3L,
            path = "attachments/att-1_notes.txt",
            kind = io.github.whywood.deepdeep.data.AttachmentKind.TEXT,
        )
        val file = store.file(ref, root)
        file.parentFile?.mkdirs()
        file.writeText("abc")
        val ready = CompletableDeferred<AttachmentImport>()
        store.ready = ready

        viewModel.launchAttachmentImport(sessionA, root) { ready.await() }
        advanceTimeBy(1L)
        viewModel.openConversation("session-b")
        advanceUntilIdle()
        ready.complete(AttachmentImport.Imported(ref))
        advanceUntilIdle()

        assertEquals("session-b", viewModel.state.value.conversationId)
        assertTrue(viewModel.state.value.attachments.isEmpty())
        assertFalse(file.exists())
        root.deleteRecursively()
    }

    @Test
    fun newSessionCanStartWhilePreviousSessionIsRunning() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        val firstSession = viewModel.state.value.conversationId!!
        runtime.emitFor(firstSession, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        assertTrue(viewModel.state.value.sending)

        viewModel.newSession(HOME_ID)
        advanceUntilIdle()
        val secondSession = viewModel.state.value.conversationId!!
        assertTrue(secondSession != firstSession)
        assertFalse(viewModel.state.value.sending)

        runtime.emitFor(firstSession, RuntimeEvent.TextDelta(runtime.runId, "background"))
        runtime.emitFor(firstSession, RuntimeEvent.RunFinished(runtime.runId, "background", RunStatus.DONE, 2000L))
        runtime.histories = runtime.histories + (firstSession to listOf(
            AssistantMessage(1, AssistantRole.USER, "first"),
            AssistantMessage(2, AssistantRole.ASSISTANT, "background"),
        ))

        viewModel.openConversation(firstSession)
        advanceUntilIdle()
        assertEquals("background", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun batchesRapidDeltasWithoutLosingText() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("stream")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "a"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "b"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "c"))
        advanceUntilIdle()
        val streamed = viewModel.state.value.messages.last()
        assertEquals("abc", streamed.steps.filterIsInstance<AssistantStep.Text>().last().text)

        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "abc", RunStatus.DONE, 2000L))
        advanceUntilIdle()
        assertEquals("abc", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun quotedReplyPrefixesPromptAndShowsInUserMessage() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.quote(9, "**重点**内容")
        assertEquals("重点内容", viewModel.state.value.quote?.text)

        viewModel.onInputChange("展开说说")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("> 重点内容\n\n展开说说", runtime.sent.single())
        assertEquals("> 重点内容\n\n展开说说", viewModel.state.value.messages.first().text)
        assertNull(viewModel.state.value.quote)
    }


    @Test
    fun blankCachedConversationAlwaysReloadsHistory() = runTest(dispatcher) {
        val existing = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(existing)
            histories = mapOf("session-a" to emptyList())
        }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.newSession(HOME_ID)
        advanceUntilIdle()
        val newSessionId = viewModel.state.value.conversationId!!
        assertTrue(viewModel.state.value.messages.isEmpty())

        runtime.histories = runtime.histories + (newSessionId to listOf(
            AssistantMessage(1, AssistantRole.USER, "reminder body"),
        ))
        viewModel.openConversation("session-a")
        advanceUntilIdle()
        viewModel.openConversation(newSessionId)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals("reminder body", viewModel.state.value.messages.single().text)
        assertTrue((runtime.historyCalls[newSessionId] ?: 0) >= 1)
        assertTrue(viewModel.state.value.historyLoaded)
    }

    @Test
    fun turnInjectedCreatesPlaceholderAndStreams() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply { sessions = listOf(session) }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!
        val raw = "[定时提醒] 喝水\n这是你设置的定时提醒，现在到时间了。\n\n喝水提醒"

        runtime.emitFor(sessionId, RuntimeEvent.TurnInjected("run-t", raw, "喝水"))
        advanceUntilIdle()

        val injected = viewModel.state.value
        assertTrue(injected.sending)
        assertEquals(2, injected.messages.size)
        val user = injected.messages[0]
        assertEquals(AssistantRole.USER, user.role)
        assertEquals("喝水提醒", user.text)
        assertEquals("喝水", user.reminder)
        val assistant = injected.messages[1]
        assertEquals(AssistantRole.ASSISTANT, assistant.role)
        assertTrue(assistant.streaming)
        assertEquals("run-t", assistant.runId)

        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-t", "收到"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-t", "收到", RunStatus.DONE, 2000L))
        advanceUntilIdle()
        assertEquals("收到", viewModel.state.value.messages.last().text)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun retryRegeneratesReplyWithoutResendingUserText() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "402 Insufficient Balance"))
        advanceUntilIdle()

        val failed = viewModel.state.value.messages.last()
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals(ChatUiStrings.fallback().withCode(ChatUiStrings.fallback().insufficientBalance, "402"), failed.error)

        viewModel.retry(failed.id)
        advanceUntilIdle()

        assertEquals(listOf("hello"), runtime.sent)
        assertEquals(1, runtime.retried)
        val messages = viewModel.state.value.messages
        assertEquals(2, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertTrue(messages[1].retrying)
        assertEquals(MessageStatus.FAILED, messages[1].status)

        runtime.emitFor(sessionId, RuntimeEvent.RunStarted("run-2", 5000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-2", "world"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-2", "world", RunStatus.DONE, 6000L))
        advanceUntilIdle()
        val finished = viewModel.state.value.messages.last()
        assertEquals(MessageStatus.DONE, finished.status)
        assertEquals("world", finished.text)
        assertFalse(finished.retrying)
        assertNull(finished.error)
    }

    @Test
    fun autoRetryRegeneratesAfterTransientFailure() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        advanceUntilIdle()

        assertEquals(1, runtime.retried)
        val messages = viewModel.state.value.messages
        assertEquals(2, messages.size)
        assertTrue(messages.last().retrying)
        assertEquals(MessageStatus.FAILED, messages.last().status)
        assertTrue(viewModel.state.value.sending)
    }

    @Test
    fun autoRetryLabelSurvivesHostTerminalActivityClear() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        advanceTimeBy(1L)

        runtime.emitFor(sessionId, RuntimeEvent.ActivityChanged(runtime.runId, null))
        advanceTimeBy(1L)

        val retrying = viewModel.state.value
        assertTrue(retrying.sending)
        assertEquals(RuntimeActivity.Retrying(1), retrying.activity)
        assertEquals(0, runtime.retried)

        advanceTimeBy(2_500L)
        advanceUntilIdle()

        assertEquals(1, runtime.retried)
        assertEquals(RuntimeActivity.Thinking, viewModel.state.value.activity)
    }

    @Test
    fun stopCancelsPendingAutoRetry() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        advanceTimeBy(1L)

        assertTrue(viewModel.state.value.sending)
        assertEquals(0, runtime.retried)

        viewModel.stop()
        advanceTimeBy(2_500L)
        advanceUntilIdle()

        assertEquals(0, runtime.retried)
        assertFalse(viewModel.state.value.sending)
        assertEquals(listOf(sessionId), runtime.stopped)
    }

    @Test
    fun autoRetrySkipsTimeouts() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "request timed out"))
        advanceUntilIdle()

        assertEquals(0, runtime.retried)
        assertEquals(MessageStatus.FAILED, viewModel.state.value.messages.last().status)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun eachConversationKeepsItsOwnModel() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
            sessionModels = mutableMapOf(
                "session-a" to SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF),
                "session-b" to SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF),
            )
        }
        val settings = FakeSettings()
        val viewModel = viewModel(runtime, settings = settings)
        advanceUntilIdle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("model", viewModel.state.value.modelId)

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        assertEquals("model", viewModel.state.value.modelId)

        viewModel.setModel("model-2")
        advanceUntilIdle()
        assertEquals("model-2", viewModel.state.value.modelId)
        assertEquals("model-2", runtime.sessionModels["session-b"]?.modelId)
        assertEquals("model", runtime.sessionModels["session-a"]?.modelId)
        assertEquals(ModelConfig("deepseek", "model-2", ThinkingLevel.OFF), settings.saved.last())

        viewModel.openConversation("session-a")
        advanceUntilIdle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("model", viewModel.state.value.modelId)
    }

    @Test
    fun lostTerminalReconcilesAfterBusyDisappears() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sending)

        runtime.setBusy(setOf(sessionId))
        advanceUntilIdle()
        runtime.histories = mapOf(
            sessionId to listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "final from history"),
            ),
        )
        runtime.setBusy(emptySet())
        advanceTimeBy(600L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("final from history", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun foregroundReconcilesStuckSending() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sending)

        runtime.histories = mapOf(
            sessionId to listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "finished elsewhere"),
            ),
        )
        viewModel.onAppForeground()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("finished elsewhere", viewModel.state.value.messages.last().text)
    }

    @Test
    fun stopWithoutActiveRunReconcilesInsteadOfHanging() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.histories = mapOf(
            sessionId to listOf(AssistantMessage(1, AssistantRole.USER, "hello")),
        )
        viewModel.stop()
        advanceTimeBy(600L)
        advanceUntilIdle()

        assertEquals(listOf(sessionId), runtime.stopped)
        assertFalse(viewModel.state.value.sending)
        assertEquals(1, viewModel.state.value.messages.size)
    }

    @Test
    fun runErrorMapsInsufficientBalance() {
        val strings = ChatUiStrings.fallback()
        assertEquals(strings.withCode(strings.insufficientBalance, "402"), strings.runError("402 Insufficient Balance"))
        assertEquals(strings.insufficientBalance, strings.runError("insufficient_quota"))
        assertEquals(strings.withCode(strings.invalidApiKey, "401"), strings.runError("401 API key invalid"))
        assertEquals(strings.withCode(strings.modelUnavailable, "404"), strings.runError("404 model not found"))
        assertEquals(strings.withCode(strings.serviceUnavailable, "503"), strings.runError("HTTP 503"))
        assertEquals(strings.requestFailed, strings.runError("weird failure"))
        assertEquals(strings.timeout, strings.runError("request timed out"))
        assertEquals(strings.requestFailed, strings.runError(null))
    }

    @Test
    fun workshopSkillExpandsPromptBeforeSending() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(SkillExpansion("workshop", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("/skill:workshop build a todo app")
        viewModel.send()
        assertTrue(viewModel.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf(Triple(HOME_CWD, "workshop", "build a todo app")), expander.calls)
        assertEquals(SKILL_PROMPT, runtime.sent.single())
        assertEquals(SKILL_PROMPT, viewModel.state.value.messages.first().text)
        assertEquals("build a todo app", viewModel.state.value.conversationTitle)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun workshopSkillWithoutArgsUsesSkillNameAsTitle() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(SkillExpansion("workshop", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("/skill:workshop")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(Triple(HOME_CWD, "workshop", ""), expander.calls.single())
        assertEquals(SKILL_PROMPT, runtime.sent.single())
        assertEquals("workshop", viewModel.state.value.conversationTitle)
    }

    @Test
    fun skillNamespaceExpandsNamedSkill() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(SkillExpansion("pdf-tools", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("/skill:pdf-tools extract tables")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(Triple(HOME_CWD, "pdf-tools", "extract tables"), expander.calls.single())
        assertEquals(SKILL_PROMPT, runtime.sent.single())
    }

    @Test
    fun trailingSkillMentionExpandsInlineBeforeSending() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(SkillExpansion("workshop", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("使用一下这个skill /skill:workshop")
        viewModel.send()
        assertTrue(viewModel.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf(Triple(HOME_CWD, "workshop", "")), expander.calls)
        assertEquals("$SKILL_PROMPT\n\n使用一下这个skill", runtime.sent.single())
        assertEquals("使用一下这个skill", viewModel.state.value.conversationTitle)
    }

    @Test
    fun trailingUnknownSkillShowsErrorWithoutSending() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(null)
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("使用一下这个skill /skill:workshop")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(Triple(HOME_CWD, "workshop", ""), expander.calls.single())
        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ChatUiStrings.fallback().skillMissing("workshop"), viewModel.state.value.error)
    }

    @Test
    fun refreshAvailableSkillsLoadsCwdSkills() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(
            result = null,
            available = listOf(SkillSummary("foo", "Foo helper")),
        )
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.refreshAvailableSkills()
        advanceUntilIdle()

        assertEquals(listOf("foo"), viewModel.availableSkills.value.map { it.name })
        assertEquals(listOf(HOME_CWD), expander.listCalls)
    }

    @Test
    fun missingSkillExpanderShowsError() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.onInputChange("/skill:nope do it")
        viewModel.send()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ChatUiStrings.fallback().skillMissing("nope"), viewModel.state.value.error)
        assertEquals("/skill:nope do it", viewModel.state.value.input)
    }

    @Test
    fun unknownSkillShowsErrorWithoutSending() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val expander = FakeSkillExpander(null)
        val viewModel = viewModel(runtime, skillExpander = expander)
        advanceUntilIdle()

        viewModel.onInputChange("/skill:workshop build something")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(Triple(HOME_CWD, "workshop", "build something"), expander.calls.single())
        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ChatUiStrings.fallback().skillMissing("workshop"), viewModel.state.value.error)
    }

    @Test
    fun newSessionPrefillsInput() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.newSession(HOME_ID, initialInput = "/skill:workshop build a todo app")
        advanceUntilIdle()

        assertEquals("/skill:workshop build a todo app", viewModel.state.value.input)
        assertTrue(viewModel.state.value.messages.isEmpty())
    }

    @Test
    fun sendWhileBusySteersCurrentInput() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sending)

        viewModel.onInputChange("change direction")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("change direction"), runtime.steered)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun sendWhileBusyRejectsCommands() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        advanceUntilIdle()

        viewModel.onInputChange("/compact")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(ChatUiStrings.fallback().stopBeforeCommand, viewModel.state.value.error)
        assertTrue(runtime.steered.isEmpty())
    }

    @Test
    fun followUpNowQueuesWhileBusy() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        advanceUntilIdle()

        viewModel.onInputChange("after this")
        viewModel.followUpNow()
        advanceUntilIdle()

        assertEquals(listOf("after this"), runtime.followUps)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun stopRestoresDroppedQueueText() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        advanceUntilIdle()
        runtime.stopDropped = listOf("queued one", "queued two")

        viewModel.stop()
        advanceUntilIdle()

        assertEquals("queued one\n\nqueued two", viewModel.state.value.input)
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
    }

    @Test
    fun queuedInputEventsUpdateQueueAndDroppedTextsRestoreInput() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!

        runtime.emitFor(
            sessionId,
            RuntimeEvent.QueueChanged(
                runtime.runId,
                steering = listOf("steer text"),
                followUp = listOf("follow text"),
            ),
        )
        advanceUntilIdle()
        assertEquals(
            listOf(
                QueuedInput(InputQueueKind.STEER, "steer text"),
                QueuedInput(InputQueueKind.FOLLOW_UP, "follow text"),
            ),
            viewModel.state.value.queuedInputs,
        )

        runtime.emitFor(sessionId, RuntimeEvent.QueuedInputsDropped(listOf("lost text"), runtime.runId))
        advanceUntilIdle()
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
        assertEquals("lost text", viewModel.state.value.input)
    }

    @Test
    fun userInputAdmittedSplitsAssistantMessage() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        advanceUntilIdle()
        val sessionId = viewModel.state.value.conversationId!!

        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "before steering"))
        runtime.emitFor(sessionId, RuntimeEvent.UserInputAdmitted(runtime.runId, "steer text"))
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals(4, messages.size)
        assertEquals(MessageStatus.DONE, messages[1].status)
        assertFalse(messages[1].streaming)
        assertEquals(AssistantRole.USER, messages[2].role)
        assertEquals("steer text", messages[2].text)
        assertEquals(AssistantRole.ASSISTANT, messages[3].role)
        assertTrue(messages[3].streaming)
        assertEquals(runtime.runId, messages[3].runId)
        assertTrue(viewModel.state.value.sending)

        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "after steering"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "after steering", RunStatus.DONE, 4000L))
        advanceUntilIdle()
        assertEquals("after steering", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    private fun multiSessionRuntime(count: Int): FakeRuntime {
        val summaries = (1..count).map { index ->
            ConversationSummary(
                id = "session-$index",
                title = "S$index",
                updatedAt = (count + 1 - index).toLong(),
                filePath = "s$index.jsonl",
                cwd = HOME_CWD,
            )
        }
        return FakeRuntime().apply {
            sessions = summaries
            histories = summaries.associate { summary ->
                summary.id to listOf(AssistantMessage(1, AssistantRole.USER, summary.id))
            }
        }
    }

    @Test
    fun restoresDraftQuoteAndAttachmentsWhenSwitchingBack() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val attachment = AttachmentRef(
            id = "att-1",
            name = "photo.png",
            mime = "image/png",
            size = 12L,
            path = "attachments/att-1_photo.png",
            kind = AttachmentKind.IMAGE,
        )
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        viewModel.onInputChange("draft b")
        viewModel.quote(2L, "from b")
        viewModel.launchAttachmentImport("session-b", java.io.File(HOME_CWD)) {
            AttachmentImport.Imported(attachment)
        }
        advanceUntilIdle()
        val quote = QuotedMessage(2L, "from b")
        assertEquals(quote, viewModel.state.value.quote)
        assertEquals(listOf(attachment), viewModel.state.value.attachments)

        viewModel.openConversation("session-a")
        assertEquals("", viewModel.state.value.input)
        assertNull(viewModel.state.value.quote)
        assertTrue(viewModel.state.value.attachments.isEmpty())
        advanceUntilIdle()

        viewModel.openConversation("session-b")
        assertEquals("draft b", viewModel.state.value.input)
        assertEquals(quote, viewModel.state.value.quote)
        assertEquals(listOf(attachment), viewModel.state.value.attachments)
        advanceUntilIdle()
    }

    @Test
    fun doesNotRestoreStaleQueuedInputsFromCachedSession() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.openConversation("session-b")
        advanceUntilIdle()
        val sessionB = viewModel.state.value.conversationId!!
        runtime.emitFor(
            sessionB,
            RuntimeEvent.QueueChanged(runtime.runId, steering = listOf("steer b"), followUp = listOf("follow b")),
        )
        advanceUntilIdle()
        val queued = listOf(
            QueuedInput(InputQueueKind.STEER, "steer b"),
            QueuedInput(InputQueueKind.FOLLOW_UP, "follow b"),
        )
        assertEquals(queued, viewModel.state.value.queuedInputs)

        viewModel.openConversation("session-a")
        advanceUntilIdle()
        viewModel.openConversation("session-b")
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
        advanceUntilIdle()
    }

    @Test
    fun keepsScrollAnchorPerSession() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val viewModel = viewModel(runtime)
        advanceUntilIdle()

        viewModel.updateScrollAnchor("session-a", 6, 24)
        viewModel.updateScrollAnchor("session-a", 7, 8, atBottom = true)
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        assertNull(viewModel.scrollAnchorFor("session-b"))

        viewModel.openConversation("session-b")
        advanceUntilIdle()
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        viewModel.updateScrollAnchor("session-b", 1, 0)

        viewModel.openConversation("session-a")
        advanceUntilIdle()
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        assertEquals(ScrollAnchor(1, 0), viewModel.scrollAnchorFor("session-b"))
    }

    @Test
    fun evictsLeastRecentlyUsedSessionBeyondLimit() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        assertEquals("session-1", viewModel.state.value.conversationId)
        viewModel.updateScrollAnchor("session-1", 4, 12)

        for (index in 2..8) {
            viewModel.openConversation("session-$index")
            advanceUntilIdle()
        }

        viewModel.openConversation("session-9")
        assertTrue(viewModel.state.value.switchingConversation)
        advanceUntilIdle()
        assertNull(viewModel.scrollAnchorFor("session-1"))

        viewModel.openConversation("session-8")
        assertFalse(viewModel.state.value.switchingConversation)
        advanceUntilIdle()

        viewModel.openConversation("session-1")
        assertTrue(viewModel.state.value.switchingConversation)
        advanceUntilIdle()
    }

    @Test
    fun keepsBusySessionWhenEvicting() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        runtime.setBusy(setOf("session-1"))
        advanceUntilIdle()

        for (index in 2..9) {
            viewModel.openConversation("session-$index")
            advanceUntilIdle()
        }

        viewModel.openConversation("session-1")
        assertFalse(viewModel.state.value.switchingConversation)
        advanceUntilIdle()

        viewModel.openConversation("session-2")
        assertTrue(viewModel.state.value.switchingConversation)
        advanceUntilIdle()
    }

    @Test
    fun onTrimMemoryClearsIdleSessionsAndKeepsCurrentAndBusy() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        runtime.setBusy(setOf("session-1"))
        advanceUntilIdle()
        for (index in 2..8) {
            viewModel.openConversation("session-$index")
            advanceUntilIdle()
        }
        viewModel.updateScrollAnchor("session-2", 3, 5)
        viewModel.updateScrollAnchor("session-3", 4, 6)

        viewModel.onTrimMemory(5)
        viewModel.openConversation("session-2")
        assertFalse(viewModel.state.value.switchingConversation)
        advanceUntilIdle()

        viewModel.onTrimMemory(10)
        assertEquals(ScrollAnchor(3, 5), viewModel.scrollAnchorFor("session-2"))
        assertNull(viewModel.scrollAnchorFor("session-3"))

        viewModel.updateScrollAnchor("session-4", 8, 1)
        viewModel.onTrimMemory(20)
        assertEquals(ScrollAnchor(8, 1), viewModel.scrollAnchorFor("session-4"))

        viewModel.openConversation("session-3")
        assertTrue(viewModel.state.value.switchingConversation)
        advanceUntilIdle()

        viewModel.openConversation("session-1")
        assertFalse(viewModel.state.value.switchingConversation)
        advanceUntilIdle()
    }

    private class FakeSkillExpander(
        private val result: SkillExpansion?,
        private val available: List<SkillSummary> = emptyList(),
    ) : SkillExpander {
        val calls = mutableListOf<Triple<String, String, String>>()
        val listCalls = mutableListOf<String>()

        override suspend fun expand(cwd: String, name: String, args: String): SkillExpansion? {
            calls += Triple(cwd, name, args)
            return result
        }

        override suspend fun list(cwd: String): List<SkillSummary> {
            listCalls += cwd
            return available
        }
    }

    private class FakeRuntime : AgentRuntime {
        private val flow = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 64)
        private val busy = MutableStateFlow<Set<String>>(emptySet())
        val runId = "run-1"
        override val events: SharedFlow<SessionRuntimeEvent> = flow
        override val busySessionIds: StateFlow<Set<String>> = busy
        val interruptedRunState = MutableStateFlow<List<InterruptedRun>>(emptyList())
        override val interruptedRuns: StateFlow<List<InterruptedRun>> = interruptedRunState
        override val activeRuns = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
        var sessions: List<ConversationSummary> = emptyList()
        var histories: Map<String, List<AssistantMessage>> = emptyMap()
        var sessionModels: MutableMap<String, SessionModelSnapshot> = mutableMapOf()
        val historyCalls = mutableMapOf<String, Int>()
        val openCalls = mutableListOf<String>()
        val sent = mutableListOf<String>()
        val sentImages = mutableListOf<List<ImagePart>>()
        val steered = mutableListOf<String>()
        val followUps = mutableListOf<String>()
        var queued: List<QueuedInput> = emptyList()
        var stopDropped: List<String> = emptyList()
        val delivered = mutableListOf<Triple<String, String, String?>>()
        val stopped = mutableListOf<String>()
        private var createdCount = 0
        private val extraProjects = mutableListOf<RuntimeWorkspace>()

        fun setBusy(ids: Set<String>) {
            busy.value = ids
        }

        override suspend fun start() = Unit
        override suspend fun ensureStarted() = Unit
        val dismissedRuns = mutableListOf<String>()
        override suspend fun dismissInterruptedRun(sessionId: String) {
            dismissedRuns += sessionId
        }

        override suspend fun workspaces(): List<RuntimeWorkspace> =
            listOf(RuntimeWorkspace(HOME_ID, "Home", HOME_CWD, isHome = true)) + extraProjects

        override suspend fun createWorkspace(name: String): RuntimeWorkspace {
            val workspace = RuntimeWorkspace("workspace-$name", name, "/workspaces/$name", createdAt = 1L)
            extraProjects += workspace
            return workspace
        }

        override suspend fun renameWorkspace(id: String, name: String) = Unit
        override suspend fun deleteWorkspace(id: String) = Unit

        override suspend fun allSessions(): List<ConversationSummary> =
            sessions.filter { !it.archived }

        override suspend fun workspaceSessionCount(workspaceId: String): Int = sessions.size

        override suspend fun newSession(workspaceId: String): OpenedSession {
            createdCount += 1
            val id = "session-$createdCount"
            sessions = sessions + ConversationSummary(id, "", createdCount.toLong(), "$id.jsonl", HOME_CWD)
            return OpenedSession(id, "$id.jsonl", HOME_CWD)
        }

        override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
            openCalls += filePath
            val summary = sessions.firstOrNull { it.filePath == filePath }
            return OpenedSession(summary?.id ?: filePath, filePath, cwd)
        }

        override suspend fun closeSession(sessionId: String) = Unit

        override suspend fun reloadSession(sessionId: String): String = sessionId

        override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) {
            sent += text
            sentImages += images
        }

        override suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) {
            steered += text
            sentImages += images
        }

        override suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) {
            followUps += text
            sentImages += images
        }

        override fun queuedInputs(sessionId: String): List<QueuedInput> = queued

        override fun clearQueuedInputs(sessionId: String): List<String> {
            val dropped = queued.map { it.text }
            queued = emptyList()
            return dropped
        }

        var retried = 0

        override suspend fun retryLastTurn(sessionId: String) {
            retried += 1
        }

        override fun stop(sessionId: String): List<String> {
            stopped += sessionId
            val dropped = stopDropped
            stopDropped = emptyList()
            return dropped
        }

        override suspend fun history(sessionId: String): List<AssistantMessage> {
            historyCalls[sessionId] = (historyCalls[sessionId] ?: 0) + 1
            return histories[sessionId].orEmpty()
        }

        override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
            delivered += Triple(sessionId, text, reminder)
        }

        override suspend fun activeRunId(sessionId: String): String? = activeRuns.value[sessionId]?.runId

        override suspend fun sessionTitle(sessionId: String): String? =
            sessions.firstOrNull { it.id == sessionId }?.title

        override suspend fun sessions(workspaceId: String): List<ConversationSummary> =
            sessions.filter { !it.archived }

        override suspend fun archivedSessions(): List<ConversationSummary> =
            sessions.filter { it.archived }

        override suspend fun renameSession(id: String, title: String) = Unit
        override suspend fun setSessionPinned(id: String, pinned: Boolean) = Unit
        override suspend fun setSessionArchived(id: String, archived: Boolean) = Unit
        override suspend fun deleteSession(id: String) = Unit

        override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = sessionModels[sessionId]

        override suspend fun setSessionModel(
            sessionId: String,
            providerId: String,
            modelId: String,
            thinkingLevel: ThinkingLevel,
        ) {
            sessionModels[sessionId] = SessionModelSnapshot(providerId, modelId, thinkingLevel)
        }

        override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) {
            sessionModels[sessionId]?.let { sessionModels[sessionId] = it.copy(thinkingLevel = thinkingLevel) }
        }

        override suspend fun currentConfig(): ModelConfig = ModelConfig()

        suspend fun emitFor(sessionId: String, event: RuntimeEvent) =
            flow.emit(SessionRuntimeEvent(sessionId, event))
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
        override fun models(providerId: String): List<ModelOption> = options.filter { it.providerId == providerId }

        override fun resolveConfig(config: ModelConfig): ModelConfig {
            val models = models(config.providerId).ifEmpty { options }
            val model = models.firstOrNull { it.id == config.modelId } ?: models.first()
            val thinking = config.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
            return ModelConfig(model.providerId, model.id, thinking)
        }

        override fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model? = null
        override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
    }

    private class FakeAttachmentStore : AttachmentStore {
        var ready: CompletableDeferred<AttachmentImport>? = null

        override suspend fun import(uri: Uri, root: java.io.File): AttachmentImport =
            ready?.await() ?: AttachmentImport.Failed

        override fun file(ref: AttachmentRef, root: java.io.File): java.io.File = java.io.File(root, ref.path)

        override fun shareIntent(ref: AttachmentRef, root: java.io.File): Intent? = null

        override fun resolve(raw: String, root: java.io.File): AttachmentRef? = null
    }

    private class FakeSettings : ModelSettings {
        private val state = MutableStateFlow(ModelConfig())
        val saved = mutableListOf<ModelConfig>()
        override val config: Flow<ModelConfig> = state
        override suspend fun read(): ModelConfig = state.value

        override suspend fun save(config: ModelConfig) {
            saved += config
            state.value = config
        }
    }

    private companion object {
        const val HOME_ID = "home"
        const val HOME_CWD = "/home"
        const val SKILL_PROMPT =
            "<skill name=\"workshop\" location=\"/home/skills/workshop/SKILL.md\">\n# Workshop\n</skill>\n\nbuild a todo app"
    }
}
