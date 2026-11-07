package io.github.whywood.deepdeep.runtime.memory

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val MEMORY_CONTEXT_BUDGET = 16_000

private const val PROFILE_BUDGET = 2_500
private const val CONTEXT_BUDGET = 2_500
private const val COOKBOOK_BUDGET = 800
private const val TODAY_BUDGET = 2_500
private const val MONTH_BUDGET = 1_200
private const val YEAR_BUDGET = 800

internal const val ZONE_PROFILE = "用户与我"
internal const val ZONE_CONTEXT = "当下与将来"
internal const val ZONE_COOKBOOK = "经验与教训"
internal const val ZONE_DIARY = "日记"

internal fun zoneTitle(target: String): String? = when (target.trim().lowercase()) {
    "profile" -> ZONE_PROFILE
    "context" -> ZONE_CONTEXT
    "cookbook" -> ZONE_COOKBOOK
    else -> null
}

internal enum class MemoryTruncation {
    START,
    END,
    MIDDLE,
}

internal object MemoryText {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthFormat = DateTimeFormatter.ofPattern("yyyy-MM")
    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayKey(): String = today().format(dayFormat)

    fun dayKey(date: LocalDate): String = date.format(dayFormat)

    fun isValidDay(value: String): Boolean =
        runCatching { LocalDate.parse(value.trim(), dayFormat) }.isSuccess

    fun monthKey(date: LocalDate = today()): String = YearMonth.from(date).format(monthFormat)

    fun yearKey(date: LocalDate = today()): String = date.year.toString()

    fun nowStamp(): String = OffsetDateTime.now(zone).format(stampFormat)

    fun nowClock(): String = OffsetDateTime.now(zone).format(clockFormat)

    fun shortSession(sessionId: String?): String =
        sessionId?.trim()?.takeIf { it.isNotEmpty() }?.take(8)?.lowercase() ?: "unknown"

    fun marker(sessionId: String?): String = "<!-- ${nowStamp()} [${shortSession(sessionId)}] -->"

    fun normalize(content: String): String = content.replace("\r\n", "\n").trimEnd()

    fun truncate(content: String, maxChars: Int, mode: MemoryTruncation): String {
        val text = normalize(content)
        if (text.length <= maxChars) return text
        val marker = "…（已截断）"
        return when (mode) {
            MemoryTruncation.START -> text.take(maxChars).trimEnd() + "\n\n" + marker
            MemoryTruncation.END -> marker + "\n\n" + text.takeLast(maxChars).trimStart()
            MemoryTruncation.MIDDLE -> {
                val half = maxChars / 2
                text.take(half).trimEnd() + "\n\n" + marker + "\n\n" + text.takeLast(half).trimStart()
            }
        }
    }

    fun preview(content: String, maxChars: Int = 1_200): String {
        val text = normalize(content)
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1_024) return "$bytes B"
        val kb = bytes / 1_024.0
        if (kb < 1_024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1_024)
    }
}

internal data class SectionSpan(
    val startLine: Int,
    val endLine: Int,
    val level: Int,
)

internal object MemorySections {
    private val headingLine = Regex("^(#{1,6})\\s+(.+?)\\s*$")

    fun find(content: String, rawTitle: String): SectionSpan? {
        val title = normalizeTitle(rawTitle) ?: return null
        val lines = content.replace("\r\n", "\n").split('\n')
        var start = -1
        var level = 0
        for (index in lines.indices) {
            val match = headingLine.matchEntire(lines[index].trim()) ?: continue
            val currentLevel = match.groupValues[1].length
            val text = normalizeTitle(match.groupValues[2]) ?: continue
            if (start < 0) {
                if (text.equals(title, ignoreCase = true)) {
                    start = index
                    level = currentLevel
                }
            } else if (currentLevel <= level) {
                return SectionSpan(start, index, level)
            }
        }
        if (start < 0) return null
        return SectionSpan(start, lines.size, level)
    }

    fun body(content: String, rawTitle: String): String? {
        val span = find(content, rawTitle) ?: return null
        val lines = content.replace("\r\n", "\n").split('\n')
        return lines.subList(span.startLine + 1, span.endLine).joinToString("\n").trim()
    }

    fun replace(content: String, rawTitle: String, newBody: String): String {
        val text = content.replace("\r\n", "\n")
        val span = find(text, rawTitle)
        val body = newBody.trim()
        if (span == null) {
            val title = normalizeTitle(rawTitle) ?: return text
            val heading = if (rawTitle.trimStart().startsWith("#")) rawTitle.trim() else "## $title"
            val merged = text.trimEnd() + "\n\n" + heading + "\n\n" + body + "\n"
            return merged
        }
        val lines = text.split('\n')
        val head = lines.subList(0, span.startLine + 1)
        val tail = lines.subList(span.endLine, lines.size)
        val block: List<String> = buildList {
            add(head.joinToString("\n"))
            if (body.isNotEmpty()) {
                add("")
                body.split('\n').forEach { add(it) }
            }
        }
        val merged = (block + (if (tail.isEmpty()) emptyList() else listOf("") + tail)).joinToString("\n")
        return merged.trimEnd() + "\n"
    }

    fun append(content: String, rawTitle: String, block: String): String {
        val text = content.replace("\r\n", "\n")
        val span = find(text, rawTitle)
        val body = block.trimEnd()
        if (span == null) {
            val title = normalizeTitle(rawTitle) ?: return text
            val heading = if (rawTitle.trimStart().startsWith("#")) rawTitle.trim() else "## $title"
            return (text.trimEnd() + "\n\n" + heading + "\n\n" + body + "\n").trimStart()
        }
        val lines = text.split('\n')
        val head = lines.subList(0, span.endLine)
        val tail = lines.subList(span.endLine, lines.size)
        val merged = (head + listOf("", body) + tail).joinToString("\n")
        return merged.trimEnd() + "\n"
    }

    private fun normalizeTitle(raw: String?): String? =
        raw?.replace("\r", "")?.trim()?.trimStart('#')?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun renderMemorySection(
    profile: String,
    context: String,
    cookbook: String,
    todayText: String,
    todayLabel: String,
    monthText: String,
    monthLabel: String,
    yearText: String,
    yearLabel: String,
): String? {
    val blocks = ArrayList<String>()

    fun addZone(title: String, body: String, budget: Int) {
        val text = MemoryText.normalize(body)
        if (text.isBlank()) return
        blocks += "# $title\n\n" + MemoryText.truncate(text, budget, MemoryTruncation.START)
    }

    addZone(ZONE_PROFILE, profile, PROFILE_BUDGET)
    addZone(ZONE_CONTEXT, context, CONTEXT_BUDGET)
    addZone(ZONE_COOKBOOK, cookbook, COOKBOOK_BUDGET)

    val diary = ArrayList<String>()
    fun addDiaryPart(heading: String, body: String, budget: Int) {
        val text = MemoryText.normalize(body)
        if (text.isBlank()) return
        diary += "## $heading\n\n" + MemoryText.truncate(text, budget, MemoryTruncation.END)
    }
    addDiaryPart("今天（$todayLabel）", todayText, TODAY_BUDGET)
    addDiaryPart("本月（$monthLabel）", monthText, MONTH_BUDGET)
    addDiaryPart("本年（$yearLabel）", yearText, YEAR_BUDGET)
    if (diary.isNotEmpty()) {
        blocks += "# $ZONE_DIARY\n\n" + diary.joinToString("\n\n")
    }

    if (blocks.isEmpty()) return null
    return MemoryText.truncate(blocks.joinToString("\n\n"), MEMORY_CONTEXT_BUDGET, MemoryTruncation.START)
}
