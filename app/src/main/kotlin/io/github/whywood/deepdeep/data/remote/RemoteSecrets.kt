package io.github.whywood.deepdeep.data.remote

import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RemoteSecrets(
    private val secrets: EncryptedSecretStore,
) {
    suspend fun read(deviceId: String): RemoteEntrySecrets? {
        val json = secrets.read(PREFIX, deviceId) ?: return null
        return runCatching {
            val item = Json.parseToJsonElement(json).jsonObject
            val deviceToken = item["deviceToken"]?.jsonPrimitive?.contentOrNull ?: return null
            RemoteEntrySecrets(
                deviceToken = deviceToken,
                sessionToken = item["sessionToken"]?.jsonPrimitive?.contentOrNull,
                csrfToken = item["csrfToken"]?.jsonPrimitive?.contentOrNull,
            )
        }.getOrNull()
    }

    suspend fun save(deviceId: String, value: RemoteEntrySecrets) {
        val json = buildJsonObject {
            put("deviceToken", value.deviceToken)
            value.sessionToken?.let { put("sessionToken", it) }
            value.csrfToken?.let { put("csrfToken", it) }
        }
        secrets.save(PREFIX, deviceId, json.toString())
    }

    suspend fun remove(deviceId: String) {
        secrets.save(PREFIX, deviceId, null)
    }

    suspend fun removeAll() {
        secrets.entries(PREFIX).first().forEach { deviceId -> remove(deviceId) }
    }

    private companion object {
        const val PREFIX = "remote."
    }
}
