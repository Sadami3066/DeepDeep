package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.paths.AgentPaths
import java.io.File
import java.nio.file.Files
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionIndexTest {
    private lateinit var root: File
    private lateinit var agentDir: File
    private lateinit var cwd: String
    private lateinit var cwd2: String
    private var messageSeq = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-session-index").toFile()
        agentDir = File(root, "agent").apply { mkdirs() }
        cwd = File(root, "home").absolutePath
        cwd2 = File(root, "workspace").absolutePath
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun ordersSessionsByLatestActivity() = runTest {
        val oldest = writeSession("a", mtime = BASE)
        writeSession("b", mtime = BASE + 1_000)
        writeSession("c", mtime = BASE + 2_000)
        val index = SessionIndex(agentDir)

        assertEquals(listOf("c", "b", "a"), index.list(cwd).map { it.sessionId })
        assertEquals(3, index.parsedFileCount)

        appendMessage(oldest)
        oldest.setLastModified(BASE + 3_000)
        assertEquals(listOf("a", "c", "b"), index.list(cwd).map { it.sessionId })
        assertEquals(4, index.parsedFileCount)
    }

    @Test
    fun secondListUsesIndexCache() = runTest {
        writeSession("a", mtime = BASE)
        writeSession("b", mtime = BASE + 1_000)
        val index = SessionIndex(agentDir)

        index.list(cwd)
        assertEquals(2, index.parsedFileCount)
        index.list(cwd)
        assertEquals(2, index.parsedFileCount)

        val reopened = SessionIndex(agentDir)
        reopened.list(cwd)
        assertEquals(0, reopened.parsedFileCount)
        assertTrue(reopened.lastLoadSucceeded)
        assertTrue(File(root, SessionIndex.INDEX_FILE_NAME).isFile)
    }

    @Test
    fun corruptIndexRebuilds() = runTest {
        writeSession("a", mtime = BASE)
        writeSession("b", mtime = BASE + 1_000)
        SessionIndex(agentDir).list(cwd)
        val indexFile = File(root, SessionIndex.INDEX_FILE_NAME)
        indexFile.writeText("{ not json")

        val rebuilt = SessionIndex(agentDir)
        assertEquals(listOf("b", "a"), rebuilt.list(cwd).map { it.sessionId })
        assertEquals(2, rebuilt.parsedFileCount)
        assertTrue(rebuilt.lastLoadSucceeded)
        assertTrue(indexFile.readText().contains("\"schemaVersion\":1"))
    }

    @Test
    fun unknownSchemaRebuilds() = runTest {
        writeSession("a", mtime = BASE)
        val indexFile = File(root, SessionIndex.INDEX_FILE_NAME)
        indexFile.writeText("""{"schemaVersion":99,"entries":{"stale":{}}}""")

        val rebuilt = SessionIndex(agentDir)
        assertEquals(listOf("a"), rebuilt.list(cwd).map { it.sessionId })
        assertEquals(1, rebuilt.parsedFileCount)
        assertTrue(indexFile.readText().contains("\"schemaVersion\":1"))
    }

    @Test
    fun parsesHeaderAndSessionInfoOnly() = runTest {
        val createdAt = "2024-05-01T00:00:00.000Z"
        val createdMillis = OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
        val file = writeSession("s1", createdAt = createdAt, name = "My chat", messages = 3, mtime = createdMillis + 5_000)

        val session = SessionIndex(agentDir).list(cwd).single()

        assertEquals("s1", session.sessionId)
        assertEquals("My chat", session.name)
        assertEquals(3, session.messageCount)
        assertEquals(createdMillis, session.createdAt)
        assertEquals(createdMillis + 5_000, session.updatedAt)
        assertEquals(file.absolutePath, session.filePath)
        assertEquals(cwd, session.cwd)
    }

    @Test
    fun updatedAtNeverOlderThanCreatedAt() = runTest {
        val createdAt = "2024-05-01T00:00:00.000Z"
        val createdMillis = OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
        writeSession("s1", createdAt = createdAt, mtime = createdMillis - 10_000)

        assertEquals(createdMillis, SessionIndex(agentDir).list(cwd).single().updatedAt)
    }

    @Test
    fun ignoresNonSessionFiles() = runTest {
        writeSession("valid", mtime = BASE)
        val dir = sessionsDirectory(cwd)
        File(dir, "notes.txt").writeText("ignored")
        File(dir, "empty.jsonl").writeText("")
        File(dir, "foreign.jsonl").writeText("""{"type":"other","id":"x"}""" + "\n")

        val index = SessionIndex(agentDir)
        assertEquals(listOf("valid"), index.list(cwd).map { it.sessionId })
        assertEquals(3, index.parsedFileCount)
        index.list(cwd)
        assertEquals(3, index.parsedFileCount)
    }

    @Test
    fun listAllMergesWorkspacesByActivity() = runTest {
        writeSession("home-old", cwd = cwd, mtime = BASE)
        writeSession("home-new", cwd = cwd, mtime = BASE + 2_000)
        writeSession("work", cwd = cwd2, mtime = BASE + 1_000)

        val sessions = SessionIndex(agentDir).listAll(listOf(cwd, cwd2))

        assertEquals(listOf("home-new", "work", "home-old"), sessions.map { it.sessionId })
        assertEquals(cwd2, sessions.first { it.sessionId == "work" }.cwd)
    }

    @Test
    fun renamedSessionInfoKeepsLatestName() = runTest {
        val file = writeSession("s1", name = "First name", mtime = BASE)
        file.appendText(sessionInfoLine("Second name"))
        file.setLastModified(BASE + 1_000)

        assertEquals("Second name", SessionIndex(agentDir).list(cwd).single().name)
    }

    @Test
    fun deletedSessionIsDroppedFromIndex() = runTest {
        val first = writeSession("a", mtime = BASE)
        writeSession("b", mtime = BASE + 1_000)
        val index = SessionIndex(agentDir)
        assertEquals(2, index.list(cwd).size)

        assertTrue(first.delete())
        assertEquals(listOf("b"), index.list(cwd).map { it.sessionId })
        val reopened = SessionIndex(agentDir)
        assertEquals(listOf("b"), reopened.list(cwd).map { it.sessionId })
        assertEquals(0, reopened.parsedFileCount)
    }

    private fun sessionsDirectory(cwd: String): File =
        AgentPaths(cwd.toPath(), agentDir.absolutePath.toPath()).sessionsForCwd().toFile()

    private fun writeSession(
        sessionId: String,
        cwd: String = this.cwd,
        createdAt: String = DEFAULT_CREATED,
        name: String? = null,
        messages: Int = 1,
        mtime: Long = BASE,
    ): File {
        val directory = sessionsDirectory(cwd).apply { mkdirs() }
        val file = File(directory, "$sessionId.jsonl")
        val lines = mutableListOf(headerLine(sessionId, cwd, createdAt))
        repeat(messages) { lines.add(messageLine(createdAt)) }
        if (name != null) lines.add(sessionInfoLine(name))
        file.writeText(lines.joinToString("\n") + "\n")
        file.setLastModified(mtime)
        return file
    }

    private fun appendMessage(file: File) {
        file.appendText(messageLine(DEFAULT_CREATED))
    }

    private fun headerLine(sessionId: String, cwd: String, createdAt: String): String =
        """{"type":"session","version":3,"id":"$sessionId","timestamp":"$createdAt","cwd":"$cwd","parentSession":null}"""

    private fun messageLine(createdAt: String): String {
        val id = "m${messageSeq++}"
        return """{"type":"message","id":"$id","parentId":null,"timestamp":"$createdAt","message":{"role":"user","content":[{"type":"text","text":"hello"}]}}"""
    }

    private fun sessionInfoLine(name: String): String =
        """{"type":"session_info","id":"n${messageSeq++}","parentId":null,"timestamp":"$DEFAULT_CREATED","name":"$name"}"""

    private companion object {
        const val BASE = 1_700_000_000_000L
        const val DEFAULT_CREATED = "2020-01-01T00:00:00.000Z"
    }
}
