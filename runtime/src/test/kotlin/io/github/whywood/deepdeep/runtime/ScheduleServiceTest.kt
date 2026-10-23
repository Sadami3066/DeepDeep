package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleServiceTest {
    @Test
    fun deliversDueReminderIntoTheSameConversation() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "写周报",
                prompt = "提交本周周报",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            assertEquals(1, fixture.runtime.deliveries.size)
            val delivery = fixture.runtime.deliveries.single()
            assertEquals("session-1", delivery.sessionId)
            assertEquals("写周报", delivery.reminder)
            assertEquals(
                "[定时提醒] 写周报\n这是你设置的定时提醒，现在到时间了。\n\n提交本周周报",
                delivery.text,
            )
            assertEquals(listOf(fixture.runtimeFile("session-1")), fixture.runtime.openedFiles)

            val updated = fixture.store.list().single()
            assertEquals(fixture.clock.millis(), updated.lastDeliveredAt)
            assertNull(updated.lastError)
            assertTrue(updated.enabled)
            assertEquals(fixture.epoch(2026, 10, 25, 9, 0), updated.nextRunAt)
            assertEquals(updated, fixture.service.schedules.value.single())
            assertEquals(created.id, updated.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun batchesDueRemindersForTheSameConversation() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "甲",
                prompt = "a",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.schedule(
                name = "乙",
                prompt = "b",
                trigger = ScheduleTrigger.Daily(LocalTime.of(10, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            assertEquals(1, fixture.runtime.deliveries.size)
            val delivery = fixture.runtime.deliveries.single()
            assertEquals("2 个提醒", delivery.reminder)
            assertEquals(
                "[定时提醒] 2 个提醒\n这是你设置的定时提醒，现在到时间了。\n\n1. 甲\na\n\n2. 乙\nb",
                delivery.text,
            )
            val updated = fixture.store.list()
            assertEquals(2, updated.size)
            assertTrue(updated.all { it.lastDeliveredAt == fixture.clock.millis() })
            assertTrue(updated.all { it.nextRunAt != null })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun groupsDueRemindersByConversation() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "One",
                prompt = "first",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.schedule(
                name = "Two",
                prompt = "second",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-2",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            assertEquals(2, fixture.runtime.deliveries.size)
            assertEquals(
                setOf("session-1", "session-2"),
                fixture.runtime.deliveries.map { it.sessionId }.toSet(),
            )
            assertTrue(fixture.runtime.deliveries.none { it.text.contains("个提醒") })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun skipsDisabledSchedules() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Paused",
                prompt = "paused",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
                enabled = false,
            )
            fixture.schedule(
                name = "Active",
                prompt = "active",
                trigger = ScheduleTrigger.Daily(LocalTime.of(10, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            assertEquals(1, fixture.runtime.deliveries.size)
            assertTrue(fixture.runtime.deliveries.single().text.contains("Active"))
            val paused = fixture.store.list().first { it.name == "Paused" }
            assertFalse(paused.enabled)
            assertNull(paused.lastDeliveredAt)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun missingConversationRecordsSessionMissingAndAdvances() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val missingFile = File(fixture.directory, "gone.jsonl").absolutePath
            val created = fixture.schedule(
                name = "Missing",
                prompt = "x",
                trigger = ScheduleTrigger.Once(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0)),
                sessionId = "session-gone",
                sessionFile = missingFile,
            )
            fixture.clock.advance(Duration.ofDays(2))
            fixture.service.deliverDue()

            assertTrue(fixture.runtime.deliveries.isEmpty())
            val updated = fixture.store.list().single()
            assertEquals(ScheduleError.SESSION_MISSING, updated.lastError)
            assertFalse(updated.enabled)
            assertNull(updated.nextRunAt)
            assertEquals(created.id, updated.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun openFailureRecordsOpenFailedAndAdvances() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Open fails",
                prompt = "x",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.runtime.openFailure = IllegalStateException("boom")
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            val updated = fixture.store.list().single()
            assertEquals(ScheduleError.OPEN_FAILED, updated.lastError)
            assertEquals(fixture.epoch(2026, 10, 25, 9, 0), updated.nextRunAt)
            assertTrue(fixture.runtime.deliveries.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deliveryFailureRecordsDeliveryFailedAndAdvances() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Delivery fails",
                prompt = "x",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.runtime.deliveryFailure = IllegalStateException("boom")
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()

            val updated = fixture.store.list().single()
            assertEquals(ScheduleError.DELIVERY_FAILED, updated.lastError)
            assertEquals(fixture.epoch(2026, 10, 25, 9, 0), updated.nextRunAt)
            assertNull(updated.lastDeliveredAt)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun runNowDoesNotChangeNextRunAt() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "Manual",
                prompt = "do it",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            val result = fixture.service.runNow(created.id)
            assertTrue(result.isSuccess)
            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals(
                "[定时提醒] Manual\n这是你设置的定时提醒，现在到时间了。\n\ndo it",
                fixture.runtime.deliveries.single().text,
            )
            val updated = fixture.store.list().single()
            assertEquals(created.nextRunAt, updated.nextRunAt)
            assertTrue(updated.enabled)
            assertEquals(fixture.clock.millis(), updated.lastDeliveredAt)
            assertNull(updated.lastError)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun runNowReturnsErrorCodeWithoutTouchingNextRunAt() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "Manual",
                prompt = "do it",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-gone",
                sessionFile = File(fixture.directory, "gone.jsonl").absolutePath,
            )
            val result = fixture.service.runNow(created.id)
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull() as ScheduleDeliveryException
            assertEquals(ScheduleError.SESSION_MISSING, exception.error)
            val updated = fixture.store.list().single()
            assertEquals(created.nextRunAt, updated.nextRunAt)
            assertNull(updated.lastDeliveredAt)
            assertEquals(ScheduleError.SESSION_MISSING, updated.lastError)
            assertTrue(fixture.runtime.deliveries.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unknownRunNowIdFails() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            assertTrue(fixture.service.runNow("missing").isFailure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sessionRemovedDeletesBoundSchedules() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Keep",
                prompt = "keep",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-2",
            )
            fixture.schedule(
                name = "Remove",
                prompt = "remove",
                trigger = ScheduleTrigger.Daily(LocalTime.of(10, 0)),
                sessionId = "session-1",
            )
            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionRemoved()),
            )
            assertEquals(listOf("Keep"), fixture.store.list().map { it.name })
            assertEquals(listOf("Keep"), fixture.service.schedules.value.map { it.name })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sessionArchivedDisablesBoundSchedules() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "Archive me",
                prompt = "x",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionArchived(archived = true)),
            )
            val archived = fixture.store.list().single()
            assertFalse(archived.enabled)
            assertNull(archived.nextRunAt)

            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionArchived(archived = false)),
            )
            val unarchived = fixture.store.list().single()
            assertFalse(unarchived.enabled)
            assertNull(unarchived.nextRunAt)
            assertEquals(created.id, unarchived.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun createUpdateDeletePublishSchedules() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.service.create(
                NewSchedule(
                    name = "Draft",
                    prompt = "draft",
                    trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                    sessionId = "session-1",
                    sessionFile = fixture.fileFor("session-1"),
                    cwd = fixture.directory.absolutePath,
                ),
            )
            assertEquals(created, fixture.service.schedules.value.single())
            val updated = fixture.service.update(created.id, SchedulePatch(name = "Renamed"))
            assertEquals("Renamed", updated?.name)
            assertEquals("Renamed", fixture.service.list().single().name)
            assertTrue(fixture.service.delete(created.id))
            assertTrue(fixture.service.schedules.value.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun startDeliversCatchUpAndPublishesSchedules() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Catch up",
                prompt = "late",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.start()
            runCurrent()

            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals("Catch up", fixture.runtime.deliveries.single().reminder)
            assertNotNull(fixture.service.schedules.value.single().lastDeliveredAt)
        } finally {
            fixture.close()
        }
    }

    private class Fixture(scope: CoroutineScope) {
        val zone: ZoneId = ZoneId.of("Asia/Shanghai")
        val clock = MutableTestClock(Instant.parse("2026-10-23T04:00:00Z"), zone)
        val directory: File = Files.createTempDirectory("deepdeep-schedule-service").toFile()
        val runtime = FakeAgentRuntime()
        val store = ScheduleStore(directory, clock)
        val service = ScheduleService(runtime, store, scope, clock)

        fun fileFor(name: String): String =
            File(directory, "$name.jsonl").apply { writeText("{}") }.absolutePath

        fun runtimeFile(name: String): String = fileFor(name)

        fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
            ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

        suspend fun schedule(
            name: String,
            prompt: String,
            trigger: ScheduleTrigger,
            sessionId: String,
            sessionFile: String? = null,
            enabled: Boolean = true,
        ): Schedule = store.create(
            NewSchedule(
                name = name,
                prompt = prompt,
                trigger = trigger,
                enabled = enabled,
                sessionId = sessionId,
                sessionFile = sessionFile ?: fileFor(sessionId),
                cwd = directory.absolutePath,
            ),
        )

        fun close() {
            service.stop()
            directory.deleteRecursively()
        }
    }

    private class Delivery(
        val sessionId: String,
        val text: String,
        val reminder: String?,
    )

    private class FakeAgentRuntime : AgentRuntime {
        override val events = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 16)
        override val busySessionIds = MutableStateFlow<Set<String>>(emptySet())

        val deliveries = mutableListOf<Delivery>()
        val openedFiles = mutableListOf<String>()
        var openFailure: Exception? = null
        var deliveryFailure: Exception? = null

        override suspend fun start() = Unit

        override suspend fun ensureStarted() = Unit

        override suspend fun workspaces(): List<RuntimeWorkspace> = emptyList()

        override suspend fun createWorkspace(name: String): RuntimeWorkspace = error("unused")

        override suspend fun renameWorkspace(id: String, name: String) = Unit

        override suspend fun deleteWorkspace(id: String) = Unit

        override suspend fun allSessions(): List<ConversationSummary> = emptyList()

        override suspend fun workspaceSessionCount(workspaceId: String): Int = 0

        override suspend fun newSession(workspaceId: String): OpenedSession = error("unused")

        override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
            openFailure?.let { throw it }
            openedFiles += filePath
            val sessionId = filePath.substringAfterLast('/').substringBeforeLast('.')
            return OpenedSession(sessionId = sessionId, filePath = filePath, cwd = cwd)
        }

        override suspend fun closeSession(sessionId: String) = Unit

        override suspend fun reloadSession(sessionId: String): String = error("unused")

        override suspend fun send(sessionId: String, text: String) = Unit

        override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
            deliveryFailure?.let { throw it }
            deliveries += Delivery(sessionId, text, reminder)
        }

        override suspend fun activeRunId(sessionId: String): String? = null

        override fun stop(sessionId: String) = Unit

        override suspend fun history(sessionId: String): List<AssistantMessage> = emptyList()

        override suspend fun sessionTitle(sessionId: String): String? = null

        override suspend fun sessions(workspaceId: String): List<ConversationSummary> = emptyList()

        override suspend fun archivedSessions(): List<ConversationSummary> = emptyList()

        override suspend fun renameSession(id: String, title: String) = Unit

        override suspend fun setSessionPinned(id: String, pinned: Boolean) = Unit

        override suspend fun setSessionArchived(id: String, archived: Boolean) = Unit

        override suspend fun deleteSession(id: String) = Unit

        override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) = Unit

        override suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) = Unit

        override suspend fun currentConfig(): ModelConfig = ModelConfig()
    }
}
