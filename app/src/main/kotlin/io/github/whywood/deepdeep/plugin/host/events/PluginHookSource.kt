package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import io.github.whywood.deepdeep.plugin.host.PluginRegistry

internal data class PluginHookSession(
    val sessionId: String,
    val cwd: String,
    val file: String? = null,
)

internal fun interface PluginHookSource {
    fun entries(): List<PluginHooksEntry>
}

internal fun registryHookSource(registry: PluginRegistry): PluginHookSource =
    PluginHookSource { registry.snapshot().hooks }

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
