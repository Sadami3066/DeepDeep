package io.github.whywood.deepdeep.plugin.websearch

import android.content.ContextWrapper
import android.content.res.Resources
import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
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

class WebSearchPluginTest {
    private val provider = FakeProvider()
    private val context = FakeContext()

    @Test
    fun identityAndTools() {
        val plugin = newPlugin()
        assertEquals("web-search", plugin.id)
        assertEquals("1.0.0", plugin.version)
        assertEquals(R.string.plugin_web_search_name, plugin.nameRes)
        assertEquals(R.string.plugin_web_search_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_web_search_size, plugin.sizeLabelRes)
        assertEquals(listOf("web_search", "web_fetch"), plugin.tools().map { it.name })
    }

    @Test
    fun availabilityIsAlwaysAvailable() = runTest {
        assertEquals(PluginAvailability.Available, newPlugin().availability())
    }

    @Test
    fun keyConfiguredIsReady() = runTest {
        assertTrue(newPlugin("sk-test").status() is PluginStatus.Ready)
    }

    @Test
    fun missingKeyIsNotReady() = runTest {
        assertTrue(newPlugin(null).status() is PluginStatus.NotReady)
        assertTrue(newPlugin("   ").status() is PluginStatus.NotReady)
    }

    @Test
    fun detailRowsReportSourceAndKeyState() = runTest {
        val rows = newPlugin("sk-test").detailRows(PluginStatus.Ready("ready"))
        assertEquals(2, rows.size)
        assertEquals(R.string.plugin_web_search_detail_source, rows[0].labelRes)
        assertEquals("res:${R.string.plugin_web_search_source_deepseek}", rows[0].value)
        assertEquals(R.string.plugin_web_search_detail_api_key, rows[1].labelRes)
        assertEquals("res:${R.string.plugin_web_search_api_key_configured}", rows[1].value)

        val missing = newPlugin(null).detailRows(PluginStatus.NotReady("missing"))
        assertEquals("res:${R.string.plugin_web_search_api_key_missing}", missing[1].value)
    }

    @Test
    fun promptBodyIsSilentWhenReady() {
        assertNull(newPlugin().promptBody(PluginStatus.Ready("ready")))
    }

    @Test
    fun toolTextFollowsTaskLanguage() {
        val tools = newPlugin().tools()
        assertEquals(listOf("web_search", "web_fetch"), tools.map { it.name })
        tools.forEach { tool ->
            assertNull(tool.promptSnippet)
            assertTrue(tool.promptGuidelines.orEmpty().isNotEmpty())
            assertTrue(tool.description.isNotBlank())
            val properties = tool.parameters["properties"] as JsonObject
            properties.values.forEach { property ->
                val description = ((property as? JsonObject)?.get("description") as? JsonPrimitive)?.contentOrNull
                description?.let { text ->
                    assertTrue(text.isNotBlank())
                }
            }
        }
    }

    @Test
    fun promptBodyExplainsMissingKey() {
        val prompt = newPlugin(null).promptBody(PluginStatus.NotReady("missing")).orEmpty()
        assertTrue(prompt.contains("API Key"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("web_fetch"))
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
        val result = newPlugin().runTool(
            "web_search",
            buildJsonObject { put("query", "深度求索") },
        )
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("深度求索"))
        assertTrue(text.contains("https://example.com/a"))
        assertTrue(text.contains("https://example.com/b"))
        assertTrue(text.contains("Snippet A"))
        assertTrue(text.contains("2026-10-24T00:00:00Z"))
        assertTrue(text.contains("外部网页内容"))
    }

    @Test
    fun searchReportsNoResults() = runTest {
        val result = newPlugin().runTool("web_search", buildJsonObject { put("query", "nothing") })
        assertNull(result.isError)
        assertTrue(result.text().contains("没有找到"))
    }

    @Test
    fun searchUsesDefaultLimitAndClampsExplicitLimit() = runTest {
        newPlugin().runTool("web_search", buildJsonObject { put("query", "q") })
        assertEquals("q" to 5, provider.lastSearch)

        newPlugin().runTool(
            "web_search",
            buildJsonObject {
                put("query", "q")
                put("maxResults", 50)
            },
        )
        assertEquals("q" to WEB_SEARCH_MAX_RESULTS, provider.lastSearch)

        newPlugin().runTool(
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
        val result = newPlugin().runTool("web_search", buildJsonObject { put("query", "q") })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("搜索服务不可用"))
    }

    @Test
    fun blankQueryBecomesErrorResult() = runTest {
        val result = newPlugin().runTool("web_search", buildJsonObject { put("query", "   ") })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("query"))
    }

    @Test
    fun fetchFormatsContentWithUrlAndNotice() = runTest {
        provider.fetchContent = "# Hello\n\nBody text."
        val result = newPlugin().runTool(
            "web_fetch",
            buildJsonObject { put("url", "https://example.com/page") },
        )
        assertNull(result.isError)
        val text = result.text()
        assertTrue(text.contains("https://example.com/page"))
        assertTrue(text.contains("Body text."))
        assertTrue(text.contains("外部网页内容"))
        assertEquals("https://example.com/page" to 12_000, provider.lastFetch)
    }

    @Test
    fun fetchClampsMaxChars() = runTest {
        newPlugin().runTool(
            "web_fetch",
            buildJsonObject {
                put("url", "https://example.com/page")
                put("maxChars", 99_999)
            },
        )
        assertEquals("https://example.com/page" to WEB_FETCH_MAX_CHARS, provider.lastFetch)

        newPlugin().runTool(
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
        val result = newPlugin().runTool(
            "web_fetch",
            buildJsonObject { put("url", "https://example.com/page") },
        )
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("抓取失败"))
    }

    @Test
    fun missingKeyFailsToolsAndStatus() = runTest {
        val plugin = WebSearchPlugin(context, { null })
        assertTrue(plugin.status() is PluginStatus.NotReady)
        val result = plugin.runTool("web_search", buildJsonObject { put("query", "q") })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("API Key"))
    }


    private fun newPlugin(key: String? = "sk-test"): WebSearchPlugin =
        WebSearchPlugin(context, { key }, provider)

    private suspend fun WebSearchPlugin.runTool(name: String, args: JsonObject): AgentToolResult =
        tools().first { it.name == name }.run(args)

    private suspend fun CodingTool.run(args: JsonObject): AgentToolResult =
        execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources
    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }

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
