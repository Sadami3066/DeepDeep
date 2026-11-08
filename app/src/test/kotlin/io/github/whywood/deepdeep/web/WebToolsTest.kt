package io.github.whywood.deepdeep.web

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebToolsTest {
    private val provider = FakeProvider()

    @Test
    fun exposesWebTools() {
        val names = webTools(provider).map { it.name }
        assertEquals(listOf("web_search", "web_fetch"), names)
    }

    @Test
    fun toolTextFollowsTaskLanguage() {
        val tools = webTools(provider)
        tools.forEach { tool ->
            assertNull(tool.promptSnippet)
            assertTrue(tool.promptGuidelines?.isNotEmpty() == true)
            assertTrue(tool.description.isNotBlank())
            val properties = tool.parameters["properties"] as JsonObject
            properties.values.forEach { property ->
                val description = ((property as? JsonObject)?.get("description") as? JsonPrimitive)?.contentOrNull
                description?.let { text -> assertTrue(text.isNotBlank()) }
            }
        }
    }

    @Test
    fun searchFormatsRankedResultsWithUrlsAndNotice() = runTest {
        provider.searchResult = WebSearchResult(
            listOf(
                WebSearchItem(
                    title = "Title A",
                    url = "https://example.com/a",
                    snippet = "Snippet A",
                    date = "2026-10-24T00:00:00Z",
                ),
                WebSearchItem("Title B", "https://example.com/b", "Snippet B"),
            ),
        )
        val result = runTool(
            "web_search",
            buildJsonObject { put("query", "深度求索") },
        )
        assertFalse(result.isError == true)
        val text = result.text()
        assertTrue(text.contains("深度求索"))
        assertTrue(text.contains("https://example.com/a"))
        assertTrue(text.contains("https://example.com/b"))
        assertTrue(text.contains("Snippet A"))
        assertTrue(text.contains("2026-10-24T00:00:00Z"))
        assertTrue(text.contains("External web content"))
    }

    @Test
    fun searchReportsNoResults() = runTest {
        val result = runTool("web_search", buildJsonObject { put("query", "nothing") })
        assertFalse(result.isError == true)
        assertTrue(result.text().contains("No results found"))
    }

    @Test
    fun searchUsesDefaultLimitAndClampsExplicitLimit() = runTest {
        runTool("web_search", buildJsonObject { put("query", "q") })
        assertEquals("q" to 5, provider.lastSearch)

        runTool(
            "web_search",
            buildJsonObject {
                put("query", "q")
                put("maxResults", 50)
            },
        )
        assertEquals("q" to WEB_SEARCH_MAX_RESULTS, provider.lastSearch)

        runTool(
            "web_search",
            buildJsonObject {
                put("query", "q")
                put("maxResults", 0)
            },
        )
        assertEquals("q" to 1, provider.lastSearch)
    }

    @Test
    fun searchProviderFailureBecomesErrorResult() = runTest {
        provider.failWith = IllegalStateException("搜索服务不可用")
        val result = runTool("web_search", buildJsonObject { put("query", "q") })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("搜索服务不可用"))
    }

    @Test
    fun blankQueryBecomesErrorResult() = runTest {
        val result = runTool("web_search", buildJsonObject { put("query", "   ") })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("query"))
    }

    @Test
    fun fetchFormatsContentWithUrlAndNotice() = runTest {
        provider.fetchContent = "# Hello\n\nBody text."
        val result = runTool(
            "web_fetch",
            buildJsonObject { put("url", "https://example.com/page") },
        )
        assertFalse(result.isError == true)
        val text = result.text()
        assertTrue(text.contains("https://example.com/page"))
        assertTrue(text.contains("Body text."))
        assertTrue(text.contains("External web content"))
        assertEquals("https://example.com/page" to 12_000, provider.lastFetch)
    }

    @Test
    fun fetchClampsMaxChars() = runTest {
        runTool(
            "web_fetch",
            buildJsonObject {
                put("url", "https://example.com/page")
                put("maxChars", 99_999)
            },
        )
        assertEquals("https://example.com/page" to WEB_FETCH_MAX_CHARS, provider.lastFetch)

        runTool(
            "web_fetch",
            buildJsonObject {
                put("url", "https://example.com/page")
                put("maxChars", 0)
            },
        )
        assertEquals("https://example.com/page" to 1, provider.lastFetch)
    }

    @Test
    fun blankFetchContentBecomesErrorResult() = runTest {
        provider.fetchContent = "  "
        val result = runTool(
            "web_fetch",
            buildJsonObject { put("url", "https://example.com/page") },
        )
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("Fetch failed"))
    }

    @Test
    fun missingKeyFailsTools() = runTest {
        val tools = webTools(DeepSeekWebProvider(apiKeyProvider = { null }))
        val result = tools.first { it.name == "web_search" }.run(buildJsonObject { put("query", "q") })
        assertTrue(result.isError == true)
        assertTrue(result.text().contains("API key"))
    }

    private suspend fun runTool(name: String, args: JsonObject): AgentToolResult =
        webTools(provider).first { it.name == name }.run(args)

    private suspend fun CodingTool.run(args: JsonObject): AgentToolResult =
        execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private class FakeProvider : WebProvider {
        var searchResult: WebSearchResult = WebSearchResult(emptyList())
        var fetchContent: String = ""
        var failWith: Exception? = null
        var lastSearch: Pair<String, Int>? = null
        var lastFetch: Pair<String, Int>? = null

        override suspend fun search(query: String, maxResults: Int): WebSearchResult {
            lastSearch = query to maxResults
            failWith?.let { throw it }
            return searchResult
        }

        override suspend fun fetch(url: String, maxChars: Int): String {
            lastFetch = url to maxChars
            failWith?.let { throw it }
            return fetchContent.take(maxChars)
        }
    }
}
