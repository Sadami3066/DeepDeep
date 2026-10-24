package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WorkshopStoreTest {
    private lateinit var root: File
    private lateinit var store: WorkshopStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-workshop").toFile()
        store = WorkshopStore(File(root, "workshop"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun manifestText(id: String, name: String = "App", type: String = "board"): String =
        "{\"id\":\"$id\",\"name\":\"$name\",\"type\":\"$type\"}"

    private fun appDir(name: String): File = File(store.appsDir, name)

    private fun draftDir(name: String): File = File(store.buildDir, name)

    private suspend fun installFailure(path: String): String = try {
        store.install(path)
        fail("expected WorkshopInstallException")
        ""
    } catch (error: WorkshopInstallException) {
        error.message.orEmpty()
    }

    @Test
    fun createDraftWritesTheFullSkeleton() = runTest {
        val draft = store.createDraft("My Board", WorkshopType.BOARD, description = "Demo board")
        val directory = draft.directory
        assertTrue(directory.isDirectory)
        assertEquals(store.buildDir, directory.parentFile)
        assertEquals("my-board", draft.manifest.id)
        assertEquals("Demo board", draft.manifest.description)
        assertEquals("🗂️", draft.manifest.icon)
        assertTrue(directory.name.startsWith("my-board-"))
        assertEquals(14, directory.name.length)
        assertEquals(draft.manifest, WorkshopManifest.parse(File(directory, "manifest.json").readText()))
        val agents = File(directory, "AGENTS.md").readText()
        assertTrue(agents.contains("window.deepdeep"))
        assertTrue(agents.contains("manifest.json"))
        assertTrue(agents.contains("data.kv"))
        val html = File(directory, "index.html").readText()
        assertTrue(html.contains("window.deepdeep"))
        assertTrue(html.contains("data.get"))
        assertTrue(html.contains("width=device-width"))
        val drafts = store.drafts()
        assertEquals(1, drafts.size)
        assertEquals(directory, drafts.first().directory)
        assertEquals(draft.manifest.id, drafts.first().manifest?.id)
    }

    @Test
    fun starterPagesAreStandalonePerType() = runTest {
        val drafts = listOf(
            store.createDraft("Board Page", WorkshopType.BOARD),
            store.createDraft("Workstation Page", WorkshopType.WORKSTATION),
            store.createDraft("Workflow Page", WorkshopType.WORKFLOW),
        )
        drafts.forEach { draft ->
            val html = File(draft.directory, "index.html").readText()
            assertTrue(html.contains("width=device-width"))
            assertTrue(html.contains("window.deepdeep"))
            assertFalse(html.contains("http://"))
            assertFalse(html.contains("https://"))
            assertTrue(html.startsWith("<!DOCTYPE html>"))
        }
        assertEquals(WorkshopType.BOARD, store.drafts().first { it.manifest?.id == "board-page" }.manifest?.type)
        val workstationHtml = File(drafts[1].directory, "index.html").readText()
        assertTrue(workstationHtml.contains("让 Agent 生成"))
        assertTrue(workstationHtml.contains("bridge.agent.run"))
        val workflowHtml = File(drafts[2].directory, "index.html").readText()
        assertTrue(workflowHtml.contains("onEvent"))
        assertTrue(workflowHtml.contains("bridge.agent.run"))
    }

    @Test
    fun createDraftSlugsAndFallsBack() = runTest {
        assertEquals("todo-list", store.createDraft("Todo List", WorkshopType.BOARD).manifest.id)
        assertEquals("todo-list-2", store.createDraft("Todo List", WorkshopType.BOARD).manifest.id)
        val fallback = store.createDraft("我的 看板！", WorkshopType.BOARD)
        assertTrue(fallback.manifest.id.startsWith("app-"))
        assertTrue(WorkshopManifest.ID_REGEX.matches(fallback.manifest.id))
        val installed = store.createInstalled("Todo List", WorkshopType.WORKSTATION)
        assertEquals("todo-list-3", installed.manifest.id)
        assertNotEquals("todo-list", installed.manifest.id)
    }

    @Test
    fun installMovesDraftIntoApps() = runTest {
        val draft = store.createDraft("Install Me", WorkshopType.BOARD)
        val creation = store.install(draft.directory.absolutePath)
        assertEquals(draft.manifest, creation.manifest)
        assertEquals(File(store.appsDir, draft.manifest.id), creation.directory)
        assertFalse(draft.directory.exists())
        assertTrue(File(creation.directory, "index.html").isFile)
        assertTrue(File(creation.directory, "AGENTS.md").isFile)
        assertTrue(store.userdataDir.isDirectory)
        assertTrue(store.drafts().isEmpty())
        val loaded = store.load(creation.manifest.id)
        assertTrue(loaded is WorkshopEntry.Valid)
        assertEquals(creation.manifest, (loaded as WorkshopEntry.Valid).creation.manifest)
    }

    @Test
    fun installRejectsMissingEntry() = runTest {
        val draft = store.createDraft("No Entry", WorkshopType.BOARD)
        assertTrue(File(draft.directory, "index.html").delete())
        val message = installFailure(draft.directory.absolutePath)
        assertTrue(message.contains("entry"))
    }

    @Test
    fun installRejectsInvalidManifest() = runTest {
        val draft = store.createDraft("Broken Manifest", WorkshopType.BOARD)
        File(draft.directory, "manifest.json").writeText("{\"id\":")
        val message = installFailure(draft.directory.absolutePath)
        assertTrue(message.contains("invalid"))
    }

    @Test
    fun installRejectsTooManyFiles() = runTest {
        val draft = store.createDraft("Too Many", WorkshopType.BOARD)
        repeat(201) { index -> File(draft.directory, "file-$index.txt").writeText("x") }
        val message = installFailure(draft.directory.absolutePath)
        assertTrue(message.contains("200"))
    }

    @Test
    fun installRejectsDuplicateId() = runTest {
        val installed = store.createInstalled("Duplicate", WorkshopType.BOARD)
        val draft = draftDir("${installed.manifest.id}-abcde")
        draft.mkdirs()
        writeText(File(draft, "manifest.json"), manifestText(installed.manifest.id))
        writeText(File(draft, "index.html"), "<html></html>")
        val message = installFailure(draft.absolutePath)
        assertTrue(message.contains("already exists"))
    }

    @Test
    fun installRejectsSymlinks() = runTest {
        val draft = store.createDraft("Symlinked", WorkshopType.BOARD)
        Files.createSymbolicLink(
            File(draft.directory, "link.html").toPath(),
            File(root, "outside.html").toPath(),
        )
        val message = installFailure(draft.directory.absolutePath)
        assertTrue(message.contains("symbolic link"))
    }

    @Test
    fun installRejectsPathsOutsideBuild() = runTest {
        assertTrue(installFailure(File(root, "elsewhere").absolutePath).contains("not inside"))
        assertTrue(installFailure(store.buildDir.absolutePath).contains("not inside"))
        val missing = draftDir("missing-draft")
        val message = installFailure(missing.absolutePath)
        assertTrue(message.contains("does not exist"))
    }

    @Test
    fun scanSeparatesValidAndInvalidApps() = runTest {
        val zeta = store.createInstalled("Zeta", WorkshopType.BOARD)
        val alpha = store.createInstalled("Alpha", WorkshopType.WORKFLOW)
        val broken = appDir("broken").also { it.mkdirs() }
        writeText(File(broken, "manifest.json"), "{not json")
        val mismatch = appDir("mismatch").also { it.mkdirs() }
        writeText(File(mismatch, "manifest.json"), manifestText("other"))
        writeText(File(mismatch, "index.html"), "<html></html>")
        val noEntry = appDir("noentry").also { it.mkdirs() }
        writeText(File(noEntry, "manifest.json"), manifestText("noentry"))
        val legacy = appDir("legacy").also { it.mkdirs() }
        writeText(File(legacy, "index.html"), "<html></html>")
        val hidden = appDir(".hidden").also { it.mkdirs() }
        writeText(File(hidden, "manifest.json"), manifestText("hidden"))
        writeText(File(hidden, "index.html"), "<html></html>")
        appDir("empty").mkdirs()
        zeta.directory.setLastModified(1_000L)
        alpha.directory.setLastModified(2_000L)
        val scan = store.scan()
        assertEquals(listOf("alpha", "zeta"), scan.creations.map { it.manifest.id })
        val reasons = scan.invalid.associate { it.id to it.reason }
        assertEquals(WorkshopInvalidReason.MANIFEST_INVALID, reasons["broken"])
        assertEquals(WorkshopInvalidReason.ID_MISMATCH, reasons["mismatch"])
        assertEquals(WorkshopInvalidReason.ENTRY_MISSING, reasons["noentry"])
        assertEquals(WorkshopInvalidReason.MANIFEST_MISSING, reasons["legacy"])
        assertFalse(reasons.containsKey(".hidden"))
    }

    @Test
    fun loadReportsMissingAndInvalid() = runTest {
        assertTrue(store.load("nothing") is WorkshopEntry.Missing)
        val broken = appDir("broken").also { it.mkdirs() }
        writeText(File(broken, "manifest.json"), "{not json")
        val invalid = store.load("broken")
        assertTrue(invalid is WorkshopEntry.Invalid)
        assertEquals(WorkshopInvalidReason.MANIFEST_INVALID, (invalid as WorkshopEntry.Invalid).reason)
    }

    @Test
    fun draftsIncludeUnparseableManifests() = runTest {
        val first = store.createDraft("Draft One", WorkshopType.BOARD)
        val second = store.createDraft("Draft Two", WorkshopType.WORKSTATION)
        val broken = draftDir("broken-abcde").also { it.mkdirs() }
        writeText(File(broken, "manifest.json"), "{oops")
        val drafts = store.drafts()
        assertEquals(3, drafts.size)
        assertEquals(2, drafts.count { it.manifest != null })
        assertEquals(1, drafts.count { it.manifest == null })
        assertTrue(drafts.any { it.directory == first.directory && it.manifest?.id == "draft-one" })
        assertTrue(drafts.any { it.directory == second.directory && it.manifest?.id == "draft-two" })
        assertTrue(drafts.any { it.directory == broken && it.manifest == null })
    }

    @Test
    fun createInstalledWritesSkeletonInApps() = runTest {
        val creation = store.createInstalled("Quick App", WorkshopType.WORKFLOW)
        assertEquals(store.appsDir, creation.directory.parentFile)
        assertEquals("quick-app", creation.manifest.id)
        assertEquals(WorkshopType.WORKFLOW, creation.manifest.type)
        assertTrue(File(creation.directory, "manifest.json").isFile)
        assertTrue(File(creation.directory, "AGENTS.md").isFile)
        assertTrue(File(creation.directory, "index.html").isFile)
        assertTrue(store.scan().creations.any { it.directory == creation.directory })
    }

    @Test
    fun renameRewritesManifestName() = runTest {
        val creation = store.createInstalled("Old Name", WorkshopType.BOARD)
        val renamed = store.rename(creation.manifest.id, "  New Name  ")
        assertNotNull(renamed)
        assertEquals("New Name", renamed!!.manifest.name)
        assertEquals("New Name", WorkshopManifest.parse(File(creation.directory, "manifest.json").readText()).name)
        assertNull(store.rename("missing-app", "Anything"))
        try {
            store.rename(creation.manifest.id, "a".repeat(41))
            fail("expected WorkshopManifestException")
        } catch (_: WorkshopManifestException) {
        }
    }

    @Test
    fun deleteRemovesAppAndDataFiles() = runTest {
        val creation = store.createInstalled("Delete Me", WorkshopType.BOARD)
        val database = store.dataFile(creation.manifest.id)
        writeText(database, "{}")
        writeText(File(database.path + "-wal"), "")
        writeText(File(database.path + "-shm"), "")
        writeText(File(database.path + "-journal"), "")
        assertTrue(store.delete(creation.manifest.id))
        assertFalse(creation.directory.exists())
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
        assertFalse(File(database.path + "-journal").exists())
        assertFalse(store.delete(creation.manifest.id))
        assertFalse(store.delete(".."))
        assertFalse(store.delete("apps"))
    }

    @Test
    fun appAndDataPathsAreInsideWorkshopRoot() {
        assertEquals(File(store.appsDir, "demo"), store.appDirectory("demo"))
        assertEquals(File(store.userdataDir, "demo.db"), store.dataFile("demo"))
    }
}
