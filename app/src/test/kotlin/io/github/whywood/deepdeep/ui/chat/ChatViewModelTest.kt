package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.ui.common.chat.ScrollAnchor

import io.github.whywood.deepdeep.ui.common.chat.QuotedMessage

import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.conversation.nextMessageId
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillExpansion
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.runtime.TurnSource
import io.github.whywood.deepdeep.runtime.UserQuestion
import io.github.whywood.deepdeep.ui.chat.testing.DelayedOpenRuntime
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import io.github.whywood.deepdeep.ui.chat.testing.HOME_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(20)
        runCurrent()
        advanceUntilIdle()
    }

    private fun TestScope.viewModel(
        runtime: AgentRuntime,
        autoRetry: Boolean = false,
        skillExpander: SkillExpander? = null,
        settings: FakeSettings = FakeSettings(),
        attachmentStore: FakeAttachmentStore = FakeAttachmentStore(),
        interruptedQuestions: suspend (String) -> List<InterruptedQuestion> = { emptyList() },
        dismissInterruptedQuestions: suspend (String) -> Unit = {},
        interruptedRuns: suspend (String) -> List<InterruptedRun> = { emptyList() },
        dismissInterruptedRun: suspend (String) -> Unit = {},
    ): ChatViewModel {
        val store = ConversationStore(
            runtime = runtime,
            scope = backgroundScope,
            ioDispatcher = dispatcher,
            strings = { ConversationStrings.fallback() },
            decorate = { state, sessionId ->
                var next = state
                val questions = interruptedQuestions(sessionId)
                if (questions.isNotEmpty() && next.messages.none { it.notice is TurnNotice.InterruptedQuestions }) {
                    next = next.copy(
                        messages = next.messages + interruptedNotice(
                            next.messages,
                            TurnNotice.InterruptedQuestions(questions.size),
                        ),
                    )
                }
                val runs = interruptedRuns(sessionId)
                if (runs.isNotEmpty() && next.messages.none { it.notice is TurnNotice.InterruptedRun }) {
                    next = next.copy(
                        messages = next.messages + interruptedNotice(
                            next.messages,
                            TurnNotice.InterruptedRun(runs.size),
                        ),
                    )
                }
                next
            },
        )
        store.start()
        return ChatViewModel(
            runtime = runtime,
            store = store,
            providers = FakeProviders(),
            modelConfigStore = settings,
            attachmentStore = attachmentStore,
            prepareRuntime = {},
            autoRetry = kotlinx.coroutines.flow.flowOf(autoRetry),
            skillExpander = skillExpander,
            ioDispatcher = dispatcher,
            dismissInterruptedQuestions = dismissInterruptedQuestions,
            dismissInterruptedRun = dismissInterruptedRun,
        )
    }

    private fun interruptedNotice(messages: List<AssistantMessage>, notice: TurnNotice): AssistantMessage {
        val now = System.currentTimeMillis()
        return AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.ASSISTANT,
            text = "",
            status = MessageStatus.DONE,
            startedAt = now,
            finishedAt = now,
            notice = notice,
        )
    }

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
    fun defaultTitleIsReplacedAfterLanguageSwitch() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

        viewModel.updateUiStrings(ConversationStrings.fallback().copy(newChatTitle = "新对话"))
        viewModel.onInputChange("hello")
        viewModel.send()

        assertEquals("hello", viewModel.state.value.conversationTitle)
    }

    @Test
    fun sendShowsWorkingStateImmediatelyAndFinishes() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

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
        settle()

        val finished = viewModel.state.value
        assertFalse(finished.sending)
        assertEquals("hi", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
    }

    @Test
    fun externalRunWithPromptAppearsInActiveConversation() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L, prompt = "from overlay"))
        runCurrent()
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "answer"))
        runCurrent()
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "answer", RunStatus.DONE, 2000L))
        advanceTimeBy(20)
        runCurrent()

        val messages = viewModel.state.value.messages
        assertEquals(listOf("from overlay", "answer"), messages.map { it.text })
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals(AssistantRole.ASSISTANT, messages[1].role)
        assertEquals(MessageStatus.DONE, messages[1].status)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun updateUiStringsLocalizesSubsequentEvents() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.updateUiStrings(ConversationStrings.fallback().copy(requestFailed = "localized failure"))

        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "unexpected"))
        settle()

        assertEquals("localized failure", viewModel.state.value.messages.last().error)
    }

    @Test
    fun ignoresStaleRunEvents() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-2", "stale"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-1", "fresh"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "fresh", RunStatus.DONE, 2000L))
        settle()
        assertEquals("fresh", viewModel.state.value.messages.last().text)
    }

    @Test
    fun keepsTextWithToolsInProcessAndFinalTextAsAnswer() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
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
        settle()

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
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val viewModel = viewModel(runtime)
        settle()
        assertEquals("from a", viewModel.state.value.messages.single().text)

        viewModel.openConversation("session-b")
        assertTrue(viewModel.state.value.switchingConversation)
        settle()
        assertEquals("from b", viewModel.state.value.messages.single().text)

        viewModel.openConversation("session-a")
        assertFalse(viewModel.state.value.switchingConversation)
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("from a", viewModel.state.value.messages.single().text)
        settle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("a.jsonl", runtime.openCalls.last())
    }

    @Test
    fun insertsInterruptedQuestionNoticeAfterOpeningSession() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()

        assertEquals(listOf("session-a"), queries)
        val message = viewModel.state.value.messages.last()
        assertEquals(TurnNotice.InterruptedQuestions(1), message.notice)
        assertEquals(2, viewModel.state.value.messages.size)
    }

    @Test
    fun keepsInterruptedQuestionNoticeAcrossConversationSwitches() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()

        viewModel.openConversation("session-b")
        settle()
        viewModel.openConversation("session-a")
        settle()

        assertEquals(1, queries.count { it == "session-a" })
        assertEquals(1, viewModel.state.value.messages.count { message ->
            message.notice is TurnNotice.InterruptedQuestions
        })
    }

    @Test
    fun dismissesInterruptedQuestionsOnNextMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val dismissed = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId -> listOf(interruptedQuestion(sessionId)) },
            dismissInterruptedQuestions = { sessionId -> dismissed += sessionId },
        )
        settle()

        viewModel.onInputChange("continue please")
        viewModel.send()
        settle()

        assertEquals(listOf("session-a"), dismissed)
        assertEquals("continue please", runtime.sent.single())
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedQuestions
        })
    }

    @Test
    fun insertsInterruptedRunNoticeAfterOpeningSession() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()

        assertEquals(listOf("session-a"), queries)
        val message = viewModel.state.value.messages.last()
        assertEquals(TurnNotice.InterruptedRun(1), message.notice)
        assertEquals(2, viewModel.state.value.messages.size)
    }

    @Test
    fun keepsInterruptedRunNoticeAcrossConversationSwitches() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()

        viewModel.openConversation("session-b")
        settle()
        viewModel.openConversation("session-a")
        settle()

        assertEquals(1, queries.count { it == "session-a" })
        assertEquals(1, viewModel.state.value.messages.count { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun dismissesInterruptedRunOnNextMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val dismissed = mutableListOf<String>()
        val viewModel = viewModel(
            runtime,
            interruptedRuns = { sessionId -> listOf(interruptedRun(sessionId)) },
            dismissInterruptedRun = { sessionId -> dismissed += sessionId },
        )
        settle()

        viewModel.onInputChange("continue please")
        viewModel.send()
        settle()

        assertEquals(listOf("session-a"), dismissed)
        assertEquals("continue please", runtime.sent.single())
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun removesBothInterruptedNoticesAfterSendingMessage() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()
        assertEquals(2, viewModel.state.value.messages.count { message -> message.notice.isInterruptedNoticeForTest() })

        viewModel.onInputChange("continue please")
        viewModel.send()
        settle()

        assertTrue(viewModel.state.value.messages.none { message -> message.notice.isInterruptedNoticeForTest() })
        assertEquals(listOf("session-a"), dismissedQuestions)
        assertEquals(listOf("session-a"), dismissedRuns)
    }

    @Test
    fun doesNotRestoreInterruptedRunNoticeAfterSwitchingBack() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
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
        settle()
        assertEquals(2, viewModel.state.value.messages.size)

        viewModel.onInputChange("continue please")
        viewModel.send()
        settle()
        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })

        viewModel.openConversation("session-b")
        settle()
        viewModel.openConversation("session-a")
        settle()

        assertTrue(viewModel.state.value.messages.none { message ->
            message.notice is TurnNotice.InterruptedRun
        })
    }

    @Test
    fun insertsInterruptedQuestionAndRunNoticesTogether() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session)
            histories = mapOf("session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "hello")))
        }
        val viewModel = viewModel(
            runtime,
            interruptedQuestions = { sessionId -> listOf(interruptedQuestion(sessionId)) },
            interruptedRuns = { sessionId -> listOf(interruptedRun(sessionId)) },
        )
        settle()

        val messages = viewModel.state.value.messages
        assertEquals(3, messages.size)
        assertEquals(TurnNotice.InterruptedQuestions(1), messages[1].notice)
        assertEquals(TurnNotice.InterruptedRun(1), messages[2].notice)
    }

    @Test
    fun backgroundRunKeepsStreamingAfterSwitchingAway() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to emptyList(),
                "session-b" to listOf(AssistantMessage(1, AssistantRole.USER, "from b")),
            )
        }
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))

        viewModel.openConversation("session-b")
        settle()
        assertEquals("from b", viewModel.state.value.messages.single().text)

        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, " more"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "partial more", RunStatus.DONE, 2000L))
        settle()
        runtime.histories = runtime.histories + (sessionId to listOf(
            AssistantMessage(1, AssistantRole.USER, "hello"),
            AssistantMessage(2, AssistantRole.ASSISTANT, "partial more"),
        ))

        viewModel.openConversation("session-a")
        settle()
        val finished = viewModel.state.value
        assertEquals("partial more", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
        assertFalse(finished.sending)
    }

    @Test
    fun attachmentImportedAfterSwitchingSessionIsDiscarded() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val store = FakeAttachmentStore()
        val viewModel = viewModel(runtime, attachmentStore = store)
        settle()
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
        settle()
        ready.complete(AttachmentImport.Imported(ref))
        settle()

        assertEquals("session-b", viewModel.state.value.conversationId)
        assertTrue(viewModel.state.value.attachments.isEmpty())
        assertFalse(file.exists())
        root.deleteRecursively()
    }

    @Test
    fun newSessionCanStartWhilePreviousSessionIsRunning() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        val firstSession = viewModel.state.value.conversationId!!
        runtime.emitFor(firstSession, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        assertTrue(viewModel.state.value.sending)

        viewModel.newSession(HOME_ID)
        settle()
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
        settle()
        assertEquals("background", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun batchesRapidDeltasWithoutLosingText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("stream")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "a"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "b"))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "c"))
        settle()
        val streamed = viewModel.state.value.messages.last()
        assertEquals("abc", streamed.steps.filterIsInstance<AssistantStep.Text>().last().text)

        runtime.emitFor(sessionId, RuntimeEvent.RunFinished(runtime.runId, "abc", RunStatus.DONE, 2000L))
        settle()
        assertEquals("abc", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun quotedReplyPrefixesPromptAndShowsInUserMessage() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

        viewModel.quote(9, "**重点**内容")
        assertEquals("重点内容", viewModel.state.value.quote?.text)

        viewModel.onInputChange("展开说说")
        viewModel.send()
        settle()

        assertEquals("> 重点内容\n\n展开说说", runtime.sent.single())
        assertEquals("> 重点内容\n\n展开说说", viewModel.state.value.messages.first().text)
        assertNull(viewModel.state.value.quote)
    }


    @Test
    fun blankCachedConversationAlwaysReloadsHistory() = runTest(dispatcher) {
        val existing = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(existing)
            histories = mapOf("session-a" to emptyList())
        }
        val viewModel = viewModel(runtime)
        settle()
        viewModel.newSession(HOME_ID)
        settle()
        val newSessionId = viewModel.state.value.conversationId!!
        assertTrue(viewModel.state.value.messages.isEmpty())

        runtime.histories = runtime.histories + (newSessionId to listOf(
            AssistantMessage(1, AssistantRole.USER, "reminder body"),
        ))
        viewModel.openConversation("session-a")
        settle()
        viewModel.openConversation(newSessionId)
        settle()

        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals("reminder body", viewModel.state.value.messages.single().text)
        assertTrue((runtime.historyCalls[newSessionId] ?: 0) >= 1)
        assertTrue(viewModel.state.value.historyLoaded)
    }

    @Test
    fun turnInjectedCreatesPlaceholderAndStreams() = runTest(dispatcher) {
        val session = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply { sessions = listOf(session) }
        val viewModel = viewModel(runtime)
        settle()
        val sessionId = viewModel.state.value.conversationId!!
        val raw = "[定时提醒] 喝水\n这是你设置的定时提醒，现在到时间了。\n\n喝水提醒"

        runtime.emitFor(sessionId, RuntimeEvent.TurnInjected("run-t", raw, "喝水"))
        settle()

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
        settle()
        assertEquals("收到", viewModel.state.value.messages.last().text)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun retryRegeneratesReplyWithoutResendingUserText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "402 Insufficient Balance"))
        settle()

        val failed = viewModel.state.value.messages.last()
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals(ConversationStrings.fallback().withCode(ConversationStrings.fallback().insufficientBalance, "402"), failed.error)

        viewModel.retry(failed.id)
        settle()

        assertEquals(listOf("hello"), runtime.sent)
        assertEquals(1, runtime.retried)
        val messages = viewModel.state.value.messages
        assertEquals(2, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertTrue(messages[1].retrying)
        assertEquals(MessageStatus.RUNNING, messages[1].status)

        runtime.emitFor(sessionId, RuntimeEvent.RunStarted("run-2", 5000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-2", "world"))
        runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-2", "world", RunStatus.DONE, 6000L))
        settle()
        val finished = viewModel.state.value.messages.last()
        assertEquals(MessageStatus.DONE, finished.status)
        assertEquals("world", finished.text)
        assertFalse(finished.retrying)
        assertNull(finished.error)
    }

    @Test
    fun autoRetryRegeneratesAfterTransientFailure() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        settle()

        assertEquals(1, runtime.retried)
        val messages = viewModel.state.value.messages
        assertEquals(2, messages.size)
        assertTrue(messages.last().retrying)
        assertEquals(MessageStatus.RUNNING, messages.last().status)
        assertTrue(viewModel.state.value.sending)
    }

    @Test
    fun autoRetryLabelSurvivesHostTerminalActivityClear() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        runCurrent()
        advanceTimeBy(1L)

        runtime.emitFor(sessionId, RuntimeEvent.ActivityChanged(runtime.runId, null))
        runCurrent()
        advanceTimeBy(1L)

        val retrying = viewModel.state.value
        assertTrue(retrying.sending)
        assertEquals(RuntimeActivity.Retrying(1), retrying.activity)
        assertEquals(0, runtime.retried)

        advanceTimeBy(2_500L)
        settle()

        assertEquals(1, runtime.retried)
        assertEquals(RuntimeActivity.Thinking, viewModel.state.value.activity)
    }

    @Test
    fun stopCancelsPendingAutoRetry() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        settle()
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
        settle()

        assertEquals(0, runtime.retried)
        assertFalse(viewModel.state.value.sending)
        assertEquals(listOf(sessionId), runtime.stopped)
    }

    @Test
    fun autoRetrySkipsTimeouts() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime, autoRetry = true)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.RunFailed(runtime.runId, "request timed out"))
        settle()

        assertEquals(0, runtime.retried)
        assertEquals(MessageStatus.FAILED, viewModel.state.value.messages.last().status)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun eachConversationKeepsItsOwnModel() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
            sessionModels = mutableMapOf(
                "session-a" to SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF),
                "session-b" to SessionModelSnapshot("deepseek", "model", ThinkingLevel.OFF),
            )
        }
        val settings = FakeSettings()
        val viewModel = viewModel(runtime, settings = settings)
        settle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("model", viewModel.state.value.modelId)

        viewModel.openConversation("session-b")
        settle()
        assertEquals("model", viewModel.state.value.modelId)

        viewModel.setModel("model-2")
        settle()
        assertEquals("model-2", viewModel.state.value.modelId)
        assertEquals("model-2", runtime.sessionModels["session-b"]?.modelId)
        assertEquals("model", runtime.sessionModels["session-a"]?.modelId)
        assertEquals(ModelConfig("deepseek", "model-2", ThinkingLevel.OFF), settings.saved.last())

        viewModel.openConversation("session-a")
        settle()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals("model", viewModel.state.value.modelId)
    }

    @Test
    fun lostTerminalReconcilesAfterBusyDisappears() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        settle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))
        settle()
        assertTrue(viewModel.state.value.sending)

        runtime.setBusy(setOf(sessionId))
        settle()
        runtime.histories = mapOf(
            sessionId to listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "final from history"),
            ),
        )
        runtime.setBusy(emptySet())
        advanceTimeBy(600L)
        settle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("final from history", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    @Test
    fun foregroundReconcilesStuckSending() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        settle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "partial"))
        settle()
        assertTrue(viewModel.state.value.sending)

        runtime.histories = mapOf(
            sessionId to listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "finished elsewhere"),
            ),
        )
        viewModel.onAppForeground()
        settle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("finished elsewhere", viewModel.state.value.messages.last().text)
    }

    @Test
    fun stopWithoutActiveRunReconcilesInsteadOfHanging() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("hello")
        viewModel.send()
        settle()
        val sessionId = viewModel.state.value.conversationId!!
        runtime.histories = mapOf(
            sessionId to listOf(AssistantMessage(1, AssistantRole.USER, "hello")),
        )
        viewModel.stop()
        advanceTimeBy(600L)
        settle()

        assertEquals(listOf(sessionId), runtime.stopped)
        assertFalse(viewModel.state.value.sending)
        assertEquals(1, viewModel.state.value.messages.size)
    }

    @Test
    fun runErrorMapsInsufficientBalance() {
        val strings = ConversationStrings.fallback()
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
    fun miniappSkillExpandsPromptBeforeSending() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(SkillExpansion("miniapp", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("/skill:miniapp build a todo app")
        viewModel.send()
        assertTrue(viewModel.state.value.sending)
        settle()

        assertEquals(listOf(Triple(HOME_CWD, "miniapp", "build a todo app")), expander.calls)
        assertEquals(SKILL_PROMPT, runtime.sent.single())
        assertEquals(SKILL_PROMPT, viewModel.state.value.messages.first().text)
        assertEquals("build a todo app", viewModel.state.value.conversationTitle)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun miniappSkillWithoutArgsUsesSkillNameAsTitle() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(SkillExpansion("miniapp", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("/skill:miniapp")
        viewModel.send()
        settle()

        assertEquals(Triple(HOME_CWD, "miniapp", ""), expander.calls.single())
        assertEquals(SKILL_PROMPT, runtime.sent.single())
        assertEquals("miniapp", viewModel.state.value.conversationTitle)
    }

    @Test
    fun skillNamespaceExpandsNamedSkill() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(SkillExpansion("pdf-tools", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("/skill:pdf-tools extract tables")
        viewModel.send()
        settle()

        assertEquals(Triple(HOME_CWD, "pdf-tools", "extract tables"), expander.calls.single())
        assertEquals(SKILL_PROMPT, runtime.sent.single())
    }

    @Test
    fun trailingSkillMentionExpandsInlineBeforeSending() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(SkillExpansion("miniapp", SKILL_PROMPT))
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("使用一下这个skill /skill:miniapp")
        viewModel.send()
        assertTrue(viewModel.state.value.sending)
        settle()

        assertEquals(listOf(Triple(HOME_CWD, "miniapp", "")), expander.calls)
        assertEquals("$SKILL_PROMPT\n\n使用一下这个skill", runtime.sent.single())
        assertEquals("使用一下这个skill", viewModel.state.value.conversationTitle)
    }

    @Test
    fun trailingUnknownSkillShowsErrorWithoutSending() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(null)
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("使用一下这个skill /skill:miniapp")
        viewModel.send()
        settle()

        assertEquals(Triple(HOME_CWD, "miniapp", ""), expander.calls.single())
        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ConversationStrings.fallback().skillMissing("miniapp"), viewModel.state.value.error)
    }

    @Test
    fun refreshAvailableSkillsLoadsCwdSkills() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(
            result = null,
            available = listOf(SkillSummary("foo", "Foo helper")),
        )
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.refreshAvailableSkills()
        settle()

        assertEquals(listOf("foo"), viewModel.availableSkills.value.map { it.name })
        assertEquals(listOf(HOME_CWD), expander.listCalls)
    }

    @Test
    fun missingSkillExpanderShowsError() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

        viewModel.onInputChange("/skill:nope do it")
        viewModel.send()
        settle()

        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ConversationStrings.fallback().skillMissing("nope"), viewModel.state.value.error)
        assertEquals("/skill:nope do it", viewModel.state.value.input)
    }

    @Test
    fun unknownSkillShowsErrorWithoutSending() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val expander = FakeSkillExpander(null)
        val viewModel = viewModel(runtime, skillExpander = expander)
        settle()

        viewModel.onInputChange("/skill:miniapp build something")
        viewModel.send()
        settle()

        assertEquals(Triple(HOME_CWD, "miniapp", "build something"), expander.calls.single())
        assertFalse(viewModel.state.value.sending)
        assertTrue(runtime.sent.isEmpty())
        assertEquals(ConversationStrings.fallback().skillMissing("miniapp"), viewModel.state.value.error)
    }

    @Test
    fun newSessionPrefillsInput() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()

        viewModel.newSession(HOME_ID, initialInput = "/skill:miniapp build a todo app")
        settle()

        assertEquals("/skill:miniapp build a todo app", viewModel.state.value.input)
        assertTrue(viewModel.state.value.messages.isEmpty())
    }

    @Test
    fun sendWhileBusySteersCurrentInput() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        settle()
        assertTrue(viewModel.state.value.sending)

        viewModel.onInputChange("change direction")
        viewModel.send()
        settle()

        assertEquals(listOf("change direction"), runtime.steered)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun sendWhileBusyRejectsCommands() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        settle()

        viewModel.onInputChange("/compact")
        viewModel.send()
        settle()

        assertEquals(ConversationStrings.fallback().stopBeforeCommand, viewModel.state.value.error)
        assertTrue(runtime.steered.isEmpty())
    }

    @Test
    fun followUpNowQueuesWhileBusy() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        settle()

        viewModel.onInputChange("after this")
        viewModel.followUpNow()
        settle()

        assertEquals(listOf("after this"), runtime.followUps)
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun stopRestoresDroppedQueueText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        settle()
        runtime.stopDropped = listOf("queued one", "queued two")

        viewModel.stop()
        settle()

        assertEquals("queued one\n\nqueued two", viewModel.state.value.input)
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
    }

    @Test
    fun queuedInputEventsUpdateQueueAndDroppedTextsRestoreInput() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        val sessionId = viewModel.state.value.conversationId!!

        runtime.emitFor(
            sessionId,
            RuntimeEvent.QueueChanged(
                runtime.runId,
                steering = listOf("steer text"),
                followUp = listOf("follow text"),
            ),
        )
        settle()
        assertEquals(
            listOf(
                QueuedInput(InputQueueKind.STEER, "steer text"),
                QueuedInput(InputQueueKind.FOLLOW_UP, "follow text"),
            ),
            viewModel.state.value.queuedInputs,
        )

        runtime.emitFor(sessionId, RuntimeEvent.QueuedInputsDropped(listOf("lost text"), runtime.runId))
        settle()
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
        assertEquals("lost text", viewModel.state.value.input)
    }

    @Test
    fun userInputAdmittedSplitsAssistantMessage() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        viewModel.onInputChange("first")
        viewModel.send()
        settle()
        val sessionId = viewModel.state.value.conversationId!!

        runtime.emitFor(sessionId, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emitFor(sessionId, RuntimeEvent.TextDelta(runtime.runId, "before steering"))
        runtime.emitFor(sessionId, RuntimeEvent.UserInputAdmitted(runtime.runId, "steer text"))
        settle()

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
        settle()
        assertEquals("after steering", viewModel.state.value.messages.last().text)
        assertEquals(MessageStatus.DONE, viewModel.state.value.messages.last().status)
    }

    private fun multiSessionRuntime(count: Int): FakeAgentRuntime {
        val summaries = (1..count).map { index ->
            ConversationSummary(
                id = "session-$index",
                title = "S$index",
                updatedAt = (count + 1 - index).toLong(),
                filePath = "s$index.jsonl",
                cwd = HOME_CWD,
            )
        }
        return FakeAgentRuntime().apply {
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
        val runtime = FakeAgentRuntime().apply {
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
        settle()

        viewModel.openConversation("session-b")
        settle()
        viewModel.onInputChange("draft b")
        viewModel.quote(2L, "from b")
        viewModel.launchAttachmentImport("session-b", java.io.File(HOME_CWD)) {
            AttachmentImport.Imported(attachment)
        }
        settle()
        val quote = QuotedMessage(2L, "from b")
        assertEquals(quote, viewModel.state.value.quote)
        assertEquals(listOf(attachment), viewModel.state.value.attachments)

        viewModel.openConversation("session-a")
        assertEquals("", viewModel.state.value.input)
        assertNull(viewModel.state.value.quote)
        assertTrue(viewModel.state.value.attachments.isEmpty())
        settle()

        viewModel.openConversation("session-b")
        assertEquals("draft b", viewModel.state.value.input)
        assertEquals(quote, viewModel.state.value.quote)
        assertEquals(listOf(attachment), viewModel.state.value.attachments)
        settle()
    }

    @Test
    fun doesNotRestoreStaleQueuedInputsFromCachedSession() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val viewModel = viewModel(runtime)
        settle()
        viewModel.openConversation("session-b")
        settle()
        val sessionB = viewModel.state.value.conversationId!!
        runtime.emitFor(
            sessionB,
            RuntimeEvent.QueueChanged(runtime.runId, steering = listOf("steer b"), followUp = listOf("follow b")),
        )
        settle()
        val queued = listOf(
            QueuedInput(InputQueueKind.STEER, "steer b"),
            QueuedInput(InputQueueKind.FOLLOW_UP, "follow b"),
        )
        assertEquals(queued, viewModel.state.value.queuedInputs)

        viewModel.openConversation("session-a")
        settle()
        viewModel.openConversation("session-b")
        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
        settle()
    }

    @Test
    fun keepsScrollAnchorPerSession() = runTest(dispatcher) {
        val first = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val second = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(first, second)
            histories = mapOf("session-a" to emptyList(), "session-b" to emptyList())
        }
        val viewModel = viewModel(runtime)
        settle()

        viewModel.updateScrollAnchor("session-a", 6, 24)
        viewModel.updateScrollAnchor("session-a", 7, 8, atBottom = true)
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        assertNull(viewModel.scrollAnchorFor("session-b"))

        viewModel.openConversation("session-b")
        settle()
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        viewModel.updateScrollAnchor("session-b", 1, 0)

        viewModel.openConversation("session-a")
        settle()
        assertEquals(ScrollAnchor(7, 8, atBottom = true), viewModel.scrollAnchorFor("session-a"))
        assertEquals(ScrollAnchor(1, 0), viewModel.scrollAnchorFor("session-b"))
    }

    @Test
    fun evictsLeastRecentlyUsedSessionBeyondLimit() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        settle()
        assertEquals("session-1", viewModel.state.value.conversationId)
        viewModel.updateScrollAnchor("session-1", 4, 12)

        for (index in 2..8) {
            viewModel.openConversation("session-$index")
            settle()
        }

        viewModel.openConversation("session-9")
        assertTrue(viewModel.state.value.switchingConversation)
        settle()
        assertNull(viewModel.scrollAnchorFor("session-1"))

        viewModel.openConversation("session-8")
        assertFalse(viewModel.state.value.switchingConversation)
        settle()

        viewModel.openConversation("session-1")
        assertTrue(viewModel.state.value.switchingConversation)
        settle()
    }

    @Test
    fun keepsBusySessionWhenEvicting() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        settle()
        runtime.setBusy(setOf("session-1"))
        settle()

        for (index in 2..9) {
            viewModel.openConversation("session-$index")
            settle()
        }

        viewModel.openConversation("session-1")
        assertFalse(viewModel.state.value.switchingConversation)
        settle()

        viewModel.openConversation("session-2")
        assertTrue(viewModel.state.value.switchingConversation)
        settle()
    }

    @Test
    fun onTrimMemoryClearsIdleSessionsAndKeepsCurrentAndBusy() = runTest(dispatcher) {
        val runtime = multiSessionRuntime(9)
        val viewModel = viewModel(runtime)
        settle()
        runtime.setBusy(setOf("session-1"))
        settle()
        for (index in 2..8) {
            viewModel.openConversation("session-$index")
            settle()
        }
        viewModel.updateScrollAnchor("session-2", 3, 5)
        viewModel.updateScrollAnchor("session-3", 4, 6)

        viewModel.onTrimMemory(5)
        viewModel.openConversation("session-2")
        assertFalse(viewModel.state.value.switchingConversation)
        settle()

        viewModel.onTrimMemory(10)
        assertEquals(ScrollAnchor(3, 5), viewModel.scrollAnchorFor("session-2"))
        assertNull(viewModel.scrollAnchorFor("session-3"))

        viewModel.updateScrollAnchor("session-4", 8, 1)
        viewModel.onTrimMemory(20)
        assertEquals(ScrollAnchor(8, 1), viewModel.scrollAnchorFor("session-4"))

        viewModel.openConversation("session-3")
        assertTrue(viewModel.state.value.switchingConversation)
        settle()

        viewModel.openConversation("session-1")
        assertFalse(viewModel.state.value.switchingConversation)
        settle()
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

    @Test
    fun openSessionKeepsDeltasArrivingBeforeHistoryLands() = runTest(dispatcher) {
        val sessionA = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val sessionB = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val streaming = AssistantMessage(
            id = 0,
            role = AssistantRole.ASSISTANT,
            text = "",
            streaming = true,
            status = MessageStatus.RUNNING,
            startedAt = 1L,
            runId = "run-live",
        )
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(sessionA, sessionB)
            histories = mapOf("session-b" to listOf(streaming))
        }
        val delayed = DelayedOpenRuntime(runtime)
        val viewModel = viewModel(delayed)
        settle()
        assertEquals("session-a", viewModel.state.value.conversationId)

        delayed.gate = CompletableDeferred()
        viewModel.openConversation("session-b")
        runCurrent()
        assertEquals("session-b", viewModel.state.value.conversationId)
        assertTrue(viewModel.state.value.switchingConversation)

        runtime.emitFor("session-b", RuntimeEvent.TextDelta("run-live", "late delta"))
        runCurrent()
        assertTrue(viewModel.state.value.messages.isEmpty())

        delayed.gate?.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.switchingConversation)
        settle()

        val streamed = viewModel.state.value.messages.single()
        assertEquals(
            "late delta",
            streamed.steps.filterIsInstance<AssistantStep.Text>().joinToString("") { it.text },
        )
    }

    @Test
    fun autoRetryCountdownSurvivesSwitchingAwayAndBack() = runTest(dispatcher) {
        val sessionA = ConversationSummary("session-a", "A", 2L, "a.jsonl", HOME_CWD)
        val sessionB = ConversationSummary("session-b", "B", 1L, "b.jsonl", HOME_CWD)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(sessionA, sessionB)
        }
        val viewModel = viewModel(runtime, autoRetry = true)
        settle()
        assertEquals("session-a", viewModel.state.value.conversationId)

        viewModel.onInputChange("hello")
        viewModel.send()
        runtime.emitFor("session-a", RuntimeEvent.RunStarted(runtime.runId, 1_000L))
        runtime.emitFor("session-a", RuntimeEvent.RunFailed(runtime.runId, "Network unreachable"))
        advanceTimeBy(1L)
        assertTrue(viewModel.state.value.sending)
        assertEquals(0, runtime.retried)

        viewModel.openConversation("session-b")
        runCurrent()
        assertEquals("session-b", viewModel.state.value.conversationId)
        viewModel.openConversation("session-a")
        runCurrent()
        assertEquals("session-a", viewModel.state.value.conversationId)
        assertEquals(0, runtime.retried)

        advanceTimeBy(2_500L)
        settle()

        assertEquals(1, runtime.retried)
        assertTrue(viewModel.state.value.sending)
    }

    @Test
    fun droppedQueuedInputsMergeInOrderWithExistingDraft() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val viewModel = viewModel(runtime)
        settle()
        val sessionId = viewModel.state.value.conversationId!!

        viewModel.onInputChange("draft text")
        runtime.emitFor(
            sessionId,
            RuntimeEvent.QueueChanged(runtime.runId, steering = listOf("s1", "s2"), followUp = listOf("f1")),
        )
        settle()
        assertEquals(3, viewModel.state.value.queuedInputs.size)

        runtime.emitFor(sessionId, RuntimeEvent.QueuedInputsDropped(listOf("first", "second"), runtime.runId))
        runtime.emitFor(sessionId, RuntimeEvent.QueuedInputsDropped(listOf("third"), runtime.runId))
        settle()

        assertEquals(emptyList<QueuedInput>(), viewModel.state.value.queuedInputs)
        assertEquals("draft text\n\nfirst\n\nsecond\n\nthird", viewModel.state.value.input)
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

        override fun sdkModel(providerId: String, modelId: String): io.github.whywood.deepdeep.sdk.ai.Model? = null
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
        const val SKILL_PROMPT =
            "<skill name=\"miniapp\" location=\"/home/skills/miniapp/SKILL.md\">\n# Miniapp\n</skill>\n\nbuild a todo app"
    }
}
