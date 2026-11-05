package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginPackageVerifierTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ddp-verifier").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun verifiesSignedPackage() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(
                    manifest = SyntheticDdp.manifestJson(publisherKeyId = key.keyId),
                    key = key,
                ),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertTrue(result is VerificationResult.Ok)
        val ok = result as VerificationResult.Ok
        assertEquals(SyntheticDdp.PLUGIN_ID, ok.manifest.id)
        assertEquals(SyntheticDdp.PLUGIN_VERSION, ok.manifest.version)
        assertEquals(key.keyId, ok.publisher.keyId)
        assertEquals("Test Publisher", ok.publisher.name)
        assertEquals(pkg.entryDigest, ok.digests.entryDigest)
        assertEquals(pkg.bundleSha256, ok.digests.bundleSha256)
        assertEquals(pkg.size, ok.digests.size)
    }

    @Test
    fun rejectsUnsignedPackage() {
        val pkg = DdpPackage.read(SyntheticDdp.build(dir))

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.SIGNATURE_MISSING, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsTamperedContent() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(dir, request = SyntheticDdp.BuildRequest(key = key, tamperedIcon = true)),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.SIGNATURE_INVALID, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsMismatchedKeyId() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(key = key, keyIdOverride = "ed25519:" + "0".repeat(64)),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.KEY_ID_MISMATCH, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsUnsupportedAlgorithm() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(dir, request = SyntheticDdp.BuildRequest(key = key, algorithm = "RSA")),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.ALGORITHM_UNSUPPORTED, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsInvalidSignatureBytes() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(key = key, signatureOverride = ByteArray(64) { 0x42 }),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.SIGNATURE_INVALID, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsMalformedSignatureJson() {
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(rawSignatureEntry = "{not json".toByteArray()),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.SIGNATURE_MALFORMED, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsSignatureWithoutRequiredFields() {
        val key = SyntheticDdp.newSigningKey()
        val incomplete = buildJsonObject {
            put("algorithm", "Ed25519")
            put("keyId", key.keyId)
        }.toString().toByteArray()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(dir, request = SyntheticDdp.BuildRequest(rawSignatureEntry = incomplete)),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.SIGNATURE_MALFORMED, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsInvalidManifest() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(
                    manifest = SyntheticDdp.manifestJson(apiLevel = 99),
                    key = key,
                ),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.MANIFEST_INVALID, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsManifestWithoutEntryClass() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(
                    manifest = SyntheticDdp.manifestJson(entryClass = null),
                    key = key,
                ),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg)

        assertEquals(VerificationCode.MANIFEST_INVALID, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun rejectsTooOldHostVersion() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(
                    manifest = SyntheticDdp.manifestJson(minHostVersion = "9.0.0"),
                    key = key,
                ),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg, hostVersion = "0.2.0-dev")

        assertEquals(VerificationCode.MANIFEST_INVALID, (result as VerificationResult.Rejected).code)
    }

    @Test
    fun acceptsManifestWhenHostVersionMatches() {
        val key = SyntheticDdp.newSigningKey()
        val pkg = DdpPackage.read(
            SyntheticDdp.build(
                dir,
                request = SyntheticDdp.BuildRequest(
                    manifest = SyntheticDdp.manifestJson(minHostVersion = "0.2.0"),
                    key = key,
                ),
            ),
        )

        val result = PluginPackageVerifier.verify(pkg, hostVersion = "0.2.0-dev")

        assertTrue(result is VerificationResult.Ok)
    }
}
