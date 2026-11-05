package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.DdpSigning
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import io.github.whywood.deepdeep.plugin.host.external.PublisherTrustStore
import io.github.whywood.deepdeep.plugin.host.external.SyntheticDdp
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExternalUpdateSupportTest {
    private lateinit var dir: File
    private lateinit var trustStore: PublisherTrustStore
    private lateinit var recordStore: ExternalPluginRecordStore
    private lateinit var installer: PluginInstaller
    private lateinit var fetcher: FakePluginFetcher
    private lateinit var support: ExternalUpdateSupport
    private lateinit var key: SyntheticDdp.SigningKey

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("external-update-support").toFile()
        val pluginsRoot = PluginPaths.pluginsRoot(dir)
        trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        installer = PluginInstaller(
            pluginsRoot = pluginsRoot,
            trustStore = trustStore,
            recordStore = recordStore,
            hostVersion = HOST_VERSION,
        )
        fetcher = FakePluginFetcher()
        support = ExternalUpdateSupport(
            filesDir = dir,
            hostVersion = HOST_VERSION,
            installer = installer,
            trustStore = trustStore,
            recordStore = recordStore,
            fetcher = fetcher,
        )
        key = SyntheticDdp.newSigningKey()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun checkUpdatesReturnsNotConfiguredWithoutIndexUrl() = runBlocking {
        assertEquals(UpdateCheckResultUi.NotConfigured, support.checkUpdates())
    }

    @Test
    fun checkUpdatesMapsCandidatesFromFakeIndex() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        val v2 = packageV2()
        serveV2(v2)

        val result = support.checkUpdates()

        assertTrue(result is UpdateCheckResultUi.Ready)
        val ready = result as UpdateCheckResultUi.Ready
        assertFalse(ready.fromCache)
        assertNull(ready.note)
        val candidate = ready.candidates.single()
        assertEquals(SyntheticDdp.PLUGIN_ID, candidate.pluginId)
        assertEquals("1.0.0", candidate.installedVersion)
        assertEquals("1.1.0", candidate.availableVersion)
        assertEquals(DdpSigning.sha256Hex(v2), candidate.sha256)
        assertEquals(v2.length(), candidate.size)
        assertEquals(key.keyId, candidate.publisherKeyId)
        assertEquals(UpdateFixtures.PACKAGE_URL, candidate.url)
    }

    @Test
    fun checkUpdatesFallsBackToCacheWhenFetchFails() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        serveV2(packageV2())
        assertTrue(support.checkUpdates() is UpdateCheckResultUi.Ready)

        fetcher.textFailure = IOException("offline")
        val cached = support.checkUpdates()

        assertTrue(cached is UpdateCheckResultUi.Ready)
        val ready = cached as UpdateCheckResultUi.Ready
        assertTrue(ready.fromCache)
        assertNotNull(ready.note)
        assertEquals(1, ready.candidates.size)
    }

    @Test
    fun checkUpdatesReturnsUnavailableWithoutCache() = runBlocking {
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        fetcher.textFailure = IOException("offline")

        assertTrue(support.checkUpdates() is UpdateCheckResultUi.Unavailable)
    }

    @Test
    fun stageUpdateRejectsUnknownPlugin() = runBlocking {
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))

        val result = support.stageUpdate(SyntheticDdp.PLUGIN_ID)

        assertTrue(result is UpdateStageResultUi.Rejected)
        assertTrue((result as UpdateStageResultUi.Rejected).reason.contains("not installed"))
    }

    @Test
    fun stageUpdateRejectsPluginWithoutCandidate() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(
            UpdateFixtures.entryJson(version = "1.0.0", publisherKeyId = key.keyId),
        )

        val result = support.stageUpdate(SyntheticDdp.PLUGIN_ID)

        assertTrue(result is UpdateStageResultUi.Rejected)
        assertTrue((result as UpdateStageResultUi.Rejected).reason.contains("no update available"))
    }

    @Test
    fun stageUpdateWritesPendingRecordWithoutApplying() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        serveV2(packageV2())

        val result = support.stageUpdate(SyntheticDdp.PLUGIN_ID)

        assertTrue(result is UpdateStageResultUi.Staged)
        val staged = result as UpdateStageResultUi.Staged
        assertEquals(SyntheticDdp.PLUGIN_ID, staged.pluginId)
        assertEquals("1.1.0", staged.version)
        val record = requireNotNull(installer.record(SyntheticDdp.PLUGIN_ID))
        assertEquals("1.0.0", record.version)
        assertEquals("1.1.0", record.pendingVersion)
        assertTrue(installer.pendingBundle(SyntheticDdp.PLUGIN_ID) is BundleState.Ready)
    }

    @Test
    fun stageUpdateRejectsShaMismatch() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        val v2 = packageV2()
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(
            UpdateFixtures.entryJson(
                version = "1.1.0",
                sha256 = "f".repeat(64),
                size = v2.length(),
                publisherKeyId = key.keyId,
            ),
        )
        fetcher.packages[UpdateFixtures.PACKAGE_URL] = v2.readBytes()

        val result = support.stageUpdate(SyntheticDdp.PLUGIN_ID)

        assertTrue(result is UpdateStageResultUi.Rejected)
        assertTrue((result as UpdateStageResultUi.Rejected).reason.contains("SHA256_MISMATCH"))
    }

    @Test
    fun stageUpdateRejectsSecondStageWhilePending() = runBlocking {
        installV1()
        support.saveSettings(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        serveV2(packageV2())
        assertTrue(support.stageUpdate(SyntheticDdp.PLUGIN_ID) is UpdateStageResultUi.Staged)

        val second = support.stageUpdate(SyntheticDdp.PLUGIN_ID)

        assertTrue(second is UpdateStageResultUi.Rejected)
        assertTrue((second as UpdateStageResultUi.Rejected).reason.contains("pending"))
    }

    @Test
    fun settingsRoundTripThroughFacade() = runBlocking {
        support.saveSettings(
            PluginSourceSettings(
                indexUrl = "  https://example.test/plugins/index.json  ",
                autoCheck = false,
                intervalHours = 0,
            ),
        )

        val loaded = support.loadSettings()

        assertEquals("https://example.test/plugins/index.json", loaded.indexUrl)
        assertFalse(loaded.autoCheck)
        assertEquals(PluginUpdateDefaults.MIN_INTERVAL_HOURS, loaded.intervalHours)
    }

    private suspend fun installV1() {
        val result = installer.install(packageV1(), acceptPublisher = true)
        assertTrue(result is InstallResult.Installed)
    }

    private fun serveV2(v2: File) {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(
            UpdateFixtures.entryJson(
                version = "1.1.0",
                url = UpdateFixtures.PACKAGE_URL,
                sha256 = DdpSigning.sha256Hex(v2),
                size = v2.length(),
                publisherKeyId = key.keyId,
            ),
        )
        fetcher.packages[UpdateFixtures.PACKAGE_URL] = v2.readBytes()
    }

    private fun packageV1(): File = SyntheticDdp.build(dir, "v1.dpp", SyntheticDdp.BuildRequest(key = key))

    private fun packageV2(): File = SyntheticDdp.build(
        dir,
        "v2.dpp",
        SyntheticDdp.BuildRequest(
            manifest = SyntheticDdp.manifestJson(version = "1.1.0"),
            key = key,
        ),
    )

    private companion object {
        const val HOST_VERSION = "0.2.0-dev"
    }
}
