package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.runtime.testing.FakeAgentRuntime
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
        val fixture = Fixture(backgroundScope, StandardTestDispatcher(testScheduler))
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
            fixture.service.start()
            runCurrent()
            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionRemoved()),
            )
            runCurrent()
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
    fun textDeltasDoNotReadSchedules() = runTest {
        val fixture = Fixture(backgroundScope, StandardTestDispatcher(testScheduler))
        try {
            fixture.schedule(
                name = "Keep",
                prompt = "keep",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.service.start()
            runCurrent()
            assertEquals(1, fixture.runtime.events.subscriptionCount.value)
            val baseline = fixture.store.loadCount.get()
            assertTrue(baseline > 0)

            emitRuntimeEvents(fixture.runtime.events, count = 10) { index ->
                SessionRuntimeEvent("session-1", RuntimeEvent.TextDelta(runId = "run-1", text = "token $index"))
            }
            assertEquals(baseline, fixture.store.loadCount.get())

            emitRuntimeEvents(fixture.runtime.events, count = 50) { index ->
                SessionRuntimeEvent("session-1", RuntimeEvent.TextDelta(runId = "run-1", text = "after $index"))
            }
            assertEquals(baseline, fixture.store.loadCount.get())

            fixture.runtime.events.tryEmit(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionRemoved()),
            )
            runCurrent()
            assertTrue(fixture.store.loadCount.get() > baseline)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun nonLifecycleRuntimeEventsDoNotReadSchedules() = runTest {
        val fixture = Fixture(backgroundScope, StandardTestDispatcher(testScheduler))
        try {
            fixture.schedule(
                name = "Keep",
                prompt = "keep",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.service.start()
            runCurrent()
            val baseline = fixture.store.loadCount.get()

            listOf<RuntimeEvent>(
                RuntimeEvent.RunStarted(runId = "run-1", startedAt = 0),
                RuntimeEvent.ActivityChanged(runId = "run-1", activity = null),
                RuntimeEvent.TextDelta(runId = "run-1", text = "a"),
                RuntimeEvent.ReasoningDelta(runId = "run-1", text = "b"),
                RuntimeEvent.ToolStarted(
                    runId = "run-1",
                    callId = "call-1",
                    name = "tool",
                    arguments = emptyMap(),
                    startedAt = 0,
                ),
                RuntimeEvent.ToolFinished(
                    runId = "run-1",
                    callId = "call-1",
                    name = "tool",
                    result = "{}",
                    isError = false,
                    durationMs = 1,
                ),
                RuntimeEvent.RunFinished(runId = "run-1", text = "done", status = RunStatus.DONE, finishedAt = 1),
                RuntimeEvent.RunFailed(runId = "run-1", message = "boom"),
                RuntimeEvent.SessionListChanged(),
            ).forEach { event ->
                assertTrue(fixture.runtime.events.tryEmit(SessionRuntimeEvent("session-1", event)))
                runCurrent()
            }
            assertEquals(baseline, fixture.store.loadCount.get())

            fixture.runtime.events.tryEmit(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionRemoved()),
            )
            runCurrent()
            assertTrue(fixture.store.loadCount.get() > baseline)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sessionLifecycleEventsReadSchedules() = runTest {
        val fixture = Fixture(backgroundScope, StandardTestDispatcher(testScheduler))
        try {
            fixture.schedule(
                name = "Keep",
                prompt = "keep",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            val before = fixture.store.loadCount.get()

            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.TextDelta(runId = "run-1", text = "token")),
            )
            assertEquals(before, fixture.store.loadCount.get())

            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionArchived(archived = false)),
            )
            assertEquals(before, fixture.store.loadCount.get())

            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionArchived(archived = true)),
            )
            assertEquals(before + 1, fixture.store.loadCount.get())

            fixture.service.handleRuntimeEvent(
                SessionRuntimeEvent("session-1", RuntimeEvent.SessionRemoved()),
            )
            assertEquals(before + 2, fixture.store.loadCount.get())
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
            awaitStoreIo {
                fixture.runtime.deliveries.size == 1 &&
                    fixture.service.schedules.value.singleOrNull()?.lastDeliveredAt != null
            }

            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals("Catch up", fixture.runtime.deliveries.single().reminder)
            assertNotNull(fixture.service.schedules.value.single().lastDeliveredAt)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun startSurvivesCorruptScheduleData() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val file = File(fixture.directory, "schedules.json")
            file.writeText("{not json")
            fixture.service.start()
            awaitStoreIo {
                File(fixture.directory, "schedules.json.corrupt").isFile &&
                    file.readText().contains("\"schemaVersion\":2")
            }
            fixture.service.stop()

            assertTrue(fixture.runtime.deliveries.isEmpty())
            assertTrue(fixture.service.schedules.value.isEmpty())
            assertEquals("{not json", File(fixture.directory, "schedules.json.corrupt").readText())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoversInterruptedDeliveryExactlyOnce() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "Recover",
                prompt = "resume",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.writeIntent(created, fixture.clock.millis())

            val restarted = fixture.restarted()
            try {
                restarted.start()
                awaitStoreIo {
                    fixture.runtime.deliveries.size == 1 &&
                        fixture.pendingIntentCount() == 0 &&
                        restarted.schedules.value.singleOrNull()?.lastDeliveredAt != null
                }
            } finally {
                restarted.stop()
            }

            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals("Recover", fixture.runtime.deliveries.single().reminder)
            assertEquals(fixture.clock.millis(), fixture.store.list().single().lastDeliveredAt)
            assertEquals(0, fixture.pendingIntentCount())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun alreadyDeliveredIntentIsNotDeliveredAgain() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "No duplicate",
                prompt = "once",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.service.deliverDue()
            assertEquals(1, fixture.runtime.deliveries.size)
            val deliveredAt = fixture.store.list().single().lastDeliveredAt!!
            fixture.writeIntent(created, deliveredAt)

            val restarted = fixture.restarted()
            try {
                restarted.start()
                awaitStoreIo {
                    fixture.pendingIntentCount() == 0 && fixture.service.schedules.value.isNotEmpty()
                }
            } finally {
                restarted.stop()
            }

            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals(0, fixture.pendingIntentCount())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun consecutiveStartsDeliverPendingIntentOnlyOnce() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val created = fixture.schedule(
                name = "Idempotent",
                prompt = "once",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.clock.advance(Duration.ofDays(1))
            fixture.writeIntent(created, fixture.clock.millis())

            fixture.service.start()
            awaitStoreIo {
                fixture.runtime.deliveries.size == 1 &&
                    fixture.pendingIntentCount() == 0 &&
                    fixture.service.schedules.value.isNotEmpty()
            }
            fixture.service.stop()
            fixture.service.start()
            awaitStoreIo { fixture.service.schedules.value.isNotEmpty() }
            fixture.service.stop()

            assertEquals(1, fixture.runtime.deliveries.size)
            assertEquals(0, fixture.pendingIntentCount())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun corruptIntentFileIsRebuilt() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.schedule(
                name = "Corrupt",
                prompt = "x",
                trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
                sessionId = "session-1",
            )
            fixture.intentsFile.writeText("{not json")

            fixture.service.start()
            runCurrent()
            fixture.service.stop()

            assertTrue(fixture.runtime.deliveries.isEmpty())
            assertEquals(0, fixture.pendingIntentCount())
            val root = Json.parseToJsonElement(fixture.intentsFile.readText()) as JsonObject
            assertEquals("1", root["schemaVersion"].toString())
            assertEquals(JsonArray(emptyList()), root["intents"])
        } finally {
            fixture.close()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.awaitStoreIo(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            runCurrent()
            if (condition()) return
            Thread.sleep(2)
        }
        runCurrent()
        if (!condition()) error("awaitStoreIo 超时：条件在 3000ms 内未成立")
    }

    private fun kotlinx.coroutines.test.TestScope.emitRuntimeEvents(
        events: MutableSharedFlow<SessionRuntimeEvent>,
        count: Int,
        event: (Int) -> SessionRuntimeEvent,
    ) {
        repeat(count) { index ->
            while (!events.tryEmit(event(index))) {
                runCurrent()
            }
            runCurrent()
        }
    }

    private class Fixture(
        private val scope: CoroutineScope,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        val zone: ZoneId = ZoneId.of("Asia/Shanghai")
        val clock = MutableTestClock(Instant.parse("2026-10-23T04:00:00Z"), zone)
        val directory: File = Files.createTempDirectory("deepdeep-schedule-service").toFile()
        val intentsFile = File(directory, "delivery-intents.json")
        val runtime = FakeAgentRuntime()
        val store = ScheduleStore(directory, clock) { zone }
        val service = ScheduleService(runtime, store, scope, clock, intentsFile, ioDispatcher)

        fun restarted(): ScheduleService = ScheduleService(runtime, store, scope, clock, intentsFile, ioDispatcher)

        fun writeIntent(schedule: Schedule, createdAt: Long) {
            val root = buildJsonObject {
                put("schemaVersion", 1)
                putJsonArray("intents") {
                    add(buildJsonObject {
                        put("dedupeKey", "${schedule.id}@${schedule.nextRunAt ?: "run-now-$createdAt"}")
                        put("scheduleId", schedule.id)
                        put("sessionId", schedule.sessionId)
                        put("createdAt", createdAt)
                    })
                }
            }
            intentsFile.writeText(root.toString())
        }

        fun pendingIntentCount(): Int {
            if (!intentsFile.isFile) return 0
            val root = Json.parseToJsonElement(intentsFile.readText()) as? JsonObject ?: return 0
            return (root["intents"] as? JsonArray)?.size ?: 0
        }

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
}
