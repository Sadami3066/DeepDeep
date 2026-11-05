package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.SecretService
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import kotlinx.coroutines.flow.first

internal class SecretPluginService(
    private val store: EncryptedSecretStore,
    pluginId: String,
) : SecretService {
    private val prefix = "plugin.${pluginId}."

    override suspend fun get(key: String): String? = store.read(prefix, key)

    override suspend fun put(key: String, value: String) {
        store.save(prefix, key, value)
    }

    override suspend fun remove(key: String) {
        store.save(prefix, key, null)
    }

    override suspend fun clear() {
        store.entries(prefix).first().forEach { key ->
            store.save(prefix, key, null)
        }
    }
}
