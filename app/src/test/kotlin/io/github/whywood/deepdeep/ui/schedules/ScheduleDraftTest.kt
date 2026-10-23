package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleDraftTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 23, 12, 0, 0, 0, zone)
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
    private val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun schedule(trigger: ScheduleTrigger, enabled: Boolean = true): Schedule = Schedule(
        id = "schedule-1",
        name = "Morning brief",
        prompt = "Summarize my notes",
        trigger = trigger,
        enabled = enabled,
        sessionId = "session-1",
        sessionFile = "/data/session.json",
        cwd = "/data/workspace",
        nextRunAt = null,
        lastDeliveredAt = null,
        lastError = null,
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun draft(
        frequency: DraftFrequency = DraftFrequency.DAILY,
        name: String = "Morning brief",
        prompt: String = "Summarize my notes",
        date: LocalDate = LocalDate.of(2026, 10, 24),
        time: LocalTime = LocalTime.of(9, 0),
        weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        sessionId: String = "session-1",
    ): ScheduleDraft = ScheduleDraft(
        name = name,
        prompt = prompt,
        frequency = frequency,
        date = date,
        time = time,
        weekdays = weekdays,
        sessionId = sessionId,
        sessionFile = "/data/session.json",
        cwd = "/data/workspace",
        conversationTitle = "Conversation",
        enabled = true,
    )

    @Test
    fun roundTripsEachFrequency() {
        val triggers = listOf(
            ScheduleTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(9, 30)),
            ScheduleTrigger.Daily(LocalTime.of(7, 5)),
            ScheduleTrigger.Weekly(weekdays, LocalTime.of(8, 0)),
            ScheduleTrigger.Weekly(weekends, LocalTime.of(10, 15)),
            ScheduleTrigger.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), LocalTime.of(18, 45)),
        )
        triggers.forEach { trigger ->
            val mapped = ScheduleDraft.from(schedule(trigger), "Conversation")
            assertEquals(trigger, mapped.toTrigger())
        }
    }

    @Test
    fun weeklyPresetsAreDetected() {
        assertEquals(
            DraftFrequency.WEEKDAYS,
            ScheduleDraft.from(schedule(ScheduleTrigger.Weekly(weekdays, LocalTime.of(8, 0))), "Conversation").frequency,
        )
        assertEquals(
            DraftFrequency.WEEKENDS,
            ScheduleDraft.from(schedule(ScheduleTrigger.Weekly(weekends, LocalTime.of(8, 0))), "Conversation").frequency,
        )
        assertEquals(
            DraftFrequency.WEEKLY,
            ScheduleDraft.from(
                schedule(ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY), LocalTime.of(8, 0))),
                "Conversation",
            ).frequency,
        )
        assertEquals(
            DraftFrequency.ONCE,
            ScheduleDraft.from(
                schedule(ScheduleTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(8, 0))),
                "Conversation",
            ).frequency,
        )
        assertEquals(
            DraftFrequency.DAILY,
            ScheduleDraft.from(schedule(ScheduleTrigger.Daily(LocalTime.of(8, 0))), "Conversation").frequency,
        )
    }

    @Test
    fun toTriggerUsesDraftValues() {
        val date = LocalDate.of(2026, 11, 2)
        val time = LocalTime.of(18, 45)
        assertEquals(
            ScheduleTrigger.Once(date, time),
            draft(frequency = DraftFrequency.ONCE, date = date, time = time).toTrigger(),
        )
        assertEquals(
            ScheduleTrigger.Daily(time),
            draft(frequency = DraftFrequency.DAILY, time = time).toTrigger(),
        )
        assertEquals(
            ScheduleTrigger.Weekly(weekdays, time),
            draft(frequency = DraftFrequency.WEEKDAYS, time = time).toTrigger(),
        )
        assertEquals(
            ScheduleTrigger.Weekly(weekends, time),
            draft(frequency = DraftFrequency.WEEKENDS, time = time).toTrigger(),
        )
        assertEquals(
            ScheduleTrigger.Weekly(setOf(DayOfWeek.TUESDAY), time),
            draft(
                frequency = DraftFrequency.WEEKLY,
                time = time,
                weekdays = setOf(DayOfWeek.TUESDAY),
            ).toTrigger(),
        )
    }

    @Test
    fun validatesEmptyName() {
        assertEquals(DraftError.EMPTY_NAME, draft(name = "  ").validate(now))
    }

    @Test
    fun validatesEmptyPrompt() {
        assertEquals(DraftError.EMPTY_PROMPT, draft(prompt = " ").validate(now))
    }

    @Test
    fun validatesEmptyWeekdays() {
        assertEquals(
            DraftError.EMPTY_WEEKDAYS,
            draft(frequency = DraftFrequency.WEEKLY, weekdays = emptySet()).validate(now),
        )
    }

    @Test
    fun validatesPastOnceTime() {
        assertEquals(
            DraftError.PAST_TIME,
            draft(
                frequency = DraftFrequency.ONCE,
                date = LocalDate.of(2026, 10, 20),
                time = LocalTime.of(9, 0),
            ).validate(now),
        )
    }

    @Test
    fun validatesMissingConversation() {
        assertEquals(DraftError.NO_CONVERSATION, draft(sessionId = "").validate(now))
    }

    @Test
    fun acceptsFutureOnceTime() {
        assertNull(
            draft(
                frequency = DraftFrequency.ONCE,
                date = LocalDate.of(2026, 10, 24),
                time = LocalTime.of(9, 0),
            ).validate(now),
        )
        assertNull(draft(frequency = DraftFrequency.DAILY, time = LocalTime.of(9, 0)).validate(now))
    }

    @Test
    fun previewFillsNameFromPrompt() {
        assertEquals("", ScheduleDraft.preview("   "))
        assertEquals("Hello world", ScheduleDraft.preview("  Hello \n  world "))
        val longText = "a".repeat(40)
        val preview = ScheduleDraft.preview(longText)
        assertEquals(29, preview.length)
        assertEquals("a".repeat(28) + "…", preview)
        assertEquals("x".repeat(28), ScheduleDraft.preview("x".repeat(28)))
    }

    @Test
    fun nextRunPreviewFollowsFrequency() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 13, 0, 0, 0, zone),
            draft(frequency = DraftFrequency.DAILY, time = LocalTime.of(13, 0)).nextRunPreview(now),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone),
            draft(frequency = DraftFrequency.DAILY, time = LocalTime.of(9, 0)).nextRunPreview(now),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone),
            draft(
                frequency = DraftFrequency.WEEKLY,
                time = LocalTime.of(9, 0),
                weekdays = setOf(DayOfWeek.WEDNESDAY),
            ).nextRunPreview(now),
        )
        assertNull(
            draft(frequency = DraftFrequency.ONCE, date = LocalDate.of(2026, 10, 20), time = LocalTime.of(9, 0))
                .nextRunPreview(now),
        )
    }

    @Test
    fun fromMapsScheduleMetadata() {
        val mapped = ScheduleDraft.from(
            schedule(ScheduleTrigger.Daily(LocalTime.of(7, 5)), enabled = false),
            "Morning conversation",
        )
        assertEquals("schedule-1", mapped.id)
        assertEquals("Morning brief", mapped.name)
        assertEquals("Summarize my notes", mapped.prompt)
        assertEquals(LocalTime.of(7, 5), mapped.time)
        assertEquals("session-1", mapped.sessionId)
        assertEquals("/data/session.json", mapped.sessionFile)
        assertEquals("/data/workspace", mapped.cwd)
        assertEquals("Morning conversation", mapped.conversationTitle)
        assertEquals(false, mapped.enabled)
    }
}
