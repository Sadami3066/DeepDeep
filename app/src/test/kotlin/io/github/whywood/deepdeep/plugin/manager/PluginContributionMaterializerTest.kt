package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContributionMaterializerTest {
    private fun runtimeWith(plugin: CountingPlugin, builtin: Boolean = true): Pair<PluginRuntimeRegistry, PluginRuntimeView> {
        val gate = PluginRuntimeRegistry()
        val manifest = managerManifest("alpha", installable = !builtin)
        gate.register(managerRecord("alpha"), managerRuntime(manifest, plugin, builtin = builtin))
        return gate to gate.view("alpha")!!
    }

    @Test
    fun materializesEveryContributionMethodOncePerRefresh() = runTest {
        val plugin = CountingPlugin()
        val (gate, view) = runtimeWith(plugin)
        val materializer = PluginContributionMaterializer(gate)

        materializer.refreshAll()

        assertEquals(1, plugin.count("availability"))
        assertEquals(1, plugin.count("status"))
        assertEquals(1, plugin.count("settings"))
        assertEquals(1, plugin.count("skills"))
        assertEquals(1, plugin.count("contributions"))
        assertEquals(1, plugin.count("promptSections"))
        assertEquals(1, plugin.count("ui"))
        assertEquals(1, plugin.count("actions"))
        assertEquals(1, plugin.count("detailRows"))

        materializer.refreshAll()
        assertEquals(2, plugin.count("contributions"))
        assertEquals(2, plugin.count("status"))
    }

    @Test
    fun consumingMaterializedSnapshotDoesNotCallPluginAgain() = runTest {
        val plugin = CountingPlugin()
        val (gate, _) = runtimeWith(plugin)
        val materializer = PluginContributionMaterializer(gate)
        materializer.refreshAll()

        val descriptor = gate.descriptor("alpha")
        assertNotNull(descriptor)
        assertEquals(listOf("counted_tool"), descriptor?.contributions?.tools?.map { it.name })
        assertEquals(PluginPhase.ACTIVE, descriptor?.phase)
        assertEquals(1, plugin.count("contributions"))
        assertEquals(1, plugin.count("availability"))

        val callsAfterRefresh = listOf("contributions", "availability", "status", "ui").associateWith(plugin::count)
        gate.descriptors()
        gate.view("alpha")?.contributions?.tools?.size
        assertEquals(callsAfterRefresh, listOf("contributions", "availability", "status", "ui").associateWith(plugin::count))
    }

    @Test
    fun disabledPluginOnlyMaterializesDeclarations() = runTest {
        val plugin = CountingPlugin()
        val gate = PluginRuntimeRegistry()
        gate.register(
            managerRecord("alpha", enabled = false),
            managerRuntime(managerManifest("alpha"), plugin),
        )
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.DISABLED, gate.descriptor("alpha")?.phase)
        assertEquals(1, plugin.count("availability"))
        assertEquals(1, plugin.count("settings"))
        assertEquals(1, plugin.count("skills"))
        assertEquals(0, plugin.count("status"))
        assertEquals(0, plugin.count("contributions"))
        assertEquals(0, plugin.count("ui"))
        assertEquals(0, plugin.count("detailRows"))
    }

    @Test
    fun installablePluginWithoutInstalledVersionStaysInstalledWithoutStatus() = runTest {
        val plugin = CountingPlugin()
        val gate = PluginRuntimeRegistry()
        gate.register(
            managerRecord("alpha", installedVersion = null),
            managerRuntime(managerManifest("alpha", installable = true), plugin, builtin = false),
        )
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
        assertEquals(0, plugin.count("status"))
        assertEquals(0, plugin.count("contributions"))
        assertEquals(1, plugin.count("settings"))
    }

    @Test
    fun unavailablePluginIsDegradedAndKeepsContributionsEmpty() = runTest {
        val plugin = CountingPlugin(availabilityResult = PluginAvailability.Unavailable("offline"))
        val (gate, _) = runtimeWith(plugin)
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.DEGRADED, gate.descriptor("alpha")?.phase)
        assertEquals(0, plugin.count("status"))
        assertEquals(1, plugin.count("contributions"))
        assertEquals(PluginAvailabilityUi.Unavailable("offline"), gate.view("alpha")?.availability)
    }

    @Test
    fun failingContributionsMarkPluginFailedWithoutBreakingRefresh() = runTest {
        val plugin = CountingPlugin(contributionsError = IllegalStateException("broken contributions"))
        val (gate, _) = runtimeWith(plugin)
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)
        assertEquals("broken contributions", gate.view("alpha")?.record?.lastError)
        assertTrue(gate.view("alpha")?.contributions?.tools.isNullOrEmpty())
    }

    @Test
    fun failedStatusMarksPluginFailed() = runTest {
        val plugin = CountingPlugin(statusResult = PluginStatus.Failed("login expired"))
        val (gate, _) = runtimeWith(plugin)
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun unloadedEnabledPluginStaysInstalledInsteadOfFailed() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), plugin = null))
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
        assertNotNull(gate.view("alpha"))
    }
}
