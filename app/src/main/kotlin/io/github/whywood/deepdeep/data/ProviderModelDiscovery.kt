package io.github.whywood.deepdeep.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DiscoveredModel(
    val id: String,
    val name: String? = null,
    val contextWindow: Long? = null,
    val maxTokens: Long? = null,
    val supportsImages: Boolean? = null,
    val reasoning: Boolean? = null,
)

private val CONTEXT_FIELDS = listOf(
    "context_window",
    "context_length",
    "contextWindow",
    "inputTokenLimit",
    "max_context_length",
)

private val MAX_TOKENS_FIELDS = listOf(
    "max_output_tokens",
    "max_completion_tokens",
    "outputTokenLimit",
    "maxTokens",
)

private val REASONING_PARAMETERS = setOf("reasoning", "include_reasoning", "reasoning_effort")

object ProviderModelDiscovery {
    suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        protocol: CustomProviderProtocol,
    ): Result<List<DiscoveredModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = baseUrl.trim().trimEnd('/') + "/models"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            when (protocol) {
                CustomProviderProtocol.AnthropicMessages -> {
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                }
                else -> connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IOException("HTTP $code ${detail.take(200)}".trim())
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseModels(body).ifEmpty { throw IOException("empty model list") }
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parseModels(body: String): List<DiscoveredModel> = runCatching {
        val data = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            if (id.isEmpty()) return@mapNotNull null
            DiscoveredModel(
                id = id,
                name = stringOrNull(item, "name") ?: stringOrNull(item, "display_name"),
                contextWindow = firstPositiveLong(item, CONTEXT_FIELDS)
                    ?: item.optJSONObject("top_provider")?.let { provider ->
                        positiveLong(provider, "context_length")
                    },
                maxTokens = firstPositiveLong(item, MAX_TOKENS_FIELDS)
                    ?: item.optJSONObject("top_provider")?.let { provider ->
                        positiveLong(provider, "max_completion_tokens")
                    },
                supportsImages = imageSupport(item),
                reasoning = reasoningSupport(item),
            )
        }.distinctBy { it.id }.sortedBy { it.id }
    }.getOrDefault(emptyList())
}

private fun stringOrNull(item: JSONObject, key: String): String? =
    item.optString(key).trim().takeIf { it.isNotEmpty() }

private fun positiveLong(item: JSONObject, key: String): Long? {
    if (!item.has(key)) return null
    return item.optLong(key).takeIf { it > 0 }
}

private fun firstPositiveLong(item: JSONObject, keys: List<String>): Long? =
    keys.firstNotNullOfOrNull { key -> positiveLong(item, key) }

private fun imageSupport(item: JSONObject): Boolean? {
    val modalities = item.optJSONArray("input_modalities")
        ?: item.optJSONObject("architecture")?.optJSONArray("input_modalities")
    if (modalities != null) return modalities.containsText("image")
    item.optJSONObject("capabilities")?.let { capabilities ->
        if (capabilities.has("vision")) return capabilities.optBoolean("vision")
    }
    if (item.has("supports_vision")) return item.optBoolean("supports_vision")
    return null
}

private fun reasoningSupport(item: JSONObject): Boolean? {
    item.optJSONArray("supported_parameters")?.let { parameters ->
        return (0 until parameters.length()).any { index ->
            parameters.optString(index).trim().lowercase() in REASONING_PARAMETERS
        }
    }
    item.optJSONObject("capabilities")?.let { capabilities ->
        if (capabilities.has("reasoning")) return capabilities.optBoolean("reasoning")
    }
    if (item.has("supports_reasoning")) return item.optBoolean("supports_reasoning")
    return null
}

private fun JSONArray.containsText(value: String): Boolean = (0 until length()).any { index ->
    optString(index).trim().equals(value, ignoreCase = true)
}
