package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginUiProjectionTest {
    private class GroupedSettingsPlugin : DeepDeepPlugin {
        override suspend fun availability(host: PluginHost) = io.github.whywood.deepdeep.plugin.api.PluginAvailability.Available

        override suspend fun status(host: PluginHost) = io.github.whywood.deepdeep.plugin.api.PluginStatus.Ready("ok")

        override fun settings(): List<SettingSpec> = listOf(
            SettingSpec.Group(
                key = "group",
                title = LocalizedText.Literal("group"),
                items = listOf(SettingSpec.Number("limit", LocalizedText.Literal("limit"), 0, 10, 1)),
            ),
        )

        override fun contributions(host: PluginHost): Contributions = Contributions()
    }

    private fun projection(): Pair<PluginUiProjection, PluginSettingsStore> {
        val file = Files.createTempDirectory("plugin-ui").resolve("settings.json").toFile()
        val store = PluginSettingsStore(file, Dispatchers.Unconfined)
        val resolver = PluginTextResolver(resourceString = { "" }, language = { "default" })
        return PluginUiProjection(resolver, store) to store
    }

    @Test
    fun pendingVersionProjectsPendingRestartFromRecord() = runTest {
        val gate = PluginRuntimeRegistry()
        val runtime = managerRuntime(managerManifest("alpha"), CountingPlugin())
        gate.register(managerRecord("alpha", pendingVersion = "2.0.0"), runtime)
        PluginContributionMaterializer(gate).refreshAll()
        val registry = PluginRegistry().apply { register(gate.descriptor("alpha")!!) }
        val (projection, _) = projection()

        val snapshot = projection.snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(PluginPhaseUi.PENDING_RESTART, snapshot.phase)
        assertEquals(false, snapshot.updateAvailable)
    }

    @Test
    fun disabledPhaseWinsOverRuntimePhase() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha", enabled = false), managerRuntime(managerManifest("alpha"), CountingPlugin()))
        PluginContributionMaterializer(gate).refreshAll()
        val registry = PluginRegistry().apply { register(gate.descriptor("alpha")!!) }
        val (projection, _) = projection()

        val snapshot = projection.snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(PluginPhaseUi.DISABLED, snapshot.phase)
        assertEquals(false, snapshot.enabled)
    }

    @Test
    fun groupLeafSettingsAreProjectedAndPersistable() = runTest {
        val gate = PluginRuntimeRegistry()
        gate.register(managerRecord("alpha"), managerRuntime(managerManifest("alpha"), GroupedSettingsPlugin()))
        PluginContributionMaterializer(gate).refreshAll()
        val registry = PluginRegistry().apply { register(gate.descriptor("alpha")!!) }
        val (projection, store) = projection()
        store.set("alpha", "limit", "7")

        val snapshot = projection.snapshots(gate.views(), registry.snapshot()).single()

        assertEquals(listOf("group"), snapshot.settings.map { it.key })
        assertEquals("7", snapshot.settingsValues["limit"])
        assertEquals(snapshot.settings.settingLeaves().single().key, "limit")
    }
}
