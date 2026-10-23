package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleStoreTest {
    private lateinit var directory: File
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), zone)

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-schedules").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(): ScheduleStore = ScheduleStore(directory, clock)

    private fun newSchedule(
        name: String = "Morning brief",
        trigger: ScheduleTrigger = ScheduleTrigger.Daily(LocalTime.of(8, 0)),
    ): NewSchedule = NewSchedule(
        name = name,
        workspaceId = "home",
        prompt = "Summarize my day",
        trigger = trigger,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun createListAndPersistRoundTrip() = runTest {
        val store = store()
        assertTrue(store.list().isEmpty())

        val created = store.create(newSchedule())
        assertTrue(created.id.isNotBlank())
        assertEquals("Morning brief", created.name)
        assertEquals("home", created.workspaceId)
        assertEquals("Summarize my day", created.prompt)
        assertEquals(ScheduleTrigger.Daily(LocalTime.of(8, 0)), created.trigger)
        assertEquals(epoch(2026, 10, 24, 8, 0), created.nextRunAt)
        assertEquals(clock.millis(), created.createdAt)
        assertEquals(clock.millis(), created.updatedAt)

        val file = File(directory, "schedules.json")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("\"schemaVersion\":1"))
        assertTrue(file.readText().contains("\"nextRunAt\""))
        assertFalse(File(directory, "schedules.json.tmp").exists())

        val reopened = ScheduleStore(directory, clock).list()
        assertEquals(listOf(created), reopened)
    }

    @Test
    fun weeklyDaysArePersistedAsIsoNumbers() = runTest {
        val store = store()
        store.create(
            newSchedule(
                trigger = ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(18, 0)),
            ),
        )
        val text = File(directory, "schedules.json").readText()
        assertTrue(text.contains("[1,5]"))
    }

    @Test
    fun onceScheduleMustBeInTheFuture() = runTest {
        val store = store()
        val past = ScheduleTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val error = runCatching { store.create(newSchedule(trigger = past)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(store.list().isEmpty())
        assertFalse(File(directory, "schedules.json").exists())
    }

    @Test
    fun futureOnceScheduleStoresNextRunAt() = runTest {
        val store = store()
        val created = store.create(
            newSchedule(
                trigger = ScheduleTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(9, 30)),
            ),
        )
        assertEquals(epoch(2026, 12, 1, 9, 30), created.nextRunAt)
    }

    @Test
    fun updateChangesOnlyProvidedFields() = runTest {
        val store = store()
        val created = store.create(newSchedule())

        val updated = store.update(
            created.id,
            SchedulePatch(
                name = "Evening brief",
                trigger = ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 30)),
                enabled = false,
            ),
        )
        assertEquals("Evening brief", updated?.name)
        assertEquals(created.prompt, updated?.prompt)
        assertEquals(created.workspaceId, updated?.workspaceId)
        assertEquals(false, updated?.enabled)
        assertEquals(
            ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 30)),
            updated?.trigger,
        )
        assertEquals(epoch(2026, 10, 23, 19, 30), updated?.nextRunAt)
        assertEquals(created.createdAt, updated?.createdAt)
        assertEquals(listOf(updated), store.list())
    }

    @Test
    fun updateWithoutTriggerOrEnabledKeepsNextRunAt() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val updated = store.update(created.id, SchedulePatch(name = "Renamed"))
        assertEquals(created.nextRunAt, updated?.nextRunAt)
        assertEquals("Renamed", updated?.name)
    }

    @Test
    fun updateRejectsPastOnceTrigger() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val past = ScheduleTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val error = runCatching {
            store.update(created.id, SchedulePatch(trigger = past))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(created, store.list().single())
    }

    @Test
    fun deletingRemovesTheSchedule() = runTest {
        val store = store()
        val first = store.create(newSchedule(name = "First"))
        val second = store.create(newSchedule(name = "Second"))

        assertTrue(store.delete(first.id))
        assertEquals(listOf(second), store.list())
        assertFalse(store.delete(first.id))
        assertFalse(store.list().any { it.id == first.id })
    }

    @Test
    fun onceAndWeeklyTriggersSurviveReload() = runTest {
        val store = store()
        val once = store.create(
            newSchedule(
                name = "One shot",
                trigger = ScheduleTrigger.Once(LocalDate.of(2026, 11, 1), LocalTime.of(9, 15)),
            ),
        )
        val weekly = store.create(
            newSchedule(
                name = "Weekly",
                trigger = ScheduleTrigger.Weekly(setOf(DayOfWeek.SUNDAY), LocalTime.of(21, 0)),
            ),
        )
        val reopened = ScheduleStore(directory, clock).list()
        assertEquals(listOf(once, weekly), reopened)
        assertNotNull(reopened[0].nextRunAt)
        assertNotNull(reopened[1].nextRunAt)
        assertNull(store.update("missing", SchedulePatch(name = "x")))
    }

    @Test
    fun corruptFileIsPreservedAndThrows() = runTest {
        val file = File(directory, "schedules.json")
        val corrupt = "{ this is not json"
        file.writeText(corrupt)
        val store = store()

        val listError = runCatching { store.list() }.exceptionOrNull()
        assertTrue(listError is CorruptScheduleDataException)
        assertEquals(corrupt, file.readText())

        val createError = runCatching { store.create(newSchedule()) }.exceptionOrNull()
        assertTrue(createError is CorruptScheduleDataException)
        assertEquals(corrupt, file.readText())
    }

    @Test
    fun unsupportedSchemaVersionThrows() = runTest {
        val file = File(directory, "schedules.json")
        val versionTwo = "{\"schemaVersion\":2,\"schedules\":[]}"
        file.writeText(versionTwo)
        val error = runCatching { store().list() }.exceptionOrNull()
        assertTrue(error is CorruptScheduleDataException)
        assertEquals(versionTwo, file.readText())
    }

    @Test
    fun corruptEntryThrows() = runTest {
        val file = File(directory, "schedules.json")
        val corruptEntry = "{\"schemaVersion\":1,\"schedules\":[{\"id\":\"x\"}]}"
        file.writeText(corruptEntry)
        val error = runCatching { store().list() }.exceptionOrNull()
        assertTrue(error is CorruptScheduleDataException)
        assertEquals(corruptEntry, file.readText())
    }

    @Test
    fun blankFileIsTreatedAsEmpty() = runTest {
        File(directory, "schedules.json").writeText("  \n")
        assertTrue(store().list().isEmpty())
    }

    @Test
    fun markRunningAndFinishedPersistRunState() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val startedAt = epoch(2026, 10, 23, 8, 0)
        val running = store.markRunning(created.id, startedAt)
        assertNotNull(running)
        assertEquals(ScheduleRunStatus.RUNNING, running!!.lastStatus)
        assertEquals(startedAt, running.lastRunAt)

        val finishedAt = startedAt + 60_000L
        val finished = store.markFinished(
            id = created.id,
            status = ScheduleRunStatus.DONE,
            sessionId = "session-1",
            error = null,
            at = finishedAt,
        )
        assertNotNull(finished)
        assertEquals(ScheduleRunStatus.DONE, finished!!.lastStatus)
        assertEquals("session-1", finished.lastSessionId)
        assertEquals(finishedAt, finished.updatedAt)
        assertEquals(epoch(2026, 10, 24, 8, 0), finished.nextRunAt)

        val reopened = ScheduleStore(directory, clock).list().single()
        assertEquals(ScheduleRunStatus.DONE, reopened.lastStatus)
        assertEquals("session-1", reopened.lastSessionId)
    }

    @Test
    fun onceScheduleDisablesAfterRun() = runTest {
        val store = store()
        val created = store.create(
            newSchedule(trigger = ScheduleTrigger.Once(LocalDate.of(2026, 10, 23), LocalTime.of(13, 0))),
        )
        store.markRunning(created.id, clock.millis())
        val finished = store.markFinished(
            id = created.id,
            status = ScheduleRunStatus.FAILED,
            sessionId = null,
            error = "boom",
            at = clock.millis() + 1_000,
        )
        assertNotNull(finished)
        assertFalse(finished!!.enabled)
        assertNull(finished.nextRunAt)
        assertEquals("boom", finished.lastError)
    }
}
