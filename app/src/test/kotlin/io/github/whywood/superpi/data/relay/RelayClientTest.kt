package io.github.whywood.superpi.data.relay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RelayClientTest {
    private val client = RelayClient(platform = "android", appVersion = "2.0.1")

    @Test
    fun fingerprintsCertificatesFromPem() {
        val certificate = generateCertificate()
        val expected = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, client.fingerprintOf(toPem(certificate)))
        assertNull(client.fingerprintOf("not a certificate"))
    }

    @Test
    fun enrollsAgainstAPlainRelay() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val server = plainServer { exchange ->
            requests += exchange.requestMethod to exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            respond(
                exchange,
                200,
                enrollmentJson(deviceId = "dev-1", token = "tok-1", fingerprint = null),
            )
        }
        server.start()
        try {
            val enrollment = client.enroll(
                origin = "http://127.0.0.1:${server.address.port}",
                caPem = null,
                deviceName = "My phone",
                pairCode = null,
                loginSecret = "secret",
            )
            assertEquals("dev-1", enrollment.deviceId)
            assertEquals("tok-1", enrollment.token)
            assertEquals("acct-1", enrollment.accountId)
            assertEquals(RelayEntryKind.Public, enrollment.entries.single().kind)
            assertEquals("POST", requests.single().first)
            assertTrue(requests.single().second.contains("\"loginSecret\":\"secret\""))
            assertTrue(requests.single().second.contains("\"deviceName\":\"My phone\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun enrollsOverPinnedTls() = runBlocking {
        val caKeyPair = keyPair()
        val ca = generateCertificate(
            X500Name("CN=Relay CA"),
            X500Name("CN=Relay CA"),
            caKeyPair.public,
            caKeyPair.private,
            isCa = true,
        )
        val leafKeyPair = keyPair()
        val leaf = generateCertificate(
            X500Name("CN=relay"),
            X500Name("CN=Relay CA"),
            leafKeyPair.public,
            caKeyPair.private,
            isCa = false,
        )
        val server = secureServer(leaf, leafKeyPair.private) { exchange ->
            respond(exchange, 200, enrollmentJson(deviceId = "dev-tls", token = "tok-tls", fingerprint = "ab".repeat(32)))
        }
        server.start()
        try {
            val enrollment = client.enroll(
                origin = "https://127.0.0.1:${server.address.port}",
                caPem = toPem(ca),
                deviceName = "Phone",
                pairCode = "ABCD2345",
                loginSecret = null,
            )
            assertEquals("dev-tls", enrollment.deviceId)
            assertEquals("ab".repeat(32), enrollment.fingerprint)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun rejectsCertificatesFromAnotherAuthority() = runBlocking {
        val caKeyPair = keyPair()
        generateCertificate(
            X500Name("CN=Relay CA"),
            X500Name("CN=Relay CA"),
            caKeyPair.public,
            caKeyPair.private,
            isCa = true,
        )
        val leafKeyPair = keyPair()
        val leaf = generateCertificate(
            X500Name("CN=relay"),
            X500Name("CN=Relay CA"),
            leafKeyPair.public,
            caKeyPair.private,
            isCa = false,
        )
        val otherKeyPair = keyPair()
        val otherCa = generateCertificate(
            X500Name("CN=Other CA"),
            X500Name("CN=Other CA"),
            otherKeyPair.public,
            otherKeyPair.private,
            isCa = true,
        )
        val server = secureServer(leaf, leafKeyPair.private) { exchange ->
            respond(exchange, 200, enrollmentJson(deviceId = "dev", token = "tok", fingerprint = null))
        }
        server.start()
        try {
            client.enroll("https://127.0.0.1:${server.address.port}", toPem(otherCa), "Phone", "ABCD2345", null)
            fail("expected a RelayException")
        } catch (error: RelayException) {
            assertEquals(RelayFailure.CertificateRejected, error.failure)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun mapsUnauthorizedResponses() = runBlocking {
        val server = plainServer { exchange ->
            respond(exchange, 401, """{"code":"unauthorized","message":"invalid login secret"}""")
        }
        server.start()
        try {
            client.enroll("http://127.0.0.1:${server.address.port}", null, "Phone", null, "wrong")
            fail("expected a RelayException")
        } catch (error: RelayException) {
            assertEquals(RelayFailure.Unauthorized, error.failure)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun mapsUnreachableRelays() = runBlocking {
        try {
            client.enroll("http://127.0.0.1:1", null, "Phone", null, "secret")
            fail("expected a RelayException")
        } catch (error: RelayException) {
            assertEquals(RelayFailure.Unreachable, error.failure)
        }
    }

    private fun plainServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/pair/enroll", handler)
        return server
    }

    private fun secureServer(certificate: X509Certificate, privateKey: PrivateKey, handler: (HttpExchange) -> Unit): HttpsServer {
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.setHttpsConfigurator(HttpsConfigurator(serverContext(certificate, privateKey)))
        server.createContext("/v1/pair/enroll", handler)
        return server
    }

    private fun serverContext(certificate: X509Certificate, privateKey: PrivateKey): SSLContext {
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry("server", privateKey, PASSWORD, arrayOf(certificate))
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, PASSWORD)
        return SSLContext.getInstance("TLS").apply { init(factory.keyManagers, null, null) }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("content-type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun enrollmentJson(deviceId: String, token: String, fingerprint: String?): String {
        val fingerprintField = if (fingerprint == null) "null" else "\"$fingerprint\""
        return """{"deviceId":"$deviceId","token":"$token","accountId":"acct-1","entries":[{"kind":"public","origin":"https://relay.test:8443"}],"fingerprint":$fingerprintField}"""
    }

    private fun toPem(certificate: X509Certificate): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
        return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
    }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun generateCertificate(): X509Certificate {
        val pair = keyPair()
        val name = X500Name("CN=SuperPI Test CA")
        return generateCertificate(name, name, pair.public, pair.private, isCa = true)
    }

    private fun generateCertificate(
        subject: X500Name,
        issuer: X500Name,
        publicKey: PublicKey,
        signingKey: PrivateKey,
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

    private companion object {
        val PASSWORD = "superpi".toCharArray()
    }
}
