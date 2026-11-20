package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.data.remote.RemoteTls
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RelayClient(
    private val platform: String = "android",
    private val appVersion: String = "",
) {
    suspend fun fetchCa(origin: String): String? = withContext(Dispatchers.IO) {
        val client = RemoteTls.client(pin = null, remote = true, bootstrap = true)
        val request = Request.Builder().url("$origin/v1/ca").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw RelayException(RelayFailure.BadResponse)
                response.body.string().takeIf { it.isNotBlank() }
            }
        } catch (error: RelayException) {
            throw error
        } catch (error: IOException) {
            throw RelayException(RelayFailure.Unreachable, cause = error)
        }
    }

    suspend fun enroll(
        origin: String,
        caPem: String?,
        deviceName: String,
        pairCode: String?,
        loginSecret: String?,
    ): RelayEnrollment = withContext(Dispatchers.IO) {
        val pin = caPem?.let { RemoteTls.decodeBytes(it.toByteArray(Charsets.UTF_8)) }
        val client = RemoteTls.client(pin = pin, remote = true)
        val body = RelayProtocol
            .enrollRequest(deviceName, platform, appVersion, pairCode, loginSecret)
            .toRequestBody(JSON)
        val request = Request.Builder().url("$origin/v1/pair/enroll").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> RelayProtocol.parseEnrollment(text)
                        ?: throw RelayException(RelayFailure.BadResponse)
                    response.code == 401 || response.code == 403 -> throw RelayException(RelayFailure.Unauthorized)
                    else -> throw RelayException(RelayFailure.BadResponse)
                }
            }
        } catch (error: RelayException) {
            throw error
        } catch (error: SSLException) {
            throw RelayException(RelayFailure.CertificateRejected, cause = error)
        } catch (error: IOException) {
            throw RelayException(RelayFailure.Unreachable, cause = error)
        }
    }

    fun fingerprintOf(caPem: String): String? {
        val certificate = RemoteTls.decodeBytes(caPem.toByteArray(Charsets.UTF_8)) ?: return null
        return RelayPairingLink.normalizeFingerprint(RemoteTls.sha256Hex(certificate))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
