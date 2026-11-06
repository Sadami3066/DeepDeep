package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.api.PluginSource
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
            settingsFor = { emptyMap() },
        )
    }

    private fun registerBuiltin(lifecycle: PluginLifecycle, plugin: CountingPlugin) {
        lifecycle.registerBuiltin(BuiltinPluginEntry(managerManifest("alpha"), plugin), null)
    }

    @Test
    fun activateInjectsSettingsThenEnablesPlugin() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        registerBuiltin(lifecycle, plugin)

        assertNull(lifecycle.activate(gate.view("alpha")!!))
        assertEquals(listOf("settings", "enable"), plugin.events)
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun deactivateCancelsScopeClosesPluginAndKeepsBuiltinInstance() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        registerBuiltin(lifecycle, plugin)
        val scope = gate.view("alpha")!!.scope
        lifecycle.activate(gate.view("alpha")!!)

        assertNull(lifecycle.deactivate("alpha"))

        assertFalse(scope.isActive)
        assertEquals(1, plugin.count("close"))
        assertNotNull(gate.view("alpha")?.plugin)
        gate.setEnabled("alpha", false)
        assertEquals(PluginPhase.DISABLED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun reenablingBuiltinPluginGetsFreshScopeAndCallsEnableAgain() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        registerBuiltin(lifecycle, plugin)
        lifecycle.activate(gate.view("alpha")!!)
        val oldScope = gate.view("alpha")!!.scope
        lifecycle.deactivate("alpha")

        assertNull(lifecycle.activate(gate.view("alpha")!!))
        val newScope = gate.view("alpha")!!.scope
        assertFalse(oldScope.isActive)
        assertTrue(newScope.isActive)
        assertTrue(oldScope !== newScope)
        assertEquals(2, plugin.count("onEnable"))
    }

    @Test
    fun deactivateDetachesExternalPlugin() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin()
        val manifest = managerManifest("alpha", source = PluginSource.EXTERNAL, installable = true)
        gate.register(
            managerRecord("alpha"),
            lifecycle.createRuntime(managerRecord("alpha"), manifest, builtin = false, source = PluginSource.EXTERNAL),
        )
        gate.markLoaded("alpha", manifest, PluginSource.EXTERNAL, plugin, testPluginHost(manifest), gate.view("alpha")!!.scope)

        lifecycle.deactivate("alpha")

        assertNull(gate.view("alpha")?.plugin)
        assertFalse(gate.view("alpha")!!.scope.isActive)
        assertEquals(1, plugin.count("close"))
    }

    @Test
    fun closeFailureIsReportedButTeardownContinues() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin(closeError = IllegalStateException("close failed"))
        registerBuiltin(lifecycle, plugin)
        val scope = gate.view("alpha")!!.scope

        assertEquals("close failed", lifecycle.deactivate("alpha"))
        assertFalse(scope.isActive)
        gate.setEnabled("alpha", false)
        assertNotNull(gate.view("alpha"))
        assertEquals(PluginPhase.DISABLED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun enableFailureMarksPluginFailedWithoutThrowing() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin(enableError = IllegalStateException("enable failed"))
        registerBuiltin(lifecycle, plugin)

        assertEquals("enable failed", lifecycle.activate(gate.view("alpha")!!))
        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)
        assertEquals("enable failed", gate.view("alpha")?.record?.lastError)
    }

    @Test
    fun settingsFailureIsIsolated() = runTest {
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate)
        val plugin = CountingPlugin(settingsChangedError = IllegalStateException("settings failed"))
        registerBuiltin(lifecycle, plugin)

        assertEquals("settings failed", lifecycle.activate(gate.view("alpha")!!))
        assertEquals(0, plugin.count("onEnable"))
    }

    @Test
    fun parentScopeCancellationCancelsPluginScope() = runTest {
        val parentScope = CoroutineScope(SupervisorJob())
        val gate = PluginRuntimeRegistry()
        val lifecycle = lifecycle(gate, parentScope)
        registerBuiltin(lifecycle, CountingPlugin())
        val pluginScope = gate.view("alpha")!!.scope

        parentScope.cancel()

        assertFalse(pluginScope.isActive)
    }
}
