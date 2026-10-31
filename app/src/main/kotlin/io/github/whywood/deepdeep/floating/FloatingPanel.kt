package io.github.whywood.deepdeep.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity

@Composable
internal fun FloatingPanel(
    runs: List<FloatingRun>,
    questions: List<PendingQuestion>,
    completed: List<FloatingCompletion>,
    now: Long,
    maxHeight: Dp,
    onOpenRun: (FloatingRun) -> Unit,
    onOpenQuestion: (PendingQuestion) -> Unit,
    onOpenCompleted: (FloatingCompletion) -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(FloatingCardCorner)
    Box(Modifier.padding(FloatingSurfaceWindowPadding).testTag("floating_panel")) {
        Column(
            Modifier
                .width(FloatingPanelWidth)
                .heightIn(max = maxHeight)
                .floatingLift(shape, colors)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, panelBorder(colors), shape),
        ) {
            PanelHeader(onCollapse = onCollapse)
            DdHairline()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val empty = runs.isEmpty() && questions.isEmpty() && completed.isEmpty()
                if (empty) {
                    DdEmptyState(
                        text = stringResource(R.string.floating_panel_empty),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                    )
                }
                if (runs.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.floating_panel_running))
                    runs.forEach { run ->
                        RunRow(run = run, now = now, onClick = { onOpenRun(run) })
                    }
                }
                if (questions.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.floating_panel_questions))
                    questions.forEach { pending ->
                        QuestionRow(pending = pending, onClick = { onOpenQuestion(pending) })
                    }
                }
                if (completed.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.floating_panel_completed))
                    completed.forEach { completion ->
                        CompletedRow(completion = completion, onClick = { onOpenCompleted(completion) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(onCollapse: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.floating_panel_title),
            modifier = Modifier.weight(1f),
            style = DdType.label.copy(fontSize = 16.sp, lineHeight = 21.sp),
            color = colors.ink,
            maxLines = 1,
        )
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onCollapse,
            contentDescription = stringResource(R.string.floating_panel_close),
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.inkTertiary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = deepDeepColors()
    Text(
        text = text,
        modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp),
        style = DdType.micro,
        color = colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
private fun RunRow(
    run: FloatingRun,
    now: Long,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    PanelRow(onClick = onClick, contentDescription = run.title) {
        DdSpinner(color = colors.accent, size = 14.dp, strokeWidth = 1.6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = run.title.ifBlank { stringResource(R.string.app_name) },
                style = DdType.bodySmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = activityText(run.activity) + " · " + elapsedText(run.startedAt, now),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
    }
}

@Composable
private fun QuestionRow(
    pending: PendingQuestion,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val question = pending.questions.firstOrNull() ?: return
    PanelRow(onClick = onClick, contentDescription = question.question) {
        DdIcon(icon = DdIcons.Message, tint = colors.accent, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = question.question,
                style = DdType.bodySmall,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (pending.questions.size > 1) {
                Text(
                    text = stringResource(R.string.floating_card_more_questions, pending.questions.size),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
    }
}

@Composable
private fun CompletedRow(
    completion: FloatingCompletion,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val title = completion.title.ifBlank { stringResource(R.string.app_name) }
    val detail = when {
        completion.status == RunStatus.FAILED -> stringResource(
            R.string.floating_card_failed,
            completion.error.orEmpty().take(80),
        )

        completion.hasDelivery -> stringResource(R.string.floating_card_delivery) +
            " · " + completion.deliveryLabel.ifBlank { title }

        completion.text.isNotBlank() -> completion.text.trim()

        completion.status == RunStatus.STOPPED -> stringResource(R.string.floating_card_stopped)
        else -> stringResource(R.string.floating_card_done)
    }
    PanelRow(onClick = onClick, contentDescription = title) {
        DdIcon(
            icon = completion.icon(),
            tint = completion.iconTint(colors),
            size = 16.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = DdType.bodySmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (completion.hasDelivery && completion.items.size > 1) {
            Text(
                text = stringResource(R.string.floating_card_delivery_count, completion.items.size),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }
        DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
    }
}

private fun FloatingCompletion.icon(): DdIcon = when {
    status == RunStatus.FAILED -> DdIcons.Close
    hasDelivery -> DdIcons.File
    status == RunStatus.STOPPED -> DdIcons.Stop
    else -> DdIcons.Check
}

private fun FloatingCompletion.iconTint(colors: DeepDeepColors): Color = when {
    status == RunStatus.FAILED -> colors.danger
    hasDelivery -> colors.accent
    status == RunStatus.STOPPED -> colors.inkTertiary
    else -> colors.success
}

@Composable
private fun PanelRow(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        contentDescription = contentDescription,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun activityText(activity: RuntimeActivity?): String = when (activity) {
    null, is RuntimeActivity.Thinking -> stringResource(R.string.floating_activity_thinking)
    is RuntimeActivity.Retrying -> stringResource(R.string.floating_activity_retrying, activity.attempt)
    is RuntimeActivity.Compacting -> stringResource(R.string.floating_activity_compacting)
    is RuntimeActivity.CompactionFailed -> stringResource(R.string.floating_activity_compacting)
    is RuntimeActivity.RunningTool -> stringResource(R.string.floating_activity_tool, activity.name)
}

@Composable
private fun elapsedText(startedAt: Long, now: Long): String {
    val elapsed = floatingElapsed(now - startedAt)
    return when {
        elapsed.hours > 0 -> stringResource(
            R.string.floating_elapsed_hours,
            elapsed.hours,
            elapsed.minutes,
        )

        elapsed.minutes > 0 -> stringResource(
            R.string.floating_elapsed_minutes,
            elapsed.minutes,
            elapsed.seconds,
        )

        else -> stringResource(R.string.floating_elapsed_seconds, elapsed.seconds)
    }
}

@Composable
private fun panelBorder(colors: DeepDeepColors) = if (colors.dark) {
    colors.ink.copy(alpha = 0.08f)
} else {
    colors.accent.copy(alpha = 0.16f)
}
