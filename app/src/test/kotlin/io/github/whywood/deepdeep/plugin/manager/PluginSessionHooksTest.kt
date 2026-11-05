package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.runtime.SessionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionHooksTest {
    private suspend fun hooks(
        plugin: CountingPlugin,
        enabled: Boolean = true,
    ): Pair<PluginSessionHooks, PluginRuntimeRegistry> {
        val gate = PluginRuntimeRegistry()
        val registry = PluginRegistry()
        gate.register(managerRecord("alpha", enabled = enabled), managerRuntime(managerManifest("alpha"), plugin))
        PluginContributionMaterializer(gate).refreshAll()
        gate.descriptor("alpha")?.let { registry.register(it) }
        return PluginSessionHooks(CoroutineScope(SupervisorJob()), gate, registry) to gate
    }

    @Test
    fun toolsMaterializeContributionsOncePerSessionBinding() = runTest {
        val plugin = CountingPlugin()
        val (hooks, _) = hooks(plugin)
        val binding = SessionBinding(supportsChatDelivery = true)

        assertEquals(listOf("counted_tool"), hooks.tools(binding).map { it.name })
        hooks.tools(binding)
        assertEquals(1, plugin.count("contributionsFor"))

        hooks.tools(SessionBinding(supportsChatDelivery = true))
        assertEquals(2, plugin.count("contributionsFor"))
    }

    @Test
    fun disabledPluginContributesNoSessionTools() = runTest {
        val plugin = CountingPlugin()
        val (hooks, _) = hooks(plugin, enabled = false)

        assertTrue(hooks.tools(SessionBinding(supportsChatDelivery = true)).isEmpty())
        assertEquals(0, plugin.count("contributionsFor"))
    }

    @Test
    fun contributionsForFailureIsIsolatedAndOnlyRecordsError() = runTest {
        val plugin = CountingPlugin()
        val (hooks, gate) = hooks(plugin)
        plugin.contributionsError = IllegalStateException("session contributions failed")

        assertTrue(hooks.tools(SessionBinding(supportsChatDelivery = true)).isEmpty())
        assertEquals("session contributions failed", gate.view("alpha")?.record?.lastError)
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }
}
