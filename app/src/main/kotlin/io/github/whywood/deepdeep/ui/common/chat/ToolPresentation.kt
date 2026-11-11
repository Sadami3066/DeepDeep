package io.github.whywood.deepdeep.ui.common.chat

import java.util.Locale

internal enum class ToolIconKind {
    Book,
    Pen,
    Terminal,
    Search,
    Brain,
    Apps,
    Clock,
    File,
    Folder,
    Target,
    Check,
    Plug,
}

internal fun toolIconKind(name: String): ToolIconKind = when {
    name == "read" -> ToolIconKind.Book
    name == "write" || name == "edit" -> ToolIconKind.Pen
    name == "bash" -> ToolIconKind.Terminal
    name == "web_search" || name == "web_fetch" -> ToolIconKind.Search
    name.startsWith("memory_") -> ToolIconKind.Brain
    name.startsWith("miniapp_") -> ToolIconKind.Apps
    name.startsWith("job_") || name.startsWith("timer_") -> ToolIconKind.Clock
    name == "ls" || name == "find" -> ToolIconKind.Folder
    name == "grep" -> ToolIconKind.File
    name == "todo_write" -> ToolIconKind.Check
    name.endsWith("_goal") -> ToolIconKind.Target
    else -> ToolIconKind.Plug
}

internal fun toolUsedLabel(format: String, name: String): String =
    String.format(Locale.ROOT, format, displayToolName(name))
