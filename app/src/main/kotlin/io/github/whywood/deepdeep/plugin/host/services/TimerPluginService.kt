package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.TimerInfo
import io.github.whywood.deepdeep.plugin.api.TimerRequest
import io.github.whywood.deepdeep.plugin.api.TimerService
import io.github.whywood.deepdeep.plugin.api.TimerTrigger as PluginTrigger
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerService as RuntimeTimerService
import io.github.whywood.deepdeep.runtime.TimerTrigger as RuntimeTrigger
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.StateFlow

internal class TimerPluginService(
    private val timers: RuntimeTimerService,
    private val sessions: ConversationQueries?,
    private val activeSessionId: StateFlow<String?>?,
) : TimerService {
    override suspend fun schedule(request: TimerRequest): TimerInfo? {
        val sessionId = request.sessionId ?: activeSessionId?.value
            ?: throw IllegalStateException("no active session; pass sessionId explicitly")
        val summary = sessions?.allSessions()?.firstOrNull { it.id == sessionId }
            ?: throw IllegalStateException("session not found: $sessionId")
        val created = timers.create(
            name = request.name,
            prompt = request.prompt,
            trigger = request.trigger.toRuntimeTrigger(),
            enabled = request.enabled,
            sessionId = sessionId,
            sessionFile = summary.filePath,
            cwd = summary.cwd,
        )
        return created.toPluginInfo()
    }

    override suspend fun list(): List<TimerInfo> = timers.list().map { it.toPluginInfo() }

    override suspend fun cancel(id: String): Boolean = timers.delete(id)

    private fun PluginTrigger.toRuntimeTrigger(): RuntimeTrigger = when (this) {
        is PluginTrigger.Once -> {
            val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), systemZone())
            RuntimeTrigger.Once(dateTime.toLocalDate(), dateTime.toLocalTime())
        }

        is PluginTrigger.Cron -> RuntimeTrigger.Cron(expression)
    }

    private fun Timer.toPluginInfo(): TimerInfo = TimerInfo(
        id = id,
        name = name,
        prompt = prompt,
        trigger = trigger.toPluginTrigger(),
        sessionId = sessionId,
        enabled = enabled,
        nextRunAt = nextRunAt,
        lastError = lastError?.name,
    )

    private fun RuntimeTrigger.toPluginTrigger(): PluginTrigger = when (this) {
        is RuntimeTrigger.Once -> {
            val epochMillis = ZonedDateTime.of(date, time, systemZone()).toInstant().toEpochMilli()
            PluginTrigger.Once(epochMillis)
        }

        is RuntimeTrigger.Daily -> PluginTrigger.Cron("${time.minute} ${time.hour} * * *")

        is RuntimeTrigger.Weekly -> {
            val daysPart = days.sortedBy { it.value }.joinToString(",") { day ->
                if (day.value == 7) "0" else day.value.toString()
            }.ifEmpty { "*" }
            PluginTrigger.Cron("${time.minute} ${time.hour} * * $daysPart")
        }

        is RuntimeTrigger.Cron -> PluginTrigger.Cron(expression)
    }

    private fun systemZone(): ZoneId = ZoneId.systemDefault()
}
