package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AfterToolCall
import io.github.whywood.deepdeep.sdk.agent.Agent
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.agent.AgentToolUpdateCallback
import io.github.whywood.deepdeep.sdk.agent.AfterToolCallContext
import io.github.whywood.deepdeep.sdk.agent.AfterToolCallResult
import io.github.whywood.deepdeep.sdk.agent.BeforeToolCall
import io.github.whywood.deepdeep.sdk.agent.BeforeToolCallContext
import io.github.whywood.deepdeep.sdk.agent.BeforeToolCallResult
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.session.CodingHooks
import kotlinx.serialization.json.JsonObject

typealias PromptTransform = suspend (String) -> String

typealias SystemContext = suspend () -> String?

data class InputRequest(
    val text: String,
    val images: List<ImagePart> = emptyList(),
)

sealed interface InputDecision {
    data class Continue(
        val text: String,
        val images: List<ImagePart> = emptyList(),
    ) : InputDecision

    data object Handled : InputDecision
}

typealias InputHandler = suspend (InputRequest) -> InputDecision

internal fun List<ImagePart>.toImageContents(): List<ImageContent>? =
    map { ImageContent(it.data, it.mimeType) }.takeIf { it.isNotEmpty() }

data class RuntimeHooks(
    val promptTransform: PromptTransform? = null,
    val systemContext: SystemContext? = null,
    val inputHandler: InputHandler? = null,
    val toolHooks: ToolExecutionHooks? = null,
    val onSessionBound: ((SessionBinding) -> Unit)? = null,
    val onSessionClosed: ((String) -> Unit)? = null,
) {
    companion object {
        val None: RuntimeHooks = RuntimeHooks()
    }
}

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

fun Agent.installToolExecutionHooks(hooks: ToolExecutionHooks?) {
    if (hooks == null) return
    val previousBefore = beforeToolCall
    beforeToolCall = bridgeBeforeToolCall(hooks, previousBefore)
    val previousAfter = afterToolCall
    afterToolCall = bridgeAfterToolCall(hooks, previousAfter)
}

internal fun bridgeBeforeToolCall(
    hooks: ToolExecutionHooks,
    chained: BeforeToolCall?,
): BeforeToolCall = { context, signal ->
    val decision = hooks.beforeToolCall(context.toolCallContext())
    when (decision) {
        is ToolCallDecision.Block -> BeforeToolCallResult(block = true, reason = decision.reason)
        is ToolCallDecision.Rewrite -> {
            context.args.clear()
            context.args.putAll(decision.args)
            chained?.invoke(context, signal)
        }

        ToolCallDecision.Continue -> chained?.invoke(context, signal)
    }
}

internal fun bridgeAfterToolCall(
    hooks: ToolExecutionHooks,
    chained: AfterToolCall?,
): AfterToolCall = { context, signal ->
    val normalized = chained?.invoke(context, signal)
    val effective = if (normalized == null) context else context.withResult(normalized)
    when (val decision = hooks.afterToolResult(effective.toolResultContext())) {
        is ToolResultDecision.Replace -> decision.result.asAfterToolCallResult()
        ToolResultDecision.Keep -> normalized
    }
}

private fun BeforeToolCallContext.toolCallContext(): ToolCallContext = ToolCallContext(
    toolName = toolCall.name,
    callId = toolCall.id,
    args = JsonObject(args),
)

private fun AfterToolCallContext.toolResultContext(): ToolResultContext = ToolResultContext(
    toolName = toolCall.name,
    callId = toolCall.id,
    args = JsonObject(args),
    result = result,
)

private fun AfterToolCallContext.withResult(normalized: AfterToolCallResult): AfterToolCallContext {
    val current = result
    val merged = AgentToolResult(
        content = normalized.content ?: current.content,
        details = normalized.details ?: current.details,
        structuredContent = normalized.structuredContent
            ?: if (normalized.content != null) null else current.structuredContent,
        usage = normalized.usage ?: current.usage,
        isError = normalized.isError ?: current.isError,
        terminate = normalized.terminate ?: current.terminate,
    )
    return AfterToolCallContext(
        assistantMessage = assistantMessage,
        toolCall = toolCall,
        args = args,
        result = merged,
        isError = normalized.isError ?: isError,
        context = context,
    )
}

private fun AgentToolResult.asAfterToolCallResult(): AfterToolCallResult = AfterToolCallResult(
    content = content,
    details = details,
    structuredContent = structuredContent,
    isError = isError,
    usage = usage,
    terminate = terminate,
)

fun CodingHooks.withPluginHooks(promptTransform: PromptTransform?): CodingHooks {
    if (promptTransform == null) return this
    return CodingHooks(
        transformPrompt = chainPromptTransforms(transformPrompt, promptTransform),
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
