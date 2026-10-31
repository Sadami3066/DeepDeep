package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.state.SessionProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantOverlayError { StartFailed, SendFailed, SwitchFailed }

data class AssistantOverlayState(
    val conversation: ConversationState = ConversationState(),
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val conversationCwd: String = "",
    val workspaceId: String = "",
    val workspaces: List<RuntimeWorkspace> = emptyList(),
    val sessions: List<ConversationSummary> = emptyList(),
    val providers: List<ProviderSummary> = emptyList(),
    val providerId: String = "",
    val modelId: String = "",
    val modelLabel: String = "",
    val models: List<ModelOption> = emptyList(),
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val thinkingLevels: List<ThinkingLevel> = listOf(ThinkingLevel.OFF),
    val error: AssistantOverlayError? = null,
)

class AssistantOverlayController(
    private val runtime: AgentRuntime,
    private val activeSessionId: () -> String?,
    private val homeWorkspaceId: String,
    private val scope: CoroutineScope,
    private val providers: ProviderDirectory,
    private val configuredProviders: () -> Set<String> = { emptySet() },
    private val defaultModel: suspend () -> ModelConfig = { ModelConfig() },
    private val onSessionSelected: (String) -> Unit = {},
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    strings: () -> ChatUiStrings = { ChatUiStrings.fallback() },
) {
    private val mutableState = MutableStateFlow(AssistantOverlayState())
    val state: StateFlow<AssistantOverlayState> = mutableState.asStateFlow()
    private val projection = SessionProjection(runtime, scope, ioDispatcher, strings)

    private var startJob: Job? = null
    private var stateJob: Job? = null

    fun start() {
        if (startJob != null) return
        startJob = scope.launch {
            runCatching { runtime.ensureStarted() }
            val sessionId = openInitialSession()
            if (sessionId == null) {
                mutableState.value = AssistantOverlayState(error = AssistantOverlayError.StartFailed)
                startJob = null
                return@launch
            }
            if (runCatching { projection.open(sessionId) }.isFailure) {
                mutableState.value = AssistantOverlayState(error = AssistantOverlayError.StartFailed)
                startJob = null
                return@launch
            }
            mutableState.value = AssistantOverlayState(
                conversation = projection.state.value,
                sessionId = sessionId,
            )
            observeProjection()
            loadMeta(sessionId)
        }
    }

    fun send(text: String, followUp: Boolean = false): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty()) return false
        val current = mutableState.value
        val sessionId = current.sessionId ?: return false
        if (!current.conversation.loaded) return false
        mutableState.update { it.copy(error = null) }
        if (isBusy(sessionId, current)) {
            enqueue(sessionId, prompt, followUp)
            return true
        }
        projection.sendTurn(prompt)
        return true
    }

    fun stop() {
        val sessionId = mutableState.value.sessionId ?: return
        runtime.stop(sessionId)
    }

    fun retry(messageId: Long) {
        val tail = mutableState.value.conversation.messages.lastOrNull() ?: return
        if (tail.id != messageId) return
        if (tail.role != AssistantRole.ASSISTANT || tail.status != MessageStatus.FAILED) return
        projection.retryLast()
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        projection.toggleStep(messageId, stepId)
    }

    fun openSession(sessionId: String) {
        if (sessionId == mutableState.value.sessionId) return
        scope.launch {
            val opened = runCatching {
                runtime.allSessions().firstOrNull { it.id == sessionId }
                    ?.let { runtime.openSession(it.filePath, it.cwd) }
            }.getOrNull()
            val target = opened?.sessionId ?: sessionId
            if (runCatching { projection.open(target) }.isFailure) {
                setError(AssistantOverlayError.StartFailed)
                return@launch
            }
            onSessionSelected(target)
            mutableState.update {
                it.copy(
                    conversation = projection.state.value,
                    sessionId = target,
                    sessionTitle = "",
                    error = null,
                )
            }
            loadMeta(target)
        }
    }

    fun newSession() {
        scope.launch {
            val workspaceId = mutableState.value.workspaceId.ifBlank { homeWorkspaceId }
            val opened = runCatching { runtime.newSession(workspaceId) }.getOrNull()
            if (opened == null) {
                setError(AssistantOverlayError.StartFailed)
                return@launch
            }
            if (runCatching { projection.open(opened.sessionId) }.isFailure) {
                setError(AssistantOverlayError.StartFailed)
                return@launch
            }
            onSessionSelected(opened.sessionId)
            mutableState.update {
                it.copy(
                    conversation = projection.state.value,
                    sessionId = opened.sessionId,
                    sessionTitle = "",
                    error = null,
                )
            }
            loadMeta(opened.sessionId)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        scope.launch {
            val sessions = runCatching { runtime.sessions(workspaceId) }.getOrDefault(emptyList())
            mutableState.update { it.copy(workspaceId = workspaceId, sessions = sessions) }
        }
    }

    fun refreshLists() {
        scope.launch {
            val workspaces = runCatching { runtime.workspaces() }.getOrDefault(emptyList())
            val workspaceId = mutableState.value.workspaceId.ifBlank { homeWorkspaceId }
            val sessions = runCatching { runtime.sessions(workspaceId) }.getOrDefault(emptyList())
            mutableState.update { it.copy(workspaces = workspaces, sessions = sessions) }
        }
    }

    fun selectProvider(providerId: String) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        val options = runCatching { providers.models(providerId) }.getOrDefault(emptyList())
        if (options.isEmpty()) return
        val model = options.firstOrNull { it.id == current.modelId } ?: options.first()
        val thinking = current.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        applyModel(sessionId, providerId, model.id, thinking)
    }

    fun selectModel(modelId: String) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val providerId = current.providerId.ifBlank { option.providerId }
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        applyModel(sessionId, providerId, option.id, thinking)
    }

    fun selectThinking(level: ThinkingLevel) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        if (level == current.thinkingLevel || level !in current.thinkingLevels) return
        mutableState.update { it.copy(error = null) }
        scope.launch {
            try {
                runtime.setSessionThinkingLevel(sessionId, level)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(AssistantOverlayError.SwitchFailed)
                return@launch
            }
            applyModelTruth(sessionId)
        }
    }

    fun clearQueue(): List<String> {
        val sessionId = mutableState.value.sessionId ?: return emptyList()
        return runCatching { runtime.clearQueuedInputs(sessionId) }.getOrDefault(emptyList())
    }

    fun reset() {
        startJob?.cancel()
        startJob = null
        stateJob?.cancel()
        stateJob = null
        projection.close()
        mutableState.value = AssistantOverlayState()
    }

    private fun observeProjection() {
        stateJob?.cancel()
        stateJob = scope.launch {
            var wasSending = projection.state.value.sending
            projection.state.collect { conversation ->
                mutableState.update { it.copy(conversation = conversation) }
                if (wasSending && !conversation.sending) {
                    mutableState.value.sessionId?.let(::loadMeta)
                }
                wasSending = conversation.sending
            }
        }
    }

    private fun loadMeta(sessionId: String) {
        scope.launch {
            val workspaces = runCatching { runtime.workspaces() }.getOrDefault(emptyList())
            val summaries = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
            val summary = summaries.firstOrNull { it.id == sessionId }
            val workspaceId = summary
                ?.let { owner -> workspaces.firstOrNull { it.cwd == owner.cwd }?.id }
                ?: homeWorkspaceId
            val sessions = runCatching { runtime.sessions(workspaceId) }.getOrDefault(emptyList())
            val title = runCatching { runtime.sessionTitle(sessionId) }.getOrNull().orEmpty()
            val cwd = summary?.cwd.orEmpty()
            val model = modelSnapshot(sessionId)
            val providerList = usableProviders()
            mutableState.update { current ->
                if (current.sessionId != sessionId) {
                    current
                } else {
                    current.copy(
                        sessionTitle = title,
                        conversationCwd = cwd,
                        workspaceId = workspaceId,
                        workspaces = workspaces,
                        sessions = sessions,
                        providers = providerList,
                        providerId = model.providerId,
                        modelId = model.modelId,
                        modelLabel = model.modelLabel,
                        models = model.models,
                        thinkingLevel = model.thinkingLevel,
                        thinkingLevels = model.thinkingLevels,
                    )
                }
            }
        }
    }

    private suspend fun modelSnapshot(sessionId: String): ModelView {
        val snapshot = runCatching { runtime.sessionModel(sessionId) }.getOrNull()
            ?: defaultModel().let { SessionModelSnapshot(it.providerId, it.modelId, it.thinkingLevel) }
        val options = runCatching { providers.models(snapshot.providerId) }.getOrDefault(emptyList())
        val option = options.firstOrNull { it.id == snapshot.modelId }
        val levels = option?.thinkingLevels?.takeIf { it.isNotEmpty() } ?: listOf(ThinkingLevel.OFF)
        return ModelView(
            providerId = snapshot.providerId,
            modelId = snapshot.modelId,
            modelLabel = option?.displayName ?: snapshot.modelId,
            models = options,
            thinkingLevel = snapshot.thinkingLevel.takeIf { it in levels } ?: ThinkingLevel.OFF,
            thinkingLevels = levels,
        )
    }

    private fun applyModel(sessionId: String, providerId: String, modelId: String, thinking: ThinkingLevel) {
        mutableState.update { it.copy(error = null) }
        scope.launch {
            try {
                runtime.setSessionModel(sessionId, providerId, modelId, thinking)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(AssistantOverlayError.SwitchFailed)
                return@launch
            }
            applyModelTruth(sessionId)
        }
    }

    private suspend fun applyModelTruth(sessionId: String) {
        val model = modelSnapshot(sessionId)
        mutableState.update { current ->
            if (current.sessionId != sessionId) {
                current
            } else {
                current.copy(
                    providerId = model.providerId,
                    modelId = model.modelId,
                    modelLabel = model.modelLabel,
                    models = model.models,
                    thinkingLevel = model.thinkingLevel,
                    thinkingLevels = model.thinkingLevels,
                )
            }
        }
    }

    private fun enqueue(sessionId: String, prompt: String, followUp: Boolean) {
        scope.launch {
            try {
                if (followUp) runtime.followUp(sessionId, prompt) else runtime.steer(sessionId, prompt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(AssistantOverlayError.SendFailed)
            }
        }
    }

    private fun isBusy(sessionId: String, current: AssistantOverlayState): Boolean =
        current.conversation.sending || runtime.activeRuns.value.containsKey(sessionId)

    private fun setError(error: AssistantOverlayError) {
        mutableState.update { it.copy(error = error) }
    }

    private fun usableProviders(): List<ProviderSummary> {
        val configured = configuredProviders()
        return if (configured.isEmpty()) {
            providers.summaries
        } else {
            providers.summaries.filter { it.id in configured }
        }
    }

    private suspend fun openInitialSession(): String? {
        val summaries = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
        val activeRun = runtime.activeRuns.value.maxByOrNull { it.value.startedAt }?.key
        val preferred = listOfNotNull(activeRun, activeSessionId()?.takeIf { it.isNotBlank() })
        for (id in preferred) {
            val summary = summaries.firstOrNull { it.id == id }
            if (summary == null) {
                onSessionSelected(id)
                return id
            }
            val opened = runCatching { runtime.openSession(summary.filePath, summary.cwd) }.getOrNull()
            onSessionSelected(opened?.sessionId ?: summary.id)
            return opened?.sessionId ?: summary.id
        }
        val latest = summaries.maxByOrNull { it.updatedAt }
        if (latest != null) {
            val opened = runCatching { runtime.openSession(latest.filePath, latest.cwd) }.getOrNull()
            onSessionSelected(opened?.sessionId ?: latest.id)
            return opened?.sessionId ?: latest.id
        }
        return runCatching { runtime.newSession(homeWorkspaceId).sessionId }.getOrNull()
            ?.also(onSessionSelected)
    }

    private data class ModelView(
        val providerId: String,
        val modelId: String,
        val modelLabel: String,
        val models: List<ModelOption>,
        val thinkingLevel: ThinkingLevel,
        val thinkingLevels: List<ThinkingLevel>,
    )
}
