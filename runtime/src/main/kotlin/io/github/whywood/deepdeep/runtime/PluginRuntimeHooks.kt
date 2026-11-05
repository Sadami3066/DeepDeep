package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.agent.AgentToolUpdateCallback
import dev.pi.ai.RunSignal
import dev.pi.coding.session.CodingHooks
import dev.pi.coding.session.InputDecision
import dev.pi.coding.session.InputRequest
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.serialization.json.JsonObject

typealias PromptTransform = suspend (String) -> String

typealias InputHandler = suspend (InputRequest) -> InputDecision

data class ToolCallContext(
    val toolName: String,
    val callId: String,
    val args: JsonObject,
)

data class ToolResultContext(
    val toolName: String,
    val callId: String,
    val args: JsonObject,
    val result: AgentToolResult,
)

sealed interface ToolCallDecision {
    data object Continue : ToolCallDecision

    data class Block(val reason: String) : ToolCallDecision

    data class Rewrite(val args: JsonObject) : ToolCallDecision
}

sealed interface ToolResultDecision {
    data object Keep : ToolResultDecision

    data class Replace(val result: AgentToolResult) : ToolResultDecision
}

interface ToolExecutionHooks {
    suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision = ToolCallDecision.Continue

    suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision = ToolResultDecision.Keep
}

fun CodingTool.withExecutionHooks(hooks: ToolExecutionHooks?): CodingTool {
    if (hooks == null) return this
    val tool = this
    return CodingTool(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters,
        label = tool.label,
        promptSnippet = tool.promptSnippet,
        promptGuidelines = tool.promptGuidelines,
        prepareArguments = tool.prepareArguments,
        execute = { toolCallId, args, signal, onUpdate ->
            when (val decision = hooks.beforeToolCall(ToolCallContext(tool.name, toolCallId, args))) {
                is ToolCallDecision.Block -> textResult(decision.reason, isError = true)
                is ToolCallDecision.Rewrite -> tool.runWithAfterHooks(hooks, toolCallId, decision.args, signal, onUpdate)
                ToolCallDecision.Continue -> tool.runWithAfterHooks(hooks, toolCallId, args, signal, onUpdate)
            }
        },
    )
}

private suspend fun CodingTool.runWithAfterHooks(
    hooks: ToolExecutionHooks,
    toolCallId: String,
    args: JsonObject,
    signal: RunSignal?,
    onUpdate: AgentToolUpdateCallback?,
): AgentToolResult {
    val result = execute(toolCallId, args, signal, onUpdate)
    return when (val decision = hooks.afterToolResult(ToolResultContext(name, toolCallId, args, result))) {
        is ToolResultDecision.Replace -> decision.result
        ToolResultDecision.Keep -> result
    }
}

fun CodingHooks.withPluginHooks(
    promptTransform: PromptTransform?,
    inputHandler: InputHandler?,
): CodingHooks {
    if (promptTransform == null && inputHandler == null) return this
    return CodingHooks(
        transformPrompt = chainPromptTransforms(transformPrompt, promptTransform),
        onInput = chainInputHandlers(onInput, inputHandler),
        extraSystemPromptSections = extraSystemPromptSections,
        onSessionStart = onSessionStart,
        onSessionShutdown = onSessionShutdown,
    )
}

private fun chainPromptTransforms(
    existing: (suspend (String) -> String)?,
    plugin: PromptTransform?,
): (suspend (String) -> String)? = when {
    plugin == null -> existing
    existing == null -> plugin
    else -> { text -> plugin(existing(text)) }
}

private fun chainInputHandlers(
    existing: (suspend (InputRequest) -> InputDecision)?,
    plugin: InputHandler?,
): (suspend (InputRequest) -> InputDecision)? = when {
    plugin == null -> existing
    existing == null -> plugin
    else -> { request ->
        when (val decision = existing(request)) {
            is InputDecision.Continue -> plugin(InputRequest(decision.text, decision.images))
            InputDecision.Handled -> decision
        }
    }
}
