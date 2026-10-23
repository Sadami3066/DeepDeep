package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.runtime.IntervalUnit
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import io.github.whywood.deepdeep.runtime.nextRun
import io.github.whywood.deepdeep.runtime.validationError
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

private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

private const val DEFAULT_CRON_EXPRESSION = "0 9 * * *"

enum class DraftFrequency {
    ONCE,
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    WEEKLY,
    INTERVAL,
    MONTHLY,
    CRON,
}

enum class DraftError {
    EMPTY_NAME,
    EMPTY_PROMPT,
    EMPTY_WEEKDAYS,
    PAST_TIME,
    INVALID_INTERVAL,
    INVALID_DAY_OF_MONTH,
    INVALID_CRON,
    NO_CONVERSATION,
}

data class ScheduleDraft(
    val id: String? = null,
    val name: String = "",
    val prompt: String = "",
    val frequency: DraftFrequency = DraftFrequency.DAILY,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = DEFAULT_TIME,
    val weekdays: Set<DayOfWeek> = PRESET_WEEKDAYS,
    val intervalEvery: Int = 2,
    val intervalUnit: IntervalUnit = IntervalUnit.HOURS,
    val intervalStartDate: LocalDate = LocalDate.now(),
    val monthlyDay: Int = 1,
    val cronExpression: String = DEFAULT_CRON_EXPRESSION,
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
        DraftFrequency.INTERVAL -> when (intervalUnit) {
            IntervalUnit.MINUTES, IntervalUnit.HOURS -> ScheduleTrigger.Interval(intervalEvery, intervalUnit)
            IntervalUnit.DAYS -> ScheduleTrigger.Interval(intervalEvery, intervalUnit, time, intervalStartDate)
        }
        DraftFrequency.MONTHLY -> ScheduleTrigger.Monthly(monthlyDay, time)
        DraftFrequency.CRON -> ScheduleTrigger.Cron(cronExpression.trim())
    }

    fun validate(now: ZonedDateTime): DraftError? = when {
        name.isBlank() -> DraftError.EMPTY_NAME
        prompt.isBlank() -> DraftError.EMPTY_PROMPT
        frequency == DraftFrequency.WEEKLY && weekdays.isEmpty() -> DraftError.EMPTY_WEEKDAYS
        frequency == DraftFrequency.ONCE && nextRunPreview(now) == null -> DraftError.PAST_TIME
        frequency == DraftFrequency.INTERVAL && !intervalInRange() -> DraftError.INVALID_INTERVAL
        frequency == DraftFrequency.MONTHLY && monthlyDay !in 1..31 -> DraftError.INVALID_DAY_OF_MONTH
        frequency == DraftFrequency.CRON && ScheduleTrigger.Cron(cronExpression).validationError(now) != null -> DraftError.INVALID_CRON
        sessionId.isBlank() -> DraftError.NO_CONVERSATION
        else -> null
    }

    fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = toTrigger().nextRun(now)

    private fun intervalInRange(): Boolean = when (intervalUnit) {
        IntervalUnit.MINUTES -> intervalEvery in 1..1440
        IntervalUnit.HOURS -> intervalEvery in 1..24
        IntervalUnit.DAYS -> intervalEvery in 1..365
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
                is ScheduleTrigger.Interval -> DraftFrequency.INTERVAL
                is ScheduleTrigger.Monthly -> DraftFrequency.MONTHLY
                is ScheduleTrigger.Cron -> DraftFrequency.CRON
            }
            val date = (trigger as? ScheduleTrigger.Once)?.date ?: LocalDate.now()
            val time = when (trigger) {
                is ScheduleTrigger.Once -> trigger.time
                is ScheduleTrigger.Daily -> trigger.time
                is ScheduleTrigger.Weekly -> trigger.time
                is ScheduleTrigger.Interval -> trigger.time ?: DEFAULT_TIME
                is ScheduleTrigger.Monthly -> trigger.time
                is ScheduleTrigger.Cron -> DEFAULT_TIME
            }
            val weekdays = (trigger as? ScheduleTrigger.Weekly)?.days ?: PRESET_WEEKDAYS
            val intervalEvery = (trigger as? ScheduleTrigger.Interval)?.every ?: 2
            val intervalUnit = (trigger as? ScheduleTrigger.Interval)?.unit ?: IntervalUnit.HOURS
            val intervalStartDate = (trigger as? ScheduleTrigger.Interval)?.startDate ?: LocalDate.now()
            val monthlyDay = (trigger as? ScheduleTrigger.Monthly)?.day ?: 1
            val cronExpression = (trigger as? ScheduleTrigger.Cron)?.expression ?: DEFAULT_CRON_EXPRESSION
            return ScheduleDraft(
                id = schedule.id,
                name = schedule.name,
                prompt = schedule.prompt,
                frequency = frequency,
                date = date,
                time = time,
                weekdays = weekdays,
                intervalEvery = intervalEvery,
                intervalUnit = intervalUnit,
                intervalStartDate = intervalStartDate,
                monthlyDay = monthlyDay,
                cronExpression = cronExpression,
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
