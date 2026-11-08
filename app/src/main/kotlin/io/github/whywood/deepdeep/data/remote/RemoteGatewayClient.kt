package io.github.whywood.deepdeep.data.remote

import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class RemoteGatewayFailure {
    Unauthorized,
    Forbidden,
    DeviceLimit,
    RateLimited,
    Unreachable,
    CertificateRejected,
    BadResponse,
}

class RemoteGatewayException(val failure: RemoteGatewayFailure) : Exception(failure.name)

data class RemotePairing(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val deviceToken: String,
    val deviceExpiresAt: Long,
    val sessionToken: String,
    val csrfToken: String,
    val sessionExpiresAt: Long,
    val certDerBase64: String?,
    val certSha256: String?,
)

data class RemoteRenewal(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val sessionToken: String,
    val csrfToken: String,
    val sessionExpiresAt: Long,
)

data class RemoteProbe(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val deviceExpiresAt: Long,
)

class RemoteGatewayClient {
    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    suspend fun pair(link: RemotePairingLink, label: String): RemotePairing = withContext(Dispatchers.IO) {
        val remote = link.kind == RemoteEntryKind.Public
        val request = postJson(pairUrl(link.origin), link.origin, buildPairRequestBody(link.token, label))
        when (val result = execute(RemoteTls.client(null, remote), request)) {
            is HttpResult.Response -> pairingFrom(result, pin = null)
            is HttpResult.TlsFailure -> {
                val ca = bootstrapCertificate(link.origin, remote)
                    ?: throw RemoteGatewayException(RemoteGatewayFailure.CertificateRejected)
                when (val retried = execute(clientFor(ca, remote), request)) {
                    is HttpResult.Response -> pairingFrom(retried, pin = ca)
                    is HttpResult.TlsFailure -> throw RemoteGatewayException(RemoteGatewayFailure.CertificateRejected)
                    HttpResult.NetworkFailure -> throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
                }
            }
            HttpResult.NetworkFailure -> throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
        }
    }

    suspend fun renew(
        origin: String,
        certDerBase64: String?,
        deviceToken: String,
        remote: Boolean,
    ): RemoteRenewal =
        withContext(Dispatchers.IO) {
            val pin = certDerBase64?.let(RemoteTls::decode)
            val request = postJson(renewUrl(origin), origin, buildDeviceTokenRequestBody(deviceToken))
            when (val result = execute(clientFor(pin, remote), request)) {
                is HttpResult.Response -> {
                    if (result.status != 200) throw RemoteGatewayException(mapFailure(result.status, result.text))
                    val payload = parseRenewalPayload(result.text)
                        ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
                    RemoteRenewal(
                        instanceId = payload.instanceId,
                        installationId = payload.installationId,
                        deviceId = payload.deviceId,
                        sessionToken = payload.sessionToken,
                        csrfToken = payload.csrfToken,
                        sessionExpiresAt = payload.sessionExpiresAt,
                    )
                }
                is HttpResult.TlsFailure -> throw RemoteGatewayException(RemoteGatewayFailure.CertificateRejected)
                HttpResult.NetworkFailure -> throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
            }
        }

    suspend fun probe(
        origin: String,
        certDerBase64: String?,
        deviceToken: String,
        remote: Boolean,
    ): RemoteProbe =
        withContext(Dispatchers.IO) {
            val pin = certDerBase64?.let(RemoteTls::decode)
            val request = postJson(probeUrl(origin), origin, buildDeviceTokenRequestBody(deviceToken))
            when (val result = execute(clientFor(pin, remote), request)) {
                is HttpResult.Response -> {
                    if (result.status != 200) throw RemoteGatewayException(mapFailure(result.status, result.text))
                    val payload = parseProbePayload(result.text)
                        ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
                    RemoteProbe(
                        instanceId = payload.instanceId,
                        installationId = payload.installationId,
                        deviceId = payload.deviceId,
                        deviceExpiresAt = payload.deviceExpiresAt,
                    )
                }
                is HttpResult.TlsFailure -> throw RemoteGatewayException(RemoteGatewayFailure.CertificateRejected)
                HttpResult.NetworkFailure -> throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
            }
        }

    private fun pairingFrom(result: HttpResult.Response, pin: X509Certificate?): RemotePairing {
        if (result.status != 201) throw RemoteGatewayException(mapFailure(result.status, result.text))
        val payload = parsePairingPayload(result.text)
            ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
        return RemotePairing(
            instanceId = payload.instanceId,
            installationId = payload.installationId,
            deviceId = payload.deviceId,
            deviceToken = payload.deviceToken,
            deviceExpiresAt = payload.deviceExpiresAt,
            sessionToken = payload.sessionToken,
            csrfToken = payload.csrfToken,
            sessionExpiresAt = payload.sessionExpiresAt,
            certDerBase64 = pin?.let(RemoteTls::encode),
            certSha256 = pin?.let(RemoteTls::sha256Hex),
        )
    }

    private fun bootstrapCertificate(origin: String, remote: Boolean): X509Certificate? {
        val capture = CaptureTrustManager()
        val request = Request.Builder()
            .url("${origin.trimEnd('/')}/mobile-access/ca.cer")
            .get()
            .build()
        val response = execute(RemoteTls.capturingClient(capture, remote), request) as? HttpResult.Response
        if (response == null || response.status != 200) return null
        val ca = RemoteTls.decodeBytes(response.bytes)
        return ca?.takeIf { RemoteTls.verifies(it, capture.chain) }
    }

    private fun clientFor(pin: X509Certificate?, remote: Boolean): OkHttpClient {
        if (pin == null) return RemoteTls.client(null, remote)
        val key = "${RemoteTls.sha256Hex(pin)}:$remote"
        return pinnedClients.getOrPut(key) { RemoteTls.client(pin, remote) }
    }

    private fun execute(client: OkHttpClient, request: Request): HttpResult = try {
        client.newCall(request).execute().use { response ->
            HttpResult.Response(response.code, response.body.bytes())
        }
    } catch (_: SSLException) {
        HttpResult.TlsFailure
    } catch (_: IOException) {
        HttpResult.NetworkFailure
    }

    private fun postJson(url: String, origin: String, body: String): Request =
        Request.Builder()
            .url(url)
            .header("Origin", origin.trimEnd('/'))
            .header("Accept", "application/json")
            .header("x-dsh-mobile-capabilities", "installation-id")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private sealed interface HttpResult {
        data class Response(val status: Int, val bytes: ByteArray) : HttpResult {
            val text: String get() = bytes.toString(Charsets.UTF_8)
        }

        data object TlsFailure : HttpResult

        data object NetworkFailure : HttpResult
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal data class RemotePairingPayload(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val deviceToken: String,
    val deviceExpiresAt: Long,
    val sessionToken: String,
    val csrfToken: String,
    val sessionExpiresAt: Long,
)

internal data class RemoteRenewalPayload(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val sessionToken: String,
    val csrfToken: String,
    val sessionExpiresAt: Long,
)

internal data class RemoteProbePayload(
    val instanceId: String,
    val installationId: String?,
    val deviceId: String,
    val deviceExpiresAt: Long,
)

internal fun buildPairRequestBody(token: String, label: String): String =
    """{"token":"${token.escapeJson()}","label":"${label.escapeJson()}"}"""

internal fun buildDeviceTokenRequestBody(deviceToken: String): String =
    """{"deviceToken":"${deviceToken.escapeJson()}"}"""

internal fun parsePairingPayload(body: String): RemotePairingPayload? {
    val item = parseObject(body) ?: return null
    return RemotePairingPayload(
        instanceId = item.string("instanceId") ?: return null,
        installationId = item.string("installationId"),
        deviceId = item.string("deviceId") ?: return null,
        deviceToken = item.string("deviceToken") ?: return null,
        deviceExpiresAt = item.timestamp("deviceExpiresAt") ?: return null,
        sessionToken = item.string("sessionToken") ?: return null,
        csrfToken = item.string("csrfToken") ?: return null,
        sessionExpiresAt = item.timestamp("sessionExpiresAt") ?: return null,
    )
}

internal fun parseRenewalPayload(body: String): RemoteRenewalPayload? {
    val item = parseObject(body) ?: return null
    return RemoteRenewalPayload(
        instanceId = item.string("instanceId") ?: return null,
        installationId = item.string("installationId"),
        deviceId = item.string("deviceId") ?: return null,
        sessionToken = item.string("sessionToken") ?: return null,
        csrfToken = item.string("csrfToken") ?: return null,
        sessionExpiresAt = item.timestamp("sessionExpiresAt") ?: return null,
    )
}

internal fun parseProbePayload(body: String): RemoteProbePayload? {
    val item = parseObject(body) ?: return null
    return RemoteProbePayload(
        instanceId = item.string("instanceId") ?: return null,
        installationId = item.string("installationId"),
        deviceId = item.string("deviceId") ?: return null,
        deviceExpiresAt = item.timestamp("deviceExpiresAt") ?: return null,
    )
}

internal fun mapFailure(status: Int, body: String): RemoteGatewayFailure {
    val code = parseObject(body)?.string("error")
    return when {
        status == 401 -> RemoteGatewayFailure.Unauthorized
        status == 403 -> RemoteGatewayFailure.Forbidden
        status == 409 -> RemoteGatewayFailure.DeviceLimit
        status == 429 -> RemoteGatewayFailure.RateLimited
        status in 500..599 -> RemoteGatewayFailure.BadResponse
        code != null -> RemoteGatewayFailure.BadResponse
        else -> RemoteGatewayFailure.BadResponse
    }
}

private fun pairUrl(origin: String) = "${origin.trimEnd('/')}/mobile-access/auth/native-pair"

private fun renewUrl(origin: String) = "${origin.trimEnd('/')}/mobile-access/auth/native-renew"

private fun probeUrl(origin: String) = "${origin.trimEnd('/')}/mobile-access/auth/native-probe"

private fun parseObject(body: String) =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()

private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun kotlinx.serialization.json.JsonObject.timestamp(key: String): Long? {
    val element = this[key] ?: return null
    val primitive = runCatching { element.jsonPrimitive }.getOrNull() ?: return null
    primitive.longOrNull?.let { return it }
    return parseTimestamp(primitive.contentOrNull)
}

internal fun parseTimestamp(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    raw.toLongOrNull()?.let { return it }
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
}

private fun String.escapeJson(): String = buildString(length) {
    for (character in this@escapeJson) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001f' -> append("\\u%04x".format(character.code))
            else -> append(character)
        }
    }
}
