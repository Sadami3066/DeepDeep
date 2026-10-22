package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    @Test
    fun sendShowsWorkingStateImmediatelyAndFinishes() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {}, ioDispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.onInputChange("hello")
        viewModel.send()

        val sending = viewModel.state.value
        assertTrue(sending.sending)
        assertEquals("思考中", sending.activity)
        assertEquals(2, sending.messages.size)
        assertEquals(MessageStatus.RUNNING, sending.messages.last().status)
        assertTrue(sending.conversationTitle.isNotBlank())

        runtime.emit(RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emit(RuntimeEvent.TextDelta(runtime.runId, "hi"))
        runtime.emit(RuntimeEvent.RunFinished(runtime.runId, "hi", RunStatus.DONE, 2000L))
        advanceUntilIdle()

        val finished = viewModel.state.value
        assertFalse(finished.sending)
        assertEquals("hi", finished.messages.last().text)
        assertEquals(MessageStatus.DONE, finished.messages.last().status)
    }

    @Test
    fun ignoresStaleRunEvents() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {}, ioDispatcher = dispatcher)
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        runtime.emit(RuntimeEvent.RunStarted("run-1", 1000L))
        runtime.emit(RuntimeEvent.TextDelta("run-2", "stale"))
        runtime.emit(RuntimeEvent.TextDelta("run-1", "fresh"))
        runtime.emit(RuntimeEvent.RunFinished("run-1", "fresh", RunStatus.DONE, 2000L))
        advanceUntilIdle()
        assertEquals("fresh", viewModel.state.value.messages.last().text)
    }


    @Test
    fun keepsTextWithToolsInProcessAndFinalTextAsAnswer() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {}, ioDispatcher = dispatcher)
        advanceUntilIdle()
        viewModel.onInputChange("do it")
        viewModel.send()
        runtime.emit(RuntimeEvent.RunStarted(runtime.runId, 1000L))
        runtime.emit(RuntimeEvent.TextDelta(runtime.runId, "starting"))
        runtime.emit(
            RuntimeEvent.ToolStarted(
                runId = runtime.runId,
                callId = "call-1",
                name = "bash",
                arguments = mapOf("command" to "sleep 1"),
                startedAt = 1200L,
            ),
        )
        runtime.emit(
            RuntimeEvent.ToolFinished(
                runId = runtime.runId,
                callId = "call-1",
                name = "bash",
                result = "ok",
                isError = false,
                durationMs = 1000L,
            ),
        )
        runtime.emit(RuntimeEvent.TextDelta(runtime.runId, "done"))
        runtime.emit(RuntimeEvent.RunFinished(runtime.runId, "done", RunStatus.DONE, 3000L))
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
        val first = ConversationSummary("session-a", "A", 1L, "a.jsonl")
        val second = ConversationSummary("session-b", "B", 2L, "b.jsonl")
        val runtime = FakeRuntime().apply {
            sessionId = "session-a"
            sessions = listOf(first, second)
            histories = mapOf(
                "session-a" to listOf(AssistantMessage(1, AssistantRole.USER, "from a")),
                "session-b" to listOf(AssistantMessage(2, AssistantRole.USER, "from b")),
            )
        }
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {}, ioDispatcher = dispatcher)
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
    fun quotedReplyPrefixesPromptAndShowsInUserMessage() = runTest(dispatcher) {
        val runtime = FakeRuntime()
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {}, ioDispatcher = dispatcher)
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
        private val flow = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
        val runId = "run-1"
        override val events: SharedFlow<RuntimeEvent> = flow
        var sessionId: String = "session-1"
        override val currentSessionId: String get() = sessionId
        override val currentSessionFile: String get() = "$sessionId.jsonl"
        override var busy: Boolean = false
        var sessions: List<ConversationSummary> = emptyList()
        var histories: Map<String, List<AssistantMessage>> = emptyMap()
        val openCalls = mutableListOf<String>()
        val sent = mutableListOf<String>()
        override suspend fun start() = Unit
        override suspend fun ensureStarted() = Unit
        override suspend fun send(text: String) {
            sent += text
        }

        override fun stop() = Unit
        override suspend fun newChat() = Unit
        override suspend fun open(filePath: String) {
            openCalls += filePath
            sessions.firstOrNull { it.filePath == filePath }?.let { sessionId = it.id }
        }

        override suspend fun history(): List<AssistantMessage> = histories[sessionId].orEmpty()
        override suspend fun sessions(includeArchived: Boolean): List<ConversationSummary> =
            if (includeArchived) sessions else sessions.filter { !it.archived }

        override suspend fun renameSession(id: String, title: String) = Unit
        override suspend fun setSessionPinned(id: String, pinned: Boolean) = Unit
        override suspend fun setSessionArchived(id: String, archived: Boolean) = Unit
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) = Unit
        override suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) = Unit
        override suspend fun currentConfig(): ModelConfig = ModelConfig()
        suspend fun emit(event: RuntimeEvent) = flow.emit(event)
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

    private class FakeSettings : ModelSettings {
        override val config: Flow<ModelConfig> = MutableStateFlow(ModelConfig())
        override suspend fun read(): ModelConfig = ModelConfig()
        override suspend fun save(config: ModelConfig) = Unit
    }
}
