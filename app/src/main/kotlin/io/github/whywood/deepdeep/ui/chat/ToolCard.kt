package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
    val duration = tool.durationMs?.takeIf { it > 0L }
    val statusText = when (tool.status) {
        ToolActivityStatus.RUNNING -> stringResource(R.string.assistant_tool_running)
        ToolActivityStatus.DONE -> stringResource(R.string.assistant_tool_done)
        ToolActivityStatus.FAILED -> stringResource(R.string.assistant_tool_failed)
    }
    val muted = if (tool.status == ToolActivityStatus.FAILED) colors.inkSecondary else colors.inkTertiary
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 8.dp)
                .testTag("tool_toggle_${tool.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayToolName(tool.name),
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.inkSecondary,
            )
            if (preview.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = preview,
                    modifier = Modifier.weight(1f),
                    style = DdType.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                color = muted,
            )
            if (duration != null) {
                Text(
                    text = " · ",
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                )
                Text(
                    text = durationLabel(duration),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.width(4.dp))
            DdIcon(
                icon = DdIcons.ChevronDown,
                modifier = Modifier.rotate(rotation),
                tint = colors.inkTertiary.copy(alpha = 0.7f),
                size = 12.dp,
            )
            Spacer(Modifier.width(2.dp))
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
            Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                if (tool.arguments.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_tool_args),
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tool.arguments.entries.joinToString("\n") { "${it.key} = ${it.value}" },
                        style = DdType.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                        ),
                        color = colors.inkTertiary,
                    )
                }
                if (output != null) {
                    if (tool.arguments.isNotEmpty()) Spacer(Modifier.height(8.dp))
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
                            modifier = Modifier.size(26.dp).testTag("tool_copy_${tool.id}"),
                            shape = RoundedCornerShape(6.dp),
                            contentDescription = stringResource(R.string.assistant_tool_copy),
                        ) {
                            DdIcon(icon = DdIcons.Copy, tint = colors.inkTertiary, size = 13.dp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = output.trim(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        style = DdType.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                        ),
                        color = colors.inkSecondary,
                    )
                }
            }
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
