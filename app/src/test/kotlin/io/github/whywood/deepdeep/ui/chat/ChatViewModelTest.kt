package io.github.whywood.deepdeep.ui.chat

import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillExpansion
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
    ) = ChatViewModel(
        runtime,
        FakeProviders(),
        FakeSettings(),
        FakeAttachmentStore(),
        prepareRuntime = {},
        autoRetry = kotlinx.coroutines.flow.flowOf(autoRetry),
        skillExpander = skillExpander,
        ioDispatcher = dispatcher,
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
        assertEquals(ChatUiStrings.fallback().thinking, sending.activity)
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

        viewModel.onInputChange("/workshop-skill build a todo app")
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

        viewModel.onInputChange("/workshop-skill")
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

        viewModel.onInputChange("/workshop-skill build something")
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

        viewModel.newSession(HOME_ID, initialInput = "/workshop-skill build a todo app")
        advanceUntilIdle()

        assertEquals("/workshop-skill build a todo app", viewModel.state.value.input)
        assertTrue(viewModel.state.value.messages.isEmpty())
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
        var sessions: List<ConversationSummary> = emptyList()
        var histories: Map<String, List<AssistantMessage>> = emptyMap()
        var activeRuns: Map<String, String> = emptyMap()
        val historyCalls = mutableMapOf<String, Int>()
        val openCalls = mutableListOf<String>()
        val sent = mutableListOf<String>()
        val delivered = mutableListOf<Triple<String, String, String?>>()
        private var createdCount = 0
        private val extraProjects = mutableListOf<RuntimeWorkspace>()

        override suspend fun start() = Unit
        override suspend fun ensureStarted() = Unit

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

        override suspend fun send(sessionId: String, text: String) {
            sent += text
        }

        var retried = 0

        override suspend fun retryLastTurn(sessionId: String) {
            retried += 1
        }

        override fun stop(sessionId: String) = Unit

        override suspend fun history(sessionId: String): List<AssistantMessage> {
            historyCalls[sessionId] = (historyCalls[sessionId] ?: 0) + 1
            return histories[sessionId].orEmpty()
        }

        override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
            delivered += Triple(sessionId, text, reminder)
        }

        override suspend fun activeRunId(sessionId: String): String? = activeRuns[sessionId]

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
        override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) = Unit
        override suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) = Unit
        override suspend fun currentConfig(): ModelConfig = ModelConfig()

        suspend fun emitFor(sessionId: String, event: RuntimeEvent) =
            flow.emit(SessionRuntimeEvent(sessionId, event))
    }

    private class FakeProviders : ProviderDirectory {
        private val option = ModelOption("deepseek", "model", "DeepSeek", listOf(ThinkingLevel.OFF))
        override val summaries: List<ProviderSummary> = listOf(
            ProviderSummary("deepseek", "DeepSeek", 1, null, false, true),
        )
        override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }
        override fun models(providerId: String): List<ModelOption> = listOf(option)
        override fun resolveConfig(config: ModelConfig): ModelConfig = config.copy(modelId = option.id)
        override fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model? = null
        override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
    }

    private class FakeAttachmentStore : AttachmentStore {
        override suspend fun import(uri: Uri, root: java.io.File): AttachmentImport = AttachmentImport.Failed

        override fun file(ref: AttachmentRef, root: java.io.File): java.io.File = java.io.File(root, ref.path)

        override fun shareIntent(ref: AttachmentRef, root: java.io.File): Intent? = null

        override fun resolve(raw: String, root: java.io.File): AttachmentRef? = null
    }

    private class FakeSettings : ModelSettings {
        override val config: Flow<ModelConfig> = MutableStateFlow(ModelConfig())
        override suspend fun read(): ModelConfig = ModelConfig()
        override suspend fun save(config: ModelConfig) = Unit
    }

    private companion object {
        const val HOME_ID = "home"
        const val HOME_CWD = "/home"
        const val SKILL_PROMPT =
            "<skill name=\"workshop\" location=\"/home/skills/workshop/SKILL.md\">\n# Workshop\n</skill>\n\nbuild a todo app"
    }
}
