package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSessionSnapshot
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginToolAdapter
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksAdapter
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksFactory
import io.github.whywood.deepdeep.plugin.host.events.RuntimeEventMapper
import io.github.whywood.deepdeep.plugin.host.events.RuntimeHookSource
import io.github.whywood.deepdeep.plugin.host.events.SessionEventPump
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.plugin.api.Contributions
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PluginSessionHooks(
    private val scope: CoroutineScope,
    private val gate: PluginRuntimeRegistry,
    private val registry: PluginRegistry,
) {
    private val source = RuntimeHookSource(registry)
    private val hooks = PluginRuntimeHooksFactory(source)
    private val adapters = linkedMapOf<String, PluginRuntimeHooksAdapter>()
    private val toolsCache = WeakHashMap<SessionBinding, List<dev.pi.coding.tools.CodingTool>>()
    private val mapper = RuntimeEventMapper(emit = { emptyList() })
    private val pump = SessionEventPump(scope, ::dispatchEvent)

    fun contributions(binding: SessionBinding): Map<String, Contributions> {
        val snapshot = registry.snapshot()
        val session = pluginSessionInfo(binding)
        return buildMap {
            gate.views().forEach { view ->
                if (snapshot.plugin(view.id)?.phase?.contributes != true) return@forEach
                val plugin = view.plugin ?: return@forEach
                val contributions = try {
                    plugin.contributionsFor(view.host, session)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    gate.markError(view.id, error.message ?: error.javaClass.simpleName)
                    Contributions()
                }
                put(view.id, contributions)
            }
        }
    }

    fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool> {
        synchronized(toolsCache) { toolsCache[binding] }?.let { return it }
        val sessionId = { binding.current()?.sessionId.orEmpty() }
        val tools = contributions(binding).values
            .flatMap { contributions -> PluginToolAdapter.toCodingTools(contributions.tools, sessionId) }
            .distinctBy { it.name }
        synchronized(toolsCache) { toolsCache[binding] = tools }
        return tools
    }

    fun bind(session: BoundSession, delegate: PluginHooksDelegate) {
        val adapter = hooks.create(session.sessionId, session.cwd, session.sessionFile)
        synchronized(adapters) { adapters[session.sessionId] = adapter }
        delegate.bind(adapter)
        pump.updateSnapshot(session.sessionId, source.snapshot())
        pump.submit(session.sessionId, PluginEvent.SessionStarted(session.sessionId, null, session.cwd))
    }

    fun close(sessionId: String) {
        if (!isBound(sessionId)) return
        scope.launch {
            pump.submit(sessionId, PluginEvent.SessionClosed(sessionId))
            pump.closeAndDrain(sessionId)
            synchronized(adapters) { adapters.remove(sessionId) }
        }
    }

    fun refreshSnapshots() {
        val snapshot = source.snapshot()
        val sessionIds = synchronized(adapters) { adapters.keys.toList() }
        sessionIds.forEach { sessionId -> pump.updateSnapshot(sessionId, snapshot) }
    }

    fun dispatchRuntimeEvent(event: SessionRuntimeEvent) {
        if (!isBound(event.sessionId)) return
        mapper.map(event.sessionId, event.event).forEach { pluginEvent ->
            pump.submit(event.sessionId, pluginEvent)
        }
    }

    fun dispatchSessionEvent(sessionId: String, event: dev.pi.coding.session.SessionEvent) {
        if (!isBound(sessionId)) return
        mapper.mapSessionEvent(sessionId, event).forEach { pluginEvent ->
            pump.submit(sessionId, pluginEvent)
        }
    }

    private suspend fun dispatchEvent(sessionId: String, event: PluginEvent) {
        val adapter = synchronized(adapters) { adapters[sessionId] } ?: return
        runCatching { adapter.emit(event) }
    }

    private fun isBound(sessionId: String): Boolean = synchronized(adapters) { adapters.containsKey(sessionId) }

    private fun pluginSessionInfo(binding: SessionBinding): PluginSessionInfo =
        PluginSessionInfo(binding.supportsChatDelivery) {
            binding.current()?.let { bound ->
                PluginSessionSnapshot(sessionId = bound.sessionId, file = bound.sessionFile, cwd = bound.cwd)
            }
        }
}
