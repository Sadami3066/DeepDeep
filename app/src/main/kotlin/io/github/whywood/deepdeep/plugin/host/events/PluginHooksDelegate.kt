package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.runtime.InputHandler
import io.github.whywood.deepdeep.runtime.PromptTransform
import io.github.whywood.deepdeep.runtime.ToolCallContext
import io.github.whywood.deepdeep.runtime.ToolCallDecision
import io.github.whywood.deepdeep.runtime.ToolExecutionHooks
import io.github.whywood.deepdeep.runtime.ToolResultContext
import io.github.whywood.deepdeep.runtime.ToolResultDecision

internal class PluginHooksDelegate {
    @Volatile
    private var target: PluginRuntimeHooksAdapter? = null

    fun bind(adapter: PluginRuntimeHooksAdapter?) {
        target = adapter
    }

    fun clear() {
        target = null
    }

    val promptTransform: PromptTransform = { prompt ->
        target?.promptTransform?.invoke(prompt) ?: prompt
    }

    val inputHandler: InputHandler = { request ->
        target?.inputHandler?.invoke(request) ?: InputDecision.continueWith(
            io.github.whywood.deepdeep.plugin.api.InputRequest(
                sessionId = "",
                text = request.text,
            ),
        ).toSdk(request)
    }

    val toolHooks: ToolExecutionHooks = object : ToolExecutionHooks {
        override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
            target?.toolHooks?.beforeToolCall(context) ?: ToolCallDecision.Continue

        override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision =
            target?.toolHooks?.afterToolResult(context) ?: ToolResultDecision.Keep
    }

    suspend fun emit(event: PluginEvent): List<PluginHookFailure> = target?.emit(event).orEmpty()
}

private fun InputDecision.toSdk(request: dev.pi.coding.session.InputRequest): dev.pi.coding.session.InputDecision =
    when (this) {
        is InputDecision.Continue -> dev.pi.coding.session.InputDecision.Continue(text, request.images)
        InputDecision.Handled -> dev.pi.coding.session.InputDecision.Handled
    }
