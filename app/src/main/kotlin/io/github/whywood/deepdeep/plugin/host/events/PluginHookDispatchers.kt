package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision
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

    suspend fun transformInput(request: InputRequest): InputDecision {
        var current = request
        for (entry in source.entries()) {
            val decision = dispatch(entry, PluginHookStage.INPUT_TRANSFORM, block = {
                inputTransform(current)
            }) { InputDecision.continueWith(current) }
            when (decision) {
                is InputDecision.Continue -> current = current.copy(text = decision.text, imagePaths = decision.imagePaths)
                InputDecision.Handled -> return InputDecision.Handled
            }
        }
        return InputDecision.continueWith(current)
    }

    suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision {
        var current = request
        var rewritten = false
        for (entry in source.entries()) {
            val decision = dispatch(entry, PluginHookStage.BEFORE_TOOL_CALL, block = {
                this.beforeToolCall(current)
            }) { failure -> ToolCallDecision.Block(failure.toolBlockReason()) }
            when (decision) {
                is ToolCallDecision.Block -> return decision
                is ToolCallDecision.Rewrite -> {
                    current = current.copy(args = decision.args)
                    rewritten = true
                }

                ToolCallDecision.Continue -> Unit
            }
        }
        return if (rewritten) ToolCallDecision.Rewrite(current.args) else ToolCallDecision.Continue
    }

    suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision {
        var current = request
        var replaced = false
        for (entry in source.entries()) {
            when (val decision = dispatch(entry, PluginHookStage.AFTER_TOOL_EXECUTION, block = {
                this.afterToolExecution(current)
            }) { ToolResultDecision.Keep }) {
                is ToolResultDecision.Replace -> {
                    current = current.copy(result = decision.result)
                    replaced = true
                }

                ToolResultDecision.Keep -> Unit
            }
        }
        return if (replaced) ToolResultDecision.Replace(current.result) else ToolResultDecision.Keep
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
