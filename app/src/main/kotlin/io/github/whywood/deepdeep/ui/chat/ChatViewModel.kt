package io.github.whywood.deepdeep.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val runtime: AgentRuntime,
    private val providers: ProviderDirectory,
    private val modelConfigStore: ModelSettings,
    private val prepareRuntime: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

    private var nextMessageId = 0L
    private var nextStepId = 0L
    private var activeAssistantId: Long? = null
    private var activeRunId: String? = null

    init {
        viewModelScope.launch { runtime.events.collect(::handle) }
        viewModelScope.launch {
            modelConfigStore.config.collect { applyConfig(it) }
        }
        viewModelScope.launch {
            try {
                prepareRuntime()
                withContext(ioDispatcher) { runtime.ensureStarted() }
                val config = providers.resolveConfig(modelConfigStore.read())
                modelConfigStore.save(config)
                applyConfig(config)
                loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(ready = true, historyLoaded = true, error = userFacingError(error.message ?: "运行时启动失败"))
                }
            }
        }
    }

    fun onInputChange(value: String) {
        mutableState.update { it.copy(input = value, error = null) }
    }

    fun send() {
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) {
            mutableState.update { it.copy(error = "正在恢复会话，请稍候…") }
            return
        }
        if (current.sending || current.switchingConversation) return
        val text = current.input.trim()
        if (text.isEmpty()) return
        val userMessage = AssistantMessage(nextMessageId++, AssistantRole.USER, text)
        val assistantId = nextMessageId++
        activeAssistantId = assistantId
        activeRunId = null
        val now = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                messages = it.messages + userMessage + AssistantMessage(
                    id = assistantId,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    startedAt = now,
                ),
                input = "",
                sending = true,
                activity = "思考中",
                activityStartedAt = now,
                error = null,
                conversationTitle = it.conversationTitle.ifBlank { titleFrom(text) },
            )
        }
        viewModelScope.launch {
            try {
                runtime.send(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishWithError(activeRunId, error.message ?: "请求失败，请稍后重试。")
            }
        }
    }

    fun stop() {
        runtime.stop()
    }

    fun newChat() {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { runtime.newChat() }
                resetForSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: "新建会话失败")) }
            }
        }
    }

    fun openConversation(id: String) {
        val current = mutableState.value
        if (current.sending || current.switchingConversation) return
        if (current.historyLoaded && current.conversationId == id) return
        val summary = (current.conversations + current.archivedConversations)
            .firstOrNull { it.id == id } ?: return
        activeAssistantId = null
        activeRunId = null
        viewModelScope.launch {
            mutableState.update { it.copy(switchingConversation = true, error = null) }
            try {
                withContext(ioDispatcher) { runtime.open(summary.filePath) }
                loadHistory(minimumSwitchMillis = 220L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        switchingConversation = false,
                        error = userFacingError(error.message ?: "打开会话失败"),
                    )
                }
            }
        }
    }

    fun setModel(modelId: String) {
        if (mutableState.value.sending) return
        val current = mutableState.value
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        viewModelScope.launch {
            try {
                runtime.setModel(current.providerId, option.id, thinking)
                applyConfig(ModelConfig(current.providerId, option.id, thinking))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: "切换模型失败")) }
            }
        }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            try {
                runtime.setThinkingLevel(level)
                mutableState.update { it.copy(thinkingLevel = level) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: "切换思考档位失败")) }
            }
        }
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        mutableState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    message.copy(
                        steps = message.steps.map { step ->
                            when {
                                step is AssistantStep.Reasoning && step.id == stepId ->
                                    step.copy(expanded = !step.expanded)

                                step is AssistantStep.Tool && step.trace.id == stepId ->
                                    step.copy(trace = step.trace.copy(expanded = !step.trace.expanded))

                                else -> step
                            }
                        },
                    )
                },
            )
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch {
            runCatching { runtime.renameSession(id, title) }
            refreshSessions()
        }
    }

    fun setSessionPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.setSessionPinned(id, pinned) }
            refreshSessions()
        }
    }

    fun setSessionArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.setSessionArchived(id, archived) }
            refreshSessions()
        }
    }

    fun deleteSession(id: String) {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            val wasActive = mutableState.value.conversationId == id
            runCatching { runtime.deleteSession(id) }
            if (wasActive) {
                resetForSession()
            } else {
                refreshSessions()
            }
        }
    }

    private suspend fun loadHistory(minimumSwitchMillis: Long = 0L) {
        val startedAt = System.currentTimeMillis()
        val messages = runtime.history()
        val sessions = runtime.sessions(includeArchived = false)
        val allSessions = runtime.sessions(includeArchived = true)
        val currentId = runtime.currentSessionId
        val summary = allSessions.firstOrNull { it.id == currentId }
        val remaining = minimumSwitchMillis - (System.currentTimeMillis() - startedAt)
        if (remaining > 0L) delay(remaining)
        nextMessageId = (messages.maxOfOrNull { it.id } ?: -1L) + 1
        nextStepId = (messages.flatMap { it.steps }.maxOfOrNull { it.id } ?: -1L) + 1
        mutableState.update {
            it.copy(
                ready = true,
                messages = messages,
                conversations = sessions,
                archivedConversations = allSessions.filter { item -> item.archived },
                archivedCount = allSessions.count { item -> item.archived },
                conversationId = summary?.id ?: currentId,
                conversationTitle = summary?.title ?: titleFrom(messages),
                historyLoaded = true,
                switchingConversation = false,
            )
        }
    }

    private suspend fun resetForSession(
        conversationId: String? = null,
        conversationTitle: String = "",
    ) {
        activeAssistantId = null
        activeRunId = null
        nextMessageId = 0
        nextStepId = 0
        mutableState.update {
            it.copy(
                messages = emptyList(),
                input = "",
                sending = false,
                activity = null,
                activityStartedAt = null,
                error = null,
                conversationId = conversationId,
                conversationTitle = conversationTitle,
            )
        }
        loadHistory()
    }

    private suspend fun refreshSessions() {
        val sessions = runtime.sessions(includeArchived = false)
        val allSessions = runtime.sessions(includeArchived = true)
        val currentId = runtime.currentSessionId
        val summary = allSessions.firstOrNull { it.id == currentId }
        mutableState.update {
            it.copy(
                conversations = sessions,
                archivedConversations = allSessions.filter { item -> item.archived },
                archivedCount = allSessions.count { item -> item.archived },
                conversationId = summary?.id ?: it.conversationId,
                conversationTitle = summary?.title ?: it.conversationTitle,
            )
        }
    }

    private fun applyConfig(config: ModelConfig) {
        val options = providers.models(config.providerId)
        val model = options.firstOrNull { it.id == config.modelId } ?: options.firstOrNull()
        mutableState.update {
            it.copy(
                providerId = config.providerId,
                modelId = model?.id ?: config.modelId,
                modelLabel = model?.displayName ?: config.modelId,
                thinkingLevel = config.thinkingLevel,
                models = options,
            )
        }
    }

    private fun handle(event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.RunStarted -> {
                activeRunId = event.runId
                val id = activeAssistantId ?: return
                mutableState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == id) message.copy(runId = event.runId) else message
                        },
                        sending = true,
                        activity = "思考中",
                        activityStartedAt = event.startedAt,
                    )
                }
            }

            is RuntimeEvent.ActivityChanged -> {
                if (!matchesRun(event.runId)) return
                mutableState.update {
                    it.copy(
                        activity = event.activity,
                        activityStartedAt = if (event.activity == null) null else (it.activityStartedAt ?: System.currentTimeMillis()),
                    )
                }
            }

            is RuntimeEvent.TextDelta -> {
                if (!matchesRun(event.runId)) return
                updateAssistant { message ->
                    val steps = message.steps.toMutableList()
                    val last = steps.lastOrNull()
                    if (last is AssistantStep.Text) {
                        steps[steps.lastIndex] = last.copy(text = last.text + event.text)
                    } else {
                        steps += AssistantStep.Text(nextStepId++, event.text)
                    }
                    message.copy(steps = steps)
                }
            }

            is RuntimeEvent.ReasoningDelta -> {
                if (!matchesRun(event.runId)) return
                updateAssistant { message ->
                    val steps = message.steps.toMutableList()
                    val last = steps.lastOrNull()
                    if (last is AssistantStep.Reasoning) {
                        steps[steps.lastIndex] = last.copy(text = last.text + event.text)
                    } else {
                        steps += AssistantStep.Reasoning(nextStepId++, event.text)
                    }
                    message.copy(steps = steps)
                }
            }

            is RuntimeEvent.ToolStarted -> {
                if (!matchesRun(event.runId)) return
                updateAssistant { message ->
                    message.copy(
                        steps = message.steps + AssistantStep.Tool(
                            ToolTrace(
                                id = nextStepId++,
                                name = event.name,
                                arguments = event.arguments,
                                toolCallId = event.callId,
                                startedAt = event.startedAt,
                            ),
                        ),
                    )
                }
            }

            is RuntimeEvent.ToolFinished -> {
                if (!matchesRun(event.runId)) return
                updateAssistant { message ->
                    val index = message.steps.indexOfLast { step ->
                        step is AssistantStep.Tool && step.trace.toolCallId == event.callId
                    }
                    if (index == -1) {
                        message.copy(
                            steps = message.steps + AssistantStep.Tool(
                                ToolTrace(
                                    id = nextStepId++,
                                    name = event.name,
                                    arguments = emptyMap(),
                                    status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                                    output = event.result.takeIf { !event.isError && it.isNotBlank() },
                                    error = event.result.takeIf { event.isError && it.isNotBlank() },
                                    toolCallId = event.callId,
                                    durationMs = event.durationMs,
                                ),
                            ),
                        )
                    } else {
                        val trace = (message.steps[index] as AssistantStep.Tool).trace.copy(
                            status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                            output = event.result.takeIf { !event.isError && it.isNotBlank() },
                            error = event.result.takeIf { event.isError && it.isNotBlank() },
                            durationMs = event.durationMs,
                        )
                        message.copy(
                            steps = message.steps.toMutableList().also { it[index] = AssistantStep.Tool(trace) },
                        )
                    }
                }
            }

            is RuntimeEvent.RunFinished -> {
                if (!matchesRun(event.runId)) return
                finishWithStatus(event.text, event.status, event.finishedAt)
            }

            is RuntimeEvent.RunFailed -> {
                if (!matchesRun(event.runId)) return
                finishWithError(event.runId, event.message)
            }

            is RuntimeEvent.SessionListChanged -> {
                viewModelScope.launch { refreshSessions() }
            }
        }
    }

    private fun matchesRun(runId: String): Boolean = activeRunId == runId

    private fun updateAssistant(transform: (AssistantMessage) -> AssistantMessage) {
        val id = activeAssistantId ?: return
        mutableState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun finishWithStatus(text: String, status: RunStatus, finishedAt: Long) {
        val id = activeAssistantId
        if (id != null) {
            mutableState.update { state ->
                state.copy(
                    messages = state.messages.map { message ->
                        if (message.id != id) return@map message
                        val finalText = text.ifBlank { message.text }
                        message.copy(
                            text = finalText,
                            streaming = false,
                            status = when (status) {
                                RunStatus.DONE -> MessageStatus.DONE
                                RunStatus.STOPPED -> MessageStatus.STOPPED
                                RunStatus.FAILED -> MessageStatus.FAILED
                            },
                            finishedAt = finishedAt,
                            steps = consumeFinalText(
                                message.steps.map { step ->
                                    if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                                        step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                                    } else {
                                        step
                                    }
                                },
                                finalText,
                            ),
                        )
                    },
                    sending = false,
                    activity = null,
                    activityStartedAt = null,
                )
            }
        } else {
            mutableState.update { it.copy(sending = false, activity = null, activityStartedAt = null) }
        }
        activeAssistantId = null
        activeRunId = null
        viewModelScope.launch { refreshSessions() }
    }

    private fun finishWithError(runId: String?, message: String) {
        if (runId != null && !matchesRun(runId)) return
        val id = activeAssistantId
        if (id != null) {
            mutableState.update { state ->
                state.copy(
                    messages = state.messages.map { item ->
                        if (item.id != id) return@map item
                        item.copy(
                            text = item.text.ifBlank { "（没有收到回复）" },
                            streaming = false,
                            status = MessageStatus.FAILED,
                            finishedAt = System.currentTimeMillis(),
                            steps = item.steps.map { step ->
                                if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                                    step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                                } else {
                                    step
                                }
                            },
                        )
                    },
                    sending = false,
                    activity = null,
                    activityStartedAt = null,
                    error = userFacingError(message),
                )
            }
        } else {
            mutableState.update {
                it.copy(sending = false, activity = null, activityStartedAt = null, error = userFacingError(message))
            }
        }
        activeAssistantId = null
        activeRunId = null
        viewModelScope.launch { refreshSessions() }
    }

    private fun consumeFinalText(steps: List<AssistantStep>, finalText: String): List<AssistantStep> {
        val final = finalText.trim()
        if (final.isEmpty()) return steps
        val last = steps.lastOrNull() as? AssistantStep.Text ?: return steps
        val candidate = last.text.trim()
        return if (candidate.isNotEmpty() && (final == candidate || final.endsWith(candidate))) {
            steps.dropLast(1)
        } else {
            steps
        }
    }

    private fun userFacingError(message: String): String = when {
        message.contains("API key", ignoreCase = true) || message.contains("401") -> "API Key 无效或已过期"
        message.contains("429") -> "请求太频繁，请稍后再试"
        message.contains("timed out", ignoreCase = true) -> "请求超时，请检查网络"
        message.contains("Network", ignoreCase = true) -> "无法连接模型服务，请检查网络"
        message.contains("tool input", ignoreCase = true) || message.contains("invalid JSON", ignoreCase = true) ->
            "模型返回了无效的工具参数，请重试"

        message.contains("500") || message.contains("503") -> "模型服务暂时不可用"
        else -> message
    }

    private fun titleFrom(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private fun titleFrom(messages: List<AssistantMessage>): String {
        val firstUser = messages.firstOrNull { it.role == AssistantRole.USER }?.text ?: return "新对话"
        return titleFrom(firstUser)
    }

}
