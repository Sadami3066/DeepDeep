package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginExternalFlowTest {
    @Test
    fun activateStagedNowSyncsSkillsAfterRefreshBeforeNotifyingcontributions() = runTest {
        val gate = PluginRuntimeRegistry()
        val root = Files.createTempDirectory("plugin-external-flow").toFile()
        val lifecycle = PluginLifecycle(
            parentScope = CoroutineScope(SupervisorJob()),
            hostFactory = { _, manifest, scope -> testPluginHost(manifest, scope) },
            gate = gate,
            skillStore = PluginSkillStore(root.resolve("skills"), root.resolve("repo")),
            external = null,
        )
        val manifest = managerManifest("alpha")
        gate.register(managerRecord("alpha"), lifecycle.createRuntime(managerRecord("alpha"), manifest))
        gate.markLoaded(
            pluginId = "alpha",
            manifest = manifest,
            plugin = CountingPlugin(),
            host = testPluginHost(manifest, gate.view("alpha")!!.scope),
            scope = gate.view("alpha")!!.scope,
        )
        gate.markSkills("alpha", listOf("legacy"))
        val materializer = PluginContributionMaterializer(gate)
        val events = mutableListOf<String>()
        val skillsSeenByContributions = mutableListOf<List<String>>()
        val flow = PluginExternalFlow(
            gate = gate,
            external = null,
            strings = object : PluginStrings {
                override fun get(id: Int): String = ""

                override fun get(id: Int, vararg args: Any): String = ""
            },
            textResolver = PluginTextResolver(),
            lifecycle = lifecycle,
            refresh = {
                materializer.refreshAll()
                events += "refresh"
            },
            onContributionsChanged = {
                events += "contributions"
                skillsSeenByContributions += gate.view("alpha")?.skillIds.orEmpty()
            },
        )

        assertTrue(flow.activateStagedNow("alpha"))

        assertEquals(listOf("refresh", "contributions"), events)
        assertEquals(listOf(emptyList<String>()), skillsSeenByContributions)
        assertEquals(emptyList<String>(), gate.view("alpha")?.skillIds)
    }
}
