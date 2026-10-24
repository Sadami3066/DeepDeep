package io.github.whywood.deepdeep.ui.chat

private val SKILL_NAME = Regex("[a-z0-9][a-z0-9_-]{0,47}")

enum class ChatCommand(val commandName: String, val takesArguments: Boolean) {
    COMPACT("compact", false),
    GOAL("goal", true),
    WORKSHOP_SKILL("workshop-skill", true),
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
