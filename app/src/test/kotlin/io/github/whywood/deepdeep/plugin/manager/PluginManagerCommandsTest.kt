package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerCommandsTest {
    private class FakeStrings : PluginStrings {
        override fun get(id: Int): String = "string_$id"

        override fun format(id: Int, vararg args: Any): String = "string_$id"
    }

    private class Harness(val gate: PluginRuntimeRegistry, val commands: PluginManagerCommands, val cleared: MutableList<String>)

    private fun harness(): Harness {
        val root = Files.createTempDirectory("plugin-commands").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = PluginRuntimeRegistry()
        val registry = PluginRegistry()
        val state = MutableStateFlow(PluginsUiState())
        val strings = FakeStrings()
        val textResolver = PluginTextResolver(resourceString = { "" }, language = { "default" })
        val skillStore = PluginSkillStore(root.resolve("skills"), root.resolve("repo"))
        val lifecycle = PluginLifecycle(
            parentScope = scope,
            hostFactory = { _, manifest, pluginScope -> testPluginHost(manifest, pluginScope) },
            gate = gate,
            skillStore = skillStore,
            external = null,
            settingsFor = { emptyMap() },
        )
        lifecycle.registerBuiltin(BuiltinPluginEntry(managerManifest("alpha"), CountingPlugin()), null)
        gate.setEnabled("alpha", false)
        val settingsStore = PluginSettingsStore(root.resolve("settings.json"), Dispatchers.Unconfined)
        val externalFlow = PluginExternalFlow(
            scope = scope,
            state = state,
            gate = gate,
            registry = registry,
            external = null,
            externalUpdates = null,
            strings = strings,
            textResolver = textResolver,
            lifecycle = lifecycle,
            settingsStore = settingsStore,
            refresh = {},
            onContributionsChanged = {},
        )
        val cleared = mutableListOf<String>()
        val commands = PluginManagerCommands(
            scope = scope,
            state = state,
            strings = strings,
            gate = gate,
            registry = registry,
            lifecycle = lifecycle,
            external = null,
            externalFlow = externalFlow,
            settingsStore = settingsStore,
            builtinStateStore = BuiltinPluginStateStore(root.resolve("builtin.json"), Dispatchers.Unconfined),
            busy = AtomicBoolean(false),
            mutex = Mutex(),
            refresh = {},
            clearStartupCount = { pluginId ->
                cleared += pluginId
                true
            },
            onContributionsChanged = {},
        )
        return Harness(gate, commands, cleared)
    }

    @Test
    fun manualEnableClearsActivationMarker() {
        val harness = harness()

        harness.commands.setEnabled("alpha", true)

        assertEquals(listOf("alpha"), harness.cleared)
        assertTrue(harness.gate.view("alpha")?.enabled == true)
    }

    @Test
    fun enablingAlreadyEnabledPluginDoesNotClearMarkerAgain() {
        val harness = harness()
        harness.commands.setEnabled("alpha", true)
        harness.cleared.clear()

        harness.commands.setEnabled("alpha", true)

        assertEquals(emptyList<String>(), harness.cleared)
        assertTrue(harness.gate.view("alpha")?.enabled == true)
    }
}
