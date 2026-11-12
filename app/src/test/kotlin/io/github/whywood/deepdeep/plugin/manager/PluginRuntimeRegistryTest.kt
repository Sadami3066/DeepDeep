package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.available

class PluginRuntimeRegistryTest {
    private fun registryWith(plugin: ManagerTestPlugin = ManagerTestPlugin()): Pair<PluginRuntimeRegistry, ManagedPlugin> {
        val gate = PluginRuntimeRegistry()
        val manifest = managerManifest("alpha")
        val runtime = managerRuntime(manifest, plugin)
        gate.register(managerRecord("alpha"), runtime)
        return gate to runtime
    }

    @Test
    fun registerRejectsDuplicateIds() {
        val (gate, _) = registryWith()
        assertFalse(gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), null)))
        assertEquals(listOf("alpha"), gate.ids())
    }

    @Test
    fun viewReflectsRecordAndRuntimeFacts() {
        val (gate, runtime) = registryWith()
        val view = gate.view("alpha") ?: error("missing view")
        assertTrue(view.enabled)
        assertTrue(view.installed)
        assertEquals("alpha", view.manifest.id)
        assertSame(runtime, gate.runtime("alpha"))
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun notYetActivatedPluginKeepsInstalledPhase() {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), plugin = null))
        assertEquals(PluginPhase.INSTALLED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun markFailureWritesLastErrorAndFailsPhase() {
        val (gate, _) = registryWith()
        assertNotNull(gate.markFailure("alpha", "boom"))
        assertEquals("boom", gate.view("alpha")?.record?.lastError)
        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)

        gate.clearFailure("alpha")
        assertNull(gate.view("alpha")?.record?.lastError)
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun materializedContributionsBecomeDescriptorContributionsOnce() {
        val (gate, _) = registryWith()
        val result = MaterializedPlugin(
            contributions = PluginContributions(tools = listOf(managerTool("alpha_tool"))),
            availability = PluginAvailabilityUi.Available,
            skills = emptyList(),
            failure = null,
        )
        val descriptor = gate.applyMaterialized("alpha", result)
        assertEquals(listOf("alpha_tool"), descriptor?.contributions?.tools?.map { it.name })
        assertEquals(PluginPhase.ACTIVE, descriptor?.phase)
    }

    @Test
    fun materializationFailureFailsPhaseAndRecordsError() {
        val (gate, _) = registryWith()
        val result = MaterializedPlugin(
            contributions = PluginContributions(),
            availability = PluginAvailabilityUi.Available,
            skills = emptyList(),
            failure = "materialize failed",
        )
        gate.applyMaterialized("alpha", result)
        assertEquals(PluginPhase.FAILED, gate.descriptor("alpha")?.phase)
        assertEquals("materialize failed", gate.view("alpha")?.record?.lastError)
    }

    @Test
    fun unavailablePluginIsDegraded() {
        val (gate, _) = registryWith()
        val result = MaterializedPlugin(
            contributions = PluginContributions(),
            availability = PluginAvailabilityUi.Unavailable("offline"),
            skills = emptyList(),
            failure = null,
        )
        gate.applyMaterialized("alpha", result)
        assertEquals(PluginPhase.DEGRADED, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun enabledFlagFlipsPhaseWithoutLosingRuntime() {
        val (gate, _) = registryWith()
        gate.setEnabled("alpha", false)
        assertEquals(PluginPhase.DISABLED, gate.descriptor("alpha")?.phase)
        assertNotNull(gate.runtime("alpha"))
        gate.setEnabled("alpha", true)
        assertEquals(PluginPhase.ACTIVE, gate.descriptor("alpha")?.phase)
    }

    @Test
    fun releaseIsAtomicAndReturnsFinalView() {
        val (gate, _) = registryWith()
        val released = gate.release("alpha")
        assertEquals("alpha", released?.id)
        assertNull(gate.view("alpha"))
        assertNull(gate.record("alpha"))
        assertNull(gate.runtime("alpha"))
        assertEquals(emptyList<String>(), gate.ids())
        assertNull(gate.release("alpha"))
    }

    @Test
    fun concurrentSkillUpdatesPublishWholeSnapshotsOnly() {
        val (gate, _) = registryWith()
        val first = (0 until 20).map { "skill_a_$it" }
        val second = (0 until 20).map { "skill_b_$it" }
        val start = CountDownLatch(1)
        val writer = Thread {
            start.await()
            repeat(500) { index -> gate.markSkills("alpha", if (index % 2 == 0) first else second) }
        }
        val failure = AtomicReference<Throwable?>(null)
        val reader = Thread {
            start.await()
            repeat(500) {
                val skills = gate.view("alpha")?.skillIds ?: return@repeat
                if (skills.isEmpty() || skills === first || skills === second) return@repeat
                failure.compareAndSet(null, AssertionError("torn snapshot: ${skills.size}"))
            }
        }
        listOf(writer, reader).forEach(Thread::start)
        start.countDown()
        listOf(writer, reader).forEach(Thread::join)

        assertNull(failure.get())
        assertTrue(gate.view("alpha")?.skillIds === first || gate.view("alpha")?.skillIds === second)
    }

    @Test
    fun concurrentEnableTogglesNeverCorruptRecord() {
        val (gate, _) = registryWith()
        val start = CountDownLatch(1)
        val threads = (0 until 8).map { index ->
            Thread {
                start.await()
                repeat(200) { round -> gate.setEnabled("alpha", (index + round) % 2 == 0) }
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)

        val record = gate.record("alpha")
        assertNotNull(record)
        assertEquals("alpha", record?.id)
        assertNotNull(gate.view("alpha"))
        assertEquals(1, gate.views().size)
    }
}

internal class ManagerTestPlugin : io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin {
    override suspend fun availability(host: io.github.whywood.deepdeep.plugin.api.PluginHost): io.github.whywood.deepdeep.plugin.api.PluginAvailability =
        io.github.whywood.deepdeep.plugin.api.available()
}
