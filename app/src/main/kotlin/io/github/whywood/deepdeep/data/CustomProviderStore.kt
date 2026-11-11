package io.github.whywood.deepdeep.data

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
)

data class CustomProviderEntry(
    val id: String,
    val name: String,
    val api: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<CustomProviderModel>,
)

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
                add(CustomProviderModel(id = modelId, name = model.optString("name").trim().ifBlank { modelId }))
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
                                put(JSONObject().put("id", model.id).put("name", model.name))
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
            if (modelId.isEmpty()) null else CustomProviderModel(modelId, model.name.trim().ifBlank { modelId })
        }
        if (models.isEmpty()) return null
        return CustomProviderEntry(id, name, api, baseUrl, apiKey, models)
    }

    companion object {
        private val PROVIDER_ID_REGEX = Regex("[a-z0-9][a-z0-9._-]{0,63}")

        fun newId(): String = "custom-" + UUID.randomUUID().toString().replace("-", "").take(8)
    }
}
