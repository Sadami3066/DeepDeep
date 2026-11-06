package io.github.whywood.deepdeep.plugin.manager

import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.events.EventRecordingHooks
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.events.probeHooks
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionHooksTest {
    @Test
    fun toolsMaterializeContributionsOncePerSessionBinding() = runTest {
        val plugin = CountingPlugin()
        val fixture = Fixture()
        fixture.add(plugin)
        val binding = binding()

        assertEquals(listOf("counted_tool"), fixture.hooks.tools(binding).map { it.name })
        fixture.hooks.tools(binding)
        assertEquals(1, plugin.count("contributionsFor"))

        fixture.hooks.tools(binding())
        assertEquals(2, plugin.count("contributionsFor"))
    }

    @Test
    fun disabledPluginContributesNoSessionTools() = runTest {
        val plugin = CountingPlugin()
        val fixture = Fixture()
        fixture.add(plugin, enabled = false)

        assertTrue(fixture.hooks.tools(binding()).isEmpty())
        assertEquals(0, plugin.count("contributionsFor"))
    }

    @Test
    fun contributionsForFailureIsIsolatedAndOnlyRecordsError() = runTest {
        val plugin = CountingPlugin()
        val fixture = Fixture()
        fixture.add(plugin)
        plugin.contributionsError = IllegalStateException("session contributions failed")

        assertTrue(fixture.hooks.tools(binding()).isEmpty())
        assertEquals("session contributions failed", fixture.gate.view(SESSION_PLUGIN_ID)?.record?.lastError)
        assertEquals(PluginPhase.ACTIVE, fixture.gate.descriptor(SESSION_PLUGIN_ID)?.phase)
    }

    @Test
    fun sessionHooksFromContributionsForAreDispatchedAfterBind() = runTest {
        val recorder = EventRecordingHooks()
        val fixture = Fixture()
        fixture.add(SessionPlugin(sessionHooks = recorder))
        fixture.hooks.bind(binding(), PluginHooksDelegate())
        recorder.calls.clear()

        fixture.hooks.dispatchRuntimeEvent(runStarted())

        assertEquals(listOf("runStarted"), recorder.calls)
    }

    @Test
    fun defaultSessionContributionsDeliverHooksOnce() = runTest {
        val recorder = EventRecordingHooks()
        val fixture = Fixture()
        fixture.add(SessionPlugin(baseHooks = recorder))
        fixture.hooks.bind(binding(), PluginHooksDelegate())
        recorder.calls.clear()

        fixture.hooks.dispatchRuntimeEvent(runStarted())

        assertEquals(listOf("runStarted"), recorder.calls)
    }

    @Test
    fun sessionContributionsOverrideBaseHooksOfSamePlugin() = runTest {
        val base = probeHooks(onPrompt = { request -> "base:${request.prompt}" })
        val session = probeHooks(onPrompt = { request -> "session:${request.prompt}" })
        val fixture = Fixture()
        fixture.add(SessionPlugin(baseHooks = base, sessionHooks = session))
        val delegate = PluginHooksDelegate()

        fixture.hooks.bind(binding(), delegate)

        assertEquals("session:hi", delegate.promptTransform("hi"))
    }

    @Test
    fun toolsOnlySessionContributionsKeepGlobalHooks() = runTest {
        val recorder = EventRecordingHooks()
        val fixture = Fixture()
        fixture.add(SessionPlugin(baseHooks = recorder, sessionHooks = PluginHooks.None))
        fixture.hooks.bind(binding(), PluginHooksDelegate())
        recorder.calls.clear()

        fixture.hooks.dispatchRuntimeEvent(runStarted())

        assertEquals(listOf("runStarted"), recorder.calls)
    }

    @Test
    fun toolsAndHooksShareSingleContributionsForCall() = runTest {
        val recorder = EventRecordingHooks()
        val plugin = SessionPlugin(baseHooks = recorder)
        val fixture = Fixture()
        fixture.add(plugin)
        val binding = binding()

        assertEquals(listOf("counted_tool"), fixture.hooks.tools(binding).map { it.name })
        fixture.hooks.bind(binding, PluginHooksDelegate())

        assertEquals(1, plugin.count("contributionsFor"))
    }

    @Test
    fun frozenRunHooksSurvivePluginDisableUntilRunFinish() = runTest {
        val recorder = EventRecordingHooks()
        val fixture = Fixture()
        fixture.add(SessionPlugin(baseHooks = recorder))
        fixture.hooks.bind(binding(), PluginHooksDelegate())
        recorder.calls.clear()
        fixture.hooks.dispatchRuntimeEvent(runStarted())

        fixture.setEnabled(SESSION_PLUGIN_ID, false)
        assertTrue(fixture.registry.snapshot().hooks.isEmpty())

        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(1))
        assertEquals(listOf("runStarted", "retryScheduled"), recorder.calls)

        fixture.hooks.dispatchRuntimeEvent(runFinished())
        assertEquals(listOf("runStarted", "retryScheduled", "runFinished"), recorder.calls)

        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(2))
        fixture.hooks.dispatchRuntimeEvent(runStarted(runId = "run-2"))
        assertEquals(3, recorder.calls.size)
    }

    @Test
    fun frozenRunTransformsUseFrozenHooks() = runTest {
        val session = probeHooks(onPrompt = { request -> "frozen:${request.prompt}" })
        val fixture = Fixture()
        fixture.add(SessionPlugin(sessionHooks = session))
        val delegate = PluginHooksDelegate()
        fixture.hooks.bind(binding(), delegate)
        fixture.hooks.dispatchRuntimeEvent(runStarted())

        fixture.setEnabled(SESSION_PLUGIN_ID, false)
        assertEquals("frozen:hi", delegate.promptTransform("hi"))

        fixture.hooks.dispatchRuntimeEvent(runFinished())
        assertEquals("hi", delegate.promptTransform("hi"))
    }

    @Test
    fun runFinishRefreshesSnapshotForNextRun() = runTest {
        val first = EventRecordingHooks()
        val second = EventRecordingHooks()
        val fixture = Fixture()
        fixture.add(SessionPlugin(baseHooks = first))
        fixture.hooks.bind(binding(), PluginHooksDelegate())
        first.calls.clear()
        fixture.hooks.dispatchRuntimeEvent(runStarted())

        fixture.add(SessionPlugin(id = "beta", baseHooks = second))
        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(1))

        assertEquals(listOf("runStarted", "retryScheduled"), first.calls)
        assertTrue(second.calls.isEmpty())

        fixture.hooks.dispatchRuntimeEvent(runFinished())
        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(2))

        assertEquals(listOf("retryScheduled"), second.calls)
    }

    @Test
    fun runFinishReleasesEmptyFrozenRunAndPicksUpNewHooks() = runTest {
        val recorder = EventRecordingHooks()
        val fixture = Fixture()
        fixture.hooks.bind(binding(), PluginHooksDelegate())

        fixture.hooks.dispatchRuntimeEvent(runStarted())
        fixture.add(SessionPlugin(baseHooks = recorder))
        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(1))
        assertTrue(recorder.calls.isEmpty())

        fixture.hooks.dispatchRuntimeEvent(runFinished())
        fixture.hooks.dispatchSessionEvent(SESSION_ID, retryScheduled(2))

        assertEquals(listOf("retryScheduled"), recorder.calls)
    }

    private class Fixture {
        val gate = PluginRuntimeRegistry()
        val registry = PluginRegistry()
        val hooks = PluginSessionHooks(CoroutineScope(Dispatchers.Unconfined), gate, registry)

        suspend fun add(plugin: CountingPlugin, enabled: Boolean = true) {
            gate.register(managerRecord(SESSION_PLUGIN_ID, enabled = enabled), managerRuntime(managerManifest(SESSION_PLUGIN_ID), plugin))
            refresh()
        }

        suspend fun add(plugin: SessionPlugin, enabled: Boolean = true) {
            gate.register(managerRecord(plugin.id, enabled = enabled), managerRuntime(managerManifest(plugin.id), plugin))
            refresh()
        }

        suspend fun setEnabled(pluginId: String, enabled: Boolean) {
            gate.setEnabled(pluginId, enabled)
            refresh()
        }

        suspend fun refresh() {
            PluginContributionMaterializer(gate).refreshAll()
            gate.publishTo(registry)
            hooks.refreshSnapshots()
        }
    }

    private class SessionPlugin(
        val id: String = SESSION_PLUGIN_ID,
        private val baseHooks: PluginHooks = PluginHooks.None,
        private val sessionHooks: PluginHooks? = null,
        private val tools: List<String> = listOf("counted_tool"),
    ) : DeepDeepPlugin {
        private val hits = ConcurrentHashMap<String, Int>()

        fun count(name: String): Int = hits[name] ?: 0

        private fun hit(name: String) {
            hits.merge(name, 1) { current, _ -> current + 1 }
        }

        override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

        override suspend fun status(host: PluginHost): PluginStatus = PluginStatus.Ready("ready")

        override fun contributions(host: PluginHost): Contributions {
            hit("contributions")
            return Contributions(tools = tools.map(::managerTool), hooks = baseHooks)
        }

        override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
            hit("contributionsFor")
            return sessionHooks?.let { hooks ->
                Contributions(tools = tools.map(::managerTool), hooks = hooks)
            } ?: contributions(host)
        }
    }

    private companion object {
        const val SESSION_ID = "session-1"
        const val SESSION_PLUGIN_ID = "alpha"

        fun binding(): SessionBinding = SessionBinding(supportsChatDelivery = true).also { binding ->
            binding.bind(BoundSession(sessionId = SESSION_ID, sessionFile = null, cwd = "/workspace"))
        }

        fun runStarted(runId: String = "run-1"): SessionRuntimeEvent =
            SessionRuntimeEvent(SESSION_ID, RuntimeEvent.RunStarted(runId, 0L))

        fun runFinished(runId: String = "run-1"): SessionRuntimeEvent =
            SessionRuntimeEvent(SESSION_ID, RuntimeEvent.RunFinished(runId, "", RunStatus.DONE, 0L))

        fun retryScheduled(attempt: Int): SessionEvent = SessionEvent.RetryScheduled(attempt, 500L, null)
    }
}
