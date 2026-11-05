package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal class PluginEventBus(
    private val source: PluginHookSource,
    private val reporter: PluginHookReporter = PluginHookReporter.Logging,
    private val timeoutMs: Long = PluginHooks.OBSERVE_TIMEOUT_MS,
) {
    suspend fun emit(event: PluginEvent): List<PluginHookFailure> {
        val failures = mutableListOf<PluginHookFailure>()
        for (entry in source.entries()) {
            val failure = observe(entry, event) ?: continue
            failures += failure
            reporter.report(failure)
        }
        return failures
    }

    private suspend fun observe(entry: PluginHooksEntry, event: PluginEvent): PluginHookFailure? = try {
        withTimeout(timeoutMs) { entry.hooks.observe(event) }
        null
    } catch (error: TimeoutCancellationException) {
        PluginHookFailure(
            pluginId = entry.pluginId,
            stage = PluginHookStage.OBSERVE,
            message = "${event.javaClass.simpleName} timed out after ${timeoutMs}ms",
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        PluginHookFailure(
            pluginId = entry.pluginId,
            stage = PluginHookStage.OBSERVE,
            message = error.message ?: error.javaClass.simpleName,
            cause = error,
        )
    }
}

private suspend fun PluginHooks.observe(event: PluginEvent) {
    when (event) {
        is PluginEvent.SessionStarted -> sessionStarted(event)
        is PluginEvent.SessionClosed -> sessionClosed(event)
        is PluginEvent.RunStarted -> runStarted(event)
        is PluginEvent.RunFinished -> runFinished(event)
        is PluginEvent.TurnStarted -> turnStarted(event)
        is PluginEvent.TurnFinished -> turnFinished(event)
        is PluginEvent.MessageStarted -> messageStarted(event)
        is PluginEvent.MessageDelta -> messageDelta(event)
        is PluginEvent.MessageFinished -> messageFinished(event)
        is PluginEvent.ToolExecutionUpdate -> toolExecutionUpdate(event)
        is PluginEvent.CompactionStarted -> compactionStarted(event)
        is PluginEvent.CompactionFinished -> compactionFinished(event)
        is PluginEvent.CompactionFailed -> compactionFailed(event)
        is PluginEvent.ModelChanged -> modelChanged(event)
        is PluginEvent.ThinkingLevelChanged -> thinkingLevelChanged(event)
        is PluginEvent.ToolSetChanged -> toolSetChanged(event)
        is PluginEvent.RetryScheduled -> retryScheduled(event)
        is PluginEvent.RetrySucceeded -> retrySucceeded(event)
        is PluginEvent.RetryExhausted -> retryExhausted(event)
        is PluginEvent.McpStatusChanged -> mcpStatusChanged(event)
        is PluginEvent.McpToolsChanged -> mcpToolsChanged(event)
        is PluginEvent.BusEvent -> busEvent(event)
    }
}
