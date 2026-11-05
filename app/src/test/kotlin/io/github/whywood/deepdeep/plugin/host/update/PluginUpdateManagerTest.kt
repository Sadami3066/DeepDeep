package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.PluginPhase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginUpdateManagerTest {
    private lateinit var dir: File
    private lateinit var pluginsRoot: File
    private lateinit var trustStore: PublisherTrustStore
    private lateinit var recordStore: ExternalPluginRecordStore
    private lateinit var installer: PluginInstaller
    private lateinit var settingsStore: PluginSourceSettingsStore
    private lateinit var fetcher: FakePluginFetcher
    private lateinit var manager: PluginUpdateManager
    private lateinit var key: SyntheticDdp.SigningKey

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-update-manager").toFile()
        pluginsRoot = File(dir, "plugins")
        trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        installer = PluginInstaller(
            pluginsRoot = pluginsRoot,
            trustStore = trustStore,
            recordStore = recordStore,
            hostVersion = HOST_VERSION,
        )
        settingsStore = PluginSourceSettingsStore(PluginUpdateDefaults.settingsFile(dir))
        fetcher = FakePluginFetcher()
        val client = PluginIndexClient(PluginUpdateDefaults.cacheFile(dir), fetcher)
        val downloader = PluginPackageDownloader(fetcher, PluginUpdateDefaults.downloadsDir(dir))
        manager = PluginUpdateManager(
            client = client,
            settingsStore = settingsStore,
            installer = installer,
            trustStore = trustStore,
            downloader = downloader,
            policy = UpdatePolicy(hostVersion = HOST_VERSION),
        )
        key = SyntheticDdp.newSigningKey()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun checkFindsUpdateAndStagesItForNextRestart() = runBlocking {
        installV1()
        settingsStore.save(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        val v2 = packageV2()
        serveV2(v2)

        val check = manager.check()

        assertTrue(check is UpdateCheckResult.Ready)
        val ready = check as UpdateCheckResult.Ready
        val candidate = ready.candidates.single()
        assertEquals("1.1.0", candidate.entry.version)
        assertEquals("1.0.0", candidate.installedVersion)
        assertEquals(key.keyId, candidate.entry.publisherKeyId)
        assertFalse(ready.fromCache)

        val progress = mutableListOf<Int?>()
        val result = manager.downloadAndStage(candidate) { progress += it }

        assertTrue(result is UpdateStageResult.Staged)
        val record = requireNotNull(installer.record(SyntheticDdp.PLUGIN_ID))
        assertEquals("1.0.0", record.version)
        assertEquals("1.1.0", record.pendingVersion)
        assertEquals(PluginPhase.PENDING_RESTART, record.phase)
        assertTrue(installer.pendingBundle(SyntheticDdp.PLUGIN_ID) is BundleState.Ready)
        assertEquals(100, progress.last() ?: -1)
        assertTrue(downloadsLeftover().isEmpty())
    }

    @Test
    fun checkReturnsNotConfiguredWithoutSourceSettings() = runBlocking {
        installV1()

        assertEquals(UpdateCheckResult.NotConfigured, manager.check())
    }

    @Test
    fun checkReturnsUnavailableWhenFetchFailsWithoutCache() = runBlocking {
        installV1()
        settingsStore.save(PluginSourceSettings(indexUrl = UpdateFixtures.INDEX_URL))
        fetcher.textFailure = IOException("offline")

        val check = manager.check()

        assertTrue(check is UpdateCheckResult.Unavailable)
    }

    @Test
    fun rejectsDownloadedPackageWithWrongSha256() = runBlocking {
        installV1()
        val v2 = packageV2()
        fetcher.packages[UpdateFixtures.PACKAGE_URL] = v2.readBytes()
        val entry = UpdateFixtures.entry(
            version = "1.1.0",
            sha256 = "f".repeat(64),
            size = v2.length(),
            publisherKeyId = key.keyId,
        )

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.SHA256_MISMATCH, (result as UpdateStageResult.Rejected).code)
        assertNull(installer.record(SyntheticDdp.PLUGIN_ID)?.pendingVersion)
    }

    @Test
    fun rejectsSizeMismatchBeforeStaging() = runBlocking {
        installV1()
        val v2 = packageV2()
        fetcher.packages[UpdateFixtures.PACKAGE_URL] = v2.readBytes()
        val entry = UpdateFixtures.entry(
            version = "1.1.0",
            sha256 = DdpSigning.sha256Hex(v2),
            size = v2.length() + 1,
            publisherKeyId = key.keyId,
        )

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.SIZE_MISMATCH, (result as UpdateStageResult.Rejected).code)
    }

    @Test
    fun rejectsIndexPublisherDifferentFromInstalledRecord() = runBlocking {
        installV1()
        val entry = UpdateFixtures.entry(version = "1.1.0", publisherKeyId = UpdateFixtures.OTHER_KEY_ID)

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", UpdateFixtures.OTHER_KEY_ID))

        assertEquals(UpdateRejection.PUBLISHER_MISMATCH, (result as UpdateStageResult.Rejected).code)
    }

    @Test
    fun rejectsPackageSignedByDifferentPublisherThanIndex() = runBlocking {
        installV1()
        val other = SyntheticDdp.newSigningKey()
        val v2 = SyntheticDdp.build(
            dir,
            "v2-other-key.dpp",
            SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(version = "1.1.0"), key = other),
        )
        fetcher.packages[UpdateFixtures.PACKAGE_URL] = v2.readBytes()
        val entry = UpdateFixtures.entry(
            version = "1.1.0",
            sha256 = DdpSigning.sha256Hex(v2),
            size = v2.length(),
            publisherKeyId = key.keyId,
        )

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.PUBLISHER_MISMATCH, (result as UpdateStageResult.Rejected).code)
        assertNull(installer.record(SyntheticDdp.PLUGIN_ID)?.pendingVersion)
    }

    @Test
    fun rejectsUntrustedPublisher() = runBlocking {
        installV1()
        trustStore.untrust(key.keyId)
        val entry = entryFor(packageV2())

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.PUBLISHER_UNTRUSTED, (result as UpdateStageResult.Rejected).code)
    }

    @Test
    fun rejectsVersionThatIsNotNewer() = runBlocking {
        installV1()
        val entry = UpdateFixtures.entry(version = "1.0.0", publisherKeyId = key.keyId)

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.NOT_NEWER, (result as UpdateStageResult.Rejected).code)
    }

    @Test
    fun rejectsPendingPluginUpdate() = runBlocking {
        installV1()
        installer.install(packageV2())
        val entry = UpdateFixtures.entry(version = "1.2.0", publisherKeyId = key.keyId)

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.PENDING_EXISTS, (result as UpdateStageResult.Rejected).code)
    }

    @Test
    fun rejectsUnknownPlugin() = runBlocking {
        val entry = UpdateFixtures.entry(version = "1.1.0", publisherKeyId = key.keyId)

        val result = manager.downloadAndStage(PluginUpdateCandidate(entry, "1.0.0", key.keyId))

        assertEquals(UpdateRejection.UNKNOWN_PLUGIN, (result as UpdateStageResult.Rejected).code)
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

    private fun entryFor(v2: File): PluginIndexEntry = UpdateFixtures.entry(
        version = "1.1.0",
        sha256 = DdpSigning.sha256Hex(v2),
        size = v2.length(),
        publisherKeyId = key.keyId,
    )

    private fun downloadsLeftover(): List<File> =
        PluginUpdateDefaults.downloadsDir(dir).listFiles()?.filter { it.isFile }.orEmpty()

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
