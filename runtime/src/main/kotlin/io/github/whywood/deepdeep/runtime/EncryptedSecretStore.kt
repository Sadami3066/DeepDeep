package io.github.whywood.deepdeep.runtime

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.secretDataStore by preferencesDataStore(name = "secrets")

class EncryptedSecretStore(context: Context) {
    private val store = context.applicationContext.secretDataStore

    fun entries(prefix: String): Flow<Set<String>> = store.data
        .map { values -> values.keysOf(prefix) }
        .catch { error ->
            if (error is IOException) emit(emptySet()) else throw error
        }

    fun has(prefix: String, providerId: String): Flow<Boolean> = store.data
        .map { values -> values[key(prefix, providerId)] != null }
        .catch { error ->
            if (error is IOException) emit(false) else throw error
        }

    suspend fun read(prefix: String, providerId: String): String? = withContext(Dispatchers.IO) {
        val encoded = store.data.map { values -> values[key(prefix, providerId)] }.first() ?: return@withContext null
        decrypt(encoded)
    }

    suspend fun readAll(prefix: String): Map<String, String> = withContext(Dispatchers.IO) {
        val values = store.data.first()
        values.asMap().entries.mapNotNull { (preference, value) ->
            if (!preference.name.startsWith(prefix)) return@mapNotNull null
            val providerId = preference.name.removePrefix(prefix)
            val plain = decrypt(value as String)
            if (plain.isNullOrBlank()) null else providerId to plain
        }.toMap()
    }

    suspend fun save(prefix: String, providerId: String, value: String?) = withContext(Dispatchers.IO) {
        val encrypted = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::encrypt)
        store.edit { values ->
            if (encrypted == null) {
                values.remove(key(prefix, providerId))
            } else {
                values[key(prefix, providerId)] = encrypted
            }
        }
    }

    private fun Preferences.keysOf(prefix: String): Set<String> =
        asMap().keys.mapNotNull { preference ->
            preference.name.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        }.toSet()

    private fun key(prefix: String, providerId: String) = stringPreferencesKey(prefix + providerId)

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        iv.copyInto(combined, 0)
        cipherText.copyInto(combined, iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = try {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_SIZE) {
            null
        } else {
            val iv = combined.copyOfRange(0, IV_SIZE)
            val cipherText = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun secretKey(): SecretKey = keyStore().getEntry(KEY_ALIAS, null)
        ?.let { (it as? KeyStore.SecretKeyEntry)?.secretKey }
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "deepdeep.api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
