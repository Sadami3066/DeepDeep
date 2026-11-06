package io.github.whywood.deepdeep.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.ui.common.chat.ChatCommand
import io.github.whywood.deepdeep.ui.chat.CommandItem
import io.github.whywood.deepdeep.ui.common.chat.ParsedCommand
import io.github.whywood.deepdeep.ui.common.chat.commandQuery
import io.github.whywood.deepdeep.ui.common.chat.findCommandTrigger
import io.github.whywood.deepdeep.ui.common.chat.parseChatCommand

sealed interface ShellCommandAction {
    data object Compact : ShellCommandAction
    data object GoalPause : ShellCommandAction
    data object GoalResume : ShellCommandAction
    data object GoalClear : ShellCommandAction
    data object GoalInvalid : ShellCommandAction
    data class GoalCreate(val objective: String) : ShellCommandAction
}

sealed interface ShellSubmitPlan {
    data object Send : ShellSubmitPlan
    data object RejectWhileSending : ShellSubmitPlan
    data class RunCommand(val action: ShellCommandAction) : ShellSubmitPlan
    data class UnknownCommand(val name: String) : ShellSubmitPlan
}

fun resolveGoalCommand(rawArgs: String): ShellCommandAction = when (val args = rawArgs.trim()) {
    "pause" -> ShellCommandAction.GoalPause
    "resume" -> ShellCommandAction.GoalResume
    "clear" -> ShellCommandAction.GoalClear
    "" -> ShellCommandAction.GoalInvalid
    else -> ShellCommandAction.GoalCreate(args)
}

fun resolveChatCommand(command: ChatCommand, args: String): ShellCommandAction = when (command) {
    ChatCommand.COMPACT -> ShellCommandAction.Compact
    ChatCommand.GOAL -> resolveGoalCommand(args)
}

fun planSubmission(input: String, sending: Boolean): ShellSubmitPlan {
    val parsed = parseChatCommand(input)
    if (sending && parsed !is ParsedCommand.None) return ShellSubmitPlan.RejectWhileSending
    return when (parsed) {
        ParsedCommand.None, is ParsedCommand.Skill -> ShellSubmitPlan.Send
        is ParsedCommand.Found -> ShellSubmitPlan.RunCommand(resolveChatCommand(parsed.command, parsed.args))
        is ParsedCommand.Unknown -> ShellSubmitPlan.UnknownCommand(parsed.name)
    }
}

fun commandInsertion(currentInput: String, insertText: String): String {
    val trigger = findCommandTrigger(currentInput)
    val base = if (trigger != null) {
        currentInput.removeRange(trigger.start, currentInput.length).trimEnd()
    } else {
        currentInput.trimEnd()
    }
    val separator = if (base.isEmpty() || base.last().isWhitespace()) "" else " "
    return base + separator + insertText
}

fun buildCommandItems(
    conversationId: String?,
    sending: Boolean,
    compacting: Boolean,
    skills: List<SkillSummary>,
    compactTitle: String,
    compactDescription: String,
    goalTitle: String,
    goalDescription: String,
): List<CommandItem> = buildList {
    add(
        CommandItem(
            ChatCommand.COMPACT.commandName, ChatCommand.COMPACT.commandName,
            compactTitle, compactDescription,
            conversationId != null && !sending && !compacting,
            "/${ChatCommand.COMPACT.commandName} ",
        ),
    )
    add(
        CommandItem(
            ChatCommand.GOAL.commandName, ChatCommand.GOAL.commandName, goalTitle, goalDescription,
            conversationId != null, "/${ChatCommand.GOAL.commandName} ",
        ),
    )
    skills.forEach { skill ->
        add(
            CommandItem(
                "skill:${skill.name}", "skill:${skill.name}", skill.name, skill.description,
                conversationId != null && !sending, "/skill:${skill.name} ",
            ),
        )
    }
}
interface ShellCommandExecutor {
    fun perform(action: ShellCommandAction)
}

@Composable
internal fun ShellCommandPalette(input: String, refreshSkills: () -> Unit) {
    val open = commandQuery(input) != null
    LaunchedEffect(open) {
        if (open) refreshSkills()
    }
}
