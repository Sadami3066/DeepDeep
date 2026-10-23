package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import io.github.whywood.deepdeep.runtime.nextRun
import io.github.whywood.deepdeep.runtime.validationError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

internal const val DEFAULT_CRON_EXPRESSION = "0 9 * * *"

enum class DraftFrequency {
    ONCE,
    CRON,
}

enum class DraftError {
    EMPTY_NAME,
    EMPTY_PROMPT,
    PAST_TIME,
    INVALID_CRON,
    NO_CONVERSATION,
}

data class ScheduleDraft(
    val id: String? = null,
    val name: String = "",
    val prompt: String = "",
    val frequency: DraftFrequency = DraftFrequency.CRON,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = DEFAULT_TIME,
    val cronExpression: String = DEFAULT_CRON_EXPRESSION,
    val sessionId: String = "",
    val sessionFile: String = "",
    val cwd: String = "",
    val conversationTitle: String = "",
    val enabled: Boolean = true,
) {
    fun toTrigger(): ScheduleTrigger = when (frequency) {
        DraftFrequency.ONCE -> ScheduleTrigger.Once(date, time)
        DraftFrequency.CRON -> ScheduleTrigger.Cron(cronExpression.trim())
    }

    fun validate(now: ZonedDateTime): DraftError? = when {
        name.isBlank() -> DraftError.EMPTY_NAME
        prompt.isBlank() -> DraftError.EMPTY_PROMPT
        frequency == DraftFrequency.ONCE && nextRunPreview(now) == null -> DraftError.PAST_TIME
        frequency == DraftFrequency.CRON && ScheduleTrigger.Cron(cronExpression).validationError(now) != null -> DraftError.INVALID_CRON
        sessionId.isBlank() -> DraftError.NO_CONVERSATION
        else -> null
    }

    fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = toTrigger().nextRun(now)

    companion object {
        private const val NAME_PREVIEW_LIMIT = 28

        fun from(schedule: Schedule, conversationTitle: String): ScheduleDraft {
            val trigger = schedule.trigger
            val frequency = if (trigger is ScheduleTrigger.Once) DraftFrequency.ONCE else DraftFrequency.CRON
            return ScheduleDraft(
                id = schedule.id,
                name = schedule.name,
                prompt = schedule.prompt,
                frequency = frequency,
                date = (trigger as? ScheduleTrigger.Once)?.date ?: LocalDate.now(),
                time = when (trigger) {
                    is ScheduleTrigger.Once -> trigger.time
                    is ScheduleTrigger.Daily -> trigger.time
                    is ScheduleTrigger.Weekly -> trigger.time
                    is ScheduleTrigger.Cron -> DEFAULT_TIME
                },
                cronExpression = when (trigger) {
                    is ScheduleTrigger.Once -> DEFAULT_CRON_EXPRESSION
                    is ScheduleTrigger.Daily -> "${trigger.time.minute} ${trigger.time.hour} * * *"
                    is ScheduleTrigger.Weekly -> weeklyCronExpression(trigger.days, trigger.time)
                    is ScheduleTrigger.Cron -> trigger.expression
                },
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

        private fun weeklyCronExpression(days: Set<DayOfWeek>, time: LocalTime): String {
            val weekdays = days.map { it.value % 7 }.sorted().joinToString(",")
            return "${time.minute} ${time.hour} * * $weekdays"
        }
    }
}
