package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.conversation.ConversationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DeltaBatcher(
    private val scope: CoroutineScope,
    private val writeTarget: ConversationWriteTarget,
    private val activeSessionId: () -> String?,
    private val reduceEvent: (ConversationState, RuntimeEvent) -> ConversationState,
    private val delayMillis: Long = 16L,
) {
    private val pending = mutableListOf<RuntimeEvent>()
    private var pendingRunId: String? = null
    private var flushJob: Job? = null

    val hasPending: Boolean get() = pending.isNotEmpty()

    fun accept(sessionId: String, event: RuntimeEvent): Boolean {
        if (event !is RuntimeEvent.TextDelta && event !is RuntimeEvent.ReasoningDelta) return false
        if (sessionId != activeSessionId()) return false
        enqueue(event.runId, event)
        return true
    }

    fun enqueue(runId: String, event: RuntimeEvent) {
        if (pendingRunId != null && pendingRunId != runId) flush()
        pendingRunId = runId
        val merged = pending.lastOrNull()?.let { merge(it, event) }
        if (merged != null) pending[pending.lastIndex] = merged else pending += event
        schedule()
    }

    fun flush() {
        flushJob?.cancel()
        flushJob = null
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        pendingRunId = null
        val sessionId = activeSessionId() ?: return
        writeTarget.update(sessionId) { snapshot ->
            batch.fold(snapshot) { state, event -> reduceEvent(state, event) }
        }
    }

    fun clear(sessionId: String) {
        flushJob?.cancel()
        flushJob = null
        pending.clear()
        pendingRunId = null
    }

    private fun schedule() {
        if (flushJob != null) return
        flushJob = scope.launch {
            delay(delayMillis)
            flushJob = null
            flush()
        }
    }

    private fun merge(first: RuntimeEvent, second: RuntimeEvent): RuntimeEvent? = when {
        first is RuntimeEvent.TextDelta && second is RuntimeEvent.TextDelta && first.runId == second.runId ->
            RuntimeEvent.TextDelta(first.runId, first.text + second.text)

        first is RuntimeEvent.ReasoningDelta && second is RuntimeEvent.ReasoningDelta && first.runId == second.runId ->
            RuntimeEvent.ReasoningDelta(first.runId, first.text + second.text)

        else -> null
    }
}
