package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus

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

internal enum class ProcessActivity { Thinking, CallingTool, Replying }

internal fun processActivity(steps: List<AssistantStep>, streaming: Boolean): ProcessActivity? {
    if (!streaming) return null
    if (steps.any { it is AssistantStep.Tool && it.trace.status == ToolActivityStatus.RUNNING }) {
        return ProcessActivity.CallingTool
    }
    val last = steps.lastOrNull()
    if (last is AssistantStep.Text && last.text.isNotBlank()) return ProcessActivity.Replying
    return ProcessActivity.Thinking
}

internal enum class ProcessStatus { Running, Done, Stopped, Failed }

internal fun processStatus(streaming: Boolean, status: MessageStatus): ProcessStatus = when {
    streaming -> ProcessStatus.Running
    status == MessageStatus.STOPPED -> ProcessStatus.Stopped
    status == MessageStatus.FAILED -> ProcessStatus.Failed
    else -> ProcessStatus.Done
}
