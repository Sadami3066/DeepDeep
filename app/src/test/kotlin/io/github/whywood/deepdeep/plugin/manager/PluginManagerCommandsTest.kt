package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerCommandsTest {
    private class FakeStrings : PluginStrings {
        override fun get(id: Int): String = "string_$id"
    }

    private class Harness(
        val gate: PluginRuntimeRegistry,
        val lifecycle: PluginLifecycle,
        val manifest: PluginManifest,
        val root: File,
        val commands: PluginManagerCommands,
        val cleared: MutableList<String>,
        val notifications: AtomicInteger,
    )

    private fun harness(plugin: DeepDeepPlugin = CountingPlugin()): Harness {
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
        )
        val manifest = managerManifest("alpha")
        val runtime = lifecycle.createRuntime(managerRecord("alpha", enabled = false), manifest)
        gate.register(managerRecord("alpha", enabled = false), runtime)
        gate.markLoaded(
            pluginId = "alpha",
            manifest = manifest,
            plugin = plugin,
            host = testPluginHost(manifest),
            scope = runtime.scope,
        )
        val notifications = AtomicInteger()
        val refresh: suspend () -> Unit = { PluginContributionMaterializer(gate).refreshAll() }
        val onContributionsChanged: () -> Unit = { notifications.incrementAndGet() }
        val externalFlow = PluginExternalFlow(
            gate = gate,
            external = null,
            strings = strings,
            textResolver = textResolver,
            lifecycle = lifecycle,
            refresh = refresh,
            onContributionsChanged = onContributionsChanged,
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
            busy = AtomicBoolean(false),
            mutex = Mutex(),
            refresh = refresh,
            clearStartupCount = { pluginId ->
                cleared += pluginId
                true
            },
            onContributionsChanged = onContributionsChanged,
        )
        return Harness(gate, lifecycle, manifest, root, commands, cleared, notifications)
    }

    private class SkillPlugin(private val skillId: String) : DeepDeepPlugin {
        override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

        override fun skills(host: PluginHost): List<PluginSkill> = listOf(PluginSkill(skillId, "# $skillId"))
    }

    private suspend fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        withTimeout(timeoutMillis) {
            while (!condition()) delay(20)
        }
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

    @Test
    fun disableRemovesActiveSkillsAndEnableRestoresThem() = runBlocking {
        val harness = harness(SkillPlugin("alpha-skill"))
        val activeSkill = File(harness.root, "skills/alpha-skill/SKILL.md")
        val repoSkill = File(harness.root, "repo/alpha-skill/SKILL.md")

        harness.commands.setEnabled("alpha", true)
        await {
            val view = harness.gate.view("alpha")
            harness.notifications.get() == 1 && view != null && view.enabled &&
                view.skillIds == listOf("alpha-skill") && activeSkill.isFile
        }
        assertTrue(activeSkill.isFile)
        assertTrue(repoSkill.isFile)

        harness.commands.setEnabled("alpha", false)
        await {
            val view = harness.gate.view("alpha")
            harness.notifications.get() == 2 && view != null && !view.enabled &&
                view.contributions.skills.isEmpty() && view.skillIds.isEmpty()
        }
        assertFalse(activeSkill.exists())
        assertTrue(repoSkill.isFile)

        harness.gate.markLoaded(
            pluginId = "alpha",
            manifest = harness.manifest,
            plugin = SkillPlugin("alpha-skill"),
            host = testPluginHost(harness.manifest),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        harness.commands.setEnabled("alpha", true)
        await {
            val view = harness.gate.view("alpha")
            harness.notifications.get() == 3 && view != null && view.enabled &&
                view.skillIds == listOf("alpha-skill") && activeSkill.isFile
        }
        assertTrue(activeSkill.isFile)
    }
}
