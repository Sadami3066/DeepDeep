package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.oauth.OAuthCallbackServerOptions
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDeepOAuthCallbackServerFactoryTest {
    private fun httpGet(redirectUri: String, requestTarget: String): String {
        val uri = URI(redirectUri)
        Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 5_000
            val request = "GET $requestTarget HTTP/1.1\r\nHost: ${uri.host}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.encodeToByteArray())
            socket.getOutputStream().flush()
            return socket.getInputStream().readBytes().decodeToString()
        }
    }

    @Test
    fun completesLoginAndReturnsDeepLinkPage() = runBlocking {
        val server = DeepDeepOAuthCallbackServerFactory().start(
            OAuthCallbackServerOptions<String>(
                providerName = "Test Provider",
                host = "127.0.0.1",
                port = 0,
                path = "/callback",
                state = "state-value",
                complete = { "completed" },
            ),
        )
        val response = httpGet(server.redirectUri, "/callback?code=abc&state=state-value")
        assertTrue(response.contains("200 OK"))
        assertTrue(response.contains("deepdeep://oauth/callback"))
        assertEquals("completed", withTimeout(3_000) { server.wait() })
        server.close()
    }

    @Test
    fun wrongStateIsRejected() = runBlocking {
        val server = DeepDeepOAuthCallbackServerFactory().start(
            OAuthCallbackServerOptions<String>(
                providerName = "Test Provider",
                host = "127.0.0.1",
                port = 0,
                path = "/callback",
                state = "state-value",
                complete = { "completed" },
            ),
        )
        val response = httpGet(server.redirectUri, "/callback?code=abc&state=other")
        assertTrue(response.contains("400"))
        assertTrue(response.contains("deepdeep://oauth/callback"))
        server.close()
    }
}
