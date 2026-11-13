package io.github.whywood.deepdeep.plugin.host.extensions.data

import io.github.whywood.deepdeep.plugin.api.PluginWebPage
import io.github.whywood.deepdeep.plugin.api.PluginWebResult
import io.github.whywood.deepdeep.plugin.api.WebService
import io.github.whywood.deepdeep.web.WEB_FETCH_MAX_CHARS
import io.github.whywood.deepdeep.web.WEB_SEARCH_MAX_RESULTS
import io.github.whywood.deepdeep.web.WebProvider

internal class WebPluginServiceImpl(
    private val provider: WebProvider?,
    private val availableCheck: () -> Boolean = { provider != null },
) : WebService {
    override val available: Boolean get() = provider != null && availableCheck()

    override suspend fun search(query: String, limit: Int): List<PluginWebResult> {
        val active = provider ?: throw IllegalStateException(unavailableMessage())
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val result = active.search(text, limit.coerceIn(1, WEB_SEARCH_MAX_RESULTS))
        return result.items.take(limit.coerceAtLeast(1)).map { item ->
            PluginWebResult(
                title = item.title,
                url = item.url,
                snippet = item.snippet.takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun fetch(url: String, maxChars: Int): PluginWebPage? {
        val active = provider ?: throw IllegalStateException(unavailableMessage())
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            throw IllegalArgumentException("Only http(s) URLs can be fetched: $url")
        }
        val limit = maxChars.coerceIn(1, WEB_FETCH_MAX_CHARS)
        val content = active.fetch(target, limit)
        if (content.isBlank()) return null
        return PluginWebPage(
            url = target,
            title = null,
            text = content,
            truncated = content.length >= limit,
        )
    }

    private fun unavailableMessage(): String =
        "Web search is unavailable: configure the DeepSeek API key or a web search provider first"
}
