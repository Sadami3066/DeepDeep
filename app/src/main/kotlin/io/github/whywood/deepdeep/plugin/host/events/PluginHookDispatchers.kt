package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.PluginContextRequest
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallAction
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultAction
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision
import io.github.whywood.deepdeep.plugin.api.inputContinue
import io.github.whywood.deepdeep.plugin.api.inputHandled
import io.github.whywood.deepdeep.plugin.api.resultKeep
import io.github.whywood.deepdeep.plugin.api.resultReplace
import io.github.whywood.deepdeep.plugin.api.toolBlock
import io.github.whywood.deepdeep.plugin.api.toolContinue
import io.github.whywood.deepdeep.plugin.api.toolRewrite
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal class PluginHookDispatchers(
    private val source: PluginHookSource,
    private val reporter: PluginHookReporter = PluginHookReporter.Logging,
    private val timeoutMs: Long = PluginHooks.TRANSFORM_TIMEOUT_MS,
) {
    suspend fun transformPrompt(request: PromptRequest): String {
        var prompt = request.prompt
        for (entry in source.entries()) {
            prompt = dispatch(entry, PluginHookStage.PROMPT_TRANSFORM, block = {
                promptTransform(request.copy(prompt = prompt))
            }) { prompt }
        }
        return prompt
    }

    suspend fun collectContext(request: PluginContextRequest): List<Pair<String, String>> {
        val contributions = mutableListOf<Pair<String, String>>()
        var total = 0
        for (entry in source.entries()) {
            if (total >= MAX_CONTEXT_TOTAL) break
            val text = dispatch(entry, PluginHookStage.CONTEXT, block = {
                context(request)
            }) { null }?.takeIf { it.isNotBlank() } ?: continue
            val remaining = MAX_CONTEXT_TOTAL - total
            val value = text.take(minOf(MAX_CONTEXT_PER_PLUGIN, remaining))
            contributions += entry.pluginId to value
            total += value.length
        }
        return contributions
    }

    suspend fun transformInput(request: InputRequest): InputDecision {
        var currentText = request.text
        var currentImages = request.imagePaths
        for (entry in source.entries()) {
            val decision = dispatch(entry, PluginHookStage.INPUT_TRANSFORM, block = {
                inputTransform(InputRequest(request.sessionId, currentText, currentImages))
            }) { inputContinue(currentText, currentImages) }
            if (decision.handled) return inputHandled()
            currentText = decision.text ?: currentText
            currentImages = decision.imagePaths
        }
        return inputContinue(currentText, currentImages)
    }

    suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision {
        var current = request
        var rewritten = false
        for (entry in source.entries()) {
            val decision = dispatch(entry, PluginHookStage.BEFORE_TOOL_CALL, block = {
                this.beforeToolCall(current)
            }) { failure -> toolBlock(failure.toolBlockReason()) }
            when (decision.action) {
                ToolCallAction.BLOCK -> return decision
                ToolCallAction.REWRITE -> {
                    current = current.copy(args = decision.args ?: current.args)
                    rewritten = true
                }

                else -> Unit
            }
        }
        return if (rewritten) toolRewrite(current.args) else toolContinue()
    }

    suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision {
        var current = request
        var replaced = false
        for (entry in source.entries()) {
            val decision = dispatch(entry, PluginHookStage.AFTER_TOOL_EXECUTION, block = {
                this.afterToolExecution(current)
            }) { resultKeep() }
            if (decision.action == ToolResultAction.REPLACE) {
                current = current.copy(result = decision.result ?: current.result)
                replaced = true
            }
        }
        return if (replaced) resultReplace(current.result) else resultKeep()
    }

    private companion object {
        const val MAX_CONTEXT_PER_PLUGIN: Int = 8 * 1024

        const val MAX_CONTEXT_TOTAL: Int = 32 * 1024
    }

    private suspend fun <T> dispatch(
        entry: PluginHooksEntry,
        stage: PluginHookStage,
        block: suspend PluginHooks.() -> T,
        onFailure: (PluginHookFailure) -> T,
    ): T = try {
        withTimeout(timeoutMs) { entry.hooks.block() }
    } catch (error: TimeoutCancellationException) {
        report(entry, stage, "timed out after ${timeoutMs}ms", null, onFailure)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        report(entry, stage, error.message ?: error.javaClass.simpleName, error, onFailure)
    }

    private fun <T> report(
        entry: PluginHooksEntry,
        stage: PluginHookStage,
        message: String,
        cause: Throwable?,
        onFailure: (PluginHookFailure) -> T,
    ): T {
        val failure = PluginHookFailure(entry.pluginId, stage, message, cause)
        reporter.report(failure)
        return onFailure(failure)
    }
}

private fun PluginHookFailure.toolBlockReason(): String = "plugin $pluginId failed: $message"
