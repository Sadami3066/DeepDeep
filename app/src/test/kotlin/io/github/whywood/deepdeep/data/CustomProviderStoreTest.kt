package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomProviderStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File
        get() = File(folder.root, "agent/models.json")

    private fun entry(id: String = "custom-abc12345", name: String = "My provider") = CustomProviderEntry(
        id = id,
        name = name,
        api = CustomProviderProtocol.OpenAiCompletions.value,
        baseUrl = "https://api.example.com/v1",
        apiKey = "sk-test",
        models = listOf(CustomProviderModel(id = "gpt-4o", name = "GPT-4o")),
    )

    @Test
    fun savesAndReloadsEntries() = runTest {
        CustomProviderStore(file).save(listOf(entry()))
        val reloaded = CustomProviderStore(file)
        assertEquals(listOf(entry()), reloaded.entries.value)
        assertTrue(file.isFile)
    }

    @Test
    fun upsertsAndDeletesEntries() = runTest {
        val store = CustomProviderStore(file)
        store.save(listOf(entry()))
        store.saveEntry(entry(name = "My provider").copy(name = "Renamed"))
        assertEquals(listOf(entry().copy(name = "Renamed")), CustomProviderStore(file).entries.value)
        store.delete(entry().id)
        assertEquals(emptyList<CustomProviderEntry>(), CustomProviderStore(file).entries.value)
    }

    @Test
    fun ignoresBrokenJson() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("{ not json")
        assertEquals(emptyList<CustomProviderEntry>(), CustomProviderStore(file).entries.value)
    }

    @Test
    fun skipsIncompleteProvidersAndModels() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "providers": {
                "custom-valid": {
                  "name": "Valid",
                  "api": "openai-responses",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "models": [{"id": "gpt-5", "name": ""}]
                },
                "custom-invalid": {
                  "name": "Invalid",
                  "api": "openai-completions",
                  "models": []
                },
                "custom-badurl": {
                  "name": "Bad URL",
                  "api": "openai-completions",
                  "baseUrl": "ftp://api.example.com",
                  "apiKey": "sk-test",
                  "models": [{"id": "gpt-5"}]
                },
                "custom-badapi": {
                  "name": "Bad API",
                  "api": "unknown-api",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "models": [{"id": "gpt-5"}]
                }
              }
            }
            """.trimIndent(),
        )
        val entries = CustomProviderStore(file).entries.value
        assertEquals(1, entries.size)
        assertEquals("custom-valid", entries.first().id)
        assertEquals(listOf(CustomProviderModel(id = "gpt-5", name = "gpt-5")), entries.first().models)
    }

    @Test
    fun generatesUniqueSafeIds() {
        val ids = List(32) { CustomProviderStore.newId() }
        assertEquals(32, ids.toSet().size)
        ids.forEach { id -> assertTrue(id.matches(Regex("custom-[0-9a-f]{8}"))) }
    }
}
