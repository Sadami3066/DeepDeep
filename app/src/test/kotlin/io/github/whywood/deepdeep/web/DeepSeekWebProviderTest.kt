package io.github.whywood.deepdeep.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekWebProviderTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val requests = CopyOnWriteArrayList<Request>()
    private var reply: (path: String) -> Reply = { Reply(200, "{}") }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/v1/tools"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun searchSendsAuthorizedRequestAndParsesResults() = runTest {
        reply = {
            Reply(
                200,
                """
                {"results":[
                  {"title":"DeepSeek V4","url":"https://example.com/a","snippet":"Snippet A","summary":"Summary A","date":"2026-06-01T00:00:00+00:00"},
                  {"title":"Second","url":"https://example.com/b","snippet":"Snippet B","summary":"Summary B","date":null}
                ]}
                """.trimIndent(),
            )
        }

        val result = provider().search("深度求索", 3)

        val request = requests.single()
        assertEquals("/v1/tools/search", request.path)
        assertEquals("Bearer sk-test", request.authorization)
        assertEquals("application/json", request.contentType)
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("深度求索", body.getValue("query").jsonPrimitive.content)
        assertEquals(3, body.getValue("count").jsonPrimitive.int)
        assertEquals(false, body.getValue("need_content").jsonPrimitive.boolean)

        assertEquals(2, result.items.size)
        assertEquals("DeepSeek V4", result.items[0].title)
        assertEquals("https://example.com/a", result.items[0].url)
        assertEquals("Snippet A", result.items[0].snippet)
        assertEquals("2026-06-01T00:00:00+00:00", result.items[0].date)
        assertEquals("Snippet B", result.items[1].snippet)
        assertNull(result.items[1].date)
    }

    @Test
    fun searchFallsBackToSummaryThenDate() = runTest {
        reply = {
            Reply(
                200,
                """
                {"results":[
                  {"title":"T1","url":"https://example.com/1","snippet":"","summary":"Long summary","date":"2026-01-01"},
                  {"title":"T2","url":"https://example.com/2","snippet":"","summary":"","date":"2026-01-02"}
                ]}
                """.trimIndent(),
            )
        }

        val result = provider().search("q", 5)

        assertEquals("Long summary", result.items[0].snippet)
        assertEquals("2026-01-02", result.items[1].snippet)
    }

    @Test
    fun searchCapsCountAndResults() = runTest {
        val items = (1..12).joinToString(",") {
            """{"title":"T$it","url":"https://example.com/$it","snippet":"S$it"}"""
        }
        reply = { Reply(200, """{"results":[$items]}""") }

        val result = provider().search("q", 50)

        val body = Json.parseToJsonElement(requests.single().body).jsonObject
        assertEquals(WEB_SEARCH_MAX_RESULTS, body.getValue("count").jsonPrimitive.int)
        assertEquals(WEB_SEARCH_MAX_RESULTS, result.items.size)
    }

    @Test
    fun searchHandlesEmptyResults() = runTest {
        reply = { Reply(200, """{"results":[]}""") }

        val result = provider().search("q", 5)

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun fetchSendsUrlAndParsesContent() = runTest {
        reply = { Reply(200, """{"title":"Example","content":"# Hello\n\nWorld"}""") }

        val content = provider().fetch("https://example.com/article", 100)

        val request = requests.single()
        assertEquals("/v1/tools/fetch", request.path)
        assertEquals("Bearer sk-test", request.authorization)
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("https://example.com/article", body.getValue("url").jsonPrimitive.content)
        assertEquals("# Hello\n\nWorld", content)
    }

    @Test
    fun fetchCapsMaxCharacters() = runTest {
        val content = "a".repeat(40_000)
        reply = { Reply(200, """{"content":"$content"}""") }

        assertEquals(WEB_FETCH_MAX_CHARS, provider().fetch("https://example.com/long", 99_999).length)
        assertEquals(1, provider().fetch("https://example.com/long", 0).length)
    }

    @Test
    fun nonSuccessSearchSurfacesServerMessage() = runTest {
        reply = { Reply(401, """{"error":{"message":"Authentication Fails"}}""") }

        val error = assertFails { provider().search("q", 5) }

        assertTrue(error.message!!.contains("HTTP 401"))
        assertTrue(error.message!!.contains("Authentication Fails"))
    }

    @Test
    fun nonSuccessFetchSurfacesServerMessage() = runTest {
        reply = { Reply(404, """{"error":{"message":"URL blocked by site policy"}}""") }

        val error = assertFails { provider().fetch("https://example.com/blocked", 100) }

        assertTrue(error.message!!.startsWith("Fetch failed (HTTP 404)"))
        assertTrue(error.message!!.contains("URL blocked by site policy"))
        assertFalse(error.message!!.contains("DeepSeek"))
    }

    @Test
    fun networkFailureDoesNotMentionProviderName() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }
        val failing = DeepSeekWebProvider(
            apiKeyProvider = { "sk-test" },
            baseUrl = "http://127.0.0.1:$closedPort/v1/tools",
        )

        val error = assertFails { failing.fetch("https://example.com", 100) }

        assertTrue(error.message!!.startsWith("Fetch failed: "))
        assertFalse(error.message!!.contains("DeepSeek"))
    }

    @Test
    fun nonSuccessWithoutJsonBodyStillReportsStatus() = runTest {
        reply = { Reply(500, "upstream down") }

        val error = assertFails { provider().search("q", 5) }

        assertTrue(error.message!!.contains("HTTP 500"))
    }

    @Test
    fun missingKeyFailsWithoutSendingRequest() = runTest {
        val error = assertFails { provider(null).search("q", 5) }

        assertTrue(error.message!!.contains("API key"))
        assertTrue(requests.isEmpty())
    }

    private fun provider(key: String? = "sk-test"): DeepSeekWebProvider =
        DeepSeekWebProvider(apiKeyProvider = { key }, baseUrl = baseUrl)

    private suspend fun assertFails(block: suspend () -> Unit): IllegalStateException {
        try {
            block()
        } catch (error: IllegalStateException) {
            return error
        }
        throw AssertionError("Expected IllegalStateException")
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        requests += Request(
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            contentType = exchange.requestHeaders.getFirst("Content-Type"),
            body = body,
        )
        val response = reply(exchange.requestURI.path)
        val bytes = response.body.toByteArray()
        exchange.sendResponseHeaders(response.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private data class Request(
        val path: String,
        val authorization: String?,
        val contentType: String?,
        val body: String,
    )

    private data class Reply(val status: Int, val body: String)
}
