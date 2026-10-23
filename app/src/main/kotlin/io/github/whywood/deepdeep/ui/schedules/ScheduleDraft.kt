package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private val PRESET_WEEKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private val PRESET_WEEKENDS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

enum class DraftFrequency {
    ONCE,
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    WEEKLY,
}

enum class DraftError {
    EMPTY_NAME,
    EMPTY_PROMPT,
    EMPTY_WEEKDAYS,
    PAST_TIME,
    NO_CONVERSATION,
}

data class ScheduleDraft(
    val id: String? = null,
    val name: String = "",
    val prompt: String = "",
    val frequency: DraftFrequency = DraftFrequency.DAILY,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(9, 0),
    val weekdays: Set<DayOfWeek> = PRESET_WEEKDAYS,
    val sessionId: String = "",
    val sessionFile: String = "",
    val cwd: String = "",
    val conversationTitle: String = "",
    val enabled: Boolean = true,
) {
    fun toTrigger(): ScheduleTrigger = when (frequency) {
        DraftFrequency.ONCE -> ScheduleTrigger.Once(date, time)
        DraftFrequency.DAILY -> ScheduleTrigger.Daily(time)
        DraftFrequency.WEEKDAYS -> ScheduleTrigger.Weekly(PRESET_WEEKDAYS, time)
        DraftFrequency.WEEKENDS -> ScheduleTrigger.Weekly(PRESET_WEEKENDS, time)
        DraftFrequency.WEEKLY -> ScheduleTrigger.Weekly(weekdays, time)
    }

    fun validate(now: ZonedDateTime): DraftError? = when {
        name.isBlank() -> DraftError.EMPTY_NAME
        prompt.isBlank() -> DraftError.EMPTY_PROMPT
        frequency == DraftFrequency.WEEKLY && weekdays.isEmpty() -> DraftError.EMPTY_WEEKDAYS
        frequency == DraftFrequency.ONCE && nextRunPreview(now) == null -> DraftError.PAST_TIME
        sessionId.isBlank() -> DraftError.NO_CONVERSATION
        else -> null
    }

    fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = when (val trigger = toTrigger()) {
        is ScheduleTrigger.Once -> ZonedDateTime.of(trigger.date, trigger.time, now.zone)
            .takeIf { it.isAfter(now) }

        is ScheduleTrigger.Daily -> {
            val today = ZonedDateTime.of(now.toLocalDate(), trigger.time, now.zone)
            if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), trigger.time, now.zone)
        }

        is ScheduleTrigger.Weekly -> {
            var result: ZonedDateTime? = null
            for (offset in 0..7) {
                val candidateDate = now.toLocalDate().plusDays(offset.toLong())
                if (candidateDate.dayOfWeek !in trigger.days) continue
                val candidate = ZonedDateTime.of(candidateDate, trigger.time, now.zone)
                if (candidate.isAfter(now)) {
                    result = candidate
                    break
                }
            }
            result
        }
    }

    companion object {
        private const val NAME_PREVIEW_LIMIT = 28

        fun from(schedule: Schedule, conversationTitle: String): ScheduleDraft {
            val trigger = schedule.trigger
            val frequency = when (trigger) {
                is ScheduleTrigger.Once -> DraftFrequency.ONCE
                is ScheduleTrigger.Daily -> DraftFrequency.DAILY
                is ScheduleTrigger.Weekly -> when (trigger.days) {
                    PRESET_WEEKDAYS -> DraftFrequency.WEEKDAYS
                    PRESET_WEEKENDS -> DraftFrequency.WEEKENDS
                    else -> DraftFrequency.WEEKLY
                }
            }
            val date = (trigger as? ScheduleTrigger.Once)?.date ?: LocalDate.now()
            val time = when (trigger) {
                is ScheduleTrigger.Once -> trigger.time
                is ScheduleTrigger.Daily -> trigger.time
                is ScheduleTrigger.Weekly -> trigger.time
            }
            val weekdays = (trigger as? ScheduleTrigger.Weekly)?.days ?: PRESET_WEEKDAYS
            return ScheduleDraft(
                id = schedule.id,
                name = schedule.name,
                prompt = schedule.prompt,
                frequency = frequency,
                date = date,
                time = time,
                weekdays = weekdays,
                sessionId = schedule.sessionId,
                sessionFile = schedule.sessionFile,
                cwd = schedule.cwd,
                conversationTitle = conversationTitle,
                enabled = schedule.enabled,
            )
        }

        fun preview(text: String): String {
            val normalized = text.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return ""
            return if (normalized.length <= NAME_PREVIEW_LIMIT) {
                normalized
            } else {
                normalized.take(NAME_PREVIEW_LIMIT) + "…"
            }
        }
    }
}
