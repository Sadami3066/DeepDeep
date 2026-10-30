package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginSnapshot
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginsUiState as ManagerPluginsUiState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginsStateHolderTest {
    private class FakePlugin(
        override val id: String,
        override val kind: PluginKind = PluginKind.EXTERNAL,
        override val nameRes: Int = 1,
        override val summaryRes: Int = 2,
    ) : OfficialPlugin {
        override val icon = DdIcon(0)
        override val version = "1.0"
        override val sizeLabelRes = 3

        override suspend fun availability(): PluginAvailability = PluginAvailability.Available

        override suspend fun install(progress: PluginProgress) {}

        override suspend fun uninstall() {}

        override suspend fun status(): PluginStatus = PluginStatus.Ready("ready")
    }

    private fun snapshot(
        id: String,
        kind: PluginKind = PluginKind.EXTERNAL,
        enabled: Boolean = true,
        settings: Map<String, String> = emptyMap(),
    ) = PluginSnapshot(
        plugin = FakePlugin(id, kind),
        installedVersion = "1.0",
        availability = PluginAvailability.Available,
        status = PluginStatus.Ready("ready"),
        enabled = enabled,
        settings = settings,
    )

    private class FakeSource : PluginsDataSource {
        private val mutable = MutableStateFlow(ManagerPluginsUiState())
        override val state: StateFlow<ManagerPluginsUiState> = mutable

        var loadError: Exception? = null
        var loadCalls = 0
        var refreshError: Exception? = null
        var refreshCalls = 0
        var activeRefreshes = 0
        var maxActiveRefreshes = 0
        var refreshGate: CompletableDeferred<Unit>? = null
        var setEnabledError: Exception? = null
        var setEnabledGate: CompletableDeferred<Unit>? = null
        var setSettingError: Exception? = null
        var uninstallError: Exception? = null
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()
        val settingCalls = mutableListOf<Triple<String, String, String>>()
        val uninstallCalls = mutableListOf<String>()

        fun publish(
            plugins: List<PluginSnapshot>,
            loading: Boolean = false,
            busy: Boolean = false,
            error: String? = null,
        ) {
            mutable.update {
                it.copy(plugins = plugins, loading = loading, busy = busy, error = error)
            }
        }

        fun snapshotOf(pluginId: String): PluginSnapshot? = mutable.value.plugins.firstOrNull { it.plugin.id == pluginId }

        override suspend fun load() {
            loadCalls++
            loadError?.let { throw it }
        }

        override suspend fun refresh() {
            refreshCalls++
            activeRefreshes++
            maxActiveRefreshes = maxOf(maxActiveRefreshes, activeRefreshes)
            try {
                refreshGate?.await()
                refreshError?.let { throw it }
            } finally {
                activeRefreshes--
            }
        }

        override suspend fun install(pluginId: String) {}

        override suspend fun update(pluginId: String) {}

        override suspend fun uninstall(pluginId: String) {
            uninstallError?.let { throw it }
            uninstallCalls += pluginId
            publish(mutable.value.plugins.filterNot { it.plugin.id == pluginId })
        }

        override suspend fun runAction(pluginId: String, actionId: String) {}

        override suspend fun setEnabled(pluginId: String, enabled: Boolean) {
            setEnabledGate?.await()
            setEnabledError?.let { throw it }
            enabledCalls += pluginId to enabled
            publish(
                mutable.value.plugins.map { snapshot ->
                    if (snapshot.plugin.id == pluginId) snapshot.copy(enabled = enabled) else snapshot
                },
            )
        }

        override suspend fun setSetting(pluginId: String, key: String, value: String) {
            setSettingError?.let { throw it }
            settingCalls += Triple(pluginId, key, value)
            publish(
                mutable.value.plugins.map { snapshot ->
                    if (snapshot.plugin.id == pluginId) {
                        snapshot.copy(settings = snapshot.settings + (key to value))
                    } else {
                        snapshot
                    }
                },
            )
        }
    }

    private class Harness {
        val scope = CoroutineScope(Job() + UnconfinedTestDispatcher())
        val source = FakeSource()
        val holder = PluginsStateHolder(source, scope)

        fun close() {
            scope.cancel()
        }
    }

    private fun harness(): Harness = Harness()

    @Test
    fun loadMirrorsPluginsAndSplitsExternalAndInternal() = runTest {
        val h = harness()
        h.source.publish(
            listOf(
                snapshot("feishu"),
                snapshot("github"),
                snapshot("goal", kind = PluginKind.INTERNAL),
            ),
        )

        h.holder.load()

        assertEquals(1, h.source.loadCalls)
        assertEquals(listOf("feishu", "github"), h.holder.state.value.external.map { it.plugin.id })
        assertEquals(listOf("goal"), h.holder.state.value.internal.map { it.plugin.id })
        assertNull(h.holder.state.value.error)
        h.close()
    }

    @Test
    fun loadFailureSetsErrorAndRetryReloads() = runTest {
        val h = harness()
        h.source.loadError = IOException("records failed")

        h.holder.load()
        assertEquals("records failed", h.holder.state.value.error)

        h.source.loadError = null
        h.holder.retry()

        assertNull(h.holder.state.value.error)
        assertEquals(2, h.source.loadCalls)
        h.close()
    }

    @Test
    fun setEnabledFailureSetsErrorAndClearsTogglingId() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu")))
        h.source.setEnabledError = IOException("write failed")

        h.holder.setEnabled("feishu", false)

        assertEquals("write failed", h.holder.state.value.error)
        assertTrue(h.holder.state.value.togglingIds.isEmpty())
        assertTrue(h.source.enabledCalls.isEmpty())
        h.close()
    }

    @Test
    fun setEnabledTracksTogglingIdWhileRunning() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu")))
        val gate = CompletableDeferred<Unit>()
        h.source.setEnabledGate = gate

        val running = async { h.holder.setEnabled("feishu", false) }
        advanceUntilIdle()
        assertTrue(h.holder.state.value.isToggling("feishu"))

        gate.complete(Unit)
        running.await()
        advanceUntilIdle()

        assertFalse(h.holder.state.value.isToggling("feishu"))
        assertEquals(listOf("feishu" to false), h.source.enabledCalls)
        assertFalse(h.holder.state.value.snapshot("feishu")!!.enabled)
        h.close()
    }

    @Test
    fun queryFiltersLocalizedPluginItems() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu"), snapshot("github")))
        val items = listOf(
            PluginListItem(h.holder.state.value.snapshot("feishu")!!, "飞书", "同步消息"),
            PluginListItem(h.holder.state.value.snapshot("github")!!, "GitHub", "代码仓库"),
        )

        h.holder.setQuery("代码")
        assertEquals(listOf("github"), filterPlugins(items, h.holder.state.value.searchQuery).map { it.snapshot.plugin.id })

        h.holder.setQuery("message")
        assertTrue(filterPlugins(items, h.holder.state.value.searchQuery).isEmpty())

        h.holder.setQuery("")
        assertEquals(2, filterPlugins(items, h.holder.state.value.searchQuery).size)
        h.close()
    }

    @Test
    fun setSettingForwardsAndMirrorsSavedValue() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu", settings = mapOf("scope" to "read"))))

        h.holder.setSetting("feishu", "scope", "write")

        assertEquals(listOf(Triple("feishu", "scope", "write")), h.source.settingCalls)
        assertEquals("write", h.holder.state.value.snapshot("feishu")!!.settings["scope"])
        assertNull(h.holder.state.value.error)
        h.close()
    }

    @Test
    fun setSettingFailureSetsError() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu")))
        h.source.setSettingError = IOException("settings read only")

        h.holder.setSetting("feishu", "scope", "write")

        assertEquals("settings read only", h.holder.state.value.error)
        h.close()
    }

    @Test
    fun uninstallRemovesPluginFromMirroredState() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu"), snapshot("github")))

        h.holder.uninstall("feishu")

        assertEquals(listOf("feishu"), h.source.uninstallCalls)
        assertEquals(listOf("github"), h.holder.state.value.plugins.map { it.plugin.id })
        h.close()
    }

    @Test
    fun concurrentRefreshesCoalesceIntoOneFollowUp() = runTest {
        val h = harness()
        h.source.publish(listOf(snapshot("feishu")))
        h.holder.load()
        h.source.refreshCalls = 0
        h.source.maxActiveRefreshes = 0
        val gate = CompletableDeferred<Unit>()
        h.source.refreshGate = gate

        val first = async { h.holder.refresh() }
        val second = async { h.holder.refresh() }
        val third = async { h.holder.refresh() }
        advanceUntilIdle()
        assertEquals(1, h.source.refreshCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        third.await()

        assertEquals(2, h.source.refreshCalls)
        assertEquals(1, h.source.maxActiveRefreshes)
        assertNull(h.holder.state.value.error)
        h.close()
    }

    @Test
    fun refreshFailureSetsError() = runTest {
        val h = harness()
        h.source.refreshError = IOException("refresh failed")

        h.holder.refresh()

        assertEquals("refresh failed", h.holder.state.value.error)
        assertNotNull(h.holder.state.value.error)
        h.close()
    }
}
