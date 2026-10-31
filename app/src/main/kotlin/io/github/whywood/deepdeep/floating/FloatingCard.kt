package io.github.whywood.deepdeep.floating

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.ui.chat.PresentedItemType

@Composable
internal fun FloatingEventCard(
    card: FloatingEventCard,
    sessionTitle: String,
    queueSize: Int,
    maxHeight: Dp,
    arrowSide: FloatingArrowSide,
    arrowOffset: Dp,
    onAnswerQuestion: (PendingQuestion, QuestionOption) -> Unit,
    onOpenQuestion: (PendingQuestion) -> Unit,
    onOpenCompleted: (FloatingCompletion) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(FloatingCardCorner)
    val near = FloatingSurfaceNearPadding
    val far = FloatingSurfaceWindowPadding * 2 - near
    val topPadding = if (arrowSide == FloatingArrowSide.TOP) near else far
    val bottomPadding = if (arrowSide == FloatingArrowSide.BOTTOM) near else far
    val onClick = when (card) {
        is FloatingEventCard.Question -> null
        is FloatingEventCard.Completed -> ({ onOpenCompleted(card.completion) })
    }
    Box(
        Modifier
            .padding(
                start = FloatingSurfaceWindowPadding,
                end = FloatingSurfaceWindowPadding,
                top = topPadding,
                bottom = bottomPadding,
            )
            .testTag("floating_card"),
    ) {
        Column(Modifier.width(FloatingCardWidth)) {
            if (arrowSide == FloatingArrowSide.TOP) {
                FloatingArrow(side = FloatingArrowSide.TOP, offset = arrowOffset)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .floatingLift(shape, colors)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, cardBorder(colors), shape)
                    .then(
                        if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
                    ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                ) {
                    when (card) {
                        is FloatingEventCard.Question -> QuestionBody(
                            pending = card.pending,
                            sessionTitle = sessionTitle,
                            queueSize = queueSize,
                            onAnswer = onAnswerQuestion,
                            onOpenOverlay = { onOpenQuestion(card.pending) },
                        )

                        is FloatingEventCard.Completed -> CompletedBody(
                            completion = card.completion,
                            sessionTitle = sessionTitle,
                            queueSize = queueSize,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
            if (arrowSide == FloatingArrowSide.BOTTOM) {
                FloatingArrow(side = FloatingArrowSide.BOTTOM, offset = arrowOffset)
            }
        }
    }
}

@Composable
private fun QuestionBody(
    pending: PendingQuestion,
    sessionTitle: String,
    queueSize: Int,
    onAnswer: (PendingQuestion, QuestionOption) -> Unit,
    onOpenOverlay: () -> Unit,
) {
    val colors = deepDeepColors()
    val question = pending.questions.firstOrNull() ?: return
    val inlineOptions = floatingInlineOptions(pending)
    CardHeader(
        label = stringResource(R.string.floating_card_question),
        labelColor = colors.accent,
        title = sessionTitle,
        queueSize = queueSize,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = question.question,
        style = DdType.body,
        color = colors.ink,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
    if (inlineOptions != null) {
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            inlineOptions.forEach { option ->
                QuestionOptionRow(option = option, onClick = { onAnswer(pending, option) })
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(R.string.floating_card_answer_in_overlay),
                onClick = onOpenOverlay,
                color = colors.inkSecondary,
            )
        }
    } else {
        if (pending.questions.size > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.floating_card_more_questions, pending.questions.size),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.height(10.dp))
        DdPrimaryButton(
            text = stringResource(R.string.floating_card_answer_in_overlay),
            onClick = onOpenOverlay,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
    }
}

@Composable
private fun QuestionOptionRow(
    option: QuestionOption,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(12.dp)
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
        shape = shape,
        contentDescription = option.label,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = DdType.label,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedBody(
    completion: FloatingCompletion,
    sessionTitle: String,
    queueSize: Int,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    if (completion.hasDelivery) {
        CardHeader(
            label = stringResource(R.string.floating_card_delivery),
            labelColor = colors.success,
            title = sessionTitle,
            queueSize = queueSize,
            onClose = onDismiss,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(
                icon = completion.items.first().type.icon(),
                tint = colors.accent,
                size = 18.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = completion.deliveryLabel,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (completion.items.size > 1) {
                    Text(
                        text = stringResource(
                            R.string.floating_card_delivery_count,
                            completion.items.size,
                        ),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 15.dp)
        }
    } else {
        val label = when (completion.status) {
            RunStatus.DONE -> stringResource(R.string.floating_card_done)
            RunStatus.STOPPED -> stringResource(R.string.floating_card_stopped)
            RunStatus.FAILED -> stringResource(
                R.string.floating_card_failed,
                completion.error?.take(120).orEmpty(),
            )
        }
        val labelColor = if (completion.status == RunStatus.FAILED) colors.danger else colors.inkSecondary
        CardHeader(
            label = label,
            labelColor = labelColor,
            title = sessionTitle,
            queueSize = queueSize,
            onClose = onDismiss,
        )
        val preview = completion.text.trim().take(220)
        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = preview,
                style = DdType.bodySmall,
                color = colors.inkSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.floating_card_open_in_overlay),
        style = DdType.bodySmall,
        color = colors.inkTertiary,
    )
}

@Composable
private fun CardHeader(
    label: String,
    labelColor: Color,
    title: String,
    queueSize: Int,
    onClose: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (queueSize > 1) {
                label + stringResource(R.string.floating_card_queue, queueSize)
            } else {
                label
            },
            style = DdType.micro,
            color = labelColor,
            maxLines = 1,
        )
        Text(
            text = title.ifBlank { stringResource(R.string.app_name) },
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        if (onClose != null) {
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onClose,
                contentDescription = stringResource(R.string.floating_card_dismiss),
                modifier = Modifier.padding(start = 2.dp),
                size = 28.dp,
                iconSize = 16.dp,
                tint = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun FloatingArrow(
    side: FloatingArrowSide,
    offset: Dp,
) {
    val colors = deepDeepColors()
    val borderColor = cardBorder(colors)
    Box(Modifier.width(FloatingCardWidth).height(FloatingArrowHeight)) {
        Canvas(
            Modifier
                .offset(x = offset.coerceIn(8.dp, FloatingCardWidth - FloatingArrowWidth - 8.dp))
                .size(FloatingArrowWidth, FloatingArrowHeight),
        ) {
            val half = size.width / 2f
            val path = Path()
            if (side == FloatingArrowSide.BOTTOM) {
                path.moveTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(half, size.height)
            } else {
                path.moveTo(half, 0f)
                path.lineTo(0f, size.height)
                path.lineTo(size.width, size.height)
            }
            path.close()
            drawPath(path = path, color = colors.surface)
            val strokeWidth = Stroke(width = with(density) { 1.dp.toPx() }).width
            if (side == FloatingArrowSide.BOTTOM) {
                drawLine(borderColor, Offset(0f, 0f), Offset(half, size.height), strokeWidth)
                drawLine(borderColor, Offset(half, size.height), Offset(size.width, 0f), strokeWidth)
            } else {
                drawLine(borderColor, Offset(half, 0f), Offset(0f, size.height), strokeWidth)
                drawLine(borderColor, Offset(half, 0f), Offset(size.width, size.height), strokeWidth)
            }
        }
    }
}

@Composable
private fun cardBorder(colors: DeepDeepColors): Color =
    if (colors.dark) colors.ink.copy(alpha = 0.08f) else colors.accent.copy(alpha = 0.16f)

private fun PresentedItemType.icon(): DdIcon = when (this) {
    PresentedItemType.IMAGE -> DdIcons.Image
    PresentedItemType.HTML -> DdIcons.Globe
    PresentedItemType.FILE, PresentedItemType.CREATIVE -> DdIcons.File
}

internal fun FloatingEventCard.sessionId(): String = when (this) {
    is FloatingEventCard.Question -> pending.sessionId
    is FloatingEventCard.Completed -> completion.sessionId
}
