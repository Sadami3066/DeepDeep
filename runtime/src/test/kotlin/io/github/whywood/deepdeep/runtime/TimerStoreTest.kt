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
class TimerStoreTest {
    private lateinit var directory: File
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableTestClock(Instant.parse("2026-10-23T04:00:00Z"), zone)
    private val sessionFile = "/tmp/session-1.jsonl"

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-timers").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(): TimerStore = TimerStore(directory, clock) { zone }

    private fun newTimer(
        name: String = "Morning brief",
        trigger: TimerTrigger = TimerTrigger.Daily(LocalTime.of(8, 0)),
        enabled: Boolean = true,
        sessionId: String = "session-1",
        sessionFile: String = this.sessionFile,
        cwd: String = "/tmp",
    ): NewTimer = NewTimer(
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

        val created = store.create(newTimer())
        assertTrue(created.id.isNotBlank())
        assertEquals("Morning brief", created.name)
        assertEquals("Summarize my day", created.prompt)
        assertEquals(TimerTrigger.Daily(LocalTime.of(8, 0)), created.trigger)
        assertEquals("session-1", created.sessionId)
        assertEquals(sessionFile, created.sessionFile)
        assertEquals("/tmp", created.cwd)
        assertEquals(epoch(2026, 10, 24, 8, 0), created.nextRunAt)
        assertEquals(clock.millis(), created.createdAt)
        assertEquals(clock.millis(), created.updatedAt)

        val file = File(directory, "timers.json")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("\"schemaVersion\":3"))
        assertTrue(file.readText().contains("\"sessionId\":\"session-1\""))
        assertFalse(file.readText().contains("workspaceId"))
        assertFalse(File(directory, "timers.json.tmp").exists())

        val reopened = TimerStore(directory, clock) { zone }.list()
        assertEquals(listOf(created), reopened)
    }

    @Test
    fun weeklyDaysArePersistedAsIsoNumbers() = runTest {
        val store = store()
        store.create(
            newTimer(
                trigger = TimerTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(18, 0)),
            ),
        )
        val text = File(directory, "timers.json").readText()
        assertTrue(text.contains("[1,5]"))
    }

    @Test
    fun onceTimerMustBeInTheFuture() = runTest {
        val store = store()
        val past = TimerTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val error = runCatching { store.create(newTimer(trigger = past)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(store.list().isEmpty())
        assertFalse(File(directory, "timers.json").exists())
    }

    @Test
    fun disabledOnceTimerMayBeInThePastAndHasNoNextRun() = runTest {
        val store = store()
        val past = TimerTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val created = store.create(newTimer(trigger = past, enabled = false))
        assertFalse(created.enabled)
        assertNull(created.nextRunAt)
    }

    @Test
    fun futureOnceTimerStoresNextRunAt() = runTest {
        val store = store()
        val created = store.create(
            newTimer(
                trigger = TimerTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(9, 30)),
            ),
        )
        assertEquals(epoch(2026, 12, 1, 9, 30), created.nextRunAt)
    }

    @Test
    fun updateChangesOnlyProvidedFields() = runTest {
        val store = store()
        val created = store.create(newTimer())

        val updated = store.update(
            created.id,
            TimerPatch(
                name = "Evening brief",
                trigger = TimerTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 30)),
                enabled = false,
            ),
        )
        assertEquals("Evening brief", updated?.name)
        assertEquals(created.prompt, updated?.prompt)
        assertEquals("session-1", updated?.sessionId)
        assertEquals(false, updated?.enabled)
        assertEquals(
            TimerTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 30)),
            updated?.trigger,
        )
        assertNull(updated?.nextRunAt)
        assertEquals(created.createdAt, updated?.createdAt)
        assertEquals(listOf(updated), store.list())
    }

    @Test
    fun updateWithoutTriggerOrEnabledKeepsNextRunAt() = runTest {
        val store = store()
        val created = store.create(newTimer())
        val updated = store.update(created.id, TimerPatch(name = "Renamed"))
        assertEquals(created.nextRunAt, updated?.nextRunAt)
        assertEquals("Renamed", updated?.name)
    }

    @Test
    fun updateReenablesAndRecomputesNextRunAt() = runTest {
        val store = store()
        val created = store.create(newTimer(enabled = false))
        assertNull(created.nextRunAt)
        val updated = store.update(created.id, TimerPatch(enabled = true))
        assertNotNull(updated?.nextRunAt)
        assertEquals(epoch(2026, 10, 24, 8, 0), updated?.nextRunAt)
    }

    @Test
    fun updateRejectsPastOnceTrigger() = runTest {
        val store = store()
        val created = store.create(newTimer())
        val past = TimerTrigger.Once(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        val error = runCatching {
            store.update(created.id, TimerPatch(trigger = past))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(created, store.list().single())
    }

    @Test
    fun deletingRemovesTheTimer() = runTest {
        val store = store()
        val first = store.create(newTimer(name = "First"))
        val second = store.create(newTimer(name = "Second"))

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
        store.create(newTimer())
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
            newTimer(
                name = "One shot",
                trigger = TimerTrigger.Once(LocalDate.of(2026, 11, 1), LocalTime.of(9, 15)),
            ),
        )
        val weekly = store.create(
            newTimer(
                name = "Weekly",
                trigger = TimerTrigger.Weekly(setOf(DayOfWeek.SUNDAY), LocalTime.of(21, 0)),
            ),
        )
        val reopened = TimerStore(directory, clock) { zone }.list()
        assertEquals(listOf(once, weekly), reopened)
        assertNotNull(reopened[0].nextRunAt)
        assertNotNull(reopened[1].nextRunAt)
        assertNull(store.update("missing", TimerPatch(name = "x")))
    }

    @Test
    fun markDeliveredAdvancesRecurringTimer() = runTest {
        val store = store()
        val created = store.create(newTimer())
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
    fun markDeliveredDisablesOnceTimer() = runTest {
        val store = store()
        val created = store.create(
            newTimer(trigger = TimerTrigger.Once(LocalDate.of(2026, 10, 23), LocalTime.of(13, 0))),
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
        val created = store.create(newTimer())
        val at = epoch(2026, 10, 24, 8, 0)
        val failed = store.markDeliveryFailed(created.id, TimerError.SESSION_MISSING, at)
        assertNotNull(failed)
        assertEquals(TimerError.SESSION_MISSING, failed!!.lastError)
        assertNull(failed.lastDeliveredAt)
        assertTrue(failed.enabled)
        assertEquals(epoch(2026, 10, 25, 8, 0), failed.nextRunAt)
    }

    @Test
    fun markRunNowKeepsNextRunAt() = runTest {
        val store = store()
        val created = store.create(newTimer())
        val at = clock.millis()
        val delivered = store.markRunNow(created.id, at)
        assertEquals(created.nextRunAt, delivered?.nextRunAt)
        assertEquals(created.enabled, delivered?.enabled)
        assertEquals(at, delivered?.lastDeliveredAt)
        assertNull(delivered?.lastError)

        val failed = store.markRunNow(created.id, at + 1, TimerError.OPEN_FAILED)
        assertEquals(created.nextRunAt, failed?.nextRunAt)
        assertEquals(at, failed?.lastDeliveredAt)
        assertEquals(TimerError.OPEN_FAILED, failed?.lastError)
    }

    @Test
    fun deleteBySessionRemovesOnlyThatConversation() = runTest {
        val store = store()
        val first = store.create(newTimer(sessionId = "session-1"))
        val second = store.create(newTimer(name = "Other", sessionId = "session-2"))

        assertTrue(store.deleteBySession("session-1"))
        assertEquals(listOf(second), store.list())
        assertFalse(store.deleteBySession("session-1"))
        assertFalse(store.list().any { it.id == first.id })
    }

    @Test
    fun disableBySessionSkipsOtherConversations() = runTest {
        val store = store()
        val first = store.create(newTimer(sessionId = "session-1"))
        val second = store.create(newTimer(name = "Other", sessionId = "session-2"))

        assertTrue(store.disableBySession("session-1"))
        val updated = store.list()
        val disabled = updated.first { it.id == first.id }
        assertFalse(disabled.enabled)
        assertNull(disabled.nextRunAt)
        assertTrue(updated.first { it.id == second.id }.enabled)
        assertFalse(store.disableBySession("session-1"))
    }

    @Test
    fun recomputeTargetsRecomputesEnabledTimers() = runTest {
        val store = store()
        val daily = store.create(newTimer())
        val disabled = store.create(newTimer(name = "Paused", enabled = false))
        val missedOnce = store.create(
            newTimer(
                name = "Missed once",
                trigger = TimerTrigger.Once(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0)),
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
        assertTrue(updatedOnce.enabled)
        assertEquals(epoch(2026, 10, 24, 9, 0), updatedOnce.nextRunAt)
        assertNull(updatedOnce.lastDeliveredAt)
    }

    @Test
    fun recomputeDoesNotReviveDeliveredOnce() = runTest {
        val store = store()
        val created = store.create(
            newTimer(trigger = TimerTrigger.Once(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0))),
        )
        val deliveredAt = epoch(2026, 10, 24, 9, 0)
        store.markDelivered(created.id, deliveredAt)
        clock.advance(Duration.ofDays(5))

        store.recomputeTargets()

        val updated = store.list().single()
        assertFalse(updated.enabled)
        assertNull(updated.nextRunAt)
        assertEquals(deliveredAt, updated.lastDeliveredAt)
    }

    @Test
    fun recomputeUsesZoneProviderForFutureTargets() = runTest {
        var currentZone = zone
        val store = TimerStore(directory, clock) { currentZone }
        val created = store.create(newTimer())
        assertEquals(epoch(2026, 10, 24, 8, 0), created.nextRunAt)

        currentZone = ZoneId.of("America/New_York")
        store.recomputeTargets()

        val updated = store.list().single()
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 8, 0, 0, 0, currentZone).toInstant().toEpochMilli(),
            updated.nextRunAt,
        )
        assertTrue(updated.enabled)
    }

    @Test
    fun legacySchemaIsBackedUpAndRewrittenAsEmptyCurrent() = runTest {
        val file = File(directory, "timers.json")
        val legacy =
            "{\"schemaVersion\":1,\"timers\":[{\"id\":\"old\",\"name\":\"Old\",\"workspaceId\":\"home\",\"prompt\":\"x\",\"trigger\":{\"type\":\"daily\",\"time\":\"08:00\"},\"enabled\":true,\"notify\":true,\"nextRunAt\":null,\"lastRunAt\":null,\"lastStatus\":null,\"lastSessionId\":null,\"lastError\":null,\"createdAt\":1,\"updatedAt\":2}]}"
        file.writeText(legacy)
        val store = store()
        assertTrue(store.list().isEmpty())
        assertEquals(legacy, File(directory, "timers.v1.bak").readText())
        val migrated = file.readText()
        assertTrue(migrated.contains("\"schemaVersion\":3"))
        assertTrue(migrated.contains("\"timers\":[]"))
    }

    @Test
    fun corruptFileIsBackedUpAndReplacedWithEmpty() = runTest {
        val file = File(directory, "timers.json")
        val corrupt = "{ this is not json"
        file.writeText(corrupt)
        val store = store()

        assertTrue(store.list().isEmpty())
        assertEquals(corrupt, File(directory, "timers.json.corrupt").readText())
        assertTrue(file.readText().contains("\"schemaVersion\":3"))
        assertTrue(file.readText().contains("\"timers\":[]"))

        val created = store.create(newTimer())
        assertEquals(listOf(created), store.list())
        assertEquals(corrupt, File(directory, "timers.json.corrupt").readText())
    }

    @Test
    fun corruptBackupIsNotOverwritten() = runTest {
        val file = File(directory, "timers.json")
        val backup = File(directory, "timers.json.corrupt")
        file.writeText("{ first")
        store().list()
        file.writeText("{ second")

        assertTrue(store().list().isEmpty())
        assertEquals("{ first", backup.readText())
    }

    @Test
    fun olderUnsupportedSchemaVersionIsBackedUpAndReplaced() = runTest {
        val file = File(directory, "timers.json")
        val versionZero = "{\"schemaVersion\":0,\"timers\":[]}"
        file.writeText(versionZero)

        assertTrue(store().list().isEmpty())
        assertEquals(versionZero, File(directory, "timers.json.corrupt").readText())
        assertTrue(file.readText().contains("\"schemaVersion\":3"))
    }

    @Test
    fun missingSchemaVersionIsBackedUpAndReplaced() = runTest {
        val file = File(directory, "timers.json")
        val noVersion = "{\"timers\":[]}"
        file.writeText(noVersion)

        assertTrue(store().list().isEmpty())
        assertEquals(noVersion, File(directory, "timers.json.corrupt").readText())
    }

    @Test
    fun secondCorruptionKeepsPreviousBackupAndAddsNewOne() = runTest {
        val file = File(directory, "timers.json")
        file.writeText("{ first")
        store().list()
        file.writeText("{ second")

        assertTrue(store().list().isEmpty())
        assertEquals("{ first", File(directory, "timers.json.corrupt").readText())
        assertEquals("{ second", File(directory, "timers.json.corrupt-1").readText())
    }

    @Test
    fun mixedCorruptEntriesKeepValidTimers() = runTest {
        val store = store()
        val created = store.create(newTimer())
        val file = File(directory, "timers.json")
        val corrupted = file.readText().replaceFirst("[", "[{\"id\":\"broken\"},")
        file.writeText(corrupted)

        val loaded = store.list()

        assertEquals(listOf(created), loaded)
        assertEquals(corrupted, File(directory, "timers.json.corrupt").readText())
        assertFalse(file.readText().contains("broken"))
    }

    @Test
    fun futureSchemaIsKeptReadOnlyAndBlocksWrites() = runTest {
        val file = File(directory, "timers.json")
        val future = "{\"schemaVersion\":4,\"timers\":[]}"
        file.writeText(future)
        val store = store()

        assertTrue(store.list().isEmpty())
        assertEquals(future, file.readText())
        assertFalse(File(directory, "timers.json.corrupt").exists())

        val failure = runCatching { store.create(newTimer()) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(future, file.readText())
    }

    @Test
    fun corruptEntryIsBackedUpAndReplaced() = runTest {
        val file = File(directory, "timers.json")
        val corruptEntry = "{\"schemaVersion\":3,\"timers\":[{\"id\":\"x\"}]}"
        file.writeText(corruptEntry)

        assertTrue(store().list().isEmpty())
        assertEquals(corruptEntry, File(directory, "timers.json.corrupt").readText())
        assertTrue(file.readText().contains("\"timers\":[]"))
    }

    @Test
    fun blankFileIsTreatedAsEmpty() = runTest {
        File(directory, "timers.json").writeText("  \n")
        assertTrue(store().list().isEmpty())
    }

    @Test
    fun createRejectsInvalidCronExpression() = runTest {
        val failure = runCatching {
            store().create(newTimer(trigger = TimerTrigger.Cron("*/0 * * * *")))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun previousSchemaFormatIsUpgradedInPlace() = runTest {
        val file = File(directory, "timers.json")
        file.writeText(legacySchemaJson())
        val store = store()

        val loaded = store.list()

        assertEquals(listOf("legacy-1"), loaded.map { it.id })
        val migrated = file.readText()
        assertTrue(migrated.contains("\"schemaVersion\":3"))
        assertTrue(migrated.contains("\"timers\":"))
        assertFalse(migrated.contains("\"schedules\":"))
    }

    @Test
    fun legacyDirectoryMigrationCopiesTimersAndBackups() = runTest {
        val root = Files.createTempDirectory("deepdeep-timer-migration").toFile()
        try {
            val legacyDirectory = File(root, "schedules")
            assertTrue(legacyDirectory.mkdirs())
            File(legacyDirectory, "schedules.json").writeText(legacySchemaJson())
            File(legacyDirectory, "schedules.json.corrupt").writeText("{broken")
            File(legacyDirectory, "delivery-intents.json").writeText("{}")

            TimerStore.prepareDataDirectory(root)

            val migratedFile = File(root, "timers/timers.json")
            assertTrue(migratedFile.isFile)
            assertEquals(legacySchemaJson(), migratedFile.readText())
            assertTrue(File(root, "timers/timers.json.corrupt").isFile)
            assertTrue(File(root, "timers/delivery-intents.json").isFile)
            assertTrue(File(legacyDirectory, "schedules.json").isFile)

            val migrated = TimerStore(File(root, "timers"), clock) { zone }
            assertEquals(listOf("legacy-1"), migrated.list().map { it.id })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyBackupsDoNotBecomeLiveDataAfterMigration() = runTest {
        val root = Files.createTempDirectory("deepdeep-timer-migration").toFile()
        try {
            val legacyDirectory = File(root, "schedules")
            assertTrue(legacyDirectory.mkdirs())
            File(legacyDirectory, "schedules.v1.bak").writeText("{old}")
            File(legacyDirectory, "schedules.json.corrupt").writeText("{broken")

            TimerStore.prepareDataDirectory(root)

            assertTrue(File(root, "timers/timers.v1.bak").isFile)
            assertTrue(File(root, "timers/timers.json.corrupt").isFile)
            assertFalse(File(root, "timers/timers.json").exists())
            assertTrue(TimerStore(File(root, "timers"), clock) { zone }.list().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrationDoesNotOverwriteExistingTimers() = runTest {
        val root = Files.createTempDirectory("deepdeep-timer-migration").toFile()
        try {
            val legacyDirectory = File(root, "schedules")
            assertTrue(legacyDirectory.mkdirs())
            File(legacyDirectory, "schedules.json").writeText(legacySchemaJson())
            val currentDirectory = File(root, "timers")
            assertTrue(currentDirectory.mkdirs())
            val current = TimerStore(currentDirectory, clock) { zone }.create(newTimer(name = "Current"))

            TimerStore.prepareDataDirectory(root)

            val reopened = TimerStore(currentDirectory, clock) { zone }.list()
            assertEquals(listOf(current), reopened)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun legacySchemaJson(): String =
        "{\"schemaVersion\":2,\"schedules\":[{\"id\":\"legacy-1\",\"name\":\"Legacy\",\"prompt\":\"hello\"," +
            "\"trigger\":{\"type\":\"daily\",\"time\":\"08:00\"},\"enabled\":true,\"sessionId\":\"session-1\"," +
            "\"sessionFile\":\"/tmp/session-1.jsonl\",\"cwd\":\"/tmp\",\"nextRunAt\":null," +
            "\"lastDeliveredAt\":null,\"lastError\":null,\"createdAt\":1,\"updatedAt\":2}]}"
}
