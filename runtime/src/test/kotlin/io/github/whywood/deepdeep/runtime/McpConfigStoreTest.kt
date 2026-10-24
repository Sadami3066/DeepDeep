package io.github.whywood.deepdeep.runtime

import dev.pi.mcp.config.McpServerConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun importsUserSample() = runTest {
        val store = McpConfigStore(directory)
        val names = store.importServers(
            """
            {"mcpServers":{"mcd-mcp":{"type":"streamablehttp","url":"https://mcp.mcd.cn","headers":{"Authorization":"Bearer test-token"}}}}
            """.trimIndent(),
        )
        assertEquals(listOf("mcd-mcp"), names)
        val snapshot = store.load()
        assertTrue(snapshot.errors.isEmpty())
        val imported = snapshot.servers.getValue("mcd-mcp") as McpServerConfig.Http
        assertEquals("https://mcp.mcd.cn", imported.url)
        assertEquals("Bearer test-token", imported.headers["Authorization"])
        assertTrue(imported.enabled)
    }

    @Test
    fun importsMultipleServers() = runTest {
        val store = McpConfigStore(directory)
        val names = store.importServers(
            """
            {"mcpServers":{
              "docs":{"type":"streamableHttp","url":"https://docs.example.com/mcp"},
              "local":{"type":"stdio","command":"npx","args":["-y","server"]}
            }}
            """.trimIndent(),
        )
        assertEquals(listOf("docs", "local"), names)
        val snapshot = store.load()
        assertTrue(snapshot.errors.isEmpty())
        assertTrue(snapshot.servers.getValue("docs") is McpServerConfig.Http)
        assertTrue(snapshot.servers.getValue("local") is McpServerConfig.Stdio)
    }

    @Test
    fun normalizesStreamableHttpTypes() = runTest {
        val store = McpConfigStore(directory)
        val names = store.importServers(
            """
            {"mcpServers":{
              "a":{"type":"streamablehttp","url":"https://a.example.com/mcp"},
              "b":{"type":"streamableHttp","url":"https://b.example.com/mcp"},
              "c":{"type":"streamable_http","url":"https://c.example.com/mcp"},
              "d":{"type":"streamable-http","url":"https://d.example.com/mcp"},
              "e":{"type":"http","url":"https://e.example.com/mcp"}
            }}
            """.trimIndent(),
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), names)
        val snapshot = store.load()
        assertTrue(snapshot.errors.isEmpty())
        assertTrue(snapshot.servers.values.all { it is McpServerConfig.Http })
    }

    @Test
    fun invalidImportLeavesFileUntouched() = runTest {
        val store = McpConfigStore(directory)
        store.importServers(
            """{"mcpServers":{"docs":{"type":"http","url":"https://docs.example.com/mcp"}}}""",
        )
        val file = File(directory, "mcp.json")
        val before = file.readText()
        val failure = runCatching {
            store.importServers(
                """
                {"mcpServers":{
                  "bad":{"type":"streamablehttp","url":"not-a-url"},
                  "good":{"type":"http","url":"https://good.example.com/mcp"}
                }}
                """.trimIndent(),
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, file.readText())
        assertEquals(setOf("docs"), store.load().servers.keys)
        assertFalse(File(directory, "mcp.json.tmp").exists())
    }

    @Test
    fun importRejectsExistingAndKeepsOthers() = runTest {
        val store = McpConfigStore(directory)
        store.upsert("docs", http(url = "https://old.example.com/mcp"))
        store.upsert("keep", http(url = "https://keep.example.com/mcp"))
        val failure = runCatching {
            store.importServers(
                """{"mcpServers":{"docs":{"type":"http","url":"https://new.example.com/mcp"}}}""",
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("已存在") == true)
        val snapshot = store.load()
        assertEquals(setOf("docs", "keep"), snapshot.servers.keys)
        assertEquals(
            "https://old.example.com/mcp",
            (snapshot.servers.getValue("docs") as McpServerConfig.Http).url,
        )
        assertEquals(
            "https://keep.example.com/mcp",
            (snapshot.servers.getValue("keep") as McpServerConfig.Http).url,
        )
    }

    @Test
    fun importsNamedFragment() = runTest {
        val store = McpConfigStore(directory)
        val names = store.importServers(
            """
            "mcd-mcp": {
              "type": "streamablehttp",
              "url": "https://mcp.mcd.cn",
              "headers": {"Authorization": "Bearer token"}
            }
            }
            """.trimIndent(),
        )
        assertEquals(listOf("mcd-mcp"), names)
        val config = store.load().servers.getValue("mcd-mcp") as McpServerConfig.Http
        assertEquals("https://mcp.mcd.cn", config.url)
    }

    @Test
    fun editsEntryFromJsonAndCanRename() = runTest {
        val store = McpConfigStore(directory)
        store.upsert("old", http(url = "https://old.example.com/mcp"))
        val before = store.entryJson("old")
        assertNotNull(before)
        assertTrue(before!!.startsWith("\"old\": {"))
        val newName = store.updateFromJson(
            "old",
            """
            "new": {
              "type": "streamablehttp",
              "url": "https://new.example.com/mcp",
              "enabled": false
            }
            """.trimIndent(),
        )
        assertEquals("new", newName)
        val snapshot = store.load()
        assertFalse("old" in snapshot.servers)
        val updated = snapshot.servers.getValue("new") as McpServerConfig.Http
        assertEquals("https://new.example.com/mcp", updated.url)
        assertFalse(updated.enabled)
    }

    @Test
    fun importRequiresMcpServersObject() = runTest {
        val store = McpConfigStore(directory)
        assertTrue(runCatching { store.importServers("""{"servers":{}}""") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.importServers("""{"mcpServers":{}}""") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.importServers("{not json") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.importServers("  ") }.exceptionOrNull() is IllegalArgumentException)
        assertFalse(File(directory, "mcp.json").exists())
    }

    @Test
    fun missingFileIsEmpty() = runTest {
        val snapshot = McpConfigStore(directory).load()
        assertTrue(snapshot.entries.isEmpty())
        assertNull(snapshot.errors.firstOrNull())
    }
}
