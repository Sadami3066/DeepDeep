package io.github.whywood.deepdeep.ui.timers

import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerTrigger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerDraftTest {
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

    private fun timer(trigger: TimerTrigger, enabled: Boolean = true): Timer = Timer(
        id = "timer-1",
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
        repeat: Boolean = true,
        name: String = "Morning brief",
        prompt: String = "Summarize my notes",
        date: LocalDate = LocalDate.of(2026, 10, 24),
        time: LocalTime = LocalTime.of(9, 0),
        cronExpression: String = "0 9 * * *",
        sessionId: String = "session-1",
    ): TimerDraft = TimerDraft(
        name = name,
        prompt = prompt,
        repeat = repeat,
        date = date,
        time = time,
        cronExpression = cronExpression,
        sessionId = sessionId,
        sessionFile = "/data/session.json",
        cwd = "/data/workspace",
        conversationTitle = "Conversation",
        enabled = true,
    )

    @Test
    fun roundTripsOnceAndCron() {
        val triggers = listOf(
            TimerTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(9, 30)),
            TimerTrigger.Cron("0 9 * * 1-5"),
            TimerTrigger.Cron("*/15 * * * *"),
        )
        triggers.forEach { trigger ->
            val mapped = TimerDraft.from(timer(trigger), "Conversation")
            assertEquals(trigger, mapped.toTrigger())
        }
    }

    @Test
    fun toTriggerUsesDraftValues() {
        val date = LocalDate.of(2026, 11, 2)
        val time = LocalTime.of(18, 45)
        assertEquals(
            TimerTrigger.Once(date, time),
            draft(repeat = false, date = date, time = time).toTrigger(),
        )
        assertEquals(
            TimerTrigger.Cron("0 9 * * 1-5"),
            draft(repeat = true, cronExpression = "0 9 * * 1-5").toTrigger(),
        )
        assertEquals(
            TimerTrigger.Cron("0 9 * * *"),
            draft(repeat = true).toTrigger(),
        )
        assertEquals(
            TimerTrigger.Cron("*/5 * * * *"),
            draft(repeat = true, cronExpression = "  */5 * * * *  ").toTrigger(),
        )
    }

    @Test
    fun migratesLegacyDailyTriggerToCron() {
        val mapped = TimerDraft.from(timer(TimerTrigger.Daily(LocalTime.of(7, 5))), "Conversation")
        assertTrue(mapped.repeat)
        assertEquals("5 7 * * *", mapped.cronExpression)
        assertEquals(LocalTime.of(7, 5), mapped.time)
        assertEquals(TimerTrigger.Cron("5 7 * * *"), mapped.toTrigger())
    }

    @Test
    fun migratesLegacyWeeklyTriggerToCron() {
        val workdays = TimerDraft.from(
            timer(TimerTrigger.Weekly(weekdays, LocalTime.of(8, 0))),
            "Conversation",
        )
        assertTrue(workdays.repeat)
        assertEquals("0 8 * * 1,2,3,4,5", workdays.cronExpression)
        assertEquals(TimerTrigger.Cron("0 8 * * 1,2,3,4,5"), workdays.toTrigger())

        val weekend = TimerDraft.from(
            timer(TimerTrigger.Weekly(weekends, LocalTime.of(10, 15))),
            "Conversation",
        )
        assertEquals("15 10 * * 0,6", weekend.cronExpression)

        val picked = TimerDraft.from(
            timer(
                TimerTrigger.Weekly(
                    setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    LocalTime.of(9, 0),
                ),
            ),
            "Conversation",
        )
        assertEquals("0 9 * * 1,3,5", picked.cronExpression)
    }

    @Test
    fun keepsExistingCronExpression() {
        val mapped = TimerDraft.from(timer(TimerTrigger.Cron("0 9 * * 1-5")), "Conversation")
        assertTrue(mapped.repeat)
        assertEquals("0 9 * * 1-5", mapped.cronExpression)
        assertEquals(LocalTime.of(9, 0), mapped.time)
        assertEquals(TimerTrigger.Cron("0 9 * * 1-5"), mapped.toTrigger())
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
    fun validatesPastOnceTime() {
        assertEquals(
            DraftError.PAST_TIME,
            draft(
                repeat = false,
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
    fun validatesCronExpression() {
        assertEquals(
            DraftError.INVALID_CRON,
            draft(repeat = true, cronExpression = "not a cron").validate(now),
        )
        assertEquals(
            DraftError.INVALID_CRON,
            draft(repeat = true, cronExpression = "0 9 * *").validate(now),
        )
        assertNull(draft(repeat = true, cronExpression = "0 9 * * 1-5").validate(now))
    }

    @Test
    fun acceptsFutureOnceTime() {
        assertNull(
            draft(
                repeat = false,
                date = LocalDate.of(2026, 10, 24),
                time = LocalTime.of(9, 0),
            ).validate(now),
        )
    }

    @Test
    fun previewFillsNameFromPrompt() {
        assertEquals("", TimerDraft.preview("   "))
        assertEquals("Hello world", TimerDraft.preview("  Hello \n  world "))
        val longText = "a".repeat(40)
        val preview = TimerDraft.preview(longText)
        assertEquals(29, preview.length)
        assertEquals("a".repeat(28) + "…", preview)
        assertEquals("x".repeat(28), TimerDraft.preview("x".repeat(28)))
    }

    @Test
    fun nextRunPreviewFollowsTrigger() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 13, 0, 0, 0, zone),
            draft(repeat = true, cronExpression = "0 13 * * *").nextRunPreview(now),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone),
            draft(repeat = true, cronExpression = "0 9 * * *").nextRunPreview(now),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, zone),
            draft(repeat = true, cronExpression = "0 9 * * 1-5").nextRunPreview(now),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone),
            draft(
                repeat = false,
                date = LocalDate.of(2026, 10, 24),
                time = LocalTime.of(9, 0),
            ).nextRunPreview(now),
        )
        assertNull(
            draft(repeat = false, date = LocalDate.of(2026, 10, 20), time = LocalTime.of(9, 0))
                .nextRunPreview(now),
        )
        assertNull(draft(repeat = true, cronExpression = "not a cron").nextRunPreview(now))
    }

    @Test
    fun fromMapsTimerMetadata() {
        val mapped = TimerDraft.from(
            timer(TimerTrigger.Cron("0 9 * * 1-5"), enabled = false),
            "Morning conversation",
        )
        assertEquals("timer-1", mapped.id)
        assertEquals("Morning brief", mapped.name)
        assertEquals("Summarize my notes", mapped.prompt)
        assertEquals("session-1", mapped.sessionId)
        assertEquals("/data/session.json", mapped.sessionFile)
        assertEquals("/data/workspace", mapped.cwd)
        assertEquals("Morning conversation", mapped.conversationTitle)
        assertEquals(false, mapped.enabled)
    }
}
