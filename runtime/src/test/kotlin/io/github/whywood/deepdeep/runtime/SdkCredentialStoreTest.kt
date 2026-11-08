package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.auth.ApiKeyCredential
import io.github.whywood.deepdeep.sdk.ai.auth.OAuthCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SdkCredentialStoreTest {
    private class FakeApiKeyStore : ApiKeyRecordStore {
        val records = mutableMapOf<String, String>()
        var saveCount = 0

        override suspend fun readRecord(providerId: String): String? = records[providerId]

        override suspend fun readAllRecords(): Map<String, String> = records.toMap()

        override suspend fun saveRecord(providerId: String, key: String?) {
            saveCount++
            if (key == null) records.remove(providerId) else records[providerId] = key
        }
    }

    private class GatedApiKeyStore : ApiKeyRecordStore {
        val records = mutableMapOf<String, String>()
        val gates = mutableListOf<CompletableDeferred<Unit>>()

        override suspend fun readRecord(providerId: String): String? = records[providerId]

        override suspend fun readAllRecords(): Map<String, String> = records.toMap()

        override suspend fun saveRecord(providerId: String, key: String?) {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
            if (key == null) records.remove(providerId) else records[providerId] = key
        }
    }

    private class FakeOAuthStore : OAuthRecordStore {
        val records = mutableMapOf<String, JsonElement>()

        override suspend fun readRawRecords(): Map<String, JsonElement> = records.toMap()

        override suspend fun saveRawRecord(providerId: String, credential: JsonElement) {
            records[providerId] = credential
        }

        override suspend fun deleteRecord(providerId: String) {
            records.remove(providerId)
        }
    }

    private fun oauthJson(access: String = "access"): JsonElement = buildJsonObject {
        put("type", "oauth")
        put("refresh", "refresh")
        put("access", access)
        put("expires", 1_800_000_000_000L)
    }

    private fun TestScope.store(
        apiKeys: FakeApiKeyStore,
        oauth: FakeOAuthStore,
    ) = SdkCredentialStore(apiKeys, oauth, this, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun apiKeyWritesAndReads() = runTest {
        val apiKeys = FakeApiKeyStore()
        val oauth = FakeOAuthStore()
        val store = store(apiKeys, oauth)
        store.writeApiKey("deepseek", "sk-test", null)
        assertEquals(ProviderAuthKind.API_KEY, store.kindOf("deepseek"))
        assertEquals("sk-test", store.readApiKey("deepseek"))
        assertTrue(store.read("deepseek") is ApiKeyCredential)
        advanceUntilIdle()
        assertEquals("sk-test", apiKeys.records["deepseek"])
        assertEquals(setOf("deepseek"), store.authKinds.value.keys)
    }

    @Test
    fun oauthWriteClearsApiKey() = runTest {
        val apiKeys = FakeApiKeyStore().apply { records["openai"] = "sk-old" }
        val oauth = FakeOAuthStore()
        val store = store(apiKeys, oauth)
        store.preloadAll()
        assertEquals(ProviderAuthKind.API_KEY, store.kindOf("openai"))

        store.writeRawCredential("openai", oauthJson())
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("openai"))
        assertTrue(store.read("openai") is OAuthCredential)
        assertNull(store.readApiKey("openai"))
        assertEquals(mapOf("openai" to oauthJson()), store.readRaw())
        advanceUntilIdle()
        assertNull(apiKeys.records["openai"])
        assertTrue(oauth.records.containsKey("openai"))
    }

    @Test
    fun apiKeyWriteClearsOauth() = runTest {
        val apiKeys = FakeApiKeyStore()
        val oauth = FakeOAuthStore().apply { records["anthropic"] = oauthJson() }
        val store = store(apiKeys, oauth)
        store.preloadAll()
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("anthropic"))

        store.writeApiKey("anthropic", "sk-new", null)
        assertEquals(ProviderAuthKind.API_KEY, store.kindOf("anthropic"))
        assertTrue(store.readRaw().isEmpty())
        advanceUntilIdle()
        assertEquals("sk-new", apiKeys.records["anthropic"])
        assertTrue(oauth.records.isEmpty())
    }

    @Test
    fun deleteRemovesBothAuthKinds() = runTest {
        val apiKeys = FakeApiKeyStore()
        val oauth = FakeOAuthStore()
        val store = store(apiKeys, oauth)
        store.writeApiKey("xai", "sk", null)
        store.writeRawCredential("kimi-coding", oauthJson("kimi"))
        store.delete("xai")
        store.delete("kimi-coding")
        assertNull(store.kindOf("xai"))
        assertNull(store.kindOf("kimi-coding"))
        assertTrue(store.keys().isEmpty())
        advanceUntilIdle()
        assertTrue(apiKeys.records.isEmpty())
        assertTrue(oauth.records.isEmpty())
    }

    @Test
    fun preloadDoesNotClobberPendingOauthWrite() = runTest {
        val oauth = FakeOAuthStore()
        val store = SdkCredentialStore(
            FakeApiKeyStore(),
            oauth,
            this,
            StandardTestDispatcher(testScheduler),
        )
        store.writeRawCredential("openai", oauthJson())
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("openai"))
        store.preload("openai")
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("openai"))
        advanceUntilIdle()
        store.preload("openai")
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("openai"))
        assertTrue(oauth.records.containsKey("openai"))
    }

    @Test
    fun modifyReplacesApiKeyWithOauth() = runTest {
        val store = store(FakeApiKeyStore(), FakeOAuthStore())
        store.writeApiKey("openrouter", "sk", null)
        val result = store.modify("openrouter") { current ->
            if (current is ApiKeyCredential) {
                OAuthCredential(refresh = "r", access = "a", expires = 1)
            } else {
                current
            }
        }
        assertTrue(result is OAuthCredential)
        assertEquals(ProviderAuthKind.OAUTH, store.kindOf("openrouter"))
        assertTrue(store.read("openrouter") is OAuthCredential)
    }

    @Test
    fun onlyLatestPendingRevisionReachesDisk() = runTest {
        val apiKeys = FakeApiKeyStore()
        val store = SdkCredentialStore(apiKeys, FakeOAuthStore(), this, StandardTestDispatcher(testScheduler))

        store.writeApiKey("deepseek", "sk-old", null)
        store.writeApiKey("deepseek", "sk-new", null)
        advanceUntilIdle()

        assertEquals(1, apiKeys.saveCount)
        assertEquals("sk-new", apiKeys.records["deepseek"])
        assertEquals("sk-new", store.readApiKey("deepseek"))
    }

    @Test
    fun awaitCommitReturnsAfterNewestWriteReachedDisk() = runTest {
        val apiKeys = GatedApiKeyStore()
        val store = SdkCredentialStore(apiKeys, FakeOAuthStore(), this, StandardTestDispatcher(testScheduler))
        store.writeApiKey("deepseek", "sk-old", null)
        store.writeApiKey("deepseek", "sk-new", null)

        val committed = async { store.awaitCommit("deepseek") }
        runCurrent()
        assertEquals(1, apiKeys.gates.size)
        assertFalse(committed.isCompleted)

        apiKeys.gates[0].complete(Unit)
        runCurrent()
        committed.await()

        assertEquals("sk-new", apiKeys.readRecord("deepseek"))
        assertEquals("sk-new", store.readApiKey("deepseek"))
    }

    @Test
    fun logoutWaitsForInFlightRefreshAndStillWins() = runTest {
        val apiKeys = GatedApiKeyStore()
        val oauth = FakeOAuthStore()
        val store = SdkCredentialStore(apiKeys, oauth, this, UnconfinedTestDispatcher(testScheduler))

        store.writeRawCredential("openai", oauthJson("stale"))
        assertEquals(1, apiKeys.gates.size)
        store.delete("openai")
        assertNull(store.kindOf("openai"))

        var released = 0
        while (released < apiKeys.gates.size) {
            apiKeys.gates[released].complete(Unit)
            released++
            runCurrent()
        }
        store.awaitCommit("openai")

        assertTrue(apiKeys.records.isEmpty())
        assertTrue(oauth.records.isEmpty())
        assertNull(store.kindOf("openai"))
    }
}
