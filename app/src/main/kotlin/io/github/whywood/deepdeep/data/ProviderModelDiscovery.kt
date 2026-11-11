package io.github.whywood.deepdeep.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ProviderModelDiscovery {
    suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        protocol: CustomProviderProtocol,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
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
                parseModelIds(body).ifEmpty { throw IOException("empty model list") }
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parseModelIds(body: String): List<String> = runCatching {
        val data = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            item.optString("id").takeIf { it.isNotBlank() }
        }.distinct().sorted()
    }.getOrDefault(emptyList())
}
