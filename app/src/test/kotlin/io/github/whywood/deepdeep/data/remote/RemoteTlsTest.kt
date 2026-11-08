package io.github.whywood.deepdeep.data.remote

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTlsTest {
    private val ca: X509Certificate
    private val caKey: KeyPair
    private val leaf: X509Certificate

    init {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        caKey = generator.generateKeyPair()
        ca = generateCertificate(
            subject = X500Name("CN=DeepDeep Test CA"),
            issuer = X500Name("CN=DeepDeep Test CA"),
            publicKey = caKey.public,
            signingKey = caKey.private,
            isCa = true,
        )
        val leafKey = generator.generateKeyPair()
        leaf = generateCertificate(
            subject = X500Name("CN=DeepDeep Test Leaf"),
            issuer = X500Name("CN=DeepDeep Test CA"),
            publicKey = leafKey.public,
            signingKey = caKey.private,
            isCa = false,
        )
    }

    @Test
    fun sha256FingerprintIsUppercaseColonHex() {
        val fingerprint = RemoteTls.sha256Hex(leaf)
        assertTrue(fingerprint.matches(Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")))
        val expected = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            .joinToString(":") { "%02X".format(it) }
        assertEquals(expected, fingerprint)
    }

    @Test
    fun encodesAndDecodesCertificate() {
        val encoded = RemoteTls.encode(ca)
        val decoded = RemoteTls.decode(encoded)
        assertNotNull(decoded)
        assertTrue(ca.encoded.contentEquals(decoded!!.encoded))
        assertEquals(RemoteTls.sha256Hex(ca), RemoteTls.sha256Hex(decoded))
    }

    @Test
    fun decodesRawCertificateBytes() {
        val decoded = RemoteTls.decodeBytes(ca.encoded)
        assertNotNull(decoded)
        assertTrue(ca.encoded.contentEquals(decoded!!.encoded))
        assertNull(RemoteTls.decode("not base64"))
        assertNull(RemoteTls.decodeBytes(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun verifiesLeafAgainstItsCa() {
        assertTrue(RemoteTls.verifies(ca, arrayOf(leaf)))
        assertFalse(RemoteTls.verifies(ca, null))
        assertFalse(RemoteTls.verifies(ca, emptyArray()))
    }

    @Test
    fun rejectsLeafSignedByAnotherCa() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val otherCaKey = generator.generateKeyPair()
        val otherCa = generateCertificate(
            subject = X500Name("CN=Other CA"),
            issuer = X500Name("CN=Other CA"),
            publicKey = otherCaKey.public,
            signingKey = otherCaKey.private,
            isCa = true,
        )
        assertFalse(RemoteTls.verifies(otherCa, arrayOf(leaf)))
    }

    @Test
    fun pinnedTrustManagerAcceptsLeaf() {
        val manager = RemoteTls.pinnedTrustManager(listOf(ca))
        manager.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
    }

    private fun generateCertificate(
        subject: X500Name,
        issuer: X500Name,
        publicKey: java.security.PublicKey,
        signingKey: java.security.PrivateKey,
        isCa: Boolean,
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(now),
            Date(now - 60_000),
            Date(now + 86_400_000),
            subject,
            publicKey,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signingKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
