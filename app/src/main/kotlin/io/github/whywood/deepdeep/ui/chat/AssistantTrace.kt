package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace

@Composable
internal fun ReasoningBlock(
    step: AssistantStep.Reasoning,
    running: Boolean,
    onToggle: () -> Unit,
) {
    val colors = deepDeepColors()
    TraceBlock(
        icon = DdIcons.Brain,
        title = stringResource(R.string.assistant_reasoning_label),
        status = if (running) stringResource(R.string.assistant_reasoning_running) else null,
        expanded = step.expanded,
        testTag = "reasoning_toggle_${step.id}",
        onToggle = onToggle,
    ) {
        Text(
            text = step.text.trim(),
            modifier = Modifier.padding(start = 20.dp, end = 2.dp, bottom = 8.dp),
            style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = colors.inkTertiary,
        )
    }
}

@Composable
internal fun ToolTraceBlock(
    tool: ToolTrace,
    onToggle: () -> Unit,
) {
    val colors = deepDeepColors()
    val action = toolAction(tool.name)
    val status = when (tool.status) {
        ToolActivityStatus.RUNNING -> stringResource(R.string.assistant_tool_status_running)
        ToolActivityStatus.FAILED -> stringResource(R.string.assistant_tool_status_failed)
        ToolActivityStatus.DONE -> null
    }
    val detail = toolDetail(tool)
    val output = toolOutput(tool)
    val monospace = tool.name in MONOSPACE_TOOLS

    TraceBlock(
        icon = toolIcon(tool.name),
        title = action,
        status = status,
        expanded = tool.expanded,
        testTag = "tool_toggle_${tool.id}",
        onToggle = onToggle,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 2.dp, bottom = 8.dp)) {
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = DdType.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.inkTertiary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (output.isNotEmpty()) {
                if (detail.isNotBlank()) Spacer(Modifier.height(6.dp))
                Text(
                    text = output,
                    style = DdType.bodySmall.copy(
                        fontFamily = if (monospace) FontFamily.Monospace else DdType.bodySmall.fontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun TraceBlock(
    icon: DdIcon,
    title: String,
    status: String?,
    expanded: Boolean,
    testTag: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = deepDeepColors()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "trace-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = onToggle)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DdIcon(icon = icon, tint = colors.inkTertiary, size = 13.dp)
            Text(
                text = title,
                style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
            if (status != null) {
                Text(
                    text = status,
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.width(2.dp))
            }
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                DdIcon(
                    icon = DdIcons.ChevronDown,
                    modifier = Modifier.rotate(rotation),
                    tint = colors.inkTertiary,
                    size = 14.dp,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = shrinkVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            content()
        }
    }
}

@Composable
private fun toolAction(name: String): String {
    val res = when (name) {
        "read" -> R.string.assistant_tool_action_read
        "write" -> R.string.assistant_tool_action_write
        "edit" -> R.string.assistant_tool_action_edit
        "bash", "shell" -> R.string.assistant_tool_action_shell
        "linux_shell" -> R.string.assistant_tool_action_linux_shell
        "grep", "find" -> R.string.assistant_tool_action_search
        "ls" -> R.string.assistant_tool_action_list
        else -> null
    }
    return if (res == null) {
        stringResource(R.string.assistant_tool_action_generic, name)
    } else {
        stringResource(res)
    }
}

private fun toolIcon(name: String): DdIcon = when (name) {
    "read", "write", "edit" -> DdIcons.File
    "bash", "shell", "linux_shell" -> DdIcons.Terminal
    "ls" -> DdIcons.Folder
    "grep", "find" -> DdIcons.Sparkles
    else -> DdIcons.Sparkles
}

private fun toolDetail(tool: ToolTrace): String = when (tool.name) {
    "read", "write", "edit" -> tool.arguments["path"].orEmpty()
    "bash", "shell", "linux_shell" -> tool.arguments["command"].orEmpty()
    "grep", "find" -> listOfNotNull(
        tool.arguments["pattern"]?.takeIf { it.isNotBlank() },
        tool.arguments["path"]?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    "ls" -> tool.arguments["path"].orEmpty()
    else -> tool.arguments.entries.joinToString(" · ") { "${it.key}=${it.value}" }
}

private fun toolOutput(tool: ToolTrace): String {
    val text = tool.error ?: tool.output ?: return ""
    val trimmed = text.trim()
    if (trimmed.length <= 500) return trimmed
    return trimmed.take(500).trimEnd() + "\n…"
}

private val MONOSPACE_TOOLS = setOf("bash", "shell", "linux_shell", "grep", "find")
