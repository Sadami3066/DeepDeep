package io.github.whywood.superpi.runtime.memory

import io.github.whywood.superpi.runtime.logWarn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MemoryRollup"

private val SKIP_PATTERN = Regex("^skip[\\s.!。]*$", RegexOption.IGNORE_CASE)

class MemoryRollup(
    private val store: MemoryStore,
    private val scope: CoroutineScope,
    private val complete: suspend (prompt: String) -> String?,
    private val today: () -> String = { MemoryText.todayKey() },
) {
    private val running = AtomicBoolean(false)

    fun launchIfDue() {
        if (!running.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                runIfDue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "memory rollup failed", error)
            } finally {
                running.set(false)
            }
        }
    }

    internal suspend fun runIfDue() {
        store.ensure()
        rollupDays()
        rollupMonths()
    }

    private suspend fun rollupDays() {
        val todayKey = today()
        for (day in store.dayKeys().filter { it < todayKey }) {
            if (store.monthHasDay(day)) continue
            val raw = store.readDay(day)?.trim().orEmpty()
            if (raw.isEmpty()) {
                store.appendMonthBlock(day, "(no entries)")
                continue
            }
            val summary = rollup(dayPrompt(day, raw)) ?: return
            store.appendMonthBlock(day, summary.ifBlank { "(nothing worth keeping)" })
        }
    }

    private suspend fun rollupMonths() {
        val currentMonth = today().take(7)
        for (month in store.monthKeys().filter { it < currentMonth }) {
            if (store.yearHasMonth(month)) continue
            val raw = store.readMonth(month)?.trim().orEmpty()
            if (raw.isEmpty()) {
                store.appendYearBlock(month, "(no entries)")
                continue
            }
            val summary = rollup(monthPrompt(month, raw)) ?: return
            store.appendYearBlock(month, summary.ifBlank { "(nothing worth keeping)" })
        }
    }

    private suspend fun rollup(prompt: String): String? {
        val raw = complete(prompt) ?: return null
        return normalize(raw)
    }

    private fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val unfenced = trimmed
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (unfenced.isEmpty()) return ""
        val firstLine = unfenced.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        val stripped = firstLine.removePrefix("- ").removePrefix("* ").removePrefix("-").trim()
        if (SKIP_PATTERN.matches(stripped)) return ""
        return unfenced
    }

    private fun dayPrompt(day: String, raw: String): String = buildString {
        appendLine("Below are the raw memory records of one day (recaps and scratch notes left by that day's conversations).")
        appendLine("Compress them into a day-level long-term review: a markdown unordered list with at most 6 items, one sentence each.")
        appendLine("- Keep: what happened, decisions and the reasons behind them, promises and to-dos, lessons worth reusing, changes in state.")
        appendLine("- Drop: greetings, repetition, tool-call details, one-off information.")
        appendLine("No heading, no preamble. Write in the language used by the records below; keep proper nouns such as names, project names and technical terms exactly as they were written there; do not translate into English just because these instructions are in English.")
        appendLine("If nothing is worth keeping, output only SKIP.")
        appendLine()
        appendLine("<day date=\"$day\">")
        append(raw)
        appendLine()
        appendLine("</day>")
    }

    private fun monthPrompt(month: String, raw: String): String = buildString {
        appendLine("Below are the daily reviews for the month $month.")
        appendLine("Compress them into one review of this month, under 300 words. Keep only the main threads: goal progress, important events, decisions, open issues.")
        appendLine("No heading, no preamble. Write in the language used by the records below; keep proper nouns such as names, project names and technical terms exactly as they were written there; do not translate into English just because these instructions are in English.")
        appendLine("If nothing is worth keeping, output only SKIP.")
        appendLine()
        appendLine("<month value=\"$month\">")
        append(raw)
        appendLine()
        appendLine("</month>")
    }
}
