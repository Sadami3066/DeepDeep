package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.text
import io.github.whywood.deepdeep.ui.common.chat.PluginToolCardSlot
import java.util.Base64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun ToolCard(
    tool: ToolTrace,
    onToggle: () -> Unit,
    onCopyOutput: (String) -> Unit,
    titleOverride: String? = null,
    onFileClick: ((String) -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val rotation = animateFloatAsState(
        targetValue = if (tool.expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "tool-chevron",
    )
    val state = stringResource(
        if (tool.expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val rawArguments = tool.rawArguments
    val output = tool.resultText()
    val preview = remember(rawArguments, tool.arguments, titleOverride, output, tool.status) {
        when {
            tool.status == ToolActivityStatus.FAILED -> output
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.replace('\n', ' ')
                .orEmpty()

            titleOverride == null -> toolArgsPreview(rawArguments, tool.arguments)
            else -> ""
        }
    }
    val duration = tool.durationMs?.takeIf { it > 0L }
    val statusText = when (tool.status) {
        ToolActivityStatus.RUNNING -> stringResource(R.string.assistant_tool_running)
        ToolActivityStatus.DONE -> stringResource(R.string.assistant_tool_done)
        ToolActivityStatus.FAILED -> stringResource(R.string.assistant_tool_failed)
    }
    val statusColor = when (tool.status) {
        ToolActivityStatus.RUNNING -> colors.inkSecondary
        ToolActivityStatus.DONE -> colors.inkSecondary
        ToolActivityStatus.FAILED -> colors.danger
    }
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
                text = titleOverride ?: displayToolName(tool.name),
                style = DdType.bodySmall.copy(
                    fontFamily = if (titleOverride == null) FontFamily.Monospace else FontFamily.Default,
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
                color = statusColor,
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
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
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
            PluginToolCardSlot(
                toolName = tool.name,
                callId = tool.toolCallId,
                args = remember(rawArguments, tool.arguments) { toolArgumentsJson(rawArguments, tool.arguments) },
                result = tool.pluginSlotResult(),
            ) {
                ToolCardDefaultBody(
                    tool = tool,
                    output = output,
                    onCopyOutput = onCopyOutput,
                    onFileClick = onFileClick,
                )
            }
        }
    }
}

@Composable
private fun ToolCardDefaultBody(
    tool: ToolTrace,
    output: String?,
    onCopyOutput: (String) -> Unit,
    onFileClick: ((String) -> Unit)?,
) {
    val colors = deepDeepColors()
    val rawArguments = tool.rawArguments
    val argumentsText = remember(rawArguments, tool.arguments) { formatToolArguments(rawArguments, tool.arguments) }
    val fileRefs = remember(tool.resultPayload) { tool.resultPayload.fileRefs() }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
        if (argumentsText.isNotEmpty()) {
            Text(
                text = stringResource(R.string.assistant_tool_args),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = argumentsText,
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.inkTertiary,
            )
        }
        if (output != null) {
            if (argumentsText.isNotEmpty()) Spacer(Modifier.height(8.dp))
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
        if (fileRefs.isNotEmpty()) {
            if (argumentsText.isNotEmpty() || output != null) Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fileRefs.forEachIndexed { index, fileRef ->
                    ToolFileRefRow(
                        fileRef = fileRef,
                        onClick = onFileClick?.let { open -> { open(fileRef.path) } },
                        testTag = "tool_file_ref_${tool.id}_$index",
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolFileRefRow(
    fileRef: ToolResultPayload.Block.FileRef,
    onClick: (() -> Unit)?,
    testTag: String,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.File, tint = colors.inkTertiary, size = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = fileRef.path,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        fileRef.mimeType?.takeIf { it.isNotBlank() }?.let { mimeType ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = mimeType,
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

internal fun toolArgumentsJson(raw: JsonObject?, legacy: Map<String, String>): JsonObject =
    raw ?: JsonObject(legacy.mapValues { (_, value) -> JsonPrimitive(value) })

private fun formatToolArguments(raw: JsonObject?, legacy: Map<String, String>): String =
    if (raw != null) {
        raw.entries.joinToString("\n") { (key, value) -> "$key = ${value.displayValue()}" }
    } else {
        legacy.entries.joinToString("\n") { (key, value) -> "$key = $value" }
    }

private fun JsonElement.displayValue(): String = if (this is JsonPrimitive && isString) content else toString()

private fun ToolTrace.resultText(): String? =
    resultPayload?.text()?.takeIf { it.isNotBlank() }
        ?: error?.takeIf { it.isNotBlank() }
        ?: output?.takeIf { it.isNotBlank() }

internal fun ToolTrace.pluginSlotResult(): PluginToolResult? {
    val isError = status == ToolActivityStatus.FAILED
    val payload = resultPayload
    if (payload != null) {
        val blocks = payload.toPluginToolBlocks()
        if (blocks.isEmpty()) return null
        val first = blocks.firstOrNull()
        return if (blocks.size == 1 && first is ToolBlock.Text) {
            PluginToolResult.Text(text = first.text, isError = isError)
        } else {
            PluginToolResult.Rich(blocks = blocks, isError = isError)
        }
    }
    val text = error?.takeIf { it.isNotBlank() } ?: output?.takeIf { it.isNotBlank() } ?: return null
    return PluginToolResult.Text(text = text, isError = isError)
}

internal fun ToolResultPayload.toPluginToolBlocks(): List<ToolBlock> = when (this) {
    is ToolResultPayload.Text -> listOf(ToolBlock.Text(text))
    is ToolResultPayload.Empty -> emptyList()
    is ToolResultPayload.Blocks -> blocks.map { block ->
        when (block) {
            is ToolResultPayload.Block.Text -> ToolBlock.Text(block.text)
            is ToolResultPayload.Block.Image -> ToolBlock.Image(
                data = runCatching { Base64.getDecoder().decode(block.data) }.getOrDefault(ByteArray(0)),
                mimeType = block.mimeType,
            )

            is ToolResultPayload.Block.FileRef -> ToolBlock.FileRef(block.path, block.mimeType)
        }
    }
}

private fun ToolResultPayload?.fileRefs(): List<ToolResultPayload.Block.FileRef> =
    (this as? ToolResultPayload.Blocks)?.blocks
        ?.filterIsInstance<ToolResultPayload.Block.FileRef>()
        .orEmpty()

private fun toolArgsPreview(raw: JsonObject?, legacy: Map<String, String>): String {
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
    val value = if (raw != null) {
        preferred.firstNotNullOfOrNull { key ->
            (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                ?: raw[key]?.toString()?.takeIf { it.isNotBlank() && it != "null" && it != "\"\"" }
        } ?: raw.values.firstOrNull { it.toString().let { value -> value.isNotBlank() && value != "null" } }
            ?.let { if (it is JsonPrimitive && it.isString) it.content else it.toString() }
    } else {
        preferred.firstNotNullOfOrNull { key -> legacy[key]?.takeIf { it.isNotBlank() } }
            ?: legacy.values.firstOrNull { it.isNotBlank() }
    } ?: return ""
    return value.replace('\n', ' ').trim()
}
