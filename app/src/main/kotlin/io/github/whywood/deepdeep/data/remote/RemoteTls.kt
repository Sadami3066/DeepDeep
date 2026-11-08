package io.github.whywood.deepdeep.data.remote

import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

internal object RemoteTls {
    fun sha256Hex(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(":") { byte -> "%02X".format(byte) }
    }

    fun encode(certificate: X509Certificate): String =
        Base64.getEncoder().withoutPadding().encodeToString(certificate.encoded)

    fun decode(encoded: String): X509Certificate? = runCatching {
        decodeBytes(Base64.getDecoder().decode(encoded))
    }.getOrNull()

    fun decodeBytes(bytes: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }.getOrNull()

    fun verifies(ca: X509Certificate, chain: Array<X509Certificate>?): Boolean {
        if (chain.isNullOrEmpty()) return false
        return try {
            pinnedTrustManager(listOf(ca)).checkServerTrusted(chain, "UNKNOWN")
            true
        } catch (_: CertificateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun pinnedTrustManager(certificates: List<X509Certificate>): X509TrustManager =
        PinnedTrustManager(
            anchors = certificates.map { TrustAnchor(it, null) }.toSet(),
            accepted = certificates.toTypedArray(),
        )

    fun client(
        pin: X509Certificate?,
        remote: Boolean,
        bootstrap: Boolean = false,
    ): OkHttpClient {
        val builder = baseBuilder(remote, bootstrap)
        if (pin != null) {
            val trustManager = pinnedTrustManager(listOf(pin))
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    fun capturingClient(capture: X509TrustManager, remote: Boolean): OkHttpClient {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(capture), SecureRandom())
        }
        return baseBuilder(remote, bootstrap = true)
            .sslSocketFactory(sslContext.socketFactory, capture)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private fun baseBuilder(remote: Boolean, bootstrap: Boolean): OkHttpClient.Builder {
        val connect = when {
            bootstrap && remote -> 3L
            bootstrap -> 1L
            remote -> 10L
            else -> 3L
        }
        val read = when {
            bootstrap && remote -> 5L
            bootstrap -> 2L
            remote -> 30L
            else -> 5L
        }
        val write = if (remote) 30L else 5L
        val call = when {
            bootstrap && remote -> 10L
            bootstrap -> 5L
            remote -> 40L
            else -> 10L
        }
        return OkHttpClient.Builder()
            .connectTimeout(connect, TimeUnit.SECONDS)
            .readTimeout(read, TimeUnit.SECONDS)
            .writeTimeout(write, TimeUnit.SECONDS)
            .callTimeout(call, TimeUnit.SECONDS)
    }
}

internal class PinnedTrustManager(
    private val anchors: Set<TrustAnchor>,
    private val accepted: Array<X509Certificate>,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty()) throw CertificateException("empty certificate chain")
        val certPath = CertificateFactory.getInstance("X.509").generateCertPath(chain.toList())
        val parameters = PKIXParameters(anchors).apply { isRevocationEnabled = false }
        try {
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters)
        } catch (error: GeneralSecurityException) {
            throw CertificateException(error)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = accepted
}

internal class CaptureTrustManager : X509TrustManager {
    @Volatile
    var chain: Array<X509Certificate>? = null

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        this.chain = chain.copyOf()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
