package io.github.whywood.deepdeep.runtime

import dev.pi.ai.TextContent
import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.session.SessionManager
import dev.pi.coding.tools.readTool
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Test

/** These are observation tests: passing means the described defect was reproduced. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditRuntimeTest {
    @Test fun incompleteTailLosesFirstNewEntry() = runBlocking {
        val dir = Files.createTempDirectory("deepdeep-audit-tail").toFile()
        try {
            val file = File(dir, "session.jsonl")
            file.writeText("""{"type":"session","version":3,"id":"audit-session","timestamp":"2026-10-29T00:00:00.000Z","cwd":"/audit"}
{"type":"session_info","id":"entry001","parentId":null,"timestamp":"2026-10-29T00:00:01.000Z","name":"before"}
{"type":"message","id":"crash""" )
            val session = SessionManager.open(file.absolutePath.toPath(), FileSystem.SYSTEM)
            session.appendSessionInfo("after-crash")
            session.close()
            val reopened = SessionManager.open(file.absolutePath.toPath(), FileSystem.SYSTEM)
            println("AUDIT tail reopened-name=" + reopened.getSessionName())
            assertEquals("before", reopened.getSessionName())
            reopened.close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun credentialWritesCanFinishOutOfOrder() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val api = object : ApiKeyRecordStore {
            val records = mutableMapOf<String, String>()
            override suspend fun readRecord(providerId: String) = records[providerId]
            override suspend fun readAllRecords() = records.toMap()
            override suspend fun saveRecord(providerId: String, key: String?) {
                if (key == "audit-old") { firstWrite.complete(Unit); releaseFirst.await() }
                if (key == null) records.remove(providerId) else records[providerId] = key
            }
        }
        val oauth = object : OAuthRecordStore {
            override suspend fun readRawRecords() = emptyMap<String, JsonElement>()
            override suspend fun saveRawRecord(providerId: String, credential: JsonElement) = Unit
            override suspend fun deleteRecord(providerId: String) = Unit
        }
        val store = SdkCredentialStore(api, oauth, this, StandardTestDispatcher(testScheduler))
        store.writeApiKey("audit", "audit-old", null)
        runCurrent()
        assertTrue(firstWrite.isCompleted)
        store.writeApiKey("audit", "audit-new", null)
        runCurrent()
        assertEquals("audit-new", api.records["audit"])
        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals("audit-new", store.cachedApiKey("audit"))
        assertEquals("audit-old", api.records["audit"])
        println("AUDIT credentials memory=new persisted=old")
    }

    @Test fun workshopReadEscapesTemporaryWorkspace() = runBlocking {
        val dir = Files.createTempDirectory("deepdeep-audit-boundary").toFile()
        try {
            val workspace = File(dir, "isolated-workspace").apply { mkdirs() }
            File(dir, "outside.txt").writeText("audit-only-sentinel")
            assertTrue(workshopAgentToolVisible("read"))
            val tool = readTool(workspace.absolutePath.toPath(), FileSystem.SYSTEM)
            val result = tool.execute("audit", buildJsonObject { put("path", "../outside.txt") }, null, null)
            assertTrue(result.content.filterIsInstance<TextContent>().joinToString { it.text }.contains("audit-only-sentinel"))
            println("AUDIT workshop read can access parent directory")
        } finally { dir.deleteRecursively() }
    }

    @Test fun clockRecomputeDropsOverdueOnceReminder() = runBlocking {
        val dir = Files.createTempDirectory("deepdeep-audit-clock").toFile()
        var now = Instant.parse("2026-10-29T01:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneId.of("Asia/Shanghai")
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
            override fun instant() = now
        }
        try {
            val store = ScheduleStore(dir, clock)
            store.create(NewSchedule("audit", "audit", ScheduleTrigger.Once(LocalDate.parse("2026-10-29"), LocalTime.parse("09:01")), true, "audit-session", "/audit.jsonl", "/audit"))
            now = Instant.parse("2026-10-29T01:02:00Z")
            store.recomputeTargets()
            val reminder = store.list().single()
            assertFalse(reminder.enabled)
            assertNull(reminder.nextRunAt)
            assertNull(reminder.lastDeliveredAt)
            println("AUDIT clock recompute disabled an undelivered overdue reminder")
        } finally { dir.deleteRecursively() }
    }

    @Test fun sessionListScaling() = runBlocking {
        val dir = Files.createTempDirectory("deepdeep-audit-list").toFile()
        try {
            val paths = AgentPaths(File(dir, "workspace").absolutePath.toPath(), File(dir, "agent").absolutePath.toPath())
            val sessions = File(paths.sessionsForCwd().toString()).apply { mkdirs() }
            val body = "x".repeat(1024)
            for (i in 1..1000) {
                File(sessions, "$i.jsonl").writeText(buildString {
                    appendLine("""{"type":"session","version":3,"id":"audit-$i","timestamp":"2026-10-29T00:00:00.000Z","cwd":"${paths.cwd}"}""")
                    repeat(20) { appendLine("""{"type":"message","id":"entry$it","message":{"role":"user","content":"$body"}}""") }
                })
                if (i in listOf(10,100,1000)) {
                    repeat(2) { SessionManager.list(paths, FileSystem.SYSTEM) }
                    val times = List(7) {
                        val start = System.nanoTime()
                        val result = SessionManager.list(paths, FileSystem.SYSTEM)
                        assertEquals(i, result.size)
                        (System.nanoTime()-start)/1e6
                    }.sorted()
                    println("AUDIT_PERF session_list files=$i bytes=${sessions.listFiles()!!.sumOf { it.length() }} median_ms=${times[3]} max_ms=${times.last()}")
                }
            }
        } finally { dir.deleteRecursively() }
    }
}
