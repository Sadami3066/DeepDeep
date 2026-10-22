package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace

@Composable
internal fun ToolCard(
    tool: ToolTrace,
    onToggle: () -> Unit,
    onCopyOutput: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val rotation by animateFloatAsState(
        targetValue = if (tool.expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "tool-chevron",
    )
    val state = stringResource(
        if (tool.expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val preview = remember(tool.arguments) { toolArgsPreview(tool.arguments) }
    val output = tool.error?.takeIf { it.isNotBlank() } ?: tool.output?.takeIf { it.isNotBlank() }
    val duration = tool.durationMs?.takeIf { it > 0 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = onToggle)
                .testTag("tool_toggle_${tool.id}")
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdIcon(icon = toolIcon(tool.name), tint = colors.inkTertiary, size = 14.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = tool.name,
                    style = DdType.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                ToolStatus(tool.status, duration)
                Spacer(Modifier.width(6.dp))
                DdIcon(
                    icon = DdIcons.ChevronDown,
                    modifier = Modifier.rotate(rotation),
                    tint = colors.inkTertiary,
                    size = 14.dp,
                )
            }
            if (!tool.expanded && preview.isNotEmpty()) {
                Text(
                    text = preview,
                    modifier = Modifier.fillMaxWidth().padding(start = 21.dp, bottom = 9.dp),
                    style = DdType.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = tool.expanded,
            enter = expandVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = shrinkVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 33.dp, end = 12.dp, bottom = 10.dp)) {
                if (tool.arguments.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_tool_args),
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = tool.arguments.entries.joinToString("\n") { "${it.key} = ${it.value}" },
                        style = DdType.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        color = colors.inkSecondary,
                    )
                }
                if (output != null) {
                    if (tool.arguments.isNotEmpty()) Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_tool_output),
                            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        DdPressable(
                            onClick = { onCopyOutput(output) },
                            modifier = Modifier.size(28.dp).testTag("tool_copy_${tool.id}"),
                            shape = RoundedCornerShape(8.dp),
                            contentDescription = stringResource(R.string.assistant_tool_copy),
                        ) {
                            DdIcon(icon = DdIcons.Copy, tint = colors.inkTertiary, size = 14.dp)
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        Text(
                            text = output.trim(),
                            style = DdType.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = if (tool.status == ToolActivityStatus.FAILED) colors.ink else colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStatus(status: ToolActivityStatus, durationMs: Long?) {
    val colors = deepDeepColors()
    val (dotColor, label) = when (status) {
        ToolActivityStatus.RUNNING -> colors.accent to stringResource(R.string.assistant_tool_running)
        ToolActivityStatus.DONE -> colors.inkTertiary to stringResource(R.string.assistant_tool_done)
        ToolActivityStatus.FAILED -> colors.ink to stringResource(R.string.assistant_tool_failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = dotColor,
        )
        if (durationMs != null) {
            Text(
                text = " · ",
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
            )
            Text(
                text = durationLabel(durationMs),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
            )
        }
    }
}

private fun toolArgsPreview(arguments: Map<String, String>): String {
    val preferred = listOf(
        "command",
        "cmd",
        "path",
        "file_path",
        "pattern",
        "query",
        "url",
        "description",
        "prompt",
    )
    val value = preferred.firstNotNullOfOrNull { key -> arguments[key]?.takeIf { it.isNotBlank() } }
        ?: arguments.values.firstOrNull { it.isNotBlank() }
        ?: return ""
    return value.replace('\n', ' ').trim()
}

private fun toolIcon(name: String): DdIcon = when (name) {
    "read", "write", "edit" -> DdIcons.File
    "bash", "shell", "linux_shell" -> DdIcons.Terminal
    "ls", "find", "grep" -> DdIcons.Folder
    else -> DdIcons.Sparkles
}
