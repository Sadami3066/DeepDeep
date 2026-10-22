package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
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
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {})
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
        val viewModel = ChatViewModel(runtime, FakeProviders(), FakeSettings(), prepareRuntime = {})
        advanceUntilIdle()
        viewModel.onInputChange("hello")
        viewModel.send()
        runtime.emit(RuntimeEvent.RunStarted("run-1", 1000L))
        runtime.emit(RuntimeEvent.TextDelta("run-2", "stale"))
        runtime.emit(RuntimeEvent.TextDelta("run-1", "fresh"))
        advanceUntilIdle()
        assertEquals("fresh", viewModel.state.value.messages.last().text)
    }

    private class FakeRuntime : AgentRuntime {
        private val flow = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
        val runId = "run-1"
        override val events: SharedFlow<RuntimeEvent> = flow
        override val currentSessionId: String = "session-1"
        override val currentSessionFile: String = "session-1.jsonl"
        override var busy: Boolean = false
        override suspend fun start() = Unit
        override suspend fun ensureStarted() = Unit
        override suspend fun send(text: String) = Unit
        override fun stop() = Unit
        override suspend fun newChat() = Unit
        override suspend fun open(filePath: String) = Unit
        override suspend fun history(): List<AssistantMessage> = emptyList()
        override suspend fun sessions(includeArchived: Boolean): List<ConversationSummary> = emptyList()
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
