package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.BusEventReceived
import io.github.whywood.deepdeep.plugin.api.CompactionFailedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionFinishedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionStartedEvent
import io.github.whywood.deepdeep.plugin.api.McpStatusChangedEvent
import io.github.whywood.deepdeep.plugin.api.McpToolsChangedEvent
import io.github.whywood.deepdeep.plugin.api.MessageDeltaEvent
import io.github.whywood.deepdeep.plugin.api.MessageFinishedEvent
import io.github.whywood.deepdeep.plugin.api.MessageStartedEvent
import io.github.whywood.deepdeep.plugin.api.ModelChangedEvent
import io.github.whywood.deepdeep.plugin.api.RetryExhaustedEvent
import io.github.whywood.deepdeep.plugin.api.RetryScheduledEvent
import io.github.whywood.deepdeep.plugin.api.RetrySucceededEvent
import io.github.whywood.deepdeep.plugin.api.RunFinishedEvent
import io.github.whywood.deepdeep.plugin.api.RunStartedEvent
import io.github.whywood.deepdeep.plugin.api.SessionClosedEvent
import io.github.whywood.deepdeep.plugin.api.SessionStartedEvent
import io.github.whywood.deepdeep.plugin.api.ThinkingLevelChangedEvent
import io.github.whywood.deepdeep.plugin.api.ToolExecutionUpdateEvent
import io.github.whywood.deepdeep.plugin.api.TurnFinishedEvent
import io.github.whywood.deepdeep.plugin.api.TurnStartedEvent
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
        is SessionStartedEvent -> sessionStarted(event)
        is SessionClosedEvent -> sessionClosed(event)
        is RunStartedEvent -> runStarted(event)
        is RunFinishedEvent -> runFinished(event)
        is TurnStartedEvent -> turnStarted(event)
        is TurnFinishedEvent -> turnFinished(event)
        is MessageStartedEvent -> messageStarted(event)
        is MessageDeltaEvent -> messageDelta(event)
        is MessageFinishedEvent -> messageFinished(event)
        is ToolExecutionUpdateEvent -> toolExecutionUpdate(event)
        is CompactionStartedEvent -> compactionStarted(event)
        is CompactionFinishedEvent -> compactionFinished(event)
        is CompactionFailedEvent -> compactionFailed(event)
        is ModelChangedEvent -> modelChanged(event)
        is ThinkingLevelChangedEvent -> thinkingLevelChanged(event)
        is RetryScheduledEvent -> retryScheduled(event)
        is RetrySucceededEvent -> retrySucceeded(event)
        is RetryExhaustedEvent -> retryExhausted(event)
        is McpStatusChangedEvent -> mcpStatusChanged(event)
        is McpToolsChangedEvent -> mcpToolsChanged(event)
        is BusEventReceived -> busEvent(event)
    }
}
