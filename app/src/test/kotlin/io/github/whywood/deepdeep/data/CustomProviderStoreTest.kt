package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.sdk.ai.ModelInputModality
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun savedModelJson(): JSONObject {
        val providers = JSONObject(file.readText()).getJSONObject("providers")
        return providers.getJSONObject(entry().id).getJSONArray("models").getJSONObject(0)
    }

    private fun savedThinkingLevels(): JSONObject = savedModelJson().getJSONObject("thinkingLevelMap")

    private fun inputOf(model: JSONObject): List<String> {
        val input = model.getJSONArray("input")
        return (0 until input.length()).map { index -> input.getString(index) }
    }

    private fun levelKeys(): Set<String> =
        savedThinkingLevels().keys().asSequence().toSet()

    private fun enabledLevels(): Map<String, String> {
        val levels = savedThinkingLevels()
        return levels.keys().asSequence()
            .filter { key -> !levels.isNull(key) }
            .associateWith { key -> levels.getString(key) }
    }

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

    @Test
    fun savesAndReloadsModelCapabilities() = runTest {
        val model = CustomProviderModel(
            id = "gpt-5",
            name = "GPT-5",
            contextWindow = 200_000,
            maxTokens = 8_192,
            supportsImages = true,
            reasoning = true,
            thinkingLevels = setOf(ThinkingLevel.OFF, ThinkingLevel.HIGH),
        )
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(model))))
        val reloaded = CustomProviderStore(file).entries.value.single().models.single()
        assertEquals(model, reloaded)
        val saved = savedModelJson()
        assertEquals(200_000L, saved.getLong("contextWindow"))
        assertEquals(8_192L, saved.getLong("maxTokens"))
        assertEquals(listOf("text", "image"), inputOf(saved))
        assertTrue(saved.getBoolean("reasoning"))
        assertEquals(setOf("minimal", "low", "medium", "high", "xhigh", "max"), levelKeys())
        assertEquals(ThinkingLevel.HIGH.name.lowercase(), savedThinkingLevels().getString("high"))
        assertTrue(savedThinkingLevels().isNull("low"))
        assertTrue(savedThinkingLevels().isNull("xhigh"))
        assertTrue(savedThinkingLevels().isNull("max"))
    }

    @Test
    fun writesOnlyChosenThinkingLevels() = runTest {
        val model = CustomProviderModel(
            id = "gpt-5",
            name = "GPT-5",
            reasoning = true,
            thinkingLevels = setOf(ThinkingLevel.LOW),
        )
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(model))))
        assertEquals(mapOf("low" to "low"), enabledLevels())
        assertEquals(setOf("off", "minimal", "low", "medium", "high", "xhigh", "max"), levelKeys())
        assertTrue(savedThinkingLevels().isNull("off"))
        assertTrue(savedThinkingLevels().isNull("medium"))
        assertTrue(savedThinkingLevels().isNull("high"))
        assertTrue(savedThinkingLevels().isNull("xhigh"))
        assertTrue(savedThinkingLevels().isNull("max"))
        assertTrue(savedThinkingLevels().isNull("minimal"))
    }

    @Test
    fun writesOffSupportByOmittingKey() = runTest {
        val withOff = CustomProviderModel(
            id = "gpt-5",
            name = "GPT-5",
            reasoning = true,
            thinkingLevels = setOf(ThinkingLevel.OFF),
        )
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(withOff))))
        assertFalse(savedThinkingLevels().has("off"))
        assertEquals(emptyMap<String, String>(), enabledLevels())

        val withoutOff = withOff.copy(thinkingLevels = setOf(ThinkingLevel.MAX))
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(withoutOff))))
        assertTrue(savedThinkingLevels().isNull("off"))
        assertEquals(mapOf("max" to "max"), enabledLevels())
    }

    @Test
    fun customThinkingLevelMapMatchesSdkSemantics() {
        assertEquals(
            mapOf("off" to null, "minimal" to null, "low" to null, "medium" to null, "high" to null, "xhigh" to null, "max" to null),
            customThinkingLevelMap(emptySet()),
        )
        assertEquals(
            setOf("minimal", "low", "medium", "high", "xhigh", "max"),
            customThinkingLevelMap(setOf(ThinkingLevel.OFF)).keys,
        )
        assertEquals(
            mapOf(
                "off" to null,
                "minimal" to null,
                "low" to null,
                "medium" to null,
                "high" to null,
                "xhigh" to "xhigh",
                "max" to "max",
            ),
            customThinkingLevelMap(setOf(ThinkingLevel.XHIGH, ThinkingLevel.MAX)),
        )
    }

    @Test
    fun clearsThinkingLevelsWhenReasoningIsOff() = runTest {
        val model = CustomProviderModel(
            id = "gpt-5",
            name = "GPT-5",
            reasoning = false,
            thinkingLevels = setOf(ThinkingLevel.LOW, ThinkingLevel.MAX),
        )
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(model))))
        val reloaded = CustomProviderStore(file).entries.value.single().models.single()
        assertFalse(reloaded.reasoning)
        assertEquals(emptySet<ThinkingLevel>(), reloaded.thinkingLevels)
        assertFalse(savedModelJson().has("thinkingLevelMap"))
    }

    @Test
    fun dropsReasoningWhenNoThinkingLevelIsChosen() = runTest {
        val model = CustomProviderModel(id = "gpt-5", name = "GPT-5", reasoning = true)
        CustomProviderStore(file).save(listOf(entry().copy(models = listOf(model))))
        val reloaded = CustomProviderStore(file).entries.value.single().models.single()
        assertFalse(reloaded.reasoning)
        assertEquals(emptySet<ThinkingLevel>(), reloaded.thinkingLevels)
        assertFalse(savedModelJson().has("thinkingLevelMap"))
    }

    @Test
    fun readsLegacyMaxThinkingLevelFormat() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "providers": {
                "custom-legacy": {
                  "name": "Legacy",
                  "api": "openai-completions",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "models": [
                    {
                      "id": "gpt-5",
                      "name": "GPT-5",
                      "reasoning": true,
                      "thinkingLevelMap": {"low": "low", "medium": "medium", "high": "high"}
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val model = CustomProviderStore(file).entries.value.single().models.single()
        assertTrue(model.reasoning)
        assertEquals(
            setOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH),
            model.thinkingLevels,
        )
    }

    @Test
    fun defaultsToSdkThinkingLevelsWhenMapIsMissing() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "providers": {
                "custom-default": {
                  "name": "Default",
                  "api": "openai-completions",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "models": [{"id": "gpt-5", "reasoning": true}]
                }
              }
            }
            """.trimIndent(),
        )
        val model = CustomProviderStore(file).entries.value.single().models.single()
        assertTrue(model.reasoning)
        assertEquals(
            setOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH),
            model.thinkingLevels,
        )
    }

    @Test
    fun dropsReasoningWhenMapHasNoSupportedLevel() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "providers": {
                "custom-none": {
                  "name": "None",
                  "api": "openai-completions",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "models": [
                    {
                      "id": "gpt-5",
                      "reasoning": true,
                      "thinkingLevelMap": {
                        "off": null,
                        "minimal": null,
                        "low": null,
                        "medium": null,
                        "high": null,
                        "xhigh": null,
                        "max": null
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val model = CustomProviderStore(file).entries.value.single().models.single()
        assertFalse(model.reasoning)
        assertEquals(emptySet<ThinkingLevel>(), model.thinkingLevels)
    }

    @Test
    fun omitsModelCapabilityDefaults() = runTest {
        CustomProviderStore(file).save(listOf(entry()))
        val saved = savedModelJson()
        assertFalse(saved.has("contextWindow"))
        assertFalse(saved.has("maxTokens"))
        assertFalse(saved.has("thinkingLevelMap"))
        assertEquals(listOf("text"), inputOf(saved))
        assertFalse(saved.getBoolean("reasoning"))
    }

    @Test
    fun mapsCapabilitiesToSdkConfig() {
        val provider = entry().copy(
            models = listOf(
                CustomProviderModel(
                    id = "m1",
                    name = "M1",
                    contextWindow = 128_000,
                    maxTokens = 4_096,
                    supportsImages = true,
                    reasoning = true,
                    thinkingLevels = setOf(ThinkingLevel.HIGH),
                ),
                CustomProviderModel(id = "m2", name = "M2"),
            ),
        )
        val config = provider.toProviderConfig()
        assertEquals(provider.id, config.id)
        assertEquals(provider.api, config.api)
        assertEquals(listOf("m1", "m2"), config.models.map { it.id })
        val first = config.models[0]
        assertEquals(128_000L, first.contextWindow)
        assertEquals(4_096L, first.maxTokens)
        assertEquals(true, first.reasoning)
        assertEquals(listOf(ModelInputModality.Text, ModelInputModality.Image), first.input)
        assertEquals(
            mapOf(
                "off" to null,
                "minimal" to null,
                "low" to null,
                "medium" to null,
                "high" to "high",
                "xhigh" to null,
                "max" to null,
            ),
            first.thinkingLevelMap,
        )
        val second = config.models[1]
        assertNull(second.contextWindow)
        assertNull(second.maxTokens)
        assertNull(second.reasoning)
        assertEquals(listOf(ModelInputModality.Text), second.input)
        assertNull(second.thinkingLevelMap)
    }
}
