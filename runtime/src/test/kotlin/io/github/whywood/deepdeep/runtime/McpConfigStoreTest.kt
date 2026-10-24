package io.github.whywood.deepdeep.runtime

import dev.pi.mcp.config.McpServerConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpConfigStoreTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-mcp").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun http(
        url: String = "https://mcp.example.com/mcp",
        headers: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
    ) = McpServerConfig.Http(url = url, headers = headers, enabled = enabled)

    @Test
    fun upsertAndLoadRoundTrip() = runTest {
        val store = McpConfigStore(directory)
        store.upsert("docs", http(url = "https://mcp.example.com/mcp", headers = mapOf("X-Key" to "secret")))
        val snapshot = store.load()
        assertTrue(snapshot.errors.isEmpty())
        val loaded = snapshot.servers.getValue("docs") as McpServerConfig.Http
        assertEquals("https://mcp.example.com/mcp", loaded.url)
        assertEquals("secret", loaded.headers["X-Key"])
        assertTrue(loaded.enabled)
    }

    @Test
    fun setEnabledAndRemove() = runTest {
        val store = McpConfigStore(directory)
        store.upsert("docs", http())
        store.setEnabled("docs", false)
        assertFalse((store.load().servers.getValue("docs")).enabled)
        store.remove("docs")
        assertTrue(store.load().servers.isEmpty())
        assertFalse(File(directory, "mcp.json.tmp").exists())
    }

    @Test
    fun preservesStdioEntries() = runTest {
        val file = File(directory, "mcp.json")
        file.writeText(
            """
            {"mcpServers":{"local":{"type":"stdio","command":"npx","args":["-y","server"]}}}
            """.trimIndent(),
        )
        val store = McpConfigStore(directory)
        store.upsert("docs", http())
        val text = file.readText()
        assertTrue(text.contains("\"command\""))
        assertTrue(text.contains("\"npx\""))
        assertEquals(2, store.load().servers.size)
    }

    @Test
    fun invalidEntryIsRejected() = runTest {
        val store = McpConfigStore(directory)
        val failure = runCatching {
            store.upsert("broken", McpServerConfig.Http(url = "not-a-url"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(store.load().servers.isEmpty())
    }

    @Test
    fun corruptFileReportsErrorAndStaysUntouched() = runTest {
        val file = File(directory, "mcp.json")
        file.writeText("{not json")
        val store = McpConfigStore(directory)
        val snapshot = store.load()
        assertTrue(snapshot.servers.isEmpty())
        assertTrue(snapshot.errors.isNotEmpty())
        assertEquals("{not json", file.readText())
    }

    @Test
    fun missingFileIsEmpty() = runTest {
        val snapshot = McpConfigStore(directory).load()
        assertTrue(snapshot.entries.isEmpty())
        assertNull(snapshot.errors.firstOrNull())
    }
}
