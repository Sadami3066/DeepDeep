package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.ProcessDetail
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdMotionEnabled
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.TurnNotice
import java.util.Locale

@Composable
internal fun ActivitySection(
    steps: List<AssistantStep>,
    message: AssistantMessage,
    isLastActivity: Boolean,
    autoCollapseProcess: Boolean,
    processDetail: ProcessDetail,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onFileClick: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val motionEnabled = LocalDdMotionEnabled.current
    val firstStepId = steps.firstOrNull()?.id
    var expansionOverride by rememberSaveable(message.id, firstStepId) { mutableIntStateOf(-1) }
    val expanded = when {
        expansionOverride >= 0 -> expansionOverride == 1
        !autoCollapseProcess -> true
        message.streaming && isLastActivity -> true
        else -> false
    }
    val running = message.streaming && (
        isLastActivity ||
            steps.any { it is AssistantStep.Tool && it.trace.status == ToolActivityStatus.RUNNING }
        )
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "activity-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val semanticStatus = if (running) {
        ProcessStatus.Running
    } else {
        processStatus(streaming = false, status = if (isLastActivity) message.status else MessageStatus.DONE)
    }
    val statusLabel = when (semanticStatus) {
        ProcessStatus.Running -> stringResource(R.string.assistant_ux_process_running)
        ProcessStatus.Done -> stringResource(R.string.assistant_ux_process_done)
        ProcessStatus.Stopped -> stringResource(R.string.assistant_stopped)
        ProcessStatus.Failed -> stringResource(R.string.assistant_ux_process_failed)
    }
    fun <T> settleSpec(): AnimationSpec<T> =
        if (motionEnabled) tween(DdMotion.BASE, easing = DdMotion.EaseOut) else snap()

    val statusColor by animateColorAsState(
        targetValue = when (semanticStatus) {
            ProcessStatus.Running -> colors.ink
            ProcessStatus.Failed -> colors.danger
            else -> colors.inkTertiary
        },
        animationSpec = settleSpec(),
        label = "activity_summary_color",
    )
    val reasoningCount = steps.count { it is AssistantStep.Reasoning }
    val toolCount = steps.count { it is AssistantStep.Tool }
    val finishedAt = message.finishedAt
    val duration = if (isLastActivity && !message.streaming && finishedAt != null && message.startedAt > 0 &&
        finishedAt > message.startedAt
    ) {
        finishedAt - message.startedAt
    } else {
        null
    }
    val reasoningLabel = if (reasoningCount > 0) {
        stringResource(R.string.assistant_process_reasoning, reasoningCount)
    } else {
        null
    }
    val toolLabel = if (toolCount > 0) {
        stringResource(R.string.assistant_process_tools, toolCount)
    } else {
        null
    }
    val durationText = duration?.let { elapsed ->
        formatElapsed(
            elapsed / 1000L,
            stringResource(R.string.assistant_elapsed_hours_minutes_seconds),
            stringResource(R.string.assistant_elapsed_minutes_seconds),
            stringResource(R.string.assistant_elapsed_seconds),
        )
    }
    val activity = if (running) processActivity(message.steps, streaming = true) else null
    val pulseAlpha = if (running && motionEnabled) {
        val pulse = rememberInfiniteTransition(label = "activity_text_pulse")
        pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = DdMotion.EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "activity_text_alpha",
        ).value
    } else {
        1f
    }
    val textAlpha by animateFloatAsState(
        targetValue = pulseAlpha,
        animationSpec = if (running && motionEnabled) snap() else settleSpec(),
        label = "activity_text_alpha_settle",
    )
    val summaryFontSize by animateFloatAsState(
        targetValue = if (running) ActivityRunningFontSize else ActivityDoneFontSize,
        animationSpec = settleSpec(),
        label = "activity_summary_font_size",
    )
    val summaryLineHeight by animateFloatAsState(
        targetValue = if (running) ActivityRunningLineHeight else ActivityDoneLineHeight,
        animationSpec = settleSpec(),
        label = "activity_summary_line_height",
    )
    val chevronSize by animateDpAsState(
        targetValue = if (running) ActivityRunningChevronSize else ActivityDoneChevronSize,
        animationSpec = settleSpec(),
        label = "activity_summary_chevron_size",
    )
    val activityIconAlpha by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = settleSpec(),
        label = "activity_icon_alpha",
    )
    val activityIconWidth by animateDpAsState(
        targetValue = if (running) ActivityIconRowWidth else 0.dp,
        animationSpec = settleSpec(),
        label = "activity_icon_width",
    )
    val activityLabel = when (activity) {
        ProcessActivity.Thinking -> stringResource(R.string.assistant_process_thinking)
        ProcessActivity.CallingTool -> stringResource(R.string.assistant_process_calling_tool)
        ProcessActivity.Replying -> stringResource(R.string.assistant_process_replying)
        null -> null
    }
    val summary = activityLabel ?: (
        listOf(statusLabel) + listOfNotNull(reasoningLabel, toolLabel, durationText)
        ).joinToString(" · ")
    val activeReasoningId = if (running) {
        steps.lastOrNull()?.takeIf { it is AssistantStep.Reasoning }?.id
    } else {
        null
    }
    val lastReasoningId = steps.lastOrNull { it is AssistantStep.Reasoning }?.id
    val terminatedStatus = semanticStatus.takeIf {
        !running && (it == ProcessStatus.Stopped || it == ProcessStatus.Failed)
    }
    val processNodes = groupProcessSteps(steps)
    Column(Modifier.fillMaxWidth()) {
        DdPressable(
            onClick = { expansionOverride = if (expanded) 0 else 1 },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .semantics { stateDescription = state }
                .testTag("process_toggle_${message.id}_${firstStepId ?: "live"}"),
            shape = DdShapes.Small,
            contentDescription = summary,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activityIconWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(activityIconWidth)
                            .height(ActivityIconSize)
                            .clipToBounds(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.graphicsLayer { alpha = activityIconAlpha }) {
                            ActivityGifIcon(size = ActivityIconSize)
                        }
                    }
                }
                Text(
                    text = summary,
                    style = DdType.body.copy(
                        fontSize = summaryFontSize.sp,
                        lineHeight = summaryLineHeight.sp,
                    ),
                    color = statusColor.copy(alpha = textAlpha),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(DdSpacing.Xxs))
                DdIcon(
                    icon = DdIcons.ChevronRight,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                    tint = colors.inkTertiary.copy(alpha = DdAlpha.Secondary),
                    size = chevronSize,
                )
                Spacer(Modifier.width(DdSpacing.Xxs))
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
            Column(
                Modifier.fillMaxWidth().padding(top = DdSpacing.Xs, bottom = DdSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(DdSpacing.Xs),
            ) {
                processNodes.forEach { node ->
                    when (node) {
                        is ProcessNode.Single -> when (val step = node.step) {
                            is AssistantStep.Reasoning -> ReasoningCard(
                                step = step,
                                active = step.id == activeReasoningId,
                                onToggle = { onToggleStep(step.id) },
                                compact = processDetail == ProcessDetail.COMPACT,
                                terminatedStatus = terminatedStatus.takeIf { step.id == lastReasoningId },
                            )

                            is AssistantStep.Tool -> ProcessToolStep(
                                step = step,
                                onToggleStep = onToggleStep,
                                onCopyOutput = onCopyOutput,
                                onFileClick = onFileClick,
                                processDetail = processDetail,
                            )

                            is AssistantStep.Text -> ChatMarkdown(
                                text = step.text.trim(),
                                modifier = Modifier.padding(start = DdSpacing.Xxs, top = DdSpacing.Xxs, bottom = DdSpacing.Xs),
                                onFileClick = onFileClick,
                            )

                            is AssistantStep.Notice -> NoticeStep(step)

                            is AssistantStep.CustomCard -> Unit
                        }

                        is ProcessNode.ToolRun -> ToolRunGroup(
                            tools = node.tools,
                            messageId = message.id,
                            onToggleStep = onToggleStep,
                            onCopyOutput = onCopyOutput,
                            onFileClick = onFileClick,
                            processDetail = processDetail,
                        )
                    }
                }
            }
        }
    }
}

private const val ToolGroupMinCount = 3

private val ActivityIconSize = 15.dp
private val ActivityIconRowWidth = 21.dp
private val ActivityRunningChevronSize = 13.dp
private val ActivityDoneChevronSize = 12.dp
private const val ActivityRunningFontSize = 16f
private const val ActivityRunningLineHeight = 26f
private const val ActivityDoneFontSize = 13f
private const val ActivityDoneLineHeight = 18f

private sealed interface ProcessNode {
    data class Single(val step: AssistantStep) : ProcessNode
    data class ToolRun(val tools: List<AssistantStep.Tool>) : ProcessNode
}

private fun groupProcessSteps(steps: List<AssistantStep>): List<ProcessNode> {
    val nodes = mutableListOf<ProcessNode>()
    var index = 0
    while (index < steps.size) {
        val step = steps[index]
        if (step is AssistantStep.Tool) {
            var end = index
            while (end < steps.size && steps[end] is AssistantStep.Tool) end++
            val tools = steps.subList(index, end).filterIsInstance<AssistantStep.Tool>()
            if (tools.size >= ToolGroupMinCount) {
                nodes += ProcessNode.ToolRun(tools)
            } else {
                tools.forEach { nodes += ProcessNode.Single(it) }
            }
            index = end
        } else {
            nodes += ProcessNode.Single(step)
            index++
        }
    }
    return nodes
}

@Composable
private fun ToolRunGroup(
    tools: List<AssistantStep.Tool>,
    messageId: Long,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onFileClick: (String) -> Unit,
    processDetail: ProcessDetail,
) {
    val colors = deepDeepColors()
    val firstToolId = tools.first().id
    var expanded by rememberSaveable(messageId, firstToolId) { mutableStateOf(false) }
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "tool-group-chevron",
    )
    val running = tools.any { it.trace.status == ToolActivityStatus.RUNNING }
    val failed = tools.any { it.trace.status == ToolActivityStatus.FAILED }
    val dotTint = when {
        failed -> colors.danger
        running -> colors.warning
        else -> colors.inkTertiary.copy(alpha = DdAlpha.Muted)
    }
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val title = stringResource(R.string.assistant_process_tool_group, tools.size)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProcessRowMetrics.OuterInset)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = { expanded = !expanded })
                .padding(horizontal = ProcessRowMetrics.InnerInset)
                .testTag("tool_group_$firstToolId"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ProcessRowMetrics.IconSize), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(ProcessRowMetrics.DotSize)
                        .clip(DdShapes.Pill)
                        .background(dotTint),
                )
            }
            Spacer(Modifier.width(ProcessRowMetrics.IconGap))
            Text(
                text = title,
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(DdSpacing.Xs))
            DdIcon(
                icon = DdIcons.ChevronRight,
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                tint = colors.inkTertiary.copy(alpha = DdAlpha.Secondary),
                size = ProcessRowMetrics.ChevronSize,
            )
            Spacer(Modifier.width(DdSpacing.Xxs))
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
            Column(
                Modifier.fillMaxWidth().padding(top = DdSpacing.Xxs),
                verticalArrangement = Arrangement.spacedBy(DdSpacing.Xxs),
            ) {
                tools.forEach { tool ->
                    ProcessToolStep(
                        step = tool,
                        onToggleStep = onToggleStep,
                        onCopyOutput = onCopyOutput,
                        onFileClick = onFileClick,
                        processDetail = processDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeStep(step: AssistantStep.Notice) {
    val colors = deepDeepColors()
    val notice = step.notice as? TurnNotice.JobFinished ?: return
    val title = stringResource(
        if (notice.failed) R.string.chat_job_card_failed else R.string.chat_job_card_done,
    )
    val text = if (notice.label.isBlank()) title else "$title · ${notice.label}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(horizontal = DdSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(DdShapes.Pill)
                .background(if (notice.failed) colors.danger else colors.inkTertiary.copy(alpha = DdAlpha.Muted)),
        )
        Spacer(Modifier.width(DdSpacing.Sm))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessToolStep(
    step: AssistantStep.Tool,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onFileClick: (String) -> Unit,
    processDetail: ProcessDetail,
) {
    val trace = step.trace
    val deliveredCount = if (trace.name == "present") {
        remember(trace) { parsePresentedItems(trace)?.size }
    } else {
        null
    }
    ToolCard(
        tool = trace,
        onToggle = { onToggleStep(trace.id) },
        onCopyOutput = onCopyOutput,
        titleOverride = deliveredCount?.let { count ->
            stringResource(R.string.present_tool_delivered, count)
        },
        onFileClick = onFileClick,
        compact = processDetail == ProcessDetail.COMPACT,
    )
}

@Composable
internal fun durationLabel(durationMs: Long): String = when {
    durationMs >= 60_000L -> {
        val seconds = (durationMs / 1000L).toInt()
        stringResource(R.string.assistant_duration_minutes, seconds / 60, seconds % 60)
    }

    durationMs >= 10_000L -> stringResource(
        R.string.assistant_duration_seconds,
        (durationMs / 1000L).toString(),
    )

    else -> stringResource(
        R.string.assistant_duration_seconds,
        String.format(Locale.US, "%.1f", durationMs / 1000.0),
    )
}

internal fun formatElapsed(
    totalSeconds: Long,
    hoursMinutesSeconds: String,
    minutesSeconds: String,
    secondsOnly: String,
): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val seconds = clamped % 60
    return when {
        hours > 0 -> String.format(Locale.ROOT, hoursMinutesSeconds, hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.ROOT, minutesSeconds, minutes, seconds)
        else -> String.format(Locale.ROOT, secondsOnly, seconds)
    }
}
