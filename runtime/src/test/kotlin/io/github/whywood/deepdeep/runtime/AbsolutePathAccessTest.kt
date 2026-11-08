package io.github.whywood.deepdeep.runtime

import java.io.File
import io.github.whywood.deepdeep.sdk.coding.tools.readTool
import io.github.whywood.deepdeep.sdk.coding.tools.writeTool
import io.github.whywood.deepdeep.sdk.ai.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AbsolutePathAccessTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsAndWritesInsideRoot() {
        val root = temp.newFolder("workspace")
        val fs = FileSystem.SYSTEM
        val directory = (root.absolutePath + "/draft").toPath()
        val file = (root.absolutePath + "/draft/index.html").toPath()

        fs.createDirectories(directory)
        fs.write(file) { writeUtf8("<html></html>") }

        assertTrue(fs.exists(file))
        assertEquals("<html></html>", fs.read(file) { readUtf8() })
        assertEquals(listOf(file), fs.list(directory))
        assertEquals(root.canonicalPath.toPath(), fs.canonicalize(root.absolutePath.toPath()))
    }

    @Test
    fun rootItselfIsAllowed() {
        val root = temp.newFolder("workspace")

        assertTrue(FileSystem.SYSTEM.metadataOrNull(root.absolutePath.toPath()) != null)
    }

    @Test
    fun absoluteTraversalUsesExplicitPath() {
        val root = temp.newFolder("workspace")
        temp.newFile("outside.txt")

        allowAbsolute { FileSystem.SYSTEM.metadataOrNull((root.absolutePath + "/../outside.txt").toPath()) }
        allowAbsolute { FileSystem.SYSTEM.metadataOrNull((root.absolutePath + "/sub/../../outside.txt").toPath()) }
    }

    @Test
    fun absolutePathOutsideWorkspaceIsReadable() {
        val root = temp.newFolder("workspace")

        allowAbsolute { FileSystem.SYSTEM.metadataOrNull("/etc/passwd".toPath()) }
        allowAbsolute { FileSystem.SYSTEM.read("/etc/passwd".toPath()) { readUtf8() } }
    }

    @Test
    fun absoluteSymlinkCanPointOutsideWorkspace() {
        val root = temp.newFolder("workspace")
        val outside = temp.newFolder("outside")
        File(outside, "secret.txt").writeText("secret")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        allowAbsolute { FileSystem.SYSTEM.read((root.absolutePath + "/escape/secret.txt").toPath()) { readUtf8() } }
        allowAbsolute { FileSystem.SYSTEM.metadataOrNull((root.absolutePath + "/escape").toPath()) }
    }

    @Test
    fun siblingDirectoryIsReadable() {
        val root = temp.newFolder("root")
        val sibling = temp.newFolder("root2")
        val siblingFile = File(sibling, "data.txt").apply { writeText("data") }
        val fs = FileSystem.SYSTEM

        allowAbsolute { fs.read(siblingFile.absolutePath.toPath()) { readUtf8() } }
        allowAbsolute { fs.metadataOrNull(sibling.absolutePath.toPath()) }
        fs.metadata(File(root, "inner.txt").apply { writeText("inner") }.absolutePath.toPath())
    }

    @Test
    fun everyAllowedRootIsUsable() {
        val workspace = temp.newFolder("workspace")
        val agentDir = temp.newFolder("agent")
        val outside = temp.newFolder("outside")
        val fs = FileSystem.SYSTEM

        val workspaceFile = File(workspace, "page.html").apply { writeText("page") }
        val agentFile = File(agentDir, "settings.json").apply { writeText("{}") }

        assertEquals("page", fs.read(workspaceFile.absolutePath.toPath()) { readUtf8() })
        assertEquals("{}", fs.read(agentFile.absolutePath.toPath()) { readUtf8() })
        allowAbsolute { fs.metadataOrNull(outside.absolutePath.toPath()) }
    }

    @Test
    fun absoluteDirectoryOutsideWorkspaceIsWritable() {
        val root = temp.newFolder("workspace")
        val outside = temp.newFolder("outside")

        allowAbsolute { FileSystem.SYSTEM.sink(File(outside, "new.txt").absolutePath.toPath()).close() }
        assertTrue(File(outside, "new.txt").isFile)
    }

    private fun allowAbsolute(block: () -> Unit) { block() }
}
