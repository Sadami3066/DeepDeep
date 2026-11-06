package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import io.github.whywood.deepdeep.plugin.host.PluginRegistry

internal data class PluginHookSession(
    val sessionId: String,
    val cwd: String,
    val file: String? = null,
)

internal data class HookSnapshot(
    val version: Long,
    val entries: List<PluginHooksEntry>,
)

internal fun interface PluginHookSource {
    fun entries(): List<PluginHooksEntry>
}

internal class RuntimeHookSource(private val registry: PluginRegistry) : PluginHookSource {
    override fun entries(): List<PluginHooksEntry> = registry.snapshot().hooks

    fun snapshot(): HookSnapshot = registry.snapshot().let { HookSnapshot(it.version, it.hooks) }
}

internal class SessionHookSource(
    private val snapshots: () -> HookSnapshot,
    private val sessionHooks: Map<String, PluginHooks>,
) : PluginHookSource {
    private val lock = Any()

    private var frozen: Frozen? = null

    override fun entries(): List<PluginHooksEntry> = synchronized(lock) { currentLocked() }.entries

    fun snapshot(): HookSnapshot = synchronized(lock) { currentLocked() }

    fun freeze(runId: String): HookSnapshot = synchronized(lock) {
        compose(snapshots()).also { frozen = Frozen(runId, it) }
    }

    fun release(runId: String): HookSnapshot? = synchronized(lock) {
        if (frozen?.runId != runId) return@synchronized null
        frozen = null
        compose(snapshots())
    }

    fun frozenIsEmpty(runId: String): Boolean = synchronized(lock) {
        val current = frozen ?: return@synchronized false
        current.runId == runId && current.snapshot.entries.isEmpty()
    }

    private fun currentLocked(): HookSnapshot = frozen?.snapshot ?: compose(snapshots())

    private fun compose(snapshot: HookSnapshot): HookSnapshot {
        if (sessionHooks.isEmpty()) return snapshot
        val entries = snapshot.entries.map { entry ->
            val override = sessionHooks[entry.pluginId]?.takeIf { it !== PluginHooks.None }
            override?.let { hooks -> entry.copy(hooks = hooks) } ?: entry
        }
        return HookSnapshot(snapshot.version, entries)
    }

    private class Frozen(
        val runId: String,
        val snapshot: HookSnapshot,
    )
}

internal class PluginRuntimeHooksFactory(
    private val source: PluginHookSource,
    private val reporter: PluginHookReporter = PluginHookReporter.Logging,
    private val observeTimeoutMs: Long = PluginHooks.OBSERVE_TIMEOUT_MS,
    private val transformTimeoutMs: Long = PluginHooks.TRANSFORM_TIMEOUT_MS,
) {
    fun create(session: PluginHookSession): PluginRuntimeHooksAdapter =
        PluginRuntimeHooksAdapter(session, source, reporter, observeTimeoutMs, transformTimeoutMs)

    fun create(sessionId: String, cwd: String, file: String? = null): PluginRuntimeHooksAdapter =
        create(PluginHookSession(sessionId, cwd, file))
}
