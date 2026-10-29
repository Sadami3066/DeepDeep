package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLedgerTest {
    private fun tempDirectory(): File = Files.createTempDirectory("deepdeep-run-ledger").toFile()

    private fun runsFile(directory: File): File = File(directory, "runs.json")

    private fun runIds(directory: File): List<String> {
        val file = runsFile(directory)
        if (!file.isFile) return emptyList()
        val root = Json.parseToJsonElement(file.readText()) as? JsonObject ?: return emptyList()
        val runs = root["runs"] as? JsonArray ?: return emptyList()
        return runs.mapNotNull { element ->
            ((element as? JsonObject)?.get("runId") as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
    }

    @Test
    fun beginWritesRecordsAndClearRemovesThem() = runTest {
        val directory = tempDirectory()
        try {
            val ledger = RunLedger(directory, Dispatchers.Unconfined)
            ledger.begin("session-1", "run-1", 1_000L, TurnSource.USER)
            ledger.begin("session-2", "run-2", 2_000L, TurnSource.REMINDER)
            assertEquals(listOf("run-1", "run-2"), runIds(directory))

            ledger.clear("run-1")
            assertEquals(listOf("run-2"), runIds(directory))

            ledger.clear("missing")
            assertEquals(listOf("run-2"), runIds(directory))
            assertTrue(ledger.interruptedRuns.value.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun leftoversBecomeInterruptedAndFileIsCleared() = runTest {
        val directory = tempDirectory()
        try {
            val first = RunLedger(directory, Dispatchers.Unconfined)
            first.begin("session-1", "run-1", 1_000L, TurnSource.USER)
            first.begin("session-2", "run-2", 2_000L, TurnSource.GOAL)

            val restarted = RunLedger(directory, Dispatchers.Unconfined)
            val leftovers = restarted.loadLeftovers()
            assertEquals(listOf("run-1", "run-2"), leftovers.map { it.runId })
            assertEquals("session-1", leftovers.first().sessionId)
            assertEquals(TurnSource.USER, leftovers.first().source)
            assertEquals(leftovers, restarted.interruptedRuns.value)
            assertTrue(runIds(directory).isEmpty())

            val third = RunLedger(directory, Dispatchers.Unconfined)
            assertTrue(third.loadLeftovers().isEmpty())
            assertTrue(third.interruptedRuns.value.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun dismissRemovesOnlyTheTargetSession() = runTest {
        val directory = tempDirectory()
        try {
            val ledger = RunLedger(directory, Dispatchers.Unconfined)
            ledger.begin("session-1", "run-1", 1_000L, TurnSource.USER)
            ledger.begin("session-1", "run-2", 2_000L, TurnSource.USER)
            ledger.begin("session-2", "run-3", 3_000L, TurnSource.JOB)
            ledger.loadLeftovers()
            assertEquals(3, ledger.interruptedRuns.value.size)

            ledger.dismiss("session-1")
            assertEquals(listOf("session-2"), ledger.interruptedRuns.value.map { it.sessionId })

            ledger.dismiss("missing")
            assertEquals(listOf("session-2"), ledger.interruptedRuns.value.map { it.sessionId })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptFileIsQuarantinedAndRebuilt() = runTest {
        val directory = tempDirectory()
        try {
            directory.mkdirs()
            runsFile(directory).writeText("{not json")
            val ledger = RunLedger(directory, Dispatchers.Unconfined)
            assertTrue(ledger.loadLeftovers().isEmpty())
            assertTrue(ledger.interruptedRuns.value.isEmpty())
            assertTrue(File(directory, "runs.json.corrupt").isFile)
            assertTrue(runIds(directory).isEmpty())
            val root = Json.parseToJsonElement(runsFile(directory).readText()) as JsonObject
            assertEquals("1", root["schemaVersion"].toString())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun keepsAtMostSixtyFourRecords() = runTest {
        val directory = tempDirectory()
        try {
            val ledger = RunLedger(directory, Dispatchers.Unconfined)
            repeat(70) { index ->
                ledger.begin("session-$index", "run-$index", index.toLong(), TurnSource.USER)
            }
            assertEquals((6 until 70).map { "run-$it" }, runIds(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
