package io.github.whywood.deepdeep.runtime.memory

import io.github.whywood.deepdeep.runtime.logWarn

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
        appendLine("下面是一天的原始记忆记录（当天对话留下的纪要、临时记录等）。")
        appendLine("把它压缩成当天的长期回顾：用 markdown 无序列表，最多 6 条，每条一句话。")
        appendLine("- 保留：发生的事、决定与理由、承诺和待办、值得复用的教训、状态变化。")
        appendLine("- 丢弃：寒暄、重复、工具调用细节、一次性信息。")
        appendLine("不要标题，不要开场白；没有值得保留的内容只输出 SKIP。")
        appendLine()
        appendLine("<day date=\"$day\">")
        append(raw)
        appendLine()
        appendLine("</day>")
    }

    private fun monthPrompt(month: String, raw: String): String = buildString {
        appendLine("下面是 $month 这一月每天的记忆回顾。")
        appendLine("把它压缩成一段不超过 300 字的本月回顾，只保留主线：目标进展、重要事件、决定、遗留问题。")
        appendLine("不要标题，不要开场白；没有值得保留的内容只输出 SKIP。")
        appendLine()
        appendLine("<month value=\"$month\">")
        append(raw)
        appendLine()
        appendLine("</month>")
    }
}
