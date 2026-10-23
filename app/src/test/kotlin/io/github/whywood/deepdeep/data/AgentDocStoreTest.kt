package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentDocStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var cwd: File
    private lateinit var store: AgentDocStore

    @Before
    fun setUp() {
        cwd = temporaryFolder.newFolder()
        store = AgentDocStore()
    }

    private fun overrideFile(): File = File(cwd, "AGENTS.override.md")

    private fun baseFile(): File = File(cwd, "AGENTS.md")

    @Test
    fun readReturnsNullWhenMissing() = runTest {
        assertNull(store.read(cwd.absolutePath))
    }

    @Test
    fun saveCreatesBaseFileWhenNothingExists() = runTest {
        assertTrue(store.save(cwd.absolutePath, "hello"))
        val doc = store.read(cwd.absolutePath)!!
        assertEquals("AGENTS.md", doc.fileName)
        assertFalse(doc.isOverride)
        assertEquals("hello", doc.content)
    }

    @Test
    fun readPrefersOverride() = runTest {
        baseFile().writeText("base")
        overrideFile().writeText("override")
        val doc = store.read(cwd.absolutePath)!!
        assertEquals("AGENTS.override.md", doc.fileName)
        assertTrue(doc.isOverride)
        assertEquals("override", doc.content)
    }

    @Test
    fun readFallsBackToBaseFile() = runTest {
        baseFile().writeText("base")
        val doc = store.read(cwd.absolutePath)!!
        assertEquals("AGENTS.md", doc.fileName)
        assertFalse(doc.isOverride)
        assertEquals("base", doc.content)
    }

    @Test
    fun saveWritesExistingBaseFile() = runTest {
        baseFile().writeText("base")
        assertTrue(store.save(cwd.absolutePath, "changed"))
        assertEquals("changed", baseFile().readText())
        assertFalse(overrideFile().exists())
    }

    @Test
    fun saveWritesExistingOverride() = runTest {
        baseFile().writeText("base")
        overrideFile().writeText("override")
        assertTrue(store.save(cwd.absolutePath, "changed"))
        assertEquals("changed", overrideFile().readText())
        assertEquals("base", baseFile().readText())
    }

    @Test
    fun blankSaveDeletesEffectiveFile() = runTest {
        baseFile().writeText("base")
        overrideFile().writeText("override")
        assertTrue(store.save(cwd.absolutePath, "   "))
        assertFalse(overrideFile().exists())
        assertEquals("base", store.read(cwd.absolutePath)!!.content)
    }

    @Test
    fun oversizedSaveIsRejected() = runTest {
        val oversized = "a".repeat(AGENT_DOC_MAX_BYTES + 1)
        assertFalse(store.save(cwd.absolutePath, oversized))
        assertNull(store.read(cwd.absolutePath))
    }

    @Test
    fun maximumSizeIsAccepted() = runTest {
        val content = "a".repeat(AGENT_DOC_MAX_BYTES)
        assertTrue(store.save(cwd.absolutePath, content))
        assertEquals(AGENT_DOC_MAX_BYTES, store.read(cwd.absolutePath)!!.content.length)
    }

    @Test
    fun deleteRemovesBothFiles() = runTest {
        baseFile().writeText("base")
        overrideFile().writeText("override")
        assertTrue(store.delete(cwd.absolutePath))
        assertFalse(baseFile().exists())
        assertFalse(overrideFile().exists())
        assertNull(store.read(cwd.absolutePath))
    }

    @Test
    fun saveLeavesNoTemporaryFiles() = runTest {
        assertTrue(store.save(cwd.absolutePath, "hello"))
        val names = cwd.listFiles().orEmpty().map { it.name }
        assertEquals(listOf("AGENTS.md"), names)
    }
}
