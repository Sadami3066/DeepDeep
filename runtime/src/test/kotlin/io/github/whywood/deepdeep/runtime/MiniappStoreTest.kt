package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MiniappStoreTest {
    private lateinit var root: File
    private lateinit var store: MiniappStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-miniapp").toFile()
        store = MiniappStore(File(root, "miniapp"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun manifestText(
        id: String,
        name: String = "App",
        icon: String = "",
        version: String = "1.0.0",
        entry: String = "index.html",
        description: String = "",
    ): String = buildJsonObject {
        put("id", id)
        put("name", name)
        put("icon", icon)
        put("version", version)
        put("entry", entry)
        put("description", description)
    }.toString()

    private fun appDir(id: String): File = File(store.appsDir, id)

    private fun installApp(
        id: String,
        name: String = "App",
        icon: String = "",
        description: String = "",
    ): File {
        val directory = appDir(id)
        directory.mkdirs()
        writeText(File(directory, "manifest.json"), manifestText(id, name, icon, description = description))
        writeText(File(directory, "index.html"), "<html></html>")
        return directory
    }

    private fun draftDir(name: String): File = File(store.buildDir, name)

    private suspend fun loadFailure(path: String): String = try {
        store.loadDraft(path)
        fail("expected MiniappLoadException")
        ""
    } catch (error: MiniappLoadException) {
        error.message.orEmpty()
    }

    private suspend fun manifestFailure(block: suspend () -> Unit): String = try {
        block()
        fail("expected MiniappManifestException")
        ""
    } catch (error: MiniappManifestException) {
        error.message.orEmpty()
    }

    @Test
    fun createDraftWritesOnlyManifest() = runTest {
        val draft = store.createDraft("My Board")
        val directory = draft.directory
        assertTrue(directory.isDirectory)
        assertEquals(store.buildDir, directory.parentFile)
        assertEquals("my-board", draft.manifest.id)
        assertEquals("My Board", draft.manifest.name)
        assertEquals("", draft.manifest.icon)
        assertEquals("1.0.0", draft.manifest.version)
        assertEquals("index.html", draft.manifest.entry)
        assertEquals("", draft.manifest.description)
        assertEquals(listOf("manifest.json"), directory.listFiles()!!.map { it.name })
        assertEquals(draft.manifest, MiniappManifest.parse(File(directory, "manifest.json").readText()))
        val drafts = store.drafts()
        assertEquals(1, drafts.size)
        assertEquals(directory, drafts.first().directory)
        assertEquals(draft.manifest.id, drafts.first().manifest?.id)
        assertEquals(0, store.scan().creations.size)
    }

    @Test
    fun createDraftAppliesIconAndDescription() = runTest {
        val draft = store.createDraft("Styled", description = "  Demo app  ", icon = " 🧰 ")
        assertEquals("🧰", draft.manifest.icon)
        assertEquals("Demo app", draft.manifest.description)
        assertEquals("", store.createDraft("Plain", icon = "").manifest.icon)
        assertEquals("", store.createDraft("No Icon").manifest.icon)
        assertTrue(manifestFailure { store.createDraft("Icon", icon = "123456789") }.contains("icon"))
        assertTrue(manifestFailure { store.createDraft("Desc", description = "d".repeat(301)) }.contains("description"))
        assertTrue(manifestFailure { store.createDraft("a".repeat(41)) }.contains("name"))
    }

    @Test
    fun createDraftSlugsAndFallsBack() = runTest {
        assertEquals("todo-list", store.createDraft("Todo List").manifest.id)
        assertEquals("todo-list-2", store.createDraft("Todo List").manifest.id)
        val fallback = store.createDraft("我的 看板！")
        assertTrue(fallback.manifest.id.startsWith("app-"))
        assertTrue(MiniappManifest.ID_REGEX.matches(fallback.manifest.id))
        installApp("listed-app")
        assertEquals("listed-app-2", store.createDraft("Listed App").manifest.id)
    }

    @Test
    fun loadDraftMovesDraftIntoApps() = runTest {
        val draft = store.createDraft("Load Me")
        writeText(File(draft.directory, "index.html"), "<html><body>hi</body></html>")
        writeText(File(draft.directory, "style.css"), "body{}")
        val creation = store.loadDraft(draft.directory.absolutePath)
        assertEquals(draft.manifest, creation.manifest)
        assertEquals(File(store.appsDir, "load-me"), creation.directory)
        assertFalse(draft.directory.exists())
        assertEquals(
            setOf("manifest.json", "index.html", "style.css"),
            creation.directory.listFiles()!!.map { it.name }.toSet(),
        )
        assertTrue(store.userdataDir.isDirectory)
        assertTrue(store.drafts().isEmpty())
        val loaded = store.load("load-me")
        assertTrue(loaded is MiniappEntry.Valid)
        assertEquals(creation.manifest, (loaded as MiniappEntry.Valid).creation.manifest)
    }

    @Test
    fun loadDraftRejectsMissingEntry() = runTest {
        val draft = store.createDraft("No Entry")
        assertTrue(loadFailure(draft.directory.absolutePath).contains("entry"))
    }

    @Test
    fun loadDraftReportsCallerPathForMissingEntry() = runTest {
        val realRoot = File(root, "real").apply { mkdirs() }
        val linkRoot = File(root, "link")
        Files.createSymbolicLink(linkRoot.toPath(), realRoot.toPath())
        val linkedStore = MiniappStore(File(linkRoot, "miniapp"))
        val draft = linkedStore.createDraft("Linked Path")
        val draftPath = draft.directory.absolutePath

        val message = try {
            linkedStore.loadDraft(draftPath)
            fail("expected MiniappLoadException")
            ""
        } catch (error: MiniappLoadException) {
            error.message.orEmpty()
        }

        assertTrue(message.contains(draftPath))
        assertFalse(message.contains(realRoot.absolutePath))
    }

    @Test
    fun loadDraftRejectsInvalidManifest() = runTest {
        val draft = store.createDraft("Broken")
        File(draft.directory, "manifest.json").writeText("{\"id\":")
        assertTrue(loadFailure(draft.directory.absolutePath).contains("invalid"))
        val noManifest = draftDir("legacy-abcde").also { it.mkdirs() }
        writeText(File(noManifest, "index.html"), "<html></html>")
        assertTrue(loadFailure(noManifest.absolutePath).contains("manifest.json"))
    }

    @Test
    fun loadDraftRejectsTooManyFiles() = runTest {
        val draft = store.createDraft("Too Many")
        writeText(File(draft.directory, "index.html"), "<html></html>")
        repeat(201) { index -> File(draft.directory, "file-$index.txt").writeText("x") }
        assertTrue(loadFailure(draft.directory.absolutePath).contains("200"))
    }

    @Test
    fun loadDraftRejectsDuplicateId() = runTest {
        installApp("duplicate")
        val draft = draftDir("duplicate-abcde")
        draft.mkdirs()
        writeText(File(draft, "manifest.json"), manifestText("duplicate"))
        writeText(File(draft, "index.html"), "<html></html>")
        assertTrue(loadFailure(draft.absolutePath).contains("already exists"))
    }

    @Test
    fun loadDraftRejectsSymlinks() = runTest {
        val draft = store.createDraft("Symlinked")
        writeText(File(draft.directory, "index.html"), "<html></html>")
        Files.createSymbolicLink(
            File(draft.directory, "link.html").toPath(),
            File(root, "outside.html").toPath(),
        )
        assertTrue(loadFailure(draft.directory.absolutePath).contains("symbolic link"))
    }

    @Test
    fun loadDraftRejectsPathsOutsideBuild() = runTest {
        assertTrue(loadFailure(File(root, "elsewhere").absolutePath).contains("not inside"))
        assertTrue(loadFailure(store.buildDir.absolutePath).contains("not inside"))
        assertTrue(loadFailure(draftDir("missing-draft").absolutePath).contains("does not exist"))
    }

    @Test
    fun scanSeparatesValidAndInvalidApps() = runTest {
        val beta = installApp("beta", name = "Beta")
        val alpha = installApp("alpha", name = "Alpha", icon = "✅", description = "A")
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
        beta.setLastModified(1_000L)
        alpha.setLastModified(2_000L)
        val scan = store.scan()
        assertEquals(listOf("alpha", "beta"), scan.creations.map { it.manifest.id })
        assertEquals("✅", scan.creations[0].manifest.icon)
        assertEquals("A", scan.creations[0].manifest.description)
        val reasons = scan.invalid.associate { it.id to it.reason }
        assertEquals(MiniappInvalidReason.MANIFEST_INVALID, reasons["broken"])
        assertEquals(MiniappInvalidReason.ID_MISMATCH, reasons["mismatch"])
        assertEquals(MiniappInvalidReason.ENTRY_MISSING, reasons["noentry"])
        assertEquals(MiniappInvalidReason.MANIFEST_MISSING, reasons["legacy"])
        assertFalse(reasons.containsKey(".hidden"))
    }

    @Test
    fun loadReportsMissingAndInvalid() = runTest {
        assertTrue(store.load("nothing") is MiniappEntry.Missing)
        val broken = appDir("broken").also { it.mkdirs() }
        writeText(File(broken, "manifest.json"), "{not json")
        val invalid = store.load("broken")
        assertTrue(invalid is MiniappEntry.Invalid)
        assertEquals(MiniappInvalidReason.MANIFEST_INVALID, (invalid as MiniappEntry.Invalid).reason)
    }

    @Test
    fun draftsIncludeUnparseableManifests() = runTest {
        val first = store.createDraft("Draft One")
        val second = store.createDraft("Draft Two")
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
    fun renameRewritesManifestName() = runTest {
        installApp("old-name", name = "Old Name")
        val renamed = store.rename("old-name", "  New Name  ")
        assertNotNull(renamed)
        assertEquals("New Name", renamed!!.manifest.name)
        assertEquals("New Name", MiniappManifest.parse(File(appDir("old-name"), "manifest.json").readText()).name)
        assertNull(store.rename("missing-app", "Anything"))
        assertTrue(manifestFailure { store.rename("old-name", "a".repeat(41)) }.contains("name"))
    }

    @Test
    fun deleteRemovesAppAndDataFiles() = runTest {
        val directory = installApp("delete-me")
        val database = store.dataFile("delete-me")
        listOf(database, File(database.path + "-wal"), File(database.path + "-shm"), File(database.path + "-journal"))
            .forEach { writeText(it, "{}") }
        assertTrue(store.delete("delete-me"))
        assertFalse(directory.exists())
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
        assertFalse(File(database.path + "-journal").exists())
        assertFalse(store.delete("delete-me"))
        assertFalse(store.delete(".."))
        assertFalse(store.delete("apps"))
    }

    @Test
    fun appAndDataPathsAreInsideMiniappRoot() {
        assertEquals(File(store.appsDir, "demo"), store.appDirectory("demo"))
        assertEquals(File(store.userdataDir, "demo.db"), store.dataFile("demo"))
    }
}
