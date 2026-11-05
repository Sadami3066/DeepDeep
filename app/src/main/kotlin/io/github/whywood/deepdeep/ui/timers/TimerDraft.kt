package io.github.whywood.deepdeep.ui.timers

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerTrigger
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

data class TimerDraft(
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
    fun toTrigger(): TimerTrigger = if (repeat) {
        TimerTrigger.Cron(cronExpression.trim())
    } else {
        TimerTrigger.Once(date, time)
    }

    fun validate(now: ZonedDateTime): DraftError? = when {
        name.isBlank() -> DraftError.EMPTY_NAME
        prompt.isBlank() -> DraftError.EMPTY_PROMPT
        !repeat && nextRunPreview(now) == null -> DraftError.PAST_TIME
        repeat && TimerTrigger.Cron(cronExpression).validationError(now) != null -> DraftError.INVALID_CRON
        sessionId.isBlank() -> DraftError.NO_CONVERSATION
        else -> null
    }

    fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = toTrigger().nextRun(now)

    companion object {
        private const val NAME_PREVIEW_LIMIT = 28

        fun from(timer: Timer, conversationTitle: String): TimerDraft {
            val trigger = timer.trigger
            return TimerDraft(
                id = timer.id,
                name = timer.name,
                prompt = timer.prompt,
                repeat = trigger !is TimerTrigger.Once,
                date = (trigger as? TimerTrigger.Once)?.date ?: LocalDate.now(),
                time = when (trigger) {
                    is TimerTrigger.Once -> trigger.time
                    is TimerTrigger.Daily -> trigger.time
                    is TimerTrigger.Weekly -> trigger.time
                    is TimerTrigger.Cron -> DEFAULT_TIME
                },
                cronExpression = when (trigger) {
                    is TimerTrigger.Once -> DEFAULT_CRON_EXPRESSION
                    is TimerTrigger.Daily -> "${trigger.time.minute} ${trigger.time.hour} * * *"
                    is TimerTrigger.Weekly -> weeklyCronExpression(trigger.days, trigger.time)
                    is TimerTrigger.Cron -> trigger.expression
                },
                sessionId = timer.sessionId,
                sessionFile = timer.sessionFile,
                cwd = timer.cwd,
                conversationTitle = conversationTitle,
                enabled = timer.enabled,
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

internal val TimerDraftSaver: Saver<TimerDraft, Any> = listSaver<TimerDraft, Any>(
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
        TimerDraft(
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
