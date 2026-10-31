package io.github.whywood.deepdeep.adb

import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

data class AdbCredentials(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
)

class AdbKeyStore(private val directory: File) {
    fun loadOrCreate(): AdbCredentials {
        load()?.let { return it }
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }
        val pair = generator.generateKeyPair()
        val credentials = AdbCredentials(pair.private, generateCertificate(pair.private, pair.public))
        persist(credentials)
        return credentials
    }

    private fun load(): AdbCredentials? {
        val keyFile = File(directory, KEY_FILE)
        val certFile = File(directory, CERT_FILE)
        if (!keyFile.isFile || !certFile.isFile) return null
        return runCatching {
            val privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certFile.inputStream()) as X509Certificate
            certificate.checkValidity()
            AdbCredentials(privateKey, certificate).takeIf(::matches)
        }.getOrNull()
    }

    private fun matches(credentials: AdbCredentials): Boolean = runCatching {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(credentials.privateKey)
        signature.update(PROBE)
        val signed = signature.sign()
        signature.initVerify(credentials.certificate.publicKey)
        signature.update(PROBE)
        signature.verify(signed)
    }.getOrDefault(false)

    private fun generateCertificate(privateKey: PrivateKey, publicKey: PublicKey): X509Certificate {
        val name = X500Name("CN=DeepDeep")
        val now = Date()
        val notAfter = Date(now.time + CERT_VALIDITY_MILLIS)
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val builder = JcaX509v3CertificateBuilder(name, serial, now, notAfter, name, publicKey)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun persist(credentials: AdbCredentials) {
        directory.mkdirs()
        File(directory, KEY_FILE).writeBytes(credentials.privateKey.encoded)
        File(directory, CERT_FILE).writeBytes(credentials.certificate.encoded)
    }

    companion object {
        const val DIRECTORY_NAME = "adb"
        private const val KEY_FILE = "adb.key"
        private const val CERT_FILE = "adb.crt"
        private const val KEY_BITS = 2048
        private const val CERT_VALIDITY_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000
        private val PROBE = "deepdeep-adb-key".toByteArray()
    }
}
