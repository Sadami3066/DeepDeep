package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus

internal fun reasoningPreview(text: String): String {
    val paragraph = text.split(Regex("\\n[ \\t]*\\n"))
        .asReversed()
        .firstOrNull { it.isNotBlank() }
        ?: return ""
    val line = paragraph.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
    return cleanPreviewLine(line)
}

private fun cleanPreviewLine(line: String): String = line.trim()
    .replace(Regex("^#{1,6}\\s+"), "")
    .replace(Regex("^>\\s?"), "")
    .replace(Regex("^([-*+]|\\d+[.)])\\s+"), "")
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")
    .replace(Regex("\\s+"), " ")
    .trim()

internal sealed interface ProcessEntry {
    data class Single(val step: AssistantStep) : ProcessEntry
    data class ToolGroup(val tools: List<AssistantStep.Tool>) : ProcessEntry
}

internal fun groupToolSteps(steps: List<AssistantStep>): List<ProcessEntry> {
    val entries = mutableListOf<ProcessEntry>()
    val tools = mutableListOf<AssistantStep.Tool>()
    fun flushTools() {
        when {
            tools.size >= 2 -> entries += ProcessEntry.ToolGroup(tools.toList())
            tools.size == 1 -> entries += ProcessEntry.Single(tools.first())
        }
        tools.clear()
    }
    steps.forEach { step ->
        if (step is AssistantStep.Tool) {
            tools += step
        } else {
            flushTools()
            entries += ProcessEntry.Single(step)
        }
    }
    flushTools()
    return entries
}

internal enum class ProcessStatus { Running, Done, Stopped, Failed }

internal fun processStatus(streaming: Boolean, status: MessageStatus): ProcessStatus = when {
    streaming -> ProcessStatus.Running
    status == MessageStatus.STOPPED -> ProcessStatus.Stopped
    status == MessageStatus.FAILED -> ProcessStatus.Failed
    else -> ProcessStatus.Done
}
