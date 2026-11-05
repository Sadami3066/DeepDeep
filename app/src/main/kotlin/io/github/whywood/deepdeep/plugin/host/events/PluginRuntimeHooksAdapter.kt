package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.coding.session.InputDecision as SdkInputDecision
import dev.pi.coding.session.InputRequest as SdkInputRequest
import io.github.whywood.deepdeep.plugin.api.InputDecision as PluginInputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest as PluginInputRequest
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision as PluginToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision as PluginToolResultDecision
import io.github.whywood.deepdeep.runtime.InputHandler
import io.github.whywood.deepdeep.runtime.PromptTransform
import io.github.whywood.deepdeep.runtime.ToolCallContext
import io.github.whywood.deepdeep.runtime.ToolCallDecision as RuntimeToolCallDecision
import io.github.whywood.deepdeep.runtime.ToolExecutionHooks
import io.github.whywood.deepdeep.runtime.ToolResultContext
import io.github.whywood.deepdeep.runtime.ToolResultDecision as RuntimeToolResultDecision

internal class PluginRuntimeHooksAdapter(
    val session: PluginHookSession,
    source: PluginHookSource,
    reporter: PluginHookReporter = PluginHookReporter.Logging,
    observeTimeoutMs: Long = PluginHooks.OBSERVE_TIMEOUT_MS,
    transformTimeoutMs: Long = PluginHooks.TRANSFORM_TIMEOUT_MS,
) {
    private val dispatchers = PluginHookDispatchers(source, reporter, transformTimeoutMs)

    val events: PluginEventBus = PluginEventBus(source, reporter, observeTimeoutMs)

    val promptTransform: PromptTransform = { prompt ->
        dispatchers.transformPrompt(PromptRequest(session.sessionId, prompt))
    }

    val inputHandler: InputHandler = { request ->
        val decision = dispatchers.transformInput(
            PluginInputRequest(
                sessionId = session.sessionId,
                text = request.text,
            ),
        )
        when (decision) {
            is PluginInputDecision.Continue -> SdkInputDecision.Continue(decision.text, request.images)
            PluginInputDecision.Handled -> SdkInputDecision.Handled
        }
    }

    val toolHooks: ToolExecutionHooks = object : ToolExecutionHooks {
        override suspend fun beforeToolCall(context: ToolCallContext): RuntimeToolCallDecision {
            val decision = dispatchers.beforeToolCall(
                ToolCallRequest(
                    sessionId = session.sessionId,
                    callId = context.callId,
                    toolName = context.toolName,
                    args = context.args,
                ),
            )
            return when (decision) {
                is PluginToolCallDecision.Block -> RuntimeToolCallDecision.Block(decision.reason)
                is PluginToolCallDecision.Rewrite -> RuntimeToolCallDecision.Rewrite(decision.args)
                PluginToolCallDecision.Continue -> RuntimeToolCallDecision.Continue
            }
        }

        override suspend fun afterToolResult(context: ToolResultContext): RuntimeToolResultDecision {
            val decision = dispatchers.afterToolExecution(
                ToolExecutionResult(
                    sessionId = session.sessionId,
                    callId = context.callId,
                    toolName = context.toolName,
                    result = context.result.toPluginToolResult(),
                ),
            )
            return when (decision) {
                is PluginToolResultDecision.Replace -> RuntimeToolResultDecision.Replace(decision.result.toAgentToolResult())
                PluginToolResultDecision.Keep -> RuntimeToolResultDecision.Keep
            }
        }
    }

    suspend fun emit(event: PluginEvent): List<PluginHookFailure> = events.emit(event)
}
