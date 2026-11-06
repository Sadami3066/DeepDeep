package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContributionMaterializerTest {
    private fun runtimeWith(plugin: CountingPlugin): Pair<PluginRuntimeRegistry, PluginRuntimeView> {
        val gate = PluginRuntimeRegistry()
        val manifest = managerManifest("alpha")
        gate.register(managerRecord("alpha"), managerRuntime(manifest, plugin))
        return gate to gate.view("alpha")!!
    }

    @Test
    fun materializesEveryContributionMethodOncePerRefresh() = runTest {
        val plugin = CountingPlugin()
        val (gate, _) = runtimeWith(plugin)
        val materializer = PluginContributionMaterializer(gate)

        materializer.refreshAll()

        assertEquals(1, plugin.count("availability"))
        assertEquals(1, plugin.count("skills"))
        assertEquals(1, plugin.count("contributions"))
        assertEquals(1, plugin.count("promptSections"))
        assertEquals(1, plugin.count("ui"))

        materializer.refreshAll()
        assertEquals(2, plugin.count("contributions"))
        assertEquals(2, plugin.count("availability"))
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

        val callsAfterRefresh = listOf("contributions", "availability", "ui").associateWith(plugin::count)
        gate.descriptors()
        gate.view("alpha")?.contributions?.tools?.size
        assertEquals(callsAfterRefresh, listOf("contributions", "availability", "ui").associateWith(plugin::count))
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
        assertEquals(1, plugin.count("skills"))
        assertEquals(0, plugin.count("contributions"))
        assertEquals(0, plugin.count("promptSections"))
        assertEquals(0, plugin.count("ui"))
    }

    @Test
    fun unavailablePluginIsDegradedAndKeepsContributions() = runTest {
        val plugin = CountingPlugin(availabilityResult = PluginAvailability.Unavailable("offline"))
        val (gate, _) = runtimeWith(plugin)
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.DEGRADED, gate.descriptor("alpha")?.phase)
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
    fun unloadedEnabledPluginStaysInstalledInsteadOfFailed() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), plugin = null))
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
        assertNotNull(gate.view("alpha"))
    }

    @Test
    fun unloadedPluginWithoutErrorIsAvailable() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), plugin = null))
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginAvailabilityUi.Available, gate.view("alpha")?.availability)
        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun unloadedPluginWithErrorKeepsUnavailableReason() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(
            managerRecord("alpha", lastError = "boom"),
            managerRuntime(managerManifest("alpha"), plugin = null),
        )
        PluginContributionMaterializer(gate).refreshAll()

        assertEquals(PluginAvailabilityUi.Unavailable("boom"), gate.view("alpha")?.availability)
    }
}
