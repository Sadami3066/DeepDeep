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
import io.github.whywood.deepdeep.runtime.RuntimeProject
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
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

    private fun viewModel(runtime: FakeRuntime) = ChatViewModel(
        runtime,
        FakeProviders(),
        FakeSettings(),
        FakeAttachmentStore(),
        prepareRuntime = {},
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
        assertEquals("思考中", sending.activity)
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

        viewModel.openConversation("session-a")
        advanceUntilIdle()
        val finished = viewModel.state.value
        assertEquals("partial more", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
        assertFalse(finished.sending)
    }

    @Test
    fun newChatCanStartWhilePreviousSessionIsRunning() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = viewModel(runtime)
        advanceUntilIdle()
        viewModel.onInputChange("first")
        viewModel.send()
        val firstSession = viewModel.state.value.conversationId!!
        runtime.emitFor(firstSession, RuntimeEvent.RunStarted(runtime.runId, 1000L))
        assertTrue(viewModel.state.value.sending)

        viewModel.newChat()
        advanceUntilIdle()
        val secondSession = viewModel.state.value.conversationId!!
        assertTrue(secondSession != firstSession)
        assertFalse(viewModel.state.value.sending)

        runtime.emitFor(firstSession, RuntimeEvent.TextDelta(runtime.runId, "background"))
        runtime.emitFor(firstSession, RuntimeEvent.RunFinished(runtime.runId, "background", RunStatus.DONE, 2000L))

        viewModel.openConversation(firstSession)
        advanceUntilIdle()
        assertEquals("background", viewModel.state.value.messages.last().text)
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

    private class FakeRuntime : AgentRuntime {
        private val flow = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 64)
        private val busy = MutableStateFlow<Set<String>>(emptySet())
        val runId = "run-1"
        override val events: SharedFlow<SessionRuntimeEvent> = flow
        override val busySessionIds: StateFlow<Set<String>> = busy
        var sessions: List<ConversationSummary> = emptyList()
        var histories: Map<String, List<AssistantMessage>> = emptyMap()
        val openCalls = mutableListOf<String>()
        val sent = mutableListOf<String>()
        private var createdCount = 0
        private val extraProjects = mutableListOf<RuntimeProject>()

        override suspend fun start() = Unit
        override suspend fun ensureStarted() = Unit

        override suspend fun projects(): List<RuntimeProject> =
            listOf(RuntimeProject(HOME_ID, "Home", HOME_CWD, isHome = true)) + extraProjects

        override suspend fun createProject(name: String): RuntimeProject {
            val project = RuntimeProject("project-$name", name, "/projects/$name", createdAt = 1L)
            extraProjects += project
            return project
        }

        override suspend fun renameProject(id: String, name: String) = Unit
        override suspend fun deleteProject(id: String) = Unit

        override suspend fun newSession(projectId: String): OpenedSession {
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

        override suspend fun send(sessionId: String, text: String) {
            sent += text
        }

        override fun stop(sessionId: String) = Unit

        override suspend fun history(sessionId: String): List<AssistantMessage> =
            histories[sessionId].orEmpty()

        override suspend fun sessions(projectId: String): List<ConversationSummary> =
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
    }
}
