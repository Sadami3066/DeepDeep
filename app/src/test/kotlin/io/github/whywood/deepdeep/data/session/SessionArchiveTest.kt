package io.github.whywood.deepdeep.data.session

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.SessionHistory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var archiveCounter = 0
    private val root: File get() = File(temporaryFolder.root, "agent")

    @Test
    fun exportsAndImportsRoundTrip() = runTest {
        val (first, firstFile) = createSession("alpha", cwd = "/workspace/alpha")
        val (second, _) = createSession("beta", cwd = "/workspace/beta")
        val archive = File(temporaryFolder.root, "backup.zip")
        val exported = SessionArchive.export(archive, listOf(first, second), false, "1.0") { null }
        assertEquals(ExportResult.Success(sessions = 2, markdown = 0, skipped = 0), exported)
        val entries = readEntries(archive)
        assertEquals(setOf("manifest.json", "sessions/alpha.jsonl", "sessions/beta.jsonl"), entries.keys)
        val firstBytes = firstFile.readBytes()
        root.deleteRecursively()
        val imported = SessionArchive.import(archive, root)
        assertEquals(ImportResult.Success(imported = 2, skipped = 0), imported)
        assertTrue(File(first.filePath).readBytes().contentEquals(firstBytes))
        assertTrue(File(second.filePath).isFile)
    }

    @Test
    fun importsMarkdownRoundTripWithoutCountingItAsSessions() = runTest {
        val (session, _) = createSession("alpha", title = "会话 / 标题")
        val archive = File(temporaryFolder.root, "backup.zip")
        val exported = SessionArchive.export(archive, listOf(session), true, "1.0") { "# Markdown" }
        assertEquals(ExportResult.Success(sessions = 1, markdown = 1, skipped = 0), exported)
        val entries = readEntries(archive)
        assertEquals("# Markdown", entries.getValue("markdown/会话 _ 标题-alpha.md").toString(Charsets.UTF_8))
        root.deleteRecursively()
        val imported = SessionArchive.import(archive, root)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0), imported)
    }

    @Test
    fun importNeverOverwritesExistingSessions() = runTest {
        val (session, file) = createSession("alpha")
        val archive = File(temporaryFolder.root, "backup.zip")
        SessionArchive.export(archive, listOf(session), false, "1.0") { null }
        file.writeText("LOCAL")
        val imported = SessionArchive.import(archive, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
        assertEquals("LOCAL", file.readText())
    }

    @Test
    fun importSkipsSessionIdThatExistsElsewhere() = runTest {
        val (session, _) = createSession("alpha")
        val archive = File(temporaryFolder.root, "backup.zip")
        SessionArchive.export(archive, listOf(session), false, "1.0") { null }
        root.deleteRecursively()
        val otherDirectory = File(root, "sessions/other").apply { mkdirs() }
        val other = File(otherDirectory, "alpha.jsonl")
        other.writeText("LOCAL")
        val imported = SessionArchive.import(archive, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
        assertEquals("LOCAL", other.readText())
        assertFalse(File(session.filePath).exists())
    }

    @Test
    fun importSkipsTimestampedExistingSession() = runTest {
        val (session, _) = createSession("alpha")
        val archive = File(temporaryFolder.root, "backup.zip")
        SessionArchive.export(archive, listOf(session), false, "1.0") { null }
        root.deleteRecursively()
        val existing = File(root, "sessions/2026-11-05T07-24-18-376Z_alpha.jsonl")
        existing.parentFile.mkdirs()
        existing.writeText("LOCAL")
        val imported = SessionArchive.import(archive, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
        assertEquals("LOCAL", existing.readText())
    }

    @Test
    fun rejectsNonZipInput() {
        val source = File(temporaryFolder.root, "plain.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        assertEquals(ImportResult.Failure(SessionArchiveError.NotZip), SessionArchive.import(source, root))
    }

    @Test
    fun rejectsArchiveWithoutManifest() {
        val source = writeZip("sessions/alpha.jsonl" to sessionJson("alpha", "/w").toByteArray())
        assertEquals(ImportResult.Failure(SessionArchiveError.MissingManifest), SessionArchive.import(source, root))
    }

    @Test
    fun rejectsBrokenManifest() {
        val source = writeZip("manifest.json" to "not json".toByteArray())
        assertEquals(ImportResult.Failure(SessionArchiveError.InvalidManifest), SessionArchive.import(source, root))
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val source = writeZip("manifest.json" to manifestJson(schema = 99).toByteArray())
        assertEquals(ImportResult.Failure(SessionArchiveError.UnsupportedVersion), SessionArchive.import(source, root))
    }

    @Test
    fun importFallsBackToCwdWhenFilePathIsOutsideAllowedRoot() {
        val cwd = "/workspace/alpha"
        val outside = File(temporaryFolder.root, "outside").apply { mkdirs() }
        val outsideFile = File(outside, "alpha.jsonl")
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"$cwd","filePath":"${outsideFile.absolutePath}"}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("alpha", cwd).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0), imported)
        assertFalse(outsideFile.exists())
        val restored = restoredFile(cwd, "alpha")
        assertTrue(restored.isFile)
        assertEquals(sessionJson("alpha", cwd), restored.readText())
    }

    @Test
    fun importPrefersLocalFilePathWhenParentExists() {
        val local = File(root, "manual/deeper/alpha.jsonl")
        assertTrue(File(root, "manual/deeper").mkdirs())
        val cwd = "/workspace/other"
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"$cwd","filePath":"${local.absolutePath}"}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("alpha", cwd).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0), imported)
        assertTrue(local.isFile)
        assertEquals(sessionJson("alpha", cwd), local.readText())
        assertFalse(restoredFile(cwd, "alpha").exists())
    }

    @Test
    fun importRestoresSessionsFromAnotherDevice() {
        val cwd = "/old/device/deepdeep/workspace"
        val oldFile = File("/old/device/does/not/exist/alpha.jsonl")
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"Old","updatedAt":1,"cwd":"$cwd","filePath":"${oldFile.absolutePath}"}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("alpha", cwd).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0), imported)
        assertFalse(oldFile.exists())
        val restored = restoredFile(cwd, "alpha")
        assertTrue(restored.isFile)
        assertEquals(sessionJson("alpha", cwd), restored.readText())
    }

    @Test
    fun importFallsBackToCwdWhenFilePathParentIsMissing() {
        val cwd = "/workspace/missing"
        val missing = File(root, "gone/alpha.jsonl")
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"$cwd","filePath":"${missing.absolutePath}"}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("alpha", cwd).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0), imported)
        assertFalse(missing.exists())
        assertTrue(restoredFile(cwd, "alpha").isFile)
    }

    @Test
    fun importSkipsSessionsWithoutUsableLocation() {
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"relative/workspace","filePath":""}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("alpha", "relative/workspace").toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
    }

    @Test
    fun importSkipsSessionWhoseHeaderDoesNotMatchManifest() {
        val cwd = "/w"
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"$cwd","filePath":"${File(root, "sessions/alpha.jsonl").absolutePath}"}""",
            ).toByteArray(),
            "sessions/alpha.jsonl" to sessionJson("beta", cwd).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
        assertFalse(restoredFile(cwd, "alpha").exists())
    }

    @Test
    fun importSkipsSessionsMissingFromArchive() {
        val cwd = "/w"
        val source = writeZip(
            "manifest.json" to manifestJson(
                sessions = """{"id":"alpha","title":"A","updatedAt":1,"cwd":"$cwd","filePath":""}""",
            ).toByteArray(),
        )
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1), imported)
    }

    @Test
    fun importHandlesEmptyManifest() {
        val source = writeZip("manifest.json" to manifestJson().toByteArray())
        val imported = SessionArchive.import(source, root)
        assertEquals(ImportResult.Success(imported = 0, skipped = 0), imported)
    }

    @Test
    fun exportSkipsSessionsWhoseFilesAreMissing() = runTest {
        val missing = ConversationSummary(
            id = "ghost",
            title = "Ghost",
            updatedAt = 1L,
            filePath = File(temporaryFolder.root, "ghost.jsonl").absolutePath,
        )
        val archive = File(temporaryFolder.root, "backup.zip")
        val exported = SessionArchive.export(archive, listOf(missing), true, "1.0") { "# Markdown" }
        assertEquals(ExportResult.Success(sessions = 0, markdown = 0, skipped = 1), exported)
        assertEquals(setOf("manifest.json"), readEntries(archive).keys)
    }

    private fun createSession(
        id: String,
        cwd: String = "/workspace",
        title: String = "Session $id",
    ): Pair<ConversationSummary, File> {
        val directory = requireNotNull(SessionHistory.sessionsDirectory(cwd, root)) { "no sessions directory for $cwd" }
        assertTrue(directory.mkdirs() || directory.isDirectory)
        val file = File(directory, "$id.jsonl")
        file.writeText(sessionJson(id, cwd))
        val summary = ConversationSummary(
            id = id,
            title = title,
            updatedAt = 42L,
            filePath = file.absolutePath,
            cwd = cwd,
        )
        return summary to file
    }

    private fun restoredFile(cwd: String, id: String): File =
        File(requireNotNull(SessionHistory.sessionsDirectory(cwd, root)), "$id.jsonl")

    private fun sessionJson(id: String, cwd: String): String = buildString {
        append("{\"type\":\"session\",\"id\":\"").append(id).append("\",\"cwd\":\"").append(cwd)
        append("\",\"timestamp\":1}\n")
        append("{\"type\":\"message\",\"id\":\"m1\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}\n")
    }

    private fun manifestJson(schema: Int = 1, sessions: String = ""): String =
        """{"schema":$schema,"createdAt":1,"appVersion":"1.0","markdown":false,"sessions":[$sessions]}"""

    private fun writeZip(vararg entries: Pair<String, ByteArray>): File {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        archiveCounter++
        return File(temporaryFolder.root, "archive-$archiveCounter.zip").apply { writeBytes(output.toByteArray()) }
    }

    private fun readEntries(file: File): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
