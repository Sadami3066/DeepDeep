package io.github.whywood.deepdeep.data

import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.sdk.ai.ModelInputModality
import io.github.whywood.deepdeep.sdk.coding.models.CustomModelConfig
import io.github.whywood.deepdeep.sdk.coding.models.CustomProviderConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class CustomProviderProtocol(val value: String) {
    OpenAiCompletions("openai-completions"),
    OpenAiResponses("openai-responses"),
    AnthropicMessages("anthropic-messages"),
    ;

    companion object {
        fun fromValue(value: String): CustomProviderProtocol? = entries.firstOrNull { it.value == value }
    }
}

data class CustomProviderModel(
    val id: String,
    val name: String,
    val contextWindow: Long? = null,
    val maxTokens: Long? = null,
    val supportsImages: Boolean = false,
    val reasoning: Boolean = false,
    val thinkingLevels: Set<ThinkingLevel> = emptySet(),
)

data class CustomProviderEntry(
    val id: String,
    val name: String,
    val api: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<CustomProviderModel>,
)

private const val SDK_MINIMAL_LEVEL = "minimal"

private val SDK_DEFAULT_THINKING_LEVELS = setOf(
    ThinkingLevel.OFF,
    ThinkingLevel.LOW,
    ThinkingLevel.MEDIUM,
    ThinkingLevel.HIGH,
)

internal fun customThinkingLevelMap(levels: Set<ThinkingLevel>): Map<String, String?> = buildMap<String, String?> {
    if (ThinkingLevel.OFF !in levels) put(ThinkingLevel.OFF.name.lowercase(), null)
    put(SDK_MINIMAL_LEVEL, null)
    ThinkingLevel.entries.forEach { level ->
        if (level == ThinkingLevel.OFF) return@forEach
        val name = level.name.lowercase()
        put(name, if (level in levels) name else null)
    }
}

@Suppress("UNCHECKED_CAST")
private fun Set<ThinkingLevel>.toSdkThinkingLevelMap(): Map<String, String> =
    customThinkingLevelMap(this) as Map<String, String>

class CustomProviderStore(private val file: File) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(readFromDisk())

    val entries: StateFlow<List<CustomProviderEntry>> = state.asStateFlow()

    suspend fun save(entries: List<CustomProviderEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = entries.mapNotNull(::normalize)
            writeToDisk(next)
            state.value = next
        }
    }

    suspend fun saveEntry(entry: CustomProviderEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = normalize(entry) ?: return@withLock
            val current = state.value
            val index = current.indexOfFirst { it.id == normalized.id }
            val next = if (index >= 0) {
                current.toMutableList().also { it[index] = normalized }
            } else {
                current + normalized
            }
            writeToDisk(next)
            state.value = next
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = state.value.filterNot { it.id == id }
            writeToDisk(next)
            state.value = next
        }
    }

    private fun readFromDisk(): List<CustomProviderEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val providers = JSONObject(file.readText()).optJSONObject("providers") ?: return@runCatching emptyList()
            providers.keys().asSequence()
                .mapNotNull { id -> providers.optJSONObject(id)?.let { entry(id, it) } }
                .mapNotNull(::normalize)
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun entry(id: String, item: JSONObject): CustomProviderEntry {
        val modelsElement = item.optJSONArray("models") ?: JSONArray()
        val models = buildList {
            for (index in 0 until modelsElement.length()) {
                val model = modelsElement.optJSONObject(index) ?: continue
                val modelId = model.optString("id").trim()
                if (modelId.isEmpty()) continue
                add(model(modelId, model))
            }
        }
        return CustomProviderEntry(
            id = id.trim(),
            name = item.optString("name").trim(),
            api = item.optString("api").trim(),
            baseUrl = item.optString("baseUrl").trim(),
            apiKey = item.optString("apiKey").trim(),
            models = models,
        )
    }

    private fun model(id: String, item: JSONObject): CustomProviderModel {
        val input = item.optJSONArray("input")
        val supportsImages =
            input != null && (0 until input.length()).any { index -> input.optString(index) == "image" }
        val reasoning = item.optBoolean("reasoning", false)
        val thinkingLevels = if (reasoning) {
            thinkingLevelsFromMap(item.optJSONObject("thinkingLevelMap"))
        } else {
            emptySet()
        }
        return CustomProviderModel(
            id = id,
            name = item.optString("name").trim().ifBlank { id },
            contextWindow = item.positiveLong("contextWindow"),
            maxTokens = item.positiveLong("maxTokens"),
            supportsImages = supportsImages,
            reasoning = reasoning && thinkingLevels.isNotEmpty(),
            thinkingLevels = thinkingLevels,
        )
    }

    private fun thinkingLevelsFromMap(levels: JSONObject?): Set<ThinkingLevel> {
        if (levels == null) return SDK_DEFAULT_THINKING_LEVELS
        return ThinkingLevel.entries.filterTo(LinkedHashSet()) { level ->
            val name = level.name.lowercase()
            if (levels.has(name)) !levels.isNull(name) else level in SDK_DEFAULT_THINKING_LEVELS
        }
    }

    private fun JSONObject.positiveLong(key: String): Long? {
        if (!has(key)) return null
        val value = optLong(key)
        return value.takeIf { it > 0 }
    }

    private fun writeToDisk(entries: List<CustomProviderEntry>) {
        val providers = JSONObject()
        entries.forEach { entry ->
            providers.put(
                entry.id,
                JSONObject()
                    .put("name", entry.name)
                    .put("api", entry.api)
                    .put("baseUrl", entry.baseUrl)
                    .put("apiKey", entry.apiKey)
                    .put(
                        "models",
                        JSONArray().apply {
                            entry.models.forEach { model ->
                                put(
                                    JSONObject()
                                        .put("id", model.id)
                                        .put("name", model.name)
                                        .apply {
                                            model.contextWindow?.let { put("contextWindow", it) }
                                            model.maxTokens?.let { put("maxTokens", it) }
                                            put(
                                                "input",
                                                JSONArray().apply {
                                                    put("text")
                                                    if (model.supportsImages) put("image")
                                                },
                                            )
                                            put("reasoning", model.reasoning)
                                            if (model.reasoning) {
                                                put(
                                                    "thinkingLevelMap",
                                                    JSONObject().apply {
                                                        customThinkingLevelMap(model.thinkingLevels)
                                                            .forEach { (level, value) -> put(level, value ?: JSONObject.NULL) }
                                                    },
                                                )
                                            }
                                        },
                                )
                            }
                        },
                    ),
            )
        }
        val text = JSONObject().put("providers", providers).toString(2)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    private fun normalize(entry: CustomProviderEntry): CustomProviderEntry? {
        val id = entry.id.trim()
        val name = entry.name.trim()
        val api = entry.api.trim()
        val baseUrl = entry.baseUrl.trim()
        val apiKey = entry.apiKey.trim()
        if (!id.matches(PROVIDER_ID_REGEX)) return null
        if (name.isEmpty() || api.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty()) return null
        if (CustomProviderProtocol.fromValue(api) == null) return null
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) return null
        val models = entry.models.mapNotNull { model ->
            val modelId = model.id.trim()
            if (modelId.isEmpty()) {
                null
            } else {
                val reasoning = model.reasoning && model.thinkingLevels.isNotEmpty()
                CustomProviderModel(
                    id = modelId,
                    name = model.name.trim().ifBlank { modelId },
                    contextWindow = model.contextWindow?.takeIf { it > 0 },
                    maxTokens = model.maxTokens?.takeIf { it > 0 },
                    supportsImages = model.supportsImages,
                    reasoning = reasoning,
                    thinkingLevels = if (reasoning) model.thinkingLevels else emptySet(),
                )
            }
        }
        if (models.isEmpty()) return null
        return CustomProviderEntry(id, name, api, baseUrl, apiKey, models)
    }

    companion object {
        private val PROVIDER_ID_REGEX = Regex("[a-z0-9][a-z0-9._-]{0,63}")

        fun newId(): String = "custom-" + UUID.randomUUID().toString().replace("-", "").take(8)
    }
}

internal fun CustomProviderEntry.toProviderConfig(): CustomProviderConfig = CustomProviderConfig(
    id = id,
    name = name,
    baseUrl = baseUrl,
    api = api,
    apiKey = apiKey,
    models = models.map { model ->
        CustomModelConfig(
            id = model.id,
            name = model.name,
            contextWindow = model.contextWindow,
            maxTokens = model.maxTokens,
            reasoning = model.reasoning.takeIf { it },
            input = if (model.supportsImages) {
                listOf(ModelInputModality.Text, ModelInputModality.Image)
            } else {
                listOf(ModelInputModality.Text)
            },
            thinkingLevelMap = if (model.reasoning) model.thinkingLevels.toSdkThinkingLevelMap() else null,
        )
    },
)
