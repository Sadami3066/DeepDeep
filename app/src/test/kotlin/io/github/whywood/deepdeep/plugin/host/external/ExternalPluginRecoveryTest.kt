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

class ExternalPluginRecoveryTest {
    private lateinit var dir: File
    private lateinit var pluginsRoot: File
    private lateinit var recordStore: ExternalPluginRecordStore
    private lateinit var recovery: ExternalPluginRecovery

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("external-recovery").toFile()
        pluginsRoot = File(dir, "plugins")
        recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        recovery = ExternalPluginRecovery(pluginsRoot, recordStore)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun beginActivationCountsConsecutiveAttemptsAndCompleteClearsMarker() = runBlocking {
        installRecord("1.0.0")

        assertEquals(1, recovery.beginActivation(PLUGIN_ID, "2.0.0"))
        assertEquals(2, recovery.beginActivation(PLUGIN_ID, "2.0.0"))
        assertEquals(1, recovery.beginActivation(PLUGIN_ID, "3.0.0"))

        val marker = recovery.interruptedActivation(PLUGIN_ID)
        assertNotNull(marker)
        assertEquals("3.0.0", marker!!.version)
        assertEquals(1, marker.attempts)

        assertTrue(recovery.completeActivation(PLUGIN_ID))
        assertNull(recovery.interruptedActivation(PLUGIN_ID))
    }

    @Test
    fun beginActivationWithoutInstalledRecordIsIgnored() = runBlocking {
        assertNull(recovery.beginActivation(PLUGIN_ID, "2.0.0"))
        assertNull(recovery.interruptedActivation(PLUGIN_ID))
    }

    @Test
    fun noteStartupCountsCrashesPerVersion() = runBlocking {
        installRecord("1.0.0")

        assertEquals(1, recovery.noteStartup(PLUGIN_ID, "1.0.0"))
        assertEquals(2, recovery.noteStartup(PLUGIN_ID, "1.0.0"))
        assertEquals(1, recovery.noteStartup(PLUGIN_ID, "2.0.0"))

        assertTrue(recovery.clearStartupCount(PLUGIN_ID))
        assertNull(recovery.interruptedActivation(PLUGIN_ID))
        assertEquals(1, recovery.noteStartup(PLUGIN_ID, "2.0.0"))
    }

    @Test
    fun rollbackRestoresPreviousVersionAndRemovesBrokenVersion() = runBlocking {
        val v1Bundle = bundleBytes("1.0.0")
        val v2Bundle = bundleBytes("2.0.0")
        val v1Path = writeVersion("1.0.0", v1Bundle)
        val v2Path = writeVersion("2.0.0", v2Bundle)
        recordStore.upsert(
            record(
                version = "2.0.0",
                installPath = v2Path,
                sha256 = DdpSigning.sha256Hex(v2Bundle),
                previousVersion = "1.0.0",
                previousInstallPath = v1Path,
                previousBundleSha256 = DdpSigning.sha256Hex(v1Bundle),
            ),
        )

        val rolledBack = recovery.rollbackToPrevious(PLUGIN_ID)

        assertNotNull(rolledBack)
        assertEquals("1.0.0", rolledBack!!.version)
        assertEquals(v1Path, rolledBack.installPath)
        assertNull(rolledBack.previousVersion)
        assertNull(rolledBack.pendingVersion)
        assertNull(rolledBack.lastError)
        assertFalse(File(v2Path).exists())
        assertTrue(File(v1Path).isDirectory)
        assertEquals("1.0.0", recordStore.get(PLUGIN_ID)?.version)
    }

    @Test
    fun rollbackWithoutPreviousVersionIsRejected() = runBlocking {
        installRecord("1.0.0")

        assertNull(recovery.rollbackToPrevious(PLUGIN_ID))
        assertEquals("1.0.0", recordStore.get(PLUGIN_ID)?.version)
    }

    @Test
    fun rollbackWithTamperedPreviousBundleIsRejected() = runBlocking {
        val v2Bundle = bundleBytes("2.0.0")
        val v2Path = writeVersion("2.0.0", v2Bundle)
        val v1Path = writeVersion("1.0.0", bundleBytes("1.0.0"))
        File(File(v1Path, "bundle"), "plugin.dpp").writeBytes(byteArrayOf(9, 9, 9))
        recordStore.upsert(
            record(
                version = "2.0.0",
                installPath = v2Path,
                sha256 = DdpSigning.sha256Hex(v2Bundle),
                previousVersion = "1.0.0",
                previousInstallPath = v1Path,
                previousBundleSha256 = DdpSigning.sha256Hex(bundleBytes("1.0.0")),
            ),
        )

        assertNull(recovery.rollbackToPrevious(PLUGIN_ID))
        assertEquals("2.0.0", recordStore.get(PLUGIN_ID)?.version)
        assertTrue(File(v2Path).isDirectory)
    }

    @Test
    fun pruneVersionsKeepsReferencedDirectoriesAndData() = runBlocking {
        val active = writeVersion("2.0.0", bundleBytes("2.0.0"))
        val pending = writeVersion("3.0.0", bundleBytes("3.0.0"))
        val previous = writeVersion("1.0.0", bundleBytes("1.0.0"))
        val stale = writeVersion("0.9.0", bundleBytes("0.9.0"))
        val staging = File(PluginPaths.pluginRoot(pluginsRoot, PLUGIN_ID), ".staging-2.0.0-1").apply { mkdirs() }
        val data = File(PluginPaths.pluginRoot(pluginsRoot, PLUGIN_ID), PluginPaths.DATA_DIR).apply { mkdirs() }
        recordStore.upsert(
            record(
                version = "2.0.0",
                installPath = active,
                sha256 = DdpSigning.sha256Hex(bundleBytes("2.0.0")),
                previousVersion = "1.0.0",
                previousInstallPath = previous,
                previousBundleSha256 = DdpSigning.sha256Hex(bundleBytes("1.0.0")),
                pendingVersion = "3.0.0",
                pendingBundleSha256 = DdpSigning.sha256Hex(bundleBytes("3.0.0")),
            ),
        )

        assertTrue(recovery.pruneVersions(PLUGIN_ID))

        assertTrue(File(active).isDirectory)
        assertTrue(File(pending).isDirectory)
        assertTrue(File(previous).isDirectory)
        assertTrue(data.isDirectory)
        assertFalse(File(stale).exists())
        assertFalse(staging.exists())
    }

    private fun installRecord(version: String) = runBlocking {
        val bundle = bundleBytes(version)
        val path = writeVersion(version, bundle)
        recordStore.upsert(record(version = version, installPath = path, sha256 = DdpSigning.sha256Hex(bundle)))
    }

    private fun bundleBytes(version: String): ByteArray = "bundle-$version".toByteArray(Charsets.UTF_8)

    private fun writeVersion(version: String, bundle: ByteArray): String {
        val versionRoot = PluginPaths.versionRoot(pluginsRoot, PLUGIN_ID, version)
        val bundleDir = File(versionRoot, PluginPaths.BUNDLE_DIR).apply { mkdirs() }
        File(bundleDir, PluginPackageStager.BUNDLE_FILE).writeBytes(bundle)
        return versionRoot.absolutePath
    }

    private fun record(
        version: String,
        installPath: String,
        sha256: String,
        previousVersion: String? = null,
        previousInstallPath: String? = null,
        previousBundleSha256: String? = null,
        pendingVersion: String? = null,
        pendingBundleSha256: String? = null,
    ): ExternalPluginRecord = ExternalPluginRecord(
        id = PLUGIN_ID,
        version = version,
        installedAt = 1000L,
        enabled = true,
        installPath = installPath,
        activeBundleSha256 = sha256,
        size = 1L,
        publisherKeyId = "ed25519:deadbeef",
        apiLevel = 1,
        uiApiLevel = 1,
        previousVersion = previousVersion,
        previousInstallPath = previousInstallPath,
        previousBundleSha256 = previousBundleSha256,
        pendingVersion = pendingVersion,
        pendingBundleSha256 = pendingBundleSha256,
    )

    private companion object {
        const val PLUGIN_ID = "io.github.whywood.deepdeep.recoverytest"
    }
}
