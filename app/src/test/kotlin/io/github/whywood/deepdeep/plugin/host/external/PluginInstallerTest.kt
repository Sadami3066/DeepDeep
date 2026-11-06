package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File
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

class PluginInstallerTest {
    private lateinit var dir: File
    private lateinit var pluginsRoot: File
    private lateinit var trustStore: PublisherTrustStore
    private lateinit var recordStore: ExternalPluginRecordStore
    private lateinit var key: SyntheticDdp.SigningKey

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-installer").toFile()
        pluginsRoot = File(dir, "plugins")
        trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        key = SyntheticDdp.newSigningKey()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun freshInstallStagesFilesAndRecord() = runBlocking {
        val installer = installer()
        val pkg = packageV1()

        val result = installer.install(pkg, acceptPublisher = true)

        assertTrue(result is InstallResult.Installed)
        val record = (result as InstallResult.Installed).record
        assertEquals(SyntheticDdp.PLUGIN_ID, record.id)
        assertEquals(SyntheticDdp.PLUGIN_VERSION, record.version)
        assertEquals(key.keyId, record.publisherKeyId)
        assertTrue(record.enabled)
        assertEquals(PluginPaths.versionRoot(pluginsRoot, record.id, record.version).absolutePath, record.installPath)
        val versionRoot = File(record.installPath)
        val bundle = File(File(versionRoot, PluginPaths.BUNDLE_DIR), "plugin.dpp")
        assertTrue(bundle.isFile)
        assertEquals(DdpSigning.sha256Hex(bundle), record.activeBundleSha256)
        assertTrue(File(File(versionRoot, PluginPaths.ASSETS_DIR), "icon.png").isFile)
        assertTrue(File(File(versionRoot, PluginPaths.ASSETS_DIR), "plugin.json").isFile)
        assertTrue(File(File(versionRoot, PluginPaths.LIB_DIR), "arm64-v8a/libsample.so").isFile)
        assertNotNull(trustStore.trusted(key.keyId))
        assertEquals(record, recordStore.get(record.id))
        val state = installer.activeBundle(record.id)
        assertTrue(state is BundleState.Ready)
        assertEquals(bundle, (state as BundleState.Ready).bundleFile)
    }

    @Test
    fun requiresPublisherConfirmationBeforeTrusting() = runBlocking {
        val installer = installer()

        val result = installer.install(packageV1())

        assertTrue(result is InstallResult.NeedsPublisherConfirmation)
        val confirmation = result as InstallResult.NeedsPublisherConfirmation
        assertEquals(key.keyId, confirmation.publisher.keyId)
        assertEquals(SyntheticDdp.PLUGIN_ID, confirmation.manifest.id)
        assertNull(recordStore.get(SyntheticDdp.PLUGIN_ID))
        assertNull(trustStore.trusted(key.keyId))
        assertFalse(PluginPaths.versionRoot(pluginsRoot, SyntheticDdp.PLUGIN_ID, SyntheticDdp.PLUGIN_VERSION).exists())
    }

    @Test
    fun inspectReportsPublisherAndTrustState() = runBlocking {
        val installer = installer()
        val pkg = packageV1()

        val before = installer.inspect(pkg)
        assertTrue(before is InspectionResult.Ready)
        val beforeReady = before as InspectionResult.Ready
        assertEquals(key.keyId, beforeReady.publisher.keyId)
        assertNull(beforeReady.trusted)
        assertNull(beforeReady.installed)

        installer.install(pkg, acceptPublisher = true)
        val after = installer.inspect(pkg) as InspectionResult.Ready
        assertNotNull(after.trusted)
        assertNotNull(after.installed)
        assertEquals(after.digests.entryDigest, beforeReady.digests.entryDigest)
    }

    @Test
    fun trustedPublisherSkipsConfirmationOnUpdate() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)

        val result = installer.install(packageV2())

        assertTrue(result is InstallResult.Staged)
    }

    @Test
    fun acceptedUpdateRestoresMissingPublisherTrust() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)
        trustStore.untrust(key.keyId)

        val confirmation = installer.install(packageV2())
        assertTrue(confirmation is InstallResult.NeedsPublisherConfirmation)

        val staged = installer.install(packageV2(), acceptPublisher = true)
        assertTrue(staged is InstallResult.Staged)
        assertNotNull(trustStore.trusted(key.keyId))
    }

    @Test
    fun updateStagesThenCommitSwitchesVersion() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)

        val staged = installer.install(packageV2())
        assertTrue(staged is InstallResult.Staged)
        val pendingRecord = (staged as InstallResult.Staged).record
        assertEquals(SyntheticDdp.PLUGIN_VERSION, pendingRecord.version)
        assertEquals("2.0.0", pendingRecord.pendingVersion)
        assertTrue(installer.pendingBundle(pendingRecord.id) is BundleState.Ready)
        assertTrue(installer.activeBundle(pendingRecord.id) is BundleState.Ready)

        val committed = installer.commitPending(pendingRecord.id)
        assertTrue(committed is CommitResult.Committed)
        val record = (committed as CommitResult.Committed).record
        assertEquals("2.0.0", record.version)
        assertNull(record.pendingVersion)
        assertEquals(PluginPaths.versionRoot(pluginsRoot, record.id, "2.0.0").absolutePath, record.installPath)
        assertTrue(installer.activeBundle(record.id) is BundleState.Ready)
    }

    @Test
    fun discardPendingKeepsActiveVersion() = runBlocking {
        val installer = installer()
        val installed = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record
        installer.install(packageV2())

        assertTrue(installer.discardPending(installed.id, "activation failed"))

        val record = installer.record(installed.id)!!
        assertEquals(SyntheticDdp.PLUGIN_VERSION, record.version)
        assertNull(record.pendingVersion)
        assertEquals("activation failed", record.lastError)
        assertTrue(installer.activeBundle(installed.id) is BundleState.Ready)
        assertFalse(PluginPaths.versionRoot(pluginsRoot, installed.id, "2.0.0").exists())
    }

    @Test
    fun commitWithoutPendingIsRejected() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)

        val result = installer.commitPending(SyntheticDdp.PLUGIN_ID)

        assertTrue(result is CommitResult.Rejected)
    }

    @Test
    fun updateFromDifferentPublisherIsRejected() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)
        val otherKey = SyntheticDdp.newSigningKey()
        val otherPackage = SyntheticDdp.build(
            dir,
            "other-publisher.dpp",
            SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(version = "2.0.0"), key = otherKey),
        )

        val result = installer.install(otherPackage, acceptPublisher = true)

        assertTrue(result is InstallResult.Rejected)
        assertEquals(InstallRejection.PUBLISHER_CONFLICT, (result as InstallResult.Rejected).code)
    }

    @Test
    fun rejectsInvalidSignature() = runBlocking {
        val installer = installer()
        val unsigned = SyntheticDdp.build(dir, "unsigned.dpp")

        val result = installer.install(unsigned, acceptPublisher = true)

        assertEquals(InstallRejection.SIGNATURE_INVALID, (result as InstallResult.Rejected).code)
    }

    @Test
    fun reinstallingSameContentIsAlreadyInstalled() = runBlocking {
        val installer = installer()
        val pkg = packageV1()
        installer.install(pkg, acceptPublisher = true)

        val result = installer.install(pkg, acceptPublisher = true)

        assertTrue(result is InstallResult.AlreadyInstalled)
    }

    @Test
    fun sameVersionDifferentContentIsRejected() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)
        val changed = SyntheticDdp.build(
            dir,
            "changed.dpp",
            SyntheticDdp.BuildRequest(key = key, extraEntries = mapOf("assets/extra.txt" to byteArrayOf(9))),
        )

        val result = installer.install(changed, acceptPublisher = true)

        assertEquals(InstallRejection.VERSION_CONFLICT, (result as InstallResult.Rejected).code)
    }

    @Test
    fun downgradeIsRejected() = runBlocking {
        val installer = installer()
        installer.install(packageV1(), acceptPublisher = true)
        installer.install(packageV2())
        installer.commitPending(SyntheticDdp.PLUGIN_ID)
        val downgrade = SyntheticDdp.build(
            dir,
            "downgrade.dpp",
            SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(version = "0.9.0"), key = key),
        )

        val result = installer.install(downgrade, acceptPublisher = true)

        assertEquals(InstallRejection.VERSION_CONFLICT, (result as InstallResult.Rejected).code)
    }

    @Test
    fun tamperedBundleFailsVerification() = runBlocking {
        val installer = installer()
        val record = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record
        val bundle = File(File(File(record.installPath), PluginPaths.BUNDLE_DIR), "plugin.dpp")
        bundle.writeBytes(byteArrayOf(1, 2, 3))

        val state = installer.activeBundle(record.id)

        assertTrue(state is BundleState.Missing)
    }

    @Test
    fun uninstallRemovesRecordAndFiles() = runBlocking {
        val installer = installer()
        val record = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record

        assertTrue(installer.uninstall(record.id))
        assertNull(installer.record(record.id))
        assertFalse(PluginPaths.pluginRoot(pluginsRoot, record.id).exists())
        assertFalse(installer.uninstall(record.id))
    }

    @Test
    fun updateSkillsPersistsSkillIds() = runBlocking {
        val installer = installer()
        val record = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record

        assertTrue(installer.updateSkills(record.id, listOf("skill_a", "skill_b")))
        assertEquals(listOf("skill_a", "skill_b"), installer.record(record.id)?.skillIds)
        assertFalse(installer.updateSkills("missing", listOf("x")))
    }

    @Test
    fun setEnabledUpdatesRecord() = runBlocking {
        val installer = installer()
        val record = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record

        assertTrue(installer.setEnabled(record.id, false))
        assertEquals(false, installer.record(record.id)?.enabled)
        assertTrue(installer.setEnabled(record.id, true))
        assertEquals(true, installer.record(record.id)?.enabled)
    }

    @Test
    fun commitRecordsPreviousVersionAndKeepsItsFiles() = runBlocking {
        val installer = installer()
        val installed = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record
        installer.install(packageV2())
        val v2Path = PluginPaths.versionRoot(pluginsRoot, installed.id, "2.0.0").absolutePath

        val committed = (installer.commitPending(installed.id) as CommitResult.Committed).record

        assertEquals("2.0.0", committed.version)
        assertEquals(SyntheticDdp.PLUGIN_VERSION, committed.previousVersion)
        assertEquals(installed.installPath, committed.previousInstallPath)
        assertEquals(installed.activeBundleSha256, committed.previousBundleSha256)
        assertEquals(v2Path, committed.installPath)
        assertTrue(File(installed.installPath).isDirectory)
        assertTrue(installer.activeBundle(installed.id) is BundleState.Ready)
    }

    @Test
    fun laterCommitPrunesOldestPreviousVersion() = runBlocking {
        val installer = installer()
        val installed = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record
        installer.install(packageV2())
        val firstCommit = (installer.commitPending(installed.id) as CommitResult.Committed).record
        installer.install(packageV3())

        val committed = (installer.commitPending(installed.id) as CommitResult.Committed).record

        assertEquals("3.0.0", committed.version)
        assertEquals("2.0.0", committed.previousVersion)
        assertEquals(firstCommit.installPath, committed.previousInstallPath)
        assertFalse(File(installed.installPath).exists())
        assertTrue(File(firstCommit.installPath).isDirectory)
    }

    @Test
    fun markFailurePersistsLastErrorWithoutTouchingVersion() = runBlocking {
        val installer = installer()
        val installed = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record

        assertTrue(installer.markFailure(installed.id, "activation failed"))
        assertEquals("activation failed", installer.record(installed.id)?.lastError)
        assertEquals(SyntheticDdp.PLUGIN_VERSION, installer.record(installed.id)?.version)

        assertTrue(installer.markFailure(installed.id, null))
        assertNull(installer.record(installed.id)?.lastError)
    }

    @Test
    fun rejectsHostReservedToolNames() = runBlocking {
        val installer = installer()
        val conflicting = SyntheticDdp.build(
            dir,
            "host-tool.dpp",
            SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(tools = listOf("bash")), key = key),
        )

        val result = installer.install(conflicting, acceptPublisher = true)

        assertEquals(InstallRejection.TOOL_CONFLICT, (result as InstallResult.Rejected).code)
    }

    @Test
    fun uninstallRemovesActivePendingAndPreviousVersions() = runBlocking {
        val installer = installer()
        val installed = (installer.install(packageV1(), acceptPublisher = true) as InstallResult.Installed).record
        installer.install(packageV2())
        installer.commitPending(installed.id)
        installer.install(packageV3())

        assertTrue(installer.uninstall(installed.id))

        assertNull(installer.record(installed.id))
        assertFalse(PluginPaths.pluginRoot(pluginsRoot, installed.id).exists())
    }

    private fun installer(): PluginInstaller = PluginInstaller(
        pluginsRoot = pluginsRoot,
        trustStore = trustStore,
        recordStore = recordStore,
        hostVersion = "0.2.0-dev",
    )

    private fun packageV1(): File = SyntheticDdp.build(dir, "v1.dpp", SyntheticDdp.BuildRequest(key = key))

    private fun packageV2(): File = SyntheticDdp.build(
        dir,
        "v2.dpp",
        SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(version = "2.0.0"), key = key),
    )

    private fun packageV3(): File = SyntheticDdp.build(
        dir,
        "v3.dpp",
        SyntheticDdp.BuildRequest(manifest = SyntheticDdp.manifestJson(version = "3.0.0"), key = key),
    )
}
