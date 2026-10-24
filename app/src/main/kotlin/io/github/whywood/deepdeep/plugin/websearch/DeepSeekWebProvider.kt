package io.github.whywood.deepdeep.plugin.websearch

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class DeepSeekWebProvider(
    private val apiKeyProvider: suspend () -> String?,
    baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : WebProvider {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String, maxResults: Int): WebSearchResult {
        val count = maxResults.coerceIn(1, WEB_SEARCH_MAX_RESULTS)
        val body = buildJsonObject {
            put("query", query)
            put("count", count)
            put("need_content", false)
        }
        val response = post("$baseUrl/search", body.toString(), "搜索")
        val results = response["results"] as? JsonArray ?: return WebSearchResult(emptyList())
        return WebSearchResult(
            items = results.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val url = item.string("url").orEmpty()
                if (url.isBlank()) return@mapNotNull null
                WebSearchItem(
                    title = item.string("title").orEmpty().ifBlank { url },
                    url = url,
                    snippet = item.string("snippet").orEmpty()
                        .ifBlank { item.string("summary").orEmpty() }
                        .ifBlank { item.string("date").orEmpty() },
                    date = item.string("date")?.takeIf { it.isNotBlank() },
                )
            }.take(count),
        )
    }

    override suspend fun fetch(url: String, maxChars: Int): String {
        val body = buildJsonObject { put("url", url) }
        val response = post("$baseUrl/fetch", body.toString(), "抓取")
        return response.string("content").orEmpty().take(maxChars.coerceIn(1, WEB_FETCH_MAX_CHARS))
    }

    private suspend fun post(url: String, body: String, label: String): JsonObject = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException(MISSING_KEY_MESSAGE)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(errorMessage(label, status, payload))
            }
            parseResponse(label, payload)
        } catch (error: IOException) {
            throw IllegalStateException("DeepSeek $label 请求失败：${error.message ?: "网络不可用"}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(label: String, payload: String): JsonObject {
        val json = try {
            Json.parseToJsonElement(payload)
        } catch (error: SerializationException) {
            throw IllegalStateException("DeepSeek $label 响应格式错误")
        }
        return json as? JsonObject ?: throw IllegalStateException("DeepSeek $label 响应格式错误")
    }

    private fun errorMessage(label: String, status: Int, payload: String): String {
        val detail = runCatching {
            val json = Json.parseToJsonElement(payload) as? JsonObject ?: return@runCatching null
            (json["error"] as? JsonObject)?.string("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (detail == null) {
            "DeepSeek $label 请求失败（HTTP $status）"
        } else {
            "DeepSeek $label 请求失败（HTTP $status）：$detail"
        }
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1/tools"
        const val DEFAULT_TIMEOUT_MS = 30_000
        const val MISSING_KEY_MESSAGE = "未配置 DeepSeek API Key，请先在设置中为 DeepSeek 提供商配置 API Key。"
    }
}
