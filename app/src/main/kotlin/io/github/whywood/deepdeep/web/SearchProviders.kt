package io.github.whywood.deepdeep.web

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

class RoutingWebProvider(
    private val backends: List<SearchBackend>,
    private val fallback: WebProvider?,
) : WebProvider {
    override suspend fun search(query: String, maxResults: Int): WebSearchResult {
        val count = maxResults.coerceIn(1, WEB_SEARCH_MAX_RESULTS)
        var lastError: Exception? = null
        for (backend in backends) {
            val key = backend.key()?.trim().orEmpty()
            if (key.isEmpty()) continue
            try {
                return backend.search(key, query, count)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        if (fallback != null) {
            try {
                return fallback.search(query, count)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw lastError ?: error
            }
        }
        throw lastError ?: IllegalStateException("No web search provider is configured")
    }

    override suspend fun fetch(url: String, maxChars: Int): String {
        val provider = fallback ?: throw IllegalStateException("No web fetch provider is configured")
        return provider.fetch(url, maxChars)
    }
}

class SearchBackend(
    val key: suspend () -> String?,
    private val searchFn: suspend (String, String, Int) -> WebSearchResult,
) {
    suspend fun search(key: String, query: String, maxResults: Int): WebSearchResult =
        searchFn(key, query, maxResults)
}

internal fun exaBackend(key: suspend () -> String?): SearchBackend = SearchBackend(key) { apiKey, query, count ->
    val body = buildJsonObject {
        put("query", query)
        put("numResults", count)
        putJsonObject("contents") {
            putJsonObject("text") { put("maxCharacters", 1200) }
        }
    }
    val response = httpJson(
        method = "POST",
        url = "https://api.exa.ai/search",
        label = "Exa search",
        headers = mapOf("x-api-key" to apiKey),
        body = body.toString(),
    )
    val items = (response["results"] as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.string("url").orEmpty()
        if (url.isBlank()) return@mapNotNull null
        WebSearchItem(
            title = item.string("title").orEmpty().ifBlank { url },
            url = url,
            snippet = item.string("text").orEmpty().ifBlank { item.string("summary").orEmpty() },
            date = item.string("publishedDate")?.takeIf { it.isNotBlank() },
        )
    }.take(count)
    WebSearchResult(items)
}

internal fun tavilyBackend(key: suspend () -> String?): SearchBackend = SearchBackend(key) { apiKey, query, count ->
    val body = buildJsonObject {
        put("query", query)
        put("max_results", count)
        put("include_answer", false)
    }
    val response = httpJson(
        method = "POST",
        url = "https://api.tavily.com/search",
        label = "Tavily search",
        headers = mapOf("Authorization" to "Bearer $apiKey"),
        body = body.toString(),
    )
    val items = (response["results"] as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.string("url").orEmpty()
        if (url.isBlank()) return@mapNotNull null
        WebSearchItem(
            title = item.string("title").orEmpty().ifBlank { url },
            url = url,
            snippet = item.string("content").orEmpty(),
            date = item.string("published_date")?.takeIf { it.isNotBlank() },
        )
    }.take(count)
    WebSearchResult(items)
}

internal fun braveBackend(key: suspend () -> String?): SearchBackend = SearchBackend(key) { apiKey, query, count ->
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val response = httpJson(
        method = "GET",
        url = "https://api.search.brave.com/res/v1/web/search?q=$encoded&count=$count",
        label = "Brave search",
        headers = mapOf(
            "X-Subscription-Token" to apiKey,
            "Accept" to "application/json",
        ),
        body = null,
    )
    val results = (response["web"] as? JsonObject)?.get("results") as? JsonArray
    val items = results.orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.string("url").orEmpty()
        if (url.isBlank()) return@mapNotNull null
        WebSearchItem(
            title = item.string("title").orEmpty().ifBlank { url },
            url = url,
            snippet = item.string("description").orEmpty(),
            date = item.string("age")?.takeIf { it.isNotBlank() },
        )
    }.take(count)
    WebSearchResult(items)
}

internal fun serperBackend(key: suspend () -> String?): SearchBackend = SearchBackend(key) { apiKey, query, count ->
    val body = buildJsonObject {
        put("q", query)
        put("num", count)
    }
    val response = httpJson(
        method = "POST",
        url = "https://google.serper.dev/search",
        label = "Serper search",
        headers = mapOf("X-API-KEY" to apiKey),
        body = body.toString(),
    )
    val items = (response["organic"] as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.string("link").orEmpty()
        if (url.isBlank()) return@mapNotNull null
        WebSearchItem(
            title = item.string("title").orEmpty().ifBlank { url },
            url = url,
            snippet = item.string("snippet").orEmpty(),
            date = item.string("date")?.takeIf { it.isNotBlank() },
        )
    }.take(count)
    WebSearchResult(items)
}

private suspend fun httpJson(
    method: String,
    url: String,
    label: String,
    headers: Map<String, String>,
    body: String?,
): JsonObject = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            for ((name, value) in headers) setRequestProperty(name, value)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            connection.outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        if (status !in 200..299) throw IllegalStateException("$label failed (HTTP $status)")
        val json = try {
            Json.parseToJsonElement(payload)
        } catch (error: SerializationException) {
            throw IllegalStateException("$label returned an invalid response")
        }
        json as? JsonObject ?: throw IllegalStateException("$label returned an invalid response")
    } catch (error: IOException) {
        throw IllegalStateException("$label failed: ${error.message ?: "network unavailable"}")
    } finally {
        connection?.disconnect()
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
