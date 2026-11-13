package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackgroundTasksTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun encodesAndDecodesAllKinds() {
        val tasks = listOf(
            BackgroundTaskRecord(
                id = "once",
                kind = PluginBackgroundKind.ONCE,
                triggerAtMs = 1_000L,
                requiresNetwork = true,
            ),
            BackgroundTaskRecord(
                id = "periodic",
                kind = PluginBackgroundKind.PERIODIC,
                triggerAtMs = 2_000L,
                intervalMs = 900_000L,
                requiresCharging = true,
                lastRunAtMs = 500L,
            ),
            BackgroundTaskRecord(
                id = "exact",
                kind = PluginBackgroundKind.EXACT,
                triggerAtMs = 3_000L,
                exact = false,
            ),
            BackgroundTaskRecord(id = "boot", kind = PluginBackgroundKind.BOOT),
        )
        val decoded = BackgroundTasksCodec.decode(BackgroundTasksCodec.encode(tasks))
        assertEquals(tasks.sortedBy { it.id }, decoded)
        assertFalse(decoded.first { it.id == "exact" }.exact)
    }

    @Test
    fun encodeIsDeterministic() {
        val first = BackgroundTasksCodec.encode(
            listOf(
                BackgroundTaskRecord("b", PluginBackgroundKind.BOOT),
                BackgroundTaskRecord("a", PluginBackgroundKind.ONCE, triggerAtMs = 10L),
            ),
        )
        val second = BackgroundTasksCodec.encode(
            listOf(
                BackgroundTaskRecord("a", PluginBackgroundKind.ONCE, triggerAtMs = 10L),
                BackgroundTaskRecord("b", PluginBackgroundKind.BOOT),
            ),
        )
        assertEquals(first.toString(), second.toString())
        assertEquals(listOf("a", "b"), BackgroundTasksCodec.decode(first).map { it.id })
    }

    @Test
    fun decodeSkipsInvalidEntriesAndUnknownSchemas() {
        assertTrue(BackgroundTasksCodec.decode(null).isEmpty())
        val unknownSchema = Json.parseToJsonElement("""{"schemaVersion":99,"tasks":[]}""").jsonObject
        assertTrue(BackgroundTasksCodec.decode(unknownSchema).isEmpty())
        val malformed = Json.parseToJsonElement(
            """
            {
              "schemaVersion": 1,
              "tasks": [
                {"id": "", "kind": "once"},
                {"id": "missing-kind"},
                {"id": "bad-kind", "kind": "hourly"},
                {"id": "good", "kind": "boot"}
              ]
            }
            """.trimIndent(),
        ).jsonObject
        assertEquals(listOf("good"), BackgroundTasksCodec.decode(malformed).map { it.id })
    }

    @Test
    fun storePersistsAtomically() = runTest {
        val root = temporary.newFolder("plugin")
        val file = backgroundStoreFile(root)
        assertEquals(File(File(root, PLUGIN_BACKGROUND_DIR), PLUGIN_BACKGROUND_FILE), file)
        val store = PluginBackgroundStore(file, Dispatchers.Unconfined)
        assertTrue(store.load().isEmpty())

        store.upsert(BackgroundTaskRecord("once", PluginBackgroundKind.ONCE, triggerAtMs = 5L))
        store.upsert(BackgroundTaskRecord("boot", PluginBackgroundKind.BOOT))
        assertEquals(listOf("boot", "once"), store.load().map { it.id })
        assertTrue(file.isFile)
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())

        val marked = store.record("once")!!
        store.markRun(marked, 99L)
        assertEquals(99L, store.record("once")!!.lastRunAtMs)

        assertTrue(store.remove("once"))
        assertFalse(store.remove("missing"))
        assertEquals(listOf("boot"), store.load().map { it.id })
    }

    @Test
    fun storeSharesMutexBetweenInstances() = runTest {
        val file = backgroundStoreFile(temporary.newFolder("shared"))
        val first = PluginBackgroundStore(file, Dispatchers.Unconfined)
        val second = PluginBackgroundStore(file, Dispatchers.Unconfined)
        coroutineScope {
            listOf(
                async { first.upsert(BackgroundTaskRecord("a", PluginBackgroundKind.ONCE, triggerAtMs = 1L)) },
                async { second.upsert(BackgroundTaskRecord("b", PluginBackgroundKind.BOOT)) },
            ).awaitAll()
        }
        assertEquals(listOf("a", "b"), first.load().map { it.id })
    }

    @Test
    fun computesNextRunAndReschedules() {
        val once = BackgroundTaskRecord("once", PluginBackgroundKind.ONCE, triggerAtMs = 10L)
        assertEquals(10L, nextRunAtMs(once))
        assertTrue(isTaskDue(once, 10L))
        assertFalse(isTaskDue(once, 9L))
        assertNull(rescheduleAfterRun(once, 20L))

        val boot = BackgroundTaskRecord("boot", PluginBackgroundKind.BOOT)
        assertNull(nextRunAtMs(boot))
        assertFalse(isTaskDue(boot, Long.MAX_VALUE))

        val recurring = BackgroundTaskRecord(
            "exact",
            PluginBackgroundKind.EXACT,
            triggerAtMs = 10L,
            intervalMs = 5L,
        )
        val rescheduled = rescheduleAfterRun(recurring, 20L)
        assertEquals(20L, rescheduled?.lastRunAtMs)
        assertEquals(25L, rescheduled?.triggerAtMs)

        val periodic = BackgroundTaskRecord(
            "periodic",
            PluginBackgroundKind.PERIODIC,
            triggerAtMs = 100L,
            intervalMs = 40L,
        )
        assertEquals(100L, nextRunAtMs(periodic))
        assertEquals(140L, nextRunAtMs(periodic.copy(lastRunAtMs = 100L)))
    }

    @Test
    fun annotatesDegradedExactTasksInTaskInfo() {
        val exact = BackgroundTaskRecord("exact", PluginBackgroundKind.EXACT, triggerAtMs = 7L)
        val info = taskInfo(exact)
        assertEquals("exact", info.kind)
        assertEquals(7L, info.nextRunAtMs)

        val degraded = taskInfo(exact.copy(exact = false))
        assertEquals(EXACT_DEGRADED_KIND, degraded.kind)
    }
}
