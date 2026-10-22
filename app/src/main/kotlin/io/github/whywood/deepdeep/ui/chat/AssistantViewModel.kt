package io.github.whywood.deepdeep.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val host: AgentRuntimeHost,
    private val modelConfigStore: ModelConfigStore,
    private val prepareRuntime: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    private var nextId = 0L
    private var nextStepId = 0L
    private var activeAssistantId: Long? = null

    init {
        viewModelScope.launch {
            host.events.collect { event -> handle(event) }
        }
        viewModelScope.launch {
            try {
                prepareRuntime()
                host.ensureStarted()
                val config = modelConfigStore.read()
                mutableState.update { it.copy(thinkingLevel = config.thinkingLevel) }
                loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(historyLoaded = true, error = userFacingError(error.message ?: "运行时启动失败"))
                }
            }
        }
    }

    fun onInputChange(value: String) {
        mutableState.update { it.copy(input = value, error = null) }
    }

    fun send() {
        val current = mutableState.value
        if (!current.historyLoaded || current.sending) return
        val text = current.input.trim()
        if (text.isEmpty()) return
        val userMessage = AssistantMessage(nextId++, AssistantRole.USER, text)
        val assistantId = nextId++
        activeAssistantId = assistantId
        Log.i(TAG, "send id=$assistantId chars=${text.length}")
        mutableState.update {
            it.copy(
                messages = it.messages + userMessage + AssistantMessage(
                    id = assistantId,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = true,
                ),
                input = "",
                sending = true,
                error = null,
                conversationTitle = it.conversationTitle.ifBlank { titleFrom(text) },
            )
        }
        viewModelScope.launch {
            try {
                host.send(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishWithError(error.message ?: "请求失败，请稍后重试。")
            }
        }
    }

    fun stop() {
        host.stop()
    }

    fun newChat() {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            try {
                host.newChat()
                activeAssistantId = null
                nextId = 0
                nextStepId = 0
                mutableState.update {
                    AssistantUiState(
                        modelOverride = it.modelOverride,
                        thinkingLevel = it.thinkingLevel,
                        historyLoaded = true,
                    )
                }
                loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: "新建会话失败")) }
            }
        }
    }

    fun openConversation(id: String) {
        if (mutableState.value.sending) return
        val summary = mutableState.value.conversations.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                host.open(summary.filePath)
                activeAssistantId = null
                nextId = 0
                nextStepId = 0
                mutableState.update {
                    AssistantUiState(
                        modelOverride = it.modelOverride,
                        thinkingLevel = it.thinkingLevel,
                        conversationId = id,
                        conversationTitle = summary.title,
                        historyLoaded = true,
                    )
                }
                loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: "打开会话失败")) }
            }
        }
    }

    fun setModelOverride(model: String?) {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            try {
                val config = modelConfigStore.read()
                val target = model ?: config.modelId
                host.setModel(target, mutableState.value.thinkingLevel)
                mutableState.update { it.copy(modelOverride = model, error = null) }
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
                host.setThinkingLevel(level)
                mutableState.update { it.copy(thinkingLevel = level, error = null) }
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

    private suspend fun loadHistory() {
        val messages = host.history()
        val sessions = host.sessions()
        val currentFile = host.currentSessionFile
        val summary = sessions.firstOrNull { it.filePath == currentFile }
        nextId = (messages.maxOfOrNull { it.id } ?: -1L) + 1
        nextStepId = (messages.flatMap { it.steps }.maxOfOrNull { it.id } ?: -1L) + 1
        mutableState.update {
            it.copy(
                messages = messages,
                conversations = sessions,
                conversationId = summary?.id ?: it.conversationId,
                conversationTitle = summary?.title ?: titleFrom(messages),
                historyLoaded = true,
            )
        }
    }

    private suspend fun refreshSessions() {
        val sessions = host.sessions()
        val currentFile = host.currentSessionFile
        val summary = sessions.firstOrNull { it.filePath == currentFile }
        mutableState.update {
            it.copy(
                conversations = sessions,
                conversationId = summary?.id ?: it.conversationId,
                conversationTitle = summary?.title ?: it.conversationTitle,
            )
        }
    }

    private fun handle(event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.TextDelta -> updateAssistant { message ->
                message.copy(text = message.text + event.text)
            }

            is RuntimeEvent.ReasoningDelta -> updateAssistant { message ->
                val steps = message.steps.toMutableList()
                val last = steps.lastOrNull()
                if (last is AssistantStep.Reasoning) {
                    steps[steps.lastIndex] = last.copy(text = last.text + event.text)
                } else {
                    steps += AssistantStep.Reasoning(nextStepId++, event.text)
                }
                message.copy(steps = steps)
            }

            is RuntimeEvent.ToolStarted -> updateAssistant { message ->
                message.copy(
                    steps = message.steps + AssistantStep.Tool(
                        ToolTrace(
                            id = nextStepId++,
                            name = event.toolName,
                            arguments = event.arguments,
                            toolCallId = event.toolCallId,
                        ),
                    ),
                )
            }

            is RuntimeEvent.ToolFinished -> updateAssistant { message ->
                val index = message.steps.indexOfLast { step ->
                    step is AssistantStep.Tool && step.trace.toolCallId == event.toolCallId
                }
                if (index == -1) {
                    message.copy(
                        steps = message.steps + AssistantStep.Tool(
                            ToolTrace(
                                id = nextStepId++,
                                name = event.toolName,
                                arguments = emptyMap(),
                                status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                                output = event.result.takeIf { !event.isError && it.isNotBlank() },
                                error = event.result.takeIf { event.isError && it.isNotBlank() },
                                toolCallId = event.toolCallId,
                            ),
                        ),
                    )
                } else {
                    val step = message.steps[index] as AssistantStep.Tool
                    val updated = step.trace.copy(
                        status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                        output = event.result.takeIf { !event.isError && it.isNotBlank() },
                        error = event.result.takeIf { event.isError && it.isNotBlank() },
                    )
                    message.copy(
                        steps = message.steps.toMutableList().also { it[index] = AssistantStep.Tool(updated) },
                    )
                }
            }

            is RuntimeEvent.RunFinished -> {
                val id = terminalMessageId()
                Log.i(TAG, "RunFinished active=$activeAssistantId terminal=$id chars=${event.text.length}")
                if (id != null) {
                    updateAssistant(id) { message ->
                        message.copy(
                            text = event.text.ifBlank { message.text }.ifBlank { EMPTY_REPLY },
                            streaming = false,
                        )
                    }
                }
                activeAssistantId = null
                mutableState.update { it.copy(sending = false, error = null) }
                viewModelScope.launch { refreshSessions() }
            }

            RuntimeEvent.RunStopped -> {
                val id = terminalMessageId()
                Log.i(TAG, "RunStopped active=$activeAssistantId terminal=$id")
                if (id != null) {
                    updateAssistant(id) { message ->
                        message.copy(
                            text = if (message.text.isBlank()) {
                                STOPPED_MARKER
                            } else if (message.text.endsWith(STOPPED_MARKER)) {
                                message.text
                            } else {
                                message.text + "\n\n" + STOPPED_MARKER
                            },
                            streaming = false,
                            steps = message.steps.map { step ->
                                if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                                    step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                                } else {
                                    step
                                }
                            },
                        )
                    }
                }
                activeAssistantId = null
                mutableState.update { it.copy(sending = false) }
                viewModelScope.launch { refreshSessions() }
            }

            is RuntimeEvent.RunFailed -> finishWithError(event.message)
            is RuntimeEvent.Status -> Unit
        }
    }

    private fun finishWithError(message: String) {
        val id = terminalMessageId()
        if (id != null) {
            updateAssistant(id) { assistant ->
                assistant.copy(
                    text = assistant.text.ifBlank { NO_REPLY_MARKER },
                    streaming = false,
                    steps = assistant.steps.map { step ->
                        if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                            step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                        } else {
                            step
                        }
                    },
                )
            }
        }
        activeAssistantId = null
        mutableState.update { it.copy(sending = false, error = userFacingError(message)) }
        viewModelScope.launch { refreshSessions() }
    }

    private fun terminalMessageId(): Long? = activeAssistantId
        ?: mutableState.value.messages.lastOrNull { it.role == AssistantRole.ASSISTANT && it.streaming }?.id

    private fun updateAssistant(transform: (AssistantMessage) -> AssistantMessage) {
        val id = activeAssistantId ?: return
        updateAssistant(id, transform)
    }

    private fun updateAssistant(id: Long, transform: (AssistantMessage) -> AssistantMessage) {
        mutableState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == id) transform(message) else message
                },
            )
        }
    }

    private fun userFacingError(message: String): String = when {
        message.contains("API key", ignoreCase = true) || message.contains("401") -> "API Key 无效或已过期"
        message.contains("429") -> "请求太频繁，请稍后再试"
        message.contains("timed out", ignoreCase = true) -> "请求超时，请检查网络"
        message.contains("Network", ignoreCase = true) -> "无法连接模型服务，请检查网络"
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

private const val TAG = "DeepDeepChat"
