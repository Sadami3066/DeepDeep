package io.github.whywood.deepdeep.ui.schedules

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
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
    val repeat: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = DEFAULT_TIME,
    val cronExpression: String = DEFAULT_CRON_EXPRESSION,
    val sessionId: String = "",
    val sessionFile: String = "",
    val cwd: String = "",
    val conversationTitle: String = "",
    val enabled: Boolean = true,
) {
    fun toTrigger(): ScheduleTrigger = if (repeat) {
        ScheduleTrigger.Cron(cronExpression.trim())
    } else {
        ScheduleTrigger.Once(date, time)
    }

    fun validate(now: ZonedDateTime): DraftError? = when {
        name.isBlank() -> DraftError.EMPTY_NAME
        prompt.isBlank() -> DraftError.EMPTY_PROMPT
        !repeat && nextRunPreview(now) == null -> DraftError.PAST_TIME
        repeat && ScheduleTrigger.Cron(cronExpression).validationError(now) != null -> DraftError.INVALID_CRON
        sessionId.isBlank() -> DraftError.NO_CONVERSATION
        else -> null
    }

    fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = toTrigger().nextRun(now)

    companion object {
        private const val NAME_PREVIEW_LIMIT = 28

        fun from(schedule: Schedule, conversationTitle: String): ScheduleDraft {
            val trigger = schedule.trigger
            return ScheduleDraft(
                id = schedule.id,
                name = schedule.name,
                prompt = schedule.prompt,
                repeat = trigger !is ScheduleTrigger.Once,
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

internal val ScheduleDraftSaver: Saver<ScheduleDraft, Any> = listSaver<ScheduleDraft, Any>(
    save = { draft ->
        listOf(
            draft.id.orEmpty(),
            draft.name,
            draft.prompt,
            draft.repeat,
            draft.date.toString(),
            draft.time.toString(),
            draft.cronExpression,
            draft.sessionId,
            draft.sessionFile,
            draft.cwd,
            draft.conversationTitle,
            draft.enabled,
        )
    },
    restore = { values ->
        ScheduleDraft(
            id = (values[0] as String).ifEmpty { null },
            name = values[1] as String,
            prompt = values[2] as String,
            repeat = values[3] as Boolean,
            date = LocalDate.parse(values[4] as String),
            time = LocalTime.parse(values[5] as String),
            cronExpression = values[6] as String,
            sessionId = values[7] as String,
            sessionFile = values[8] as String,
            cwd = values[9] as String,
            conversationTitle = values[10] as String,
            enabled = values[11] as Boolean,
        )
    },
)
