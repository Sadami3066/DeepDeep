package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.FileMutationQueue
import dev.pi.coding.tools.defaultCodingTools
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspacePathsTest {
    private lateinit var root: File
    private lateinit var workspace: File
    private lateinit var cwd: Path

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-workspace-paths").toFile()
        workspace = File(root, "workspace").apply { mkdirs() }
        cwd = workspace.absolutePath.toPath()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun mapsWorkspaceRootAndPrefixToCwd() {
        assertEquals(cwd.toString(), mapWorkspacePath(cwd, "/workspace"))
        assertEquals(cwd.toString(), mapWorkspacePath(cwd, "/workspace/"))
        assertEquals(
            cwd.resolve("tool-test/a.txt").toString(),
            mapWorkspacePath(cwd, "/workspace/tool-test/a.txt"),
        )
    }

    @Test
    fun keepsRelativeAndHostAbsolutePaths() {
        assertEquals("tool-test/a.txt", mapWorkspacePath(cwd, "tool-test/a.txt"))
        assertEquals("~/notes/a.txt", mapWorkspacePath(cwd, "~/notes/a.txt"))
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/home/a.txt",
            mapWorkspacePath(cwd, "/data/user/0/io.github.whywood.deepdeep/files/home/a.txt"),
        )
    }

    @Test
    fun keepsLookalikeBroadPrefixes() {
        assertEquals("/workspace2/a.txt", mapWorkspacePath(cwd, "/workspace2/a.txt"))
        assertEquals("workspace/a.txt", mapWorkspacePath(cwd, "workspace/a.txt"))
    }

    @Test
    fun wrapperKeepsToolIdentity() {
        defaultTools().filter { it.name in FILE_TOOLS }.forEach { tool ->
            val wrapped = tool.withWorkspacePaths(cwd)
            assertEquals(tool.name, wrapped.name)
            assertEquals(tool.label, wrapped.label)
            assertEquals(tool.parameters, wrapped.parameters)
            assertTrue(wrapped.description.contains("/workspace/"))
            assertNull(wrapped.promptSnippet)
            assertNull(wrapped.promptGuidelines)
        }
    }

    @Test
    fun readWriteEditAcceptWorkspacePrefix() = runBlocking {
        assertTrue(
            runTool(
                "write",
                buildJsonObject {
                    put("path", "/workspace/sub/a.txt")
                    put("content", "alpha")
                },
            ).contains("Successfully wrote"),
        )
        assertEquals("alpha", runTool("read", buildJsonObject { put("path", "/workspace/sub/a.txt") }).trim())

        runTool(
            "edit",
            buildJsonObject {
                put("path", "/workspace/sub/a.txt")
                put("edits", buildJsonArray {
                    add(buildJsonObject {
                        put("oldText", "alpha")
                        put("newText", "beta")
                    })
                })
            },
        )
        assertEquals("beta", runTool("read", buildJsonObject { put("path", "sub/a.txt") }).trim())
    }

    @Test
    fun lsGrepFindAcceptWorkspacePrefix() = runBlocking {
        File(workspace, "sub").mkdirs()
        File(workspace, "sub/a.txt").writeText("needle\n")

        assertTrue(runTool("ls", buildJsonObject { put("path", "/workspace/sub") }).contains("a.txt"))
        assertTrue(
            runTool(
                "grep",
                buildJsonObject {
                    put("pattern", "needle")
                    put("path", "/workspace")
                },
            ).contains("sub/a.txt"),
        )
        assertTrue(
            runTool(
                "find",
                buildJsonObject {
                    put("pattern", "*.txt")
                    put("path", "/workspace")
                },
            ).contains("sub/a.txt"),
        )
    }

    private fun defaultTools(): List<CodingTool> =
        defaultCodingTools(cwd, FileSystem.SYSTEM, FileMutationQueue(), shellExecutor = null)

    private suspend fun runTool(name: String, args: JsonObject): String {
        val tool = defaultTools().first { it.name == name }.withWorkspacePaths(cwd)
        val prepared = tool.prepareArguments?.invoke(args) ?: args
        val result: AgentToolResult = tool.execute("call", prepared, null, null)
        return result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
    }

    private companion object {
        val FILE_TOOLS = setOf("read", "write", "edit", "ls", "grep", "find")
    }
}
