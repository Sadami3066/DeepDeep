package io.github.whywood.deepdeep.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScopedFileSystemTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsAndWritesInsideRoot() {
        val root = temp.newFolder("workspace")
        val fs = scoped(root)
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

        assertTrue(scoped(root).metadataOrNull(root.absolutePath.toPath()) != null)
    }

    @Test
    fun parentTraversalIsRejected() {
        val root = temp.newFolder("workspace")
        temp.newFile("outside.txt")

        assertOutside { scoped(root).metadataOrNull((root.absolutePath + "/../outside.txt").toPath()) }
        assertOutside { scoped(root).metadataOrNull((root.absolutePath + "/sub/../../outside.txt").toPath()) }
    }

    @Test
    fun absolutePathOutsideRootIsRejected() {
        val root = temp.newFolder("workspace")

        assertOutside { scoped(root).metadataOrNull("/etc/passwd".toPath()) }
        assertOutside { scoped(root).read("/etc/passwd".toPath()) { readUtf8() } }
    }

    @Test
    fun symlinkPointingOutsideIsRejected() {
        val root = temp.newFolder("workspace")
        val outside = temp.newFolder("outside")
        File(outside, "secret.txt").writeText("secret")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        assertOutside { scoped(root).read((root.absolutePath + "/escape/secret.txt").toPath()) { readUtf8() } }
        assertOutside { scoped(root).metadataOrNull((root.absolutePath + "/escape").toPath()) }
    }

    @Test
    fun siblingDirectoryWithSharedPrefixIsRejected() {
        val root = temp.newFolder("root")
        val sibling = temp.newFolder("root2")
        val siblingFile = File(sibling, "data.txt").apply { writeText("data") }
        val fs = scoped(root)

        assertOutside { fs.read(siblingFile.absolutePath.toPath()) { readUtf8() } }
        assertOutside { fs.metadataOrNull(sibling.absolutePath.toPath()) }
        fs.metadata(File(root, "inner.txt").apply { writeText("inner") }.absolutePath.toPath())
    }

    @Test
    fun everyAllowedRootIsUsable() {
        val workspace = temp.newFolder("workspace")
        val agentDir = temp.newFolder("agent")
        val outside = temp.newFolder("outside")
        val fs = ScopedFileSystem(
            FileSystem.SYSTEM,
            listOf(workspace.absolutePath.toPath(), agentDir.absolutePath.toPath()),
        )

        val workspaceFile = File(workspace, "page.html").apply { writeText("page") }
        val agentFile = File(agentDir, "settings.json").apply { writeText("{}") }

        assertEquals("page", fs.read(workspaceFile.absolutePath.toPath()) { readUtf8() })
        assertEquals("{}", fs.read(agentFile.absolutePath.toPath()) { readUtf8() })
        assertOutside { fs.metadataOrNull(outside.absolutePath.toPath()) }
    }

    @Test
    fun directoryOutsideIsNotWritable() {
        val root = temp.newFolder("workspace")
        val outside = temp.newFolder("outside")

        assertOutside { scoped(root).sink(File(outside, "new.txt").absolutePath.toPath()).close() }
        assertTrue(File(outside, "new.txt").let { !it.exists() })
    }

    private fun scoped(root: File): ScopedFileSystem =
        ScopedFileSystem(FileSystem.SYSTEM, listOf(root.absolutePath.toPath()))

    private fun assertOutside(block: () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected IOException but got $error", error is IOException)
        assertTrue(
            "expected workshop scope message but got ${error?.message}",
            error?.message.orEmpty().contains("path outside workshop workspace"),
        )
    }
}
