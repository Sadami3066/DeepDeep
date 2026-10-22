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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import java.util.Locale

@Composable
internal fun ProcessSection(
    message: AssistantMessage,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
) {
    val colors = deepDeepColors()
    var expansionOverride by rememberSaveable(message.id) { mutableIntStateOf(-1) }
    val expanded = if (expansionOverride < 0) message.streaming else expansionOverride == 1
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "process-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val reasoningCount = message.steps.count { it is AssistantStep.Reasoning }
    val toolCount = message.steps.count { it is AssistantStep.Tool }
    val duration = message.finishedAt?.let { finished ->
        if (message.startedAt > 0 && finished > message.startedAt) finished - message.startedAt else null
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
    val durationText = duration?.let { durationLabel(it) }
    val summary = listOfNotNull(progressLabel, reasoningLabel, toolLabel, durationText).joinToString(" · ")
    val activeReasoningId = if (message.streaming) {
        message.steps.lastOrNull()?.takeIf { it is AssistantStep.Reasoning }?.id
    } else {
        null
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button) {
                    expansionOverride = if (expanded) 0 else 1
                }
                .testTag("process_toggle_${message.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(2.dp))
            DdIcon(
                icon = DdIcons.ChevronDown,
                modifier = Modifier.rotate(rotation),
                tint = colors.inkTertiary.copy(alpha = 0.7f),
                size = 13.dp,
            )
            Spacer(Modifier.width(2.dp))
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
                message.steps.forEach { step ->
                    when (step) {
                        is AssistantStep.Reasoning -> ReasoningCard(
                            step = step,
                            active = step.id == activeReasoningId,
                            onToggle = { onToggleStep(step.id) },
                        )

                        is AssistantStep.Tool -> ToolCard(
                            tool = step.trace,
                            onToggle = { onToggleStep(step.trace.id) },
                            onCopyOutput = onCopyOutput,
                        )

                        is AssistantStep.Text -> ChatMarkdown(
                            text = step.text.trim(),
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 4.dp),
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
