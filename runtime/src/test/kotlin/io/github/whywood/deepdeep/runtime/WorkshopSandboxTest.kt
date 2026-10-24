package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopSandboxTest {
    private val fs = FileSystem.SYSTEM

    @Test
    fun resolveInsideAcceptsRelativePath() {
        val root = tempRoot()
        assertEquals(root / "index.html", resolveInside(root, "index.html"))
    }

    @Test
    fun resolveInsideAcceptsNestedRelativePath() {
        val root = tempRoot()
        assertEquals(root / "assets" / "app.css", resolveInside(root, "assets/app.css"))
    }

    @Test
    fun resolveInsideAcceptsAbsolutePathInsideRoot() {
        val root = tempRoot()
        assertEquals(root / "index.html", resolveInside(root, (root / "index.html").toString()))
    }

    @Test
    fun resolveInsideAcceptsRootItself() {
        val root = tempRoot()
        assertEquals(root, resolveInside(root, "."))
    }

    @Test
    fun resolveInsideRejectsParentTraversal() {
        val root = tempRoot()
        assertNull(resolveInside(root, "../escape.txt"))
        assertNull(resolveInside(root, "assets/../../escape.txt"))
    }

    @Test
    fun resolveInsideRejectsOutsideAbsolutePath() {
        val root = tempRoot()
        assertNull(resolveInside(root, (root.parent!! / "outside.txt").toString()))
    }

    @Test
    fun resolveInsideRejectsSiblingWithSharedPrefix() {
        val root = tempRoot()
        assertNull(resolveInside(root, (root.parent!! / (root.name + "2") / "index.html").toString()))
    }

    @Test
    fun sandboxToolsHaveNoBash() {
        val names = sandboxedFileTools(tempRoot(), fs).map { it.name }

        assertEquals(listOf("read", "edit", "write", "grep", "find", "ls"), names)
    }

    @Test
    fun guardHelperAllowsMissingAndInsidePaths() {
        val root = tempRoot()

        assertNull(workshopPathGuard(root, buildJsonObject {}))
        assertNull(workshopPathGuard(root, buildJsonObject { put("path", "index.html") }))
        assertEquals(true, workshopPathGuard(root, buildJsonObject { put("path", "../escape.txt") })?.isError)
    }

    @Test
    fun writingOutsideTheAppIsBlocked() = runTest {
        val root = tempRoot()
        val target = root.parent!! / ("workshop-outside-" + System.nanoTime() + ".txt")
        val write = sandboxedFileTools(root, fs).first { it.name == "write" }

        val result = write.execute(
            "call-outside",
            buildJsonObject {
                put("path", target.name.let { "../$it" })
                put("content", "should not be written")
            },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertTrue(resultText(result).contains("outside this workshop app"))
        assertFalse(fs.exists(target))

        val absoluteResult = write.execute(
            "call-outside-absolute",
            buildJsonObject {
                put("path", target.toString())
                put("content", "should not be written")
            },
            null,
            null,
        )

        assertEquals(true, absoluteResult.isError)
        assertFalse(fs.exists(target))
    }

    @Test
    fun writingInsideTheAppWorks() = runTest {
        val root = tempRoot()
        val write = sandboxedFileTools(root, fs).first { it.name == "write" }

        val result = write.execute(
            "call-inside",
            buildJsonObject {
                put("path", "notes.txt")
                put("content", "hello workshop")
            },
            null,
            null,
        )

        assertNull(result.isError)
        assertEquals("hello workshop", fs.read(root / "notes.txt") { readUtf8() })
    }

    @Test
    fun readingOutsideTheAppIsBlocked() = runTest {
        val root = tempRoot()
        val read = sandboxedFileTools(root, fs).first { it.name == "read" }

        val result = read.execute(
            "call-read",
            buildJsonObject { put("path", "../outside.txt") },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertEquals(true, resultText(result).contains("outside this workshop app"))
    }

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("workshop-sandbox-test").toFile()
        directory.deleteOnExit()
        return directory.absolutePath.toPath()
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
}
