package io.github.whywood.deepdeep.plugin.websearch

const val WEB_SEARCH_MAX_RESULTS = 10
const val WEB_FETCH_MAX_CHARS = 30_000

interface WebProvider {
    suspend fun search(query: String, maxResults: Int): WebSearchResult

    suspend fun fetch(url: String, maxChars: Int): String
}

data class WebSearchResult(
    val items: List<WebSearchItem>,
)

data class WebSearchItem(
    val title: String,
    val url: String,
    val snippet: String,
    val date: String? = null,
)
