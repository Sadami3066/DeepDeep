package io.github.whywood.deepdeep.plugin.host.services

import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkPluginServiceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun requestReturnsStatusBodyAndContentType() = runTest {
        val transport = FakeTransport {
            HttpResponse(201, mapOf("Content-Type" to "text/plain"), Buffer().writeUtf8("ok"))
        }
        val service = NetworkPluginService(transport)

        val response = service.request(
            method = "post",
            url = "https://example.com/api",
            headers = mapOf("X-Test" to "1"),
            body = "payload",
            timeoutMs = 1_000,
        )

        assertEquals(201, response.statusCode)
        assertEquals("ok", response.body)
        assertEquals("text/plain", response.contentType)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("payload", String(request.body ?: ByteArray(0), Charsets.UTF_8))
        assertEquals("https://example.com/api", request.url)
    }

    @Test
    fun downloadWritesFileReportsProgressAndVerifiesSha() = runTest {
        val target = File(temporary.newFolder("downloads"), "file.txt")
        val service = NetworkPluginService(
            FakeTransport { HttpResponse(200, mapOf("Content-Length" to "5"), Buffer().writeUtf8("hello")) },
        )
        val progress = mutableListOf<Int?>()

        val result = service.download(
            url = "https://example.com/file",
            targetPath = target.absolutePath,
            expectedSha256 = sha256Hex("hello"),
        ) { progress += it }

        assertEquals("hello", target.readText())
        assertEquals(5L, result.size)
        assertEquals(sha256Hex("hello"), result.sha256)
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.last() == 100)
    }

    @Test
    fun downloadRejectsChecksumMismatch() = runTest {
        val target = File(temporary.newFolder("downloads"), "file.txt")
        val service = NetworkPluginService(
            FakeTransport { HttpResponse(200, emptyMap(), Buffer().writeUtf8("hello")) },
        )

        var failure: IllegalStateException? = null
        try {
            service.download(
                url = "https://example.com/file",
                targetPath = target.absolutePath,
                expectedSha256 = sha256Hex("other"),
            )
        } catch (error: IllegalStateException) {
            failure = error
        }

        assertTrue(failure?.message.orEmpty().contains("checksum"))
        assertFalse(target.exists())
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private class FakeTransport(
        private val handler: (HttpRequest) -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler(request)
        }
    }
}
