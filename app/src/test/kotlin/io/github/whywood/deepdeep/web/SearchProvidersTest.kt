package io.github.whywood.deepdeep.web

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchProvidersTest {
    @Test
    fun searchSkipsUnusableBackendsAndFallsBackOnFailure() = runTest {
        val attempts = mutableListOf<String>()
        val direct = SearchBackend(key = { "configured" }) { _, _, _ ->
            attempts += "direct"
            throw IllegalStateException("upstream down")
        }
        val fallback = object : WebProvider {
            override suspend fun search(query: String, maxResults: Int): WebSearchResult {
                attempts += "fallback"
                return WebSearchResult(listOf(WebSearchItem("fallback", "https://example.com", "ok")))
            }

            override suspend fun fetch(url: String, maxChars: Int): String = "fallback fetch"
        }
        val result = RoutingWebProvider(listOf(direct), fallback).search("query", 3)
        assertEquals("fallback", result.items.single().title)
        assertEquals(listOf("direct", "fallback"), attempts)
    }

    @Test
    fun searchUsesFirstConfiguredBackend() = runTest {
        val first = SearchBackend(key = { "first-key" }) { key, _, _ ->
            assertEquals("first-key", key)
            WebSearchResult(listOf(WebSearchItem("first", "https://example.com", "ok")))
        }
        val second = SearchBackend(key = { "second-key" }) { _, _, _ ->
            WebSearchResult(listOf(WebSearchItem("second", "https://example.com", "ok")))
        }
        val result = RoutingWebProvider(listOf(first, second), fallback = null).search("query", 3)
        assertEquals("first", result.items.single().title)
    }

    @Test
    fun searchWithoutKeysOrFallbackFails() = runTest {
        val failure = runCatching {
            RoutingWebProvider(
                listOf(SearchBackend(key = { null }) { _, _, _ -> error("should not run") }),
                fallback = null,
            ).search("query", 3)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun fetchUsesFallback() = runTest {
        val fallback = object : WebProvider {
            override suspend fun search(query: String, maxResults: Int): WebSearchResult =
                WebSearchResult(emptyList())

            override suspend fun fetch(url: String, maxChars: Int): String = "content"
        }
        assertEquals("content", RoutingWebProvider(emptyList(), fallback).fetch("https://example.com", 100))
    }
}
