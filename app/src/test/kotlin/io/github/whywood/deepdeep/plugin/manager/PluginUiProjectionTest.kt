package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PublisherInfo
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.contributions
import io.github.whywood.deepdeep.plugin.api.pluginPage
import io.github.whywood.deepdeep.plugin.api.pluginSkill
import io.github.whywood.deepdeep.plugin.host.external.HostPublisherInfo
import io.github.whywood.deepdeep.plugin.host.external.hostLocalizedText

class PluginUiProjectionTest {
    private class PagedPlugin : CountingPlugin() {
        override fun skills(host: PluginHost): List<PluginSkill> = listOf(pluginSkill("demo", "# Demo"))

        override fun contributions(host: PluginHost): Contributions = contributions()

        override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
            override val bindings = emptyList<io.github.whywood.deepdeep.plugin.api.SlotBinding>()

            override val pages = listOf(pluginPage("home", hostLocalizedText("Home"), content = {}))
        }
    }

    private class FakeStrings : PluginStrings {
        override fun get(id: Int): String = "string_$id"

        override fun get(id: Int, vararg args: Any): String = "retired:${args.joinToString()}"
    }

    private fun projection(): PluginUiProjection {
        val resolver = PluginTextResolver(language = { "default" })
        return PluginUiProjection(resolver, FakeStrings())
    }

    private fun externalRecord(apiLevel: Int): ExternalPluginRecord = ExternalPluginRecord(
        id = "alpha",
        version = "1.0.0",
        installedAt = 0L,
        installPath = "/tmp/alpha",
        activeBundleSha256 = "sha",
        size = 1L,
        publisherKeyId = "ed25519:cb08701a59d66606",
        apiLevel = apiLevel,
        uiApiLevel = 1,
    )

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
        assertEquals("2.0.0", snapshot.pendingVersion)
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
    fun publisherNameComesFromManifestWithRecordKeyId() = runTest {
        val gate = PluginRuntimeRegistry()
        val manifest = managerManifest("alpha").copy(
            publisher = HostPublisherInfo("milous", "ed25519:cb08"),
        )
        val runtime = managerRuntime(manifest, CountingPlugin()).apply {
            externalRecord = externalRecord(apiLevel = 4)
        }
        gate.register(managerRecord("alpha"), runtime)
        val registry = registryFor(gate)

        val snapshot = projection().snapshots(gate.views(), registry.snapshot()).single()

        assertEquals("milous", snapshot.publisher?.name)
        assertEquals("ed25519:cb08701a59d66606", snapshot.publisher?.keyId)
        assertEquals(null, snapshot.lastError)
    }

    @Test
    fun retiredApiLevelShowsUpdateNotice() = runTest {
        val gate = PluginRuntimeRegistry()
        val runtime = managerRuntime(managerManifest("alpha"), null).apply {
            externalRecord = externalRecord(apiLevel = 5)
        }
        gate.register(managerRecord("alpha", lastError = "plugin is not loaded"), runtime)
        val registry = registryFor(gate)

        val snapshot = projection().snapshots(gate.views(), registry.snapshot()).single()

        assertEquals("retired:5", snapshot.lastError)
        assertEquals(PluginAvailabilityUi.Unavailable("retired:5"), snapshot.availability)
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
