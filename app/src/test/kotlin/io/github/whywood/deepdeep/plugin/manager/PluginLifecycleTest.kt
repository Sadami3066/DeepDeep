package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLifecycleTest {
    private fun lifecycle(
        gate: PluginRuntimeRegistry,
        parentScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    ): PluginLifecycle {
        val root = Files.createTempDirectory("plugin-lifecycle").toFile()
        return PluginLifecycle(
            parentScope = parentScope,
            hostFactory = { _, manifest, scope -> testPluginHost(manifest, scope) },
            gate = gate,
            skillStore = PluginSkillStore(root.resolve("skills"), root.resolve("repo")),
            external = null,
        )
    }

    private fun register(lifecycle: PluginLifecycle, gate: PluginRuntimeRegistry, plugin: CountingPlugin) {
        val manifest = managerManifest("alpha")
        gate.register(managerRecord("alpha"), lifecycle.createRuntime(managerRecord("alpha"), manifest))
        gate.markLoaded(
            pluginId = "alpha",
            manifest = manifest,
            plugin = plugin,
            host = testPluginHost(manifest, gate.view("alpha")!!.scope),
            scope = gate.view("alpha")!!.scope,
        )
    }

    @Test
    fun enableLoadsPluginAndMarksActive() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        register(lifecycle, gate, plugin)

        assertNull(lifecycle.enable(gate.view("alpha")!!))
        assertEquals(listOf("enable"), plugin.events)
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun deactivateCancelsScopeClosesPluginAndDetaches() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        register(lifecycle, gate, plugin)
        val scope = gate.view("alpha")!!.scope
        lifecycle.enable(gate.view("alpha")!!)

        assertNull(lifecycle.deactivate("alpha"))

        assertFalse(scope.isActive)
        assertEquals(1, plugin.count("close"))
        assertNull(gate.view("alpha")?.plugin)
        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun reenablingPluginGetsFreshScopeAndCallsEnableAgain() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        register(lifecycle, gate, plugin)
        lifecycle.enable(gate.view("alpha")!!)
        val oldScope = gate.view("alpha")!!.scope
        lifecycle.deactivate("alpha")

        gate.markLoaded(
            pluginId = "alpha",
            manifest = managerManifest("alpha"),
            plugin = plugin,
            host = testPluginHost(managerManifest("alpha"), gate.view("alpha")!!.scope),
            scope = gate.view("alpha")!!.scope,
        )
        assertNull(lifecycle.enable(gate.view("alpha")!!))
        val newScope = gate.view("alpha")!!.scope
        assertFalse(oldScope.isActive)
        assertTrue(newScope.isActive)
        assertTrue(oldScope !== newScope)
        assertEquals(2, plugin.count("onEnable"))
    }

    @Test
    fun closeFailureIsReportedButTeardownContinues() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin(closeError = IllegalStateException("close failed"))
        register(lifecycle, gate, plugin)
        val scope = gate.view("alpha")!!.scope

        assertEquals("close failed", lifecycle.deactivate("alpha"))
        assertFalse(scope.isActive)
        assertNull(gate.view("alpha")?.plugin)
    }

    @Test
    fun enableFailureMarksPluginFailedWithoutThrowing() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin(enableError = IllegalStateException("enable failed"))
        register(lifecycle, gate, plugin)

        assertEquals("enable failed", lifecycle.enable(gate.view("alpha")!!))
        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)
        assertEquals("enable failed", gate.view("alpha")?.record?.lastError)
    }

    @Test
    fun uninstallCallsDisableThenUninstallAndCloses() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        register(lifecycle, gate, plugin)

        assertNull(lifecycle.uninstallPlugin(gate.view("alpha")!!))
        assertFalse(plugin.events.contains("enable"))
        assertEquals(1, plugin.count("close"))
    }

    @Test
    fun parentScopeCancellationCancelsPluginScope() = runTest {
        val parentScope = CoroutineScope(SupervisorJob())
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate, parentScope)
        register(lifecycle, gate, CountingPlugin())
        val pluginScope = gate.view("alpha")!!.scope

        parentScope.cancel()

        assertFalse(pluginScope.isActive)
    }
}
