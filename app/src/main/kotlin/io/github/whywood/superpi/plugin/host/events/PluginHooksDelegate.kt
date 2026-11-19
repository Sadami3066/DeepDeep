package io.github.whywood.superpi.plugin.host.events

import io.github.whywood.superpi.plugin.api.PluginEvent
import io.github.whywood.superpi.runtime.InputDecision
import io.github.whywood.superpi.runtime.InputHandler
import io.github.whywood.superpi.runtime.PromptTransform
import io.github.whywood.superpi.runtime.SystemContext
import io.github.whywood.superpi.runtime.ToolCallContext
import io.github.whywood.superpi.runtime.ToolCallDecision
import io.github.whywood.superpi.runtime.ToolExecutionHooks
import io.github.whywood.superpi.runtime.ToolResultContext
import io.github.whywood.superpi.runtime.ToolResultDecision

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

    val systemContext: SystemContext = { target?.systemContext() }

    val inputHandler: InputHandler = { request ->
        target?.inputHandler?.invoke(request) ?: InputDecision.Continue(request.text, request.images)
    }

    val toolHooks: ToolExecutionHooks = object : ToolExecutionHooks {
        override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
            target?.toolHooks?.beforeToolCall(context) ?: ToolCallDecision.Continue

        override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision =
            target?.toolHooks?.afterToolResult(context) ?: ToolResultDecision.Keep
    }

    suspend fun emit(event: PluginEvent): List<PluginHookFailure> = target?.emit(event).orEmpty()
}
