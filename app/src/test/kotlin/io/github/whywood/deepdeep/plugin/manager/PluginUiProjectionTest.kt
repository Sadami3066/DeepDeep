package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginUiProjectionTest {
    private class PagedPlugin : CountingPlugin() {
        override fun skills(host: PluginHost): List<PluginSkill> = listOf(PluginSkill("demo", "# Demo"))

        override fun contributions(host: PluginHost): Contributions = Contributions()

        override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
            override val bindings = emptyList<io.github.whywood.deepdeep.plugin.api.SlotBinding>()

            override val pages = listOf(PluginPage("home", LocalizedText.Literal("Home")))
        }
    }

    private fun projection(): PluginUiProjection {
        val resolver = PluginTextResolver(resourceString = { "" }, language = { "default" })
        return PluginUiProjection(resolver)
    }

    private suspend fun registryFor(gate: PluginRuntimeRegistry): PluginRegistry {
        PluginContributionMaterializer(gate).refreshAll()
        return PluginRegistry().apply { register(gate.descriptor("alpha")!!) }
    }

    @Test
    fun pendingVersionProjectsPendingRestartFromRecord() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha", pendingVersion = "2.0.0"), managerRuntime(managerManifest("alpha"), CountingPlugin()))
        val registry = registryFor(gate)

        val snapshot = projection().snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(PluginPhaseUi.PENDING_RESTART, snapshot.phase)
        assertEquals(false, snapshot.updateAvailable)
    }

    @Test
    fun disabledPhaseWinsOverRuntimePhase() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha", enabled = false), managerRuntime(managerManifest("alpha"), CountingPlugin()))
        val registry = registryFor(gate)

        val snapshot = projection().snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(PluginPhaseUi.DISABLED, snapshot.phase)
        assertEquals(false, snapshot.enabled)
    }

    @Test
    fun projectsPagesAndSkills() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), PagedPlugin()))
        val registry = registryFor(gate)

        val snapshot = projection().snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(listOf(PluginPageUi("home", "Home")), snapshot.pages)
        assertEquals(listOf("demo"), snapshot.skills)
        assertEquals("1.0.0", snapshot.installedVersion)
    }
}
