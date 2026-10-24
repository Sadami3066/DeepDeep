package io.github.whywood.deepdeep.ui.chat

private val SKILL_NAME = Regex("[a-z0-9][a-z0-9_-]{0,47}")

enum class ChatCommand(val commandName: String) {
    COMPACT("compact"),
    GOAL("goal"),
    ;

    companion object {
        fun byName(name: String): ChatCommand? = entries.firstOrNull { it.commandName == name }
    }
}

sealed interface ParsedCommand {
    data object None : ParsedCommand

    data class Found(val command: ChatCommand, val args: String) : ParsedCommand

    data class Skill(val name: String, val args: String) : ParsedCommand

    data class Unknown(val name: String) : ParsedCommand
}

data class CommandTrigger(
    val start: Int,
    val query: String,
)

fun parseChatCommand(input: String): ParsedCommand {
    if (!input.startsWith("/")) return ParsedCommand.None
    if (input.startsWith("/skill:")) {
        val rest = input.substring("/skill:".length)
        val rawName = rest.takeWhile { !it.isWhitespace() }
        val args = rest.substring(rawName.length).trim()
        return if (SKILL_NAME.matches(rawName)) {
            ParsedCommand.Skill(rawName, args)
        } else {
            ParsedCommand.Unknown("skill:$rawName")
        }
    }
    val body = input.substring(1)
    val name = body.takeWhile { !it.isWhitespace() }
    if (name.isEmpty()) return ParsedCommand.Unknown("")
    if (name.first() !in 'a'..'z') return ParsedCommand.None
    if (name.any { it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '-' }) return ParsedCommand.None
    val args = body.substring(name.length).trim()
    val command = ChatCommand.byName(name) ?: return ParsedCommand.Unknown(name)
    return ParsedCommand.Found(command, args)
}

fun findCommandTrigger(input: String): CommandTrigger? {
    if (input.isEmpty()) return null
    val start = input.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1
    if (start >= input.length) return null
    if (input[start] != '/') return null
    return CommandTrigger(start = start, query = input.substring(start + 1))
}

fun commandQuery(input: String): String? = findCommandTrigger(input)?.query

fun formatTokenCount(value: Long): String = when {
    value < 1000L -> value.toString()
    value < 1_000_000L -> {
        val thousands = value / 1000.0
        if (thousands < 10.0) {
            String.format(java.util.Locale.US, "%.1fK", thousands)
        } else {
            "${(value / 1000L)}K"
        }
    }

    else -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
}

data class SkillMentions(
    val names: List<String>,
    val text: String,
)

fun extractSkillMentions(input: String): SkillMentions? {
    if (!input.contains("/skill:")) return null
    val code = codeRanges(input)
    val names = LinkedHashSet<String>()
    val text = StringBuilder(input.length)
    var index = 0
    while (index < input.length) {
        val name = if (code[index]) null else skillNameAt(input, index)
        if (name == null) {
            text.append(input[index])
            index++
            continue
        }
        names.add(name)
        index += SKILL_PREFIX.length + name.length
    }
    if (names.isEmpty()) return null
    return SkillMentions(names.toList(), text.toString().trim().replace(Regex("[ \\t]{2,}"), " "))
}

private const val SKILL_PREFIX = "/skill:"

private fun skillNameAt(input: String, index: Int): String? {
    if (!input.startsWith(SKILL_PREFIX, index)) return null
    if (index > 0 && !input[index - 1].isWhitespace()) return null
    val start = index + SKILL_PREFIX.length
    var end = start
    while (end < input.length && input[end].isSkillNameChar()) end++
    if (end == start) return null
    val name = input.substring(start, end)
    if (!SKILL_NAME.matches(name)) return null
    if (end < input.length && (input[end] == '/' || input[end] == ':')) return null
    return name
}

private fun Char.isSkillNameChar(): Boolean = this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '-'

private fun codeRanges(input: String): BooleanArray {
    val marked = BooleanArray(input.length)
    var lineStart = 0
    var fenceStart = -1
    while (lineStart < input.length) {
        val lineEnd = input.indexOf('\n', lineStart).let { if (it < 0) input.length else it }
        val line = input.substring(lineStart, lineEnd)
        if (line.trimStart().startsWith("```")) {
            if (fenceStart < 0) {
                fenceStart = lineStart
            } else {
                mark(marked, fenceStart, lineEnd)
                fenceStart = -1
            }
        } else if (fenceStart >= 0) {
            mark(marked, lineStart, lineEnd)
        } else {
            var tick = lineStart
            while (tick < lineEnd) {
                if (input[tick] != '`') {
                    tick++
                    continue
                }
                val close = input.indexOf('`', tick + 1)
                if (close >= 0 && close < lineEnd) {
                    mark(marked, tick, close)
                    tick = close + 1
                } else {
                    break
                }
            }
        }
        lineStart = lineEnd + 1
    }
    if (fenceStart >= 0) mark(marked, fenceStart, input.lastIndex)
    return marked
}

private fun mark(marked: BooleanArray, from: Int, to: Int) {
    for (index in from..minOf(to, marked.lastIndex)) marked[index] = true
}
