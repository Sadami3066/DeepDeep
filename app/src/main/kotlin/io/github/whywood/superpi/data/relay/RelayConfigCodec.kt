package io.github.whywood.superpi.data.relay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal object RelayConfigCodec {
    fun encode(account: RelayAccount): String = buildJsonObject {
        put("origin", account.origin)
        account.fingerprint?.let { put("fingerprint", it) }
        account.caPem?.let { put("caPem", it) }
        put("deviceId", account.deviceId)
        put("deviceName", account.deviceName)
        put("accountId", account.accountId)
        put("enrolledAt", account.enrolledAt)
    }.toString()

    fun decode(value: String?): RelayAccount? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val origin = json.string("origin") ?: return null
        val deviceId = json.string("deviceId") ?: return null
        val deviceName = json.string("deviceName") ?: return null
        val accountId = json.string("accountId") ?: return null
        val enrolledAt = json["enrolledAt"]?.jsonPrimitive?.longOrNull ?: return null
        return RelayAccount(
            origin = origin,
            fingerprint = RelayPairingLink.normalizeFingerprint(json.string("fingerprint")),
            caPem = json.string("caPem"),
            deviceId = deviceId,
            deviceName = deviceName,
            accountId = accountId,
            enrolledAt = enrolledAt,
        )
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
}
