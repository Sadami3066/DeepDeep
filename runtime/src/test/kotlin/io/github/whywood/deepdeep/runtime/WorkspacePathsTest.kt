package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.FileMutationQueue
import io.github.whywood.deepdeep.sdk.coding.tools.defaultCodingTools
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    fun normalizesAbsolutePathsWithoutAliases() {
        assertEquals("/workspace", requireAbsolutePath("/workspace"))
        assertEquals("/workspace/a.txt", requireAbsolutePath("/workspace/sub/../a.txt"))
    }

    @Test
    fun rejectsRelativeAndHomePaths() {
        listOf("tool-test/a.txt", "~/notes/a.txt", "").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { requireAbsolutePath(path) }
        }
    }

    @Test
    fun keepsAbsolutePathsOutsideWorkspace() {
        assertEquals("/another/a.txt", requireAbsolutePath("/another/a.txt"))
        assertThrows(IllegalArgumentException::class.java) { requireAbsolutePath("workspace/a.txt") }
    }

    @Test
    fun wrapperKeepsToolIdentity() {
        defaultTools().filter { it.name in FILE_TOOLS }.forEach { tool ->
            val wrapped = tool.withAbsolutePaths()
            assertEquals(tool.name, wrapped.name)
            assertEquals(tool.label, wrapped.label)
            assertTrue(wrapped.parameters.toString().contains("Absolute path"))
            assertFalse(wrapped.description.contains("An explicit absolute path"))
            assertEquals(tool.promptSnippet, wrapped.promptSnippet)
            assertTrue(wrapped.promptGuidelines.orEmpty().contains(ABSOLUTE_PATH_GUIDELINE))
        }
    }

    @Test
    fun readWriteEditAcceptWorkspacePrefix() = runBlocking {
        assertTrue(
            runTool(
                "write",
                buildJsonObject {
                    put("path", File(workspace, "sub/a.txt").absolutePath)
                    put("content", "alpha")
                },
            ).contains("Successfully wrote"),
        )
        assertEquals("alpha", runTool("read", buildJsonObject { put("path", File(workspace, "sub/a.txt").absolutePath) }).trim())

        runTool(
            "edit",
            buildJsonObject {
                put("path", File(workspace, "sub/a.txt").absolutePath)
                put("edits", buildJsonArray {
                    add(buildJsonObject {
                        put("oldText", "alpha")
                        put("newText", "beta")
                    })
                })
            },
        )
        assertEquals("beta", runTool("read", buildJsonObject { put("path", File(workspace, "sub/a.txt").absolutePath) }).trim())
    }

    @Test
    fun lsGrepFindAcceptWorkspacePrefix() = runBlocking {
        File(workspace, "sub").mkdirs()
        File(workspace, "sub/a.txt").writeText("needle\n")

        assertTrue(runTool("ls", buildJsonObject { put("path", File(workspace, "sub").absolutePath) }).contains("a.txt"))
        assertTrue(
            runTool(
                "grep",
                buildJsonObject {
                    put("pattern", "needle")
                    put("path", workspace.absolutePath)
                },
            ).contains("sub/a.txt"),
        )
        assertTrue(
            runTool(
                "find",
                buildJsonObject {
                    put("pattern", "*.txt")
                    put("path", workspace.absolutePath)
                },
            ).contains("sub/a.txt"),
        )
    }

    @Test
    fun allFileToolsRequireExplicitAbsolutePath() = runBlocking {
        defaultTools().filter { it.name in FILE_TOOLS }.forEach { raw ->
            val tool = raw.withAbsolutePaths()
            listOf(buildJsonObject {}, buildJsonObject { put("path", "a.txt") }).forEach { args ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { tool.execute("call", args, null, null) }
                }
            }
        }
    }

    private fun defaultTools(): List<CodingTool> =
        defaultCodingTools(cwd, FileSystem.SYSTEM, FileMutationQueue(), shellExecutor = null)

    private suspend fun runTool(name: String, args: JsonObject): String {
        val tool = defaultTools().first { it.name == name }.withAbsolutePaths()
        val prepared = tool.prepareArguments?.invoke(args) ?: args
        val result: AgentToolResult = tool.execute("call", prepared, null, null)
        return result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
    }

    private companion object {
        val FILE_TOOLS = setOf("read", "write", "edit", "ls", "grep", "find")
    }
}
