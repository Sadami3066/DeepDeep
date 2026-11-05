package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DdpPackageTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ddp-package").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun readsManifestDexAndOptionalEntries() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(SyntheticDdp.build(dir, request = SyntheticDdp.BuildRequest(key = key)))

        assertEquals(SyntheticDdp.PLUGIN_ID, readManifestId(pkg))
        assertEquals(1, pkg.dexEntries.size)
        assertEquals("classes.dex", pkg.dexEntries.single().path)
        assertNotNull(pkg.signatureEntry)
        assertTrue(pkg.entries.any { it.path == "assets/icon.png" })
        assertTrue(pkg.entries.any { it.path == "lib/arm64-v8a/libsample.so" })
        assertNotNull(pkg.manifestEntry)
        assertEquals(SyntheticDdp.PLUGIN_ID, pkg.manifestJson()["id"].toString().trim('"'))
    }

    @Test
    fun computesWholeFileSha256() {
        val file = SyntheticDdp.build(dir)
        val pkg = DdpPackage.read(file)

        assertEquals(sha256(file), pkg.bundleSha256)
        assertEquals(file.length(), pkg.size)
    }

    @Test
    fun canonicalDigestMatchesSpecBytes() {
        val file = File(dir, "plain.dpp")
        SyntheticDdp.writeZip(
            file,
            listOf(
                "META-INF/plugin-signature.json" to "signature".toByteArray(),
                "classes.dex" to SyntheticDdp.DEX_BYTES,
                "assets/plugin.json" to "manifest".toByteArray(),
                "assets/a.txt" to "aaa".toByteArray(),
            ),
        )
        val pkg = DdpPackage.read(file)

        val expected = buildString {
            append(canonicalLine("assets/a.txt", "aaa".toByteArray()))
            append(canonicalLine("assets/plugin.json", "manifest".toByteArray()))
            append(canonicalLine("classes.dex", SyntheticDdp.DEX_BYTES))
        }.toByteArray(Charsets.UTF_8)
        assertTrue(DdpSigning.canonicalDigestBytes(pkg.entries).contentEquals(expected))
        assertEquals(sha256(expected), pkg.entryDigest)
        assertEquals(sha256(expected), DdpSigning.entryDigest(pkg.entries))
    }

    @Test
    fun canonicalDigestIsIndependentOfZipEntryOrder() {
        val key = SyntheticDdp.newSigningKey()
        val normal = DdpPackage.read(SyntheticDdp.build(dir, "normal.dpp", SyntheticDdp.BuildRequest(key = key)))
        val reversed = DdpPackage.read(
            SyntheticDdp.build(dir, "reversed.dpp", SyntheticDdp.BuildRequest(key = key, reversedOrder = true)),
        )

        assertEquals(normal.entryDigest, reversed.entryDigest)
    }

    @Test
    fun rejectsMissingManifest() {
        val file = File(dir, "missing-manifest.dpp")
        SyntheticDdp.writeZip(file, listOf("classes.dex" to SyntheticDdp.DEX_BYTES))

        val error = assertThrows(DdpPackageException::class.java) { DdpPackage.read(file) }
        assertTrue(error.message.orEmpty().contains("assets/plugin.json"))
    }

    @Test
    fun rejectsMissingDex() {
        val file = File(dir, "missing-dex.dpp")
        SyntheticDdp.writeZip(
            file,
            listOf("assets/plugin.json" to SyntheticDdp.manifestJson().toByteArray()),
        )

        val error = assertThrows(DdpPackageException::class.java) { DdpPackage.read(file) }
        assertTrue(error.message.orEmpty().contains("classes"))
    }

    @Test
    fun rejectsUnsafeEntryPath() {
        val file = File(dir, "unsafe.dpp")
        SyntheticDdp.writeZip(
            file,
            listOf(
                "assets/plugin.json" to SyntheticDdp.manifestJson().toByteArray(),
                "classes.dex" to SyntheticDdp.DEX_BYTES,
                "../escape.txt" to byteArrayOf(1),
            ),
        )

        val error = assertThrows(DdpPackageException::class.java) { DdpPackage.read(file) }
        assertTrue(error.message.orEmpty().contains("unsafe"))
    }

    @Test
    fun ignoresDirectoryEntries() {
        val file = File(dir, "directories.dpp")
        SyntheticDdp.writeZip(
            file,
            listOf(
                "assets/" to ByteArray(0),
                "assets/plugin.json" to SyntheticDdp.manifestJson().toByteArray(),
                "classes.dex" to SyntheticDdp.DEX_BYTES,
            ),
        )
        val pkg = DdpPackage.read(file)

        assertEquals(1, pkg.dexEntries.size)
        assertTrue(pkg.entries.none { it.path == "assets/" })
    }

    @Test
    fun rejectsMissingFile() {
        assertThrows(DdpPackageException::class.java) { DdpPackage.read(File(dir, "absent.dpp")) }
    }

    @Test
    fun rejectsPackageOverSizeLimit() {
        val file = SyntheticDdp.build(dir)
        assertThrows(DdpPackageException::class.java) { DdpPackage.read(file, maxBytes = 16) }
    }

    @Test
    fun readsEntryBytesAndVerifiesDigest() {
        val pkg = DdpPackage.read(SyntheticDdp.build(dir))

        assertTrue(pkg.readEntryBytes("classes.dex").contentEquals(SyntheticDdp.DEX_BYTES))
        assertEquals(1, pkg.dexBuffers().size)
        assertThrows(DdpPackageException::class.java) { pkg.readEntryBytes("assets/missing.txt") }
    }

    @Test
    fun extractsAssetsAndLib() {
        val pkg = DdpPackage.read(SyntheticDdp.build(dir))
        val assets = File(dir, "assets-out")
        val lib = File(dir, "lib-out")

        pkg.extractAssets(assets)
        pkg.extractLib(lib)

        assertTrue(File(assets, "icon.png").isFile)
        assertTrue(File(assets, "plugin.json").isFile)
        assertTrue(File(lib, "arm64-v8a/libsample.so").isFile)
        assertTrue(File(lib, "arm64-v8a/libsample.so").readBytes().contentEquals(SyntheticDdp.LIB_BYTES))
    }

    @Test
    fun copiesBundleWithDigestCheck() {
        val pkg = DdpPackage.read(SyntheticDdp.build(dir))
        val target = File(File(dir, "bundle"), "plugin.dpp")

        pkg.copyBundleTo(target)

        assertTrue(target.isFile)
        assertEquals(pkg.bundleSha256, sha256(target))
        assertEquals(pkg.size, target.length())
    }

    @Test
    fun reportsSignatureEntryWhenPresentAndNullWhenAbsent() {
        val unsigned = DdpPackage.read(SyntheticDdp.build(dir, "unsigned.dpp"))
        assertNull(unsigned.signatureEntry)

        val signed = DdpPackage.read(SyntheticDdp.build(dir, "signed.dpp", SyntheticDdp.BuildRequest(key = SyntheticDdp.newSigningKey())))
        assertEquals(DdpSigning.SIGNATURE_ENTRY, signed.signatureEntry?.path)
        assertFalse(signed.entries.isEmpty())
    }

    @Test
    fun ordersMultipleDexEntriesNumerically() {
        val file = File(dir, "multidex.dpp")
        SyntheticDdp.writeZip(
            file,
            listOf(
                "assets/plugin.json" to SyntheticDdp.manifestJson().toByteArray(),
                "classes10.dex" to byteArrayOf(10),
                "classes2.dex" to byteArrayOf(2),
                "classes.dex" to byteArrayOf(1),
            ),
        )
        val pkg = DdpPackage.read(file)

        assertEquals(listOf("classes.dex", "classes2.dex", "classes10.dex"), pkg.dexEntries.map { it.path })
    }

    private fun readManifestId(pkg: DdpPackage): String =
        pkg.manifestJson()["id"].toString().trim('"')

    private fun canonicalLine(path: String, bytes: ByteArray): String =
        "${sha256(bytes)} ${bytes.size} $path\n"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String = sha256(file.readBytes())
}
