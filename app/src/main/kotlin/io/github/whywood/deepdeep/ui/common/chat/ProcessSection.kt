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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.ui.common.chat.AnsweredAskCard
import io.github.whywood.deepdeep.ui.common.chat.parseQuestionRecord
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun ProcessSection(
    message: AssistantMessage,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onFileClick: (String) -> Unit,
    autoCollapseProcess: Boolean = true,
    excludedStepId: Long? = null,
) {
    val colors = deepDeepColors()
    var now by remember(message.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(message.streaming) {
        if (!message.streaming) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var expansionOverride by rememberSaveable(message.id) { mutableIntStateOf(-1) }
    val trailingText = message.steps.lastOrNull() as? AssistantStep.Text
    val answerStarted = message.streaming && trailingText?.text?.isNotBlank() == true
    val expanded = when {
        expansionOverride >= 0 -> expansionOverride == 1
        autoCollapseProcess -> message.streaming && !answerStarted
        else -> true
    }
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "process-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val reasoningCount = message.steps.count { it is AssistantStep.Reasoning }
    val toolCount = message.steps.count { it is AssistantStep.Tool }
    val finishedAt = message.finishedAt
    val duration = when {
        message.streaming && message.startedAt > 0 -> (now - message.startedAt).coerceAtLeast(0L)
        !message.streaming && finishedAt != null && message.startedAt > 0 &&
            finishedAt > message.startedAt -> finishedAt - message.startedAt
        else -> null
    }
    val progressLabel = stringResource(R.string.assistant_process)
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
    val summary = listOfNotNull(progressLabel, reasoningLabel, toolLabel, durationText).joinToString(" · ")
    val activeReasoningId = if (message.streaming) {
        message.steps.lastOrNull()?.takeIf { it is AssistantStep.Reasoning }?.id
    } else {
        null
    }
    Column(Modifier.fillMaxWidth()) {
        DdPressable(
            onClick = { expansionOverride = if (expanded) 0 else 1 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .heightIn(min = 36.dp)
                .semantics { stateDescription = state }
                .testTag("process_toggle_${message.id}"),
            shape = RoundedCornerShape(8.dp),
            contentDescription = summary,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.streaming) {
                    DdSpinner(size = 13.dp, color = colors.inkTertiary)
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    text = summary,
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(2.dp))
                DdIcon(
                    icon = DdIcons.ChevronDown,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                    tint = colors.inkTertiary.copy(alpha = 0.7f),
                    size = 13.dp,
                )
                Spacer(Modifier.width(2.dp))
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
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                message.steps
                    .filter { step -> step.id != excludedStepId }
                    .forEach { step ->
                        when (step) {
                            is AssistantStep.Reasoning -> ReasoningCard(
                                step = step,
                                active = step.id == activeReasoningId,
                                onToggle = { onToggleStep(step.id) },
                            )

                            is AssistantStep.Tool -> {
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
                                )
                                val record = if (trace.status == ToolActivityStatus.DONE) {
                                    remember(trace) { parseQuestionRecord(trace) }
                                } else {
                                    null
                                }
                                if (record != null) {
                                    key(trace.toolCallId) {
                                        AnsweredAskCard(
                                            record = record,
                                            toolCallId = trace.toolCallId,
                                        )
                                    }
                                }
                            }

                            is AssistantStep.Text -> ChatMarkdown(
                                text = step.text.trim(),
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 4.dp),
                                onFileClick = onFileClick,
                            )
                        }
                    }
            }
        }
    }
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
