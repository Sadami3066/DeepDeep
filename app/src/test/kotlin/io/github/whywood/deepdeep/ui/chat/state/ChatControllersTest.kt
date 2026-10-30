package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatControllersTest {
    private var state = ChatUiState(ready = true)
    private val updates = mutableListOf<ChatUiState>()
    private val strings = ChatUiStrings.fallback()

    private fun update(transform: (ChatUiState) -> ChatUiState) {
        state = transform(state)
        updates += state
    }

    private fun summary(id: String) = ConversationSummary(id, id, 1L, "$id.jsonl", HOME_CWD)

    @Test
    fun sessionListRefreshProjectsConversations() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(summary("session-1"), summary("session-2"))
        }
        var retained: Set<String>? = null
        var cached = false
        val controller = SessionListController(
            runtime = runtime, admin = runtime, ioDispatcher = dispatcher,
            state = { state }, update = ::update, strings = { strings },
            cacheCurrentSession = { cached = true }, retainCache = { retained = it },
            target = target(), applySessionModel = {}, isRetrying = { false }, localizeError = { it ?: "" },
        )

        controller.refresh()

        assertEquals(2, state.conversations.size)
        assertEquals(setOf("session-1", "session-2"), retained)
        assertTrue(cached)
        assertTrue(state.ready)
    }

    @Test
    fun sessionListSearchMapsWorkspaceNames() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply { sessions = listOf(summary("session-1")) }
        val controller = SessionListController(
            runtime = runtime, admin = runtime, ioDispatcher = dispatcher,
            state = { state }, update = ::update, strings = { strings },
            cacheCurrentSession = {}, retainCache = {},
            target = target(), applySessionModel = {}, isRetrying = { false }, localizeError = { it ?: "" },
        )

        controller.refresh()
        val hits = controller.search()

        assertEquals(1, hits.size)
        assertEquals("Home", hits.single().workspaceName)
        assertEquals("session-1", hits.single().conversation.id)
    }

    @Test
    fun workspaceCreateRefreshesAndSelects() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime()
        var selected: String? = null
        var refreshed = 0
        val controller = WorkspaceController(
            runtime = runtime, queries = runtime, ioDispatcher = dispatcher, scope = this,
            state = { state }, update = ::update,
            selectWorkspace = { selected = it }, refreshSessions = { refreshed += 1 },
            userFacingError = { it }, createWorkspaceFailedText = { "failed" },
        )

        controller.create("Team")
        advanceUntilIdle()

        assertNotNull(selected)
        assertTrue(state.workspaces.any { it.name == "Team" })
        assertEquals(0, refreshed)
    }

    @Test
    fun workspaceDeleteLastActiveSelectsHome() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime()
        val created = runtime.createWorkspace("Team")
        state = state.copy(currentWorkspaceId = created.id)
        var selected: String? = null
        val controller = WorkspaceController(
            runtime = runtime, queries = runtime, ioDispatcher = dispatcher, scope = this,
            state = { state }, update = ::update,
            selectWorkspace = { selected = it }, refreshSessions = {},
            userFacingError = { it }, createWorkspaceFailedText = { "failed" },
        )

        controller.delete(created.id)
        advanceUntilIdle()

        assertEquals(WorkspaceController.HOME_ID, selected)
    }

    @Test
    fun modelSelectionPersistsAndUpdatesState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            sessionModels = mutableMapOf()
        }
        state = state.copy(conversationId = "session-1", providerId = "p", modelId = "m1", models = FakeProviders.options)
        val settings = FakeSettings()
        val controller = ModelController(
            runtime = runtime, queries = runtime, providers = FakeProviders, settings = settings,
            ioDispatcher = dispatcher, scope = this, state = { state }, update = ::update,
            cacheCurrentSession = {}, userFacingError = { it },
            switchModelFailedText = { "model failed" }, switchThinkingFailedText = { "thinking failed" },
        )

        controller.setModel("m2")
        advanceUntilIdle()

        assertEquals("m2", state.modelId)
        assertEquals("m2", settings.saved.lastOrNull()?.modelId)
        assertEquals("m2", runtime.sessionModels["session-1"]?.modelId)
    }

    @Test
    fun thinkingLevelOnlyUpdatesThinking() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime()
        state = state.copy(
            conversationId = "session-1",
            providerId = "p",
            modelId = "m1",
            thinkingLevel = ThinkingLevel.OFF,
            models = FakeProviders.options,
        )
        val controller = ModelController(
            runtime = runtime, queries = runtime, providers = FakeProviders, settings = FakeSettings(),
            ioDispatcher = dispatcher, scope = this, state = { state }, update = ::update,
            cacheCurrentSession = {}, userFacingError = { it },
            switchModelFailedText = { "model failed" }, switchThinkingFailedText = { "thinking failed" },
        )

        controller.setThinkingLevel(ThinkingLevel.HIGH)
        advanceUntilIdle()

        assertEquals(ThinkingLevel.HIGH, state.thinkingLevel)
        assertEquals("m1", state.modelId)
    }

    @Test
    fun usageRefreshWritesActiveState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val usage = SessionUsage(1L, 2L, 3L, 4L, 5L, 6L, 0.5)
        val runtime = UsageRuntime(usage)
        val target = target("session-1")
        state = state.copy(conversationId = "session-1")
        val controller = UsageController(
            runtime = runtime, ioDispatcher = dispatcher, target = target,
            state = { state }, update = ::update, cacheCurrentSession = {},
            titleFrom = { "title" }, localizeError = { it ?: "" },
        )

        controller.refresh("session-1")

        assertEquals(usage, target.read("session-1").usage)
    }

    @Test
    fun compactionReloadKeepsNoticesAndSettlesMessages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.ASSISTANT, "streamed", streaming = true, status = MessageStatus.RUNNING),
                ),
            )
        }
        state = state.copy(conversationId = "session-1", sending = false)
        val controller = UsageController(
            runtime = runtime, ioDispatcher = dispatcher, target = target(),
            state = { state }, update = ::update, cacheCurrentSession = {},
            titleFrom = { "title" }, localizeError = { it ?: "" },
        )

        controller.reloadHistoryAfterCompaction("session-1")

        assertEquals(1, state.messages.size)
        assertTrue(state.historyLoaded)
        assertEquals("title", state.conversationTitle)
    }

    private var activeState = ConversationState()

    private fun target(active: String? = "session-1"): ConversationWriteTarget =
        ConversationWriteTarget(activeReader = { activeState }, activeWriter = { activeState = it })
            .apply { setActive(active) }

    private class UsageRuntime(private val usage: SessionUsage) : AgentRuntime by FakeAgentRuntime() {
        override suspend fun usage(sessionId: String): SessionUsage? = usage
    }

    private object FakeProviders : ProviderDirectory {
        val options = listOf(
            ModelOption("p", "m1", "Model 1", listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH)),
            ModelOption("p", "m2", "Model 2", listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH)),
        )
        override val summaries: List<ProviderSummary> =
            listOf(ProviderSummary(id = "p", name = "Provider", modelCount = 2, baseUrl = null, hasOAuth = false, supportsApiKey = true))

        override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }

        override fun models(providerId: String): List<ModelOption> = options.filter { it.providerId == providerId }

        override fun resolveConfig(config: ModelConfig): ModelConfig = config

        override fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model? = null

        override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
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
}
