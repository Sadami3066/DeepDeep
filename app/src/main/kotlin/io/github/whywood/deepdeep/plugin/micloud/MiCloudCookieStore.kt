package io.github.whywood.deepdeep.plugin.micloud

import android.content.Context
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore

class MiCloudCookieStore(context: Context) {
    private val secrets = EncryptedSecretStore(context.applicationContext)

    suspend fun read(): String? = secrets.read(PREFIX, COOKIE_KEY)?.takeIf { it.isNotBlank() }

    suspend fun save(cookie: String) {
        secrets.save(PREFIX, COOKIE_KEY, cookie.trim())
        secrets.save(PREFIX, BLOCKED_KEY, null)
    }

    suspend fun clear() {
        secrets.save(PREFIX, COOKIE_KEY, null)
    }

    suspend fun silentRecoveryBlocked(): Boolean = secrets.read(PREFIX, BLOCKED_KEY) == "1"

    suspend fun blockSilentRecovery() {
        secrets.save(PREFIX, BLOCKED_KEY, "1")
    }

    suspend fun unblockSilentRecovery() {
        secrets.save(PREFIX, BLOCKED_KEY, null)
    }

    suspend fun userId(): String? = read()?.let { parseCookie(it)[USER_ID] }

    suspend fun serviceToken(): String? = read()?.let { parseCookie(it)[SERVICE_TOKEN] }

    private companion object {
        const val PREFIX = "micloud_"
        const val COOKIE_KEY = "cookie"
        const val BLOCKED_KEY = "recovery_blocked"
        const val USER_ID = "userId"
        const val SERVICE_TOKEN = "serviceToken"
    }
}
