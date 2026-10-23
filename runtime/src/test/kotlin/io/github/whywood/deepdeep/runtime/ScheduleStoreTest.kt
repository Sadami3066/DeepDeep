package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class MutableTestClock(
    private var current: Instant,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleStoreTest {
    private lateinit var directory: File
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableTestClock(Instant.parse("2026-10-23T04:00:00Z"), zone)
    private val sessionFile = "/tmp/session-1.jsonl"

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
        enabled: Boolean = true,
        sessionId: String = "session-1",
        sessionFile: String = this.sessionFile,
        cwd: String = "/tmp",
    ): NewSchedule = NewSchedule(
        name = name,
        prompt = "Summarize my day",
        trigger = trigger,
        enabled = enabled,
        sessionId = sessionId,
        sessionFile = sessionFile,
        cwd = cwd,
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
        assertEquals("Summarize my day", created.prompt)
        assertEquals(ScheduleTrigger.Daily(LocalTime.of(8, 0)), created.trigger)
        assertEquals("session-1", created.sessionId)
        assertEquals(sessionFile, created.sessionFile)
        assertEquals("/tmp", created.cwd)
        assertEquals(epoch(2026, 10, 24, 8, 0), created.nextRunAt)
        assertEquals(clock.millis(), created.createdAt)
        assertEquals(clock.millis(), created.updatedAt)

        val file = File(directory, "schedules.json")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("\"schemaVersion\":2"))
        assertTrue(file.readText().contains("\"sessionId\":\"session-1\""))
        assertFalse(file.readText().contains("workspaceId"))
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
    fun disabledOnceScheduleMayBeInThePastAndHasNoNextRun() = runTest {
        val store = store()
        val past = ScheduleTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val created = store.create(newSchedule(trigger = past, enabled = false))
        assertFalse(created.enabled)
        assertNull(created.nextRunAt)
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
        assertEquals("session-1", updated?.sessionId)
        assertEquals(false, updated?.enabled)
        assertEquals(
            ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 30)),
            updated?.trigger,
        )
        assertNull(updated?.nextRunAt)
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
    fun updateReenablesAndRecomputesNextRunAt() = runTest {
        val store = store()
        val created = store.create(newSchedule(enabled = false))
        assertNull(created.nextRunAt)
        val updated = store.update(created.id, SchedulePatch(enabled = true))
        assertNotNull(updated?.nextRunAt)
        assertEquals(epoch(2026, 10, 24, 8, 0), updated?.nextRunAt)
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
    fun changeFlowEmitsAfterPersist() = runTest {
        val store = store()
        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.changes.collect { emissions++ }
        }
        runCurrent()
        store.create(newSchedule())
        runCurrent()
        assertEquals(1, emissions)
        store.delete(store.list().single().id)
        runCurrent()
        assertEquals(2, emissions)
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
    fun markDeliveredAdvancesRecurringSchedule() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val at = epoch(2026, 10, 24, 8, 0)
        val delivered = store.markDelivered(created.id, at)
        assertNotNull(delivered)
        assertEquals(at, delivered!!.lastDeliveredAt)
        assertNull(delivered.lastError)
        assertTrue(delivered.enabled)
        assertEquals(epoch(2026, 10, 25, 8, 0), delivered.nextRunAt)
        assertEquals(at, delivered.updatedAt)
    }

    @Test
    fun markDeliveredDisablesOnceSchedule() = runTest {
        val store = store()
        val created = store.create(
            newSchedule(trigger = ScheduleTrigger.Once(LocalDate.of(2026, 10, 23), LocalTime.of(13, 0))),
        )
        val delivered = store.markDelivered(created.id, epoch(2026, 10, 23, 13, 0))
        assertNotNull(delivered)
        assertFalse(delivered!!.enabled)
        assertNull(delivered.nextRunAt)
        assertEquals(epoch(2026, 10, 23, 13, 0), delivered.lastDeliveredAt)
    }

    @Test
    fun markDeliveryFailedKeepsLastDeliveredAndAdvances() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val at = epoch(2026, 10, 24, 8, 0)
        val failed = store.markDeliveryFailed(created.id, ScheduleError.SESSION_MISSING, at)
        assertNotNull(failed)
        assertEquals(ScheduleError.SESSION_MISSING, failed!!.lastError)
        assertNull(failed.lastDeliveredAt)
        assertTrue(failed.enabled)
        assertEquals(epoch(2026, 10, 25, 8, 0), failed.nextRunAt)
    }

    @Test
    fun markRunNowKeepsNextRunAt() = runTest {
        val store = store()
        val created = store.create(newSchedule())
        val at = clock.millis()
        val delivered = store.markRunNow(created.id, at)
        assertEquals(created.nextRunAt, delivered?.nextRunAt)
        assertEquals(created.enabled, delivered?.enabled)
        assertEquals(at, delivered?.lastDeliveredAt)
        assertNull(delivered?.lastError)

        val failed = store.markRunNow(created.id, at + 1, ScheduleError.OPEN_FAILED)
        assertEquals(created.nextRunAt, failed?.nextRunAt)
        assertEquals(at, failed?.lastDeliveredAt)
        assertEquals(ScheduleError.OPEN_FAILED, failed?.lastError)
    }

    @Test
    fun deleteBySessionRemovesOnlyThatConversation() = runTest {
        val store = store()
        val first = store.create(newSchedule(sessionId = "session-1"))
        val second = store.create(newSchedule(name = "Other", sessionId = "session-2"))

        assertTrue(store.deleteBySession("session-1"))
        assertEquals(listOf(second), store.list())
        assertFalse(store.deleteBySession("session-1"))
        assertFalse(store.list().any { it.id == first.id })
    }

    @Test
    fun disableBySessionSkipsOtherConversations() = runTest {
        val store = store()
        val first = store.create(newSchedule(sessionId = "session-1"))
        val second = store.create(newSchedule(name = "Other", sessionId = "session-2"))

        assertTrue(store.disableBySession("session-1"))
        val updated = store.list()
        val disabled = updated.first { it.id == first.id }
        assertFalse(disabled.enabled)
        assertNull(disabled.nextRunAt)
        assertTrue(updated.first { it.id == second.id }.enabled)
        assertFalse(store.disableBySession("session-1"))
    }

    @Test
    fun recomputeTargetsRecomputesEnabledSchedules() = runTest {
        val store = store()
        val daily = store.create(newSchedule())
        val disabled = store.create(newSchedule(name = "Paused", enabled = false))
        val missedOnce = store.create(
            newSchedule(
                name = "Missed once",
                trigger = ScheduleTrigger.Once(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0)),
            ),
        )
        assertNull(disabled.nextRunAt)

        clock.advance(Duration.ofDays(10))
        store.recomputeTargets()

        val updated = store.list()
        val updatedDaily = updated.first { it.id == daily.id }
        assertEquals(epoch(2026, 11, 3, 8, 0), updatedDaily.nextRunAt)
        assertTrue(updatedDaily.enabled)
        val updatedDisabled = updated.first { it.id == disabled.id }
        assertNull(updatedDisabled.nextRunAt)
        assertFalse(updatedDisabled.enabled)
        val updatedOnce = updated.first { it.id == missedOnce.id }
        assertFalse(updatedOnce.enabled)
        assertNull(updatedOnce.nextRunAt)
    }

    @Test
    fun legacySchemaIsDiscardedAndRewrittenAsEmptyV2() = runTest {
        val file = File(directory, "schedules.json")
        file.writeText(
            "{\"schemaVersion\":1,\"schedules\":[{\"id\":\"old\",\"name\":\"Old\",\"workspaceId\":\"home\",\"prompt\":\"x\",\"trigger\":{\"type\":\"daily\",\"time\":\"08:00\"},\"enabled\":true,\"notify\":true,\"nextRunAt\":null,\"lastRunAt\":null,\"lastStatus\":null,\"lastSessionId\":null,\"lastError\":null,\"createdAt\":1,\"updatedAt\":2}]}",
        )
        val store = store()
        assertTrue(store.list().isEmpty())
        val migrated = file.readText()
        assertTrue(migrated.contains("\"schemaVersion\":2"))
        assertTrue(migrated.contains("\"schedules\":[]"))
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
        val versionThree = "{\"schemaVersion\":3,\"schedules\":[]}"
        file.writeText(versionThree)
        val error = runCatching { store().list() }.exceptionOrNull()
        assertTrue(error is CorruptScheduleDataException)
        assertEquals(versionThree, file.readText())
    }

    @Test
    fun corruptEntryThrows() = runTest {
        val file = File(directory, "schedules.json")
        val corruptEntry = "{\"schemaVersion\":2,\"schedules\":[{\"id\":\"x\"}]}"
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
    fun createRejectsInvalidCronExpression() = runTest {
        val failure = runCatching {
            store().create(newSchedule(trigger = ScheduleTrigger.Cron("*/0 * * * *")))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun createStoresNextRunForIntervalAndMonthly() = runTest {
        val interval = store().create(
            newSchedule(trigger = ScheduleTrigger.Interval(2, IntervalUnit.HOURS)),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 14, 0, 0, 0, zone).toInstant().toEpochMilli(),
            interval.nextRunAt,
        )
        val monthly = store().create(
            newSchedule(trigger = ScheduleTrigger.Monthly(31, LocalTime.of(9, 0))),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            monthly.nextRunAt,
        )
    }
}
