package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSessionSnapshot
import io.github.whywood.deepdeep.plugin.api.PluginToolExposure
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginToolAdapter
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksAdapter
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksFactory
import io.github.whywood.deepdeep.plugin.host.events.RuntimeEventMapper
import io.github.whywood.deepdeep.plugin.host.events.RuntimeHookSource
import io.github.whywood.deepdeep.plugin.host.events.SessionEventPump
import io.github.whywood.deepdeep.plugin.host.events.SessionHookSource
import io.github.whywood.deepdeep.runtime.DeferredTool
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PluginSessionHooks(
    private val scope: CoroutineScope,
    private val gate: PluginRuntimeRegistry,
    private val registry: PluginRegistry,
) {
    private val base = RuntimeHookSource(registry)
    private val mapper = RuntimeEventMapper(emit = { emptyList() })
    private val pump = SessionEventPump(scope, ::dispatchEvent)
    private val contributions = WeakHashMap<SessionBinding, Map<String, Contributions>>()
    private val contributionLocks = WeakHashMap<SessionBinding, Any>()
    private val sessions = linkedMapOf<String, SessionState>()

    fun tools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<CodingTool> {
        val sessionId = { binding.current()?.sessionId.orEmpty() }
        return sessionContributions(binding).filterKeys { it !in excludedPlugins }.values
            .flatMap { contribution ->
                PluginToolAdapter.toCodingTools(contribution.tools.filter { it.exposure == PluginToolExposure.DIRECT }, sessionId)
            }
            .distinctBy { it.name }
    }

    fun deferredTools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<DeferredTool> {
        val sessionId = { binding.current()?.sessionId.orEmpty() }
        return sessionContributions(binding).entries.filter { it.key !in excludedPlugins }
            .flatMap { (pluginId, contribution) ->
                contribution.tools
                    .filter { it.exposure == PluginToolExposure.DEFERRED }
                    .map { tool -> PluginToolAdapter.toDeferredTool(tool, "plugin:$pluginId", sessionId) }
            }
            .distinctBy { it.name }
    }

    fun bind(binding: SessionBinding, delegate: PluginHooksDelegate, excludedPlugins: Set<String> = emptySet()) {
        val bound = binding.current() ?: return
        val source = sessionSource(binding, excludedPlugins)
        val adapter = PluginRuntimeHooksFactory(source).create(bound.sessionId, bound.cwd, bound.sessionFile)
        synchronized(sessions) {
            sessions[bound.sessionId] = SessionState(binding, bound.sessionId, source, adapter)
        }
        delegate.bind(adapter)
        pump.updateSnapshot(bound.sessionId, source.snapshot())
        pump.submit(bound.sessionId, PluginEvent.SessionStarted(bound.sessionId, null, bound.cwd))
    }

    fun close(sessionId: String) {
        val state = synchronized(sessions) {
            val current = sessions[sessionId] ?: return
            if (current.closing) return
            current.closing = true
            current
        }
        scope.launch {
            pump.submit(sessionId, PluginEvent.SessionClosed(sessionId))
            pump.closeAndDrain(sessionId)
            synchronized(sessions) { sessions.remove(sessionId) }
            synchronized(contributions) {
                contributions.remove(state.binding)
                contributionLocks.remove(state.binding)
            }
        }
    }

    fun refreshSnapshots() {
        val states = synchronized(sessions) { sessions.values.toList() }
        states.forEach { state -> pump.updateSnapshot(state.sessionId, state.source.snapshot()) }
    }

    fun dispatchRuntimeEvent(event: SessionRuntimeEvent) {
        val state = activeState(event.sessionId) ?: return
        mapper.map(event.sessionId, event.event).forEach { pluginEvent ->
            when (pluginEvent) {
                is PluginEvent.RunStarted -> pump.updateSnapshot(
                    state.sessionId,
                    state.source.freeze(pluginEvent.runId),
                )

                else -> Unit
            }
            pump.submit(state.sessionId, pluginEvent)
            if (pluginEvent is PluginEvent.RunFinished && state.source.frozenIsEmpty(pluginEvent.runId)) {
                finishRun(state, pluginEvent.runId)
            }
        }
    }

    fun dispatchSessionEvent(sessionId: String, event: io.github.whywood.deepdeep.sdk.coding.session.SessionEvent) {
        if (activeState(sessionId) == null) return
        mapper.mapSessionEvent(sessionId, event).forEach { pluginEvent ->
            pump.submit(sessionId, pluginEvent)
        }
    }

    private suspend fun dispatchEvent(sessionId: String, event: PluginEvent) {
        val state = synchronized(sessions) { sessions[sessionId] } ?: return
        runCatching { state.adapter.emit(event) }
        if (event is PluginEvent.RunFinished) finishRun(state, event.runId)
    }

    private fun finishRun(state: SessionState, runId: String) {
        val snapshot = state.source.release(runId) ?: return
        pump.updateSnapshot(state.sessionId, snapshot)
    }

    private fun sessionSource(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): SessionHookSource {
        val hooks = sessionContributions(binding)
            .filterKeys { it !in excludedPlugins }
            .mapValues { (_, contribution) -> contribution.hooks }
        return SessionHookSource(snapshots = base::snapshot, sessionHooks = hooks)
    }

    private fun sessionContributions(binding: SessionBinding): Map<String, Contributions> {
        synchronized(contributions) { contributions[binding] }?.let { return it }
        val lock = synchronized(contributions) { contributionLocks.getOrPut(binding) { Any() } }
        val materialized = synchronized(lock) {
            synchronized(contributions) { contributions[binding] } ?: materialize(binding).also { value ->
                synchronized(contributions) { contributions[binding] = value }
                value
            }
        }
        synchronized(contributions) { contributionLocks.remove(binding) }
        return materialized
    }

    private fun materialize(binding: SessionBinding): Map<String, Contributions> {
        val snapshot = registry.snapshot()
        val session = pluginSessionInfo(binding)
        return buildMap {
            gate.views().forEach { view ->
                if (snapshot.plugin(view.id)?.phase?.contributes != true) return@forEach
                val plugin = view.plugin ?: return@forEach
                val contribution = try {
                    plugin.contributionsFor(view.host, session)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    gate.markError(view.id, error.message ?: error.javaClass.simpleName)
                    Contributions()
                }
                put(view.id, contribution)
            }
        }
    }

    private fun activeState(sessionId: String): SessionState? =
        synchronized(sessions) { sessions[sessionId]?.takeUnless { it.closing } }

    private fun pluginSessionInfo(binding: SessionBinding): PluginSessionInfo =
        PluginSessionInfo(binding.supportsChatDelivery) {
            binding.current()?.let { bound ->
                PluginSessionSnapshot(sessionId = bound.sessionId, file = bound.sessionFile, cwd = bound.cwd)
            }
        }

    private class SessionState(
        val binding: SessionBinding,
        val sessionId: String,
        val source: SessionHookSource,
        val adapter: PluginRuntimeHooksAdapter,
    ) {
        var closing: Boolean = false
    }
}
