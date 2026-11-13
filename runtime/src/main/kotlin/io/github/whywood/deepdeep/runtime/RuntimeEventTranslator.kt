package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.agent.AgentMessage
import io.github.whywood.deepdeep.sdk.ai.TextDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.ToolCallStartEvent
import io.github.whywood.deepdeep.sdk.ai.ThinkingDeltaEvent
import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject

internal class RuntimeEventTranslator(
    private val sdkEvents: MutableSharedFlow<SessionSdkEvent>,
    private val emit: (RuntimeHandle, RuntimeEvent) -> Unit,
    private val emitActivity: (RuntimeHandle, String, RuntimeActivity?) -> Unit,
    private val emitTerminal: suspend (RuntimeHandle, String, String, RunStatus, String?) -> Unit,
    private val emitUsage: suspend (RuntimeHandle, String) -> Unit,
    private val admitQueuedInput: (RuntimeHandle, String, AgentMessage) -> Unit,
) {
    suspend fun onSessionEvent(handle: RuntimeHandle, event: SessionEvent) {
        sdkEvents.tryEmit(SessionSdkEvent(handle.session.sessionId, event))
        val runId = handle.state.runId.orEmpty()
        val active = runId.isNotEmpty()
        when (event) {
            is SessionEvent.AgentEventReceived -> if (active) onAgentEvent(handle, runId, event.event)
            is SessionEvent.RetryScheduled -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Retrying(event.attempt))
            }

            is SessionEvent.RetrySucceeded -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Thinking)
            }

            is SessionEvent.RetryExhausted -> if (active) {
                emitTerminal(
                    handle,
                    runId,
                    handle.session.lastAssistantText().orEmpty(),
                    RunStatus.FAILED,
                    event.errorMessage.orEmpty(),
                )
            }

            is SessionEvent.CompactionStarted -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Compacting)
            }

            is SessionEvent.CompactionFinished -> {
                if (active) emitActivity(handle, runId, RuntimeActivity.Thinking)
                emit(handle, NoticeEvent(runId, TurnNotice.Compacted(event.tokensBefore, event.tokensAfter)))
                emitUsage(handle, runId)
            }

            is SessionEvent.CompactionFailed -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.CompactionFailed)
            }

            is SessionEvent.SessionError -> if (active) {
                emitTerminal(
                    handle,
                    runId,
                    handle.session.lastAssistantText().orEmpty(),
                    RunStatus.FAILED,
                    event.message,
                )
            }

            is SessionEvent.McpServerStatusChanged -> logInfo(
                TAG,
                "mcp status ${event.name}=${event.status.state.name}/${event.status.toolCount}/${event.status.lastError.orEmpty()}",
            )

            else -> Unit
        }
    }

    suspend fun onAgentEvent(handle: RuntimeHandle, runId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageStart -> {
                admitQueuedInput(handle, runId, event.message)
                emitActivity(handle, runId, RuntimeActivity.Thinking)
            }

            is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
                is TextDeltaEvent -> {
                    emit(handle, RuntimeEvent.TextDelta(runId, update.delta))
                    emitActivity(handle, runId, RuntimeActivity.Thinking)
                }

                is ThinkingDeltaEvent -> {
                    emit(handle, RuntimeEvent.ReasoningDelta(runId, update.delta))
                    emitActivity(handle, runId, RuntimeActivity.Thinking)
                }

                is ToolCallStartEvent -> {
                    val call = update.partial.content.getOrNull(update.contentIndex) as? ToolCall
                    if (call != null && call.id.isNotBlank() && call.name.isNotBlank()) {
                        emit(
                            handle,
                            RuntimeEvent.ToolCallStarted(
                                runId = runId,
                                callId = call.id,
                                name = call.name,
                                startedAt = System.currentTimeMillis(),
                            ),
                        )
                        emitActivity(handle, runId, RuntimeActivity.RunningTool(call.name))
                    }
                }

                else -> Unit
            }

            is AgentEvent.ToolExecutionStart -> {
                emit(
                    handle,
                    RuntimeEvent.ToolStarted(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        arguments = event.args.toDisplayArguments(),
                        startedAt = System.currentTimeMillis(),
                        rawArguments = event.args,
                    ),
                )
                emitActivity(handle, runId, RuntimeActivity.RunningTool(event.toolName))
            }

            is AgentEvent.ToolExecutionEnd -> {
                val payload = event.result.toToolResultPayload()
                emit(
                    handle,
                    RuntimeEvent.ToolFinished(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        result = payload.text(),
                        isError = event.isError,
                        durationMs = event.durationMs,
                        payload = payload,
                        details = event.result.details as? JsonObject,
                    ),
                )
                emitActivity(handle, runId, RuntimeActivity.Thinking)
                emitUsage(handle, runId)
            }

            else -> Unit
        }
    }

}
