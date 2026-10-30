package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.state.SessionProjection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AssistantOverlayError { Busy, SendFailed }

data class AssistantOverlayState(
    val conversation: ConversationState = ConversationState(),
    val sessionId: String? = null,
    val error: AssistantOverlayError? = null,
)

class AssistantOverlayController(
    private val runtime: AgentRuntime,
    private val activeSessionId: () -> String?,
    private val homeWorkspaceId: String,
    private val scope: CoroutineScope,
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
            val sessionId = resolveSession()
            if (sessionId == null) {
                mutableState.value = AssistantOverlayState(error = AssistantOverlayError.SendFailed)
                startJob = null
                return@launch
            }
            val opened = runCatching { projection.open(sessionId) }.isSuccess
            if (!opened) {
                mutableState.value = AssistantOverlayState(error = AssistantOverlayError.SendFailed)
                startJob = null
                return@launch
            }
            mutableState.value = AssistantOverlayState(
                conversation = projection.state.value,
                sessionId = sessionId,
            )
            stateJob?.cancel()
            stateJob = scope.launch {
                projection.state.collect { conversation ->
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        conversation = conversation,
                        error = if (!conversation.sending && current.error == AssistantOverlayError.Busy) {
                            null
                        } else {
                            current.error
                        },
                    )
                }
            }
        }
    }

    fun ask(text: String): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty()) return false
        val current = mutableState.value
        val sessionId = current.sessionId ?: return false
        if (current.conversation.sending || runtime.activeRuns.value.containsKey(sessionId)) {
            mutableState.value = current.copy(error = AssistantOverlayError.Busy)
            return false
        }
        mutableState.value = current.copy(error = null)
        projection.ask(prompt)
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

    fun reset() {
        startJob?.cancel()
        startJob = null
        stateJob?.cancel()
        stateJob = null
        projection.close()
        mutableState.value = AssistantOverlayState()
    }

    private suspend fun resolveSession(): String? {
        activeSessionId()?.takeIf { it.isNotBlank() }?.let { return it }
        val latest = runCatching { runtime.allSessions() }.getOrNull()
            ?.maxByOrNull { it.updatedAt }
        if (latest != null) {
            runCatching { runtime.openSession(latest.filePath, latest.cwd) }
                .getOrNull()
                ?.let { return it.sessionId }
        }
        return runCatching { runtime.newSession(homeWorkspaceId).sessionId }.getOrNull()
    }
}
