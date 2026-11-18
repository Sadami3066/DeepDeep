package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.guard.GuardVerdict
import com.wangzan101.mika.core.tool.Until
import com.wangzan101.mika.core.tool.UntilOutcome
import kotlinx.serialization.json.JsonObject

internal data class BatchToolCall(val id: String, val name: String, val argsJson: String)

internal data class BatchToolSpec(val name: String, val description: String, val parametersJsonSchema: String)

internal data class BatchExecution(val content: JsonObject, val changed: Boolean? = null, val waitMs: Long = 0)

internal data class BatchEvent(val atMs: Long, val step: Int, val total: Int, val stopped: Boolean, val reason: String?)

internal data class BatchRecorder(
    val now: () -> Long,
    val event: (BatchEvent) -> Unit,
)

internal interface BatchScreenPort {
    suspend fun settle(timeoutMs: Long)
    suspend fun waitUntil(until: Until, clock: () -> Long, interrupted: () -> Boolean): UntilOutcome
}

internal interface BatchActionPort {
    val recorder: BatchRecorder
    val confirms: Boolean
    suspend fun run(call: BatchToolCall, step: Int): BatchActionOutcome
}

internal sealed interface BatchActionOutcome {
    val before: List<GuardVerdict>

    data class Ran(
        val exec: BatchExecution,
        override val before: List<GuardVerdict> = emptyList(),
        val after: List<GuardVerdict> = emptyList(),
    ) : BatchActionOutcome

    data class Refused(
        val code: String,
        override val before: List<GuardVerdict> = emptyList(),
        val hit: GuardHit? = null,
    ) : BatchActionOutcome

    data class Cancelled(override val before: List<GuardVerdict>) : BatchActionOutcome

    data class GuardHit(val action: GuardAction, val matched: String)
    data class GuardAction(val name: String)
}
