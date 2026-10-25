package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentRef
import java.io.File
import io.github.whywood.deepdeep.data.parseAttachmentBlock
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.ui.questions.AskQuestionCard
import io.github.whywood.deepdeep.ui.workshop.WorkshopChatCard
import io.github.whywood.deepdeep.ui.workshop.parseRegisteredWorkshop

private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 6.dp,
    bottomStart = 20.dp,
)

private data class BubbleContent(
    val quote: String?,
    val body: String,
)

@Composable
internal fun ChatTurn(
    message: AssistantMessage,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onQuote: (Long, String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    onOpenFile: (String) -> Unit,
    onRetry: (Long) -> Unit,
    attachmentRoot: File,
    autoCollapseProcess: Boolean = true,
    pendingQuestions: List<PendingQuestion> = emptyList(),
    onAnswerQuestion: (PendingQuestion, List<QuestionAnswer>) -> Unit = { _, _ -> },
    onOpenPresentFile: (String) -> Unit = {},
    onSharePresentFile: (String) -> Unit = {},
    onOpenWorkshop: (String) -> Unit = {},
) {
    val notice = message.notice
    if (notice != null) {
        NoticeRow(notice)
        return
    }
    if (message.role == AssistantRole.USER) {
        UserBubble(
            message = message,
            onQuote = onQuote,
            onOpenAttachment = onOpenAttachment,
            attachmentRoot = attachmentRoot,
        )
    } else {
        AssistantTurn(
            message = message,
            onToggleStep = onToggleStep,
            onCopyOutput = onCopyOutput,
            onQuote = onQuote,
            onOpenFile = onOpenFile,
            onRetry = onRetry,
            attachmentRoot = attachmentRoot,
            autoCollapseProcess = autoCollapseProcess,
            pendingQuestions = pendingQuestions,
            onAnswerQuestion = onAnswerQuestion,
            onOpenPresentFile = onOpenPresentFile,
            onSharePresentFile = onSharePresentFile,
            onOpenWorkshop = onOpenWorkshop,
        )
    }
}

@Composable
private fun NoticeRow(notice: TurnNotice) {
    when (notice) {
        is TurnNotice.GoalRound -> GoalRoundNotice(notice)

        is TurnNotice.JobFinished -> JobFinishedNotice(notice)

        is TurnNotice.Compacted -> NoticeText(
            stringResource(
                R.string.chat_notice_compacted,
                formatTokenCount(notice.tokensBefore),
                notice.tokensAfter?.let(::formatTokenCount) ?: "?",
            ),
        )

        is TurnNotice.GoalFinished -> NoticeText(
            stringResource(
                if (notice.completed) R.string.chat_notice_goal_completed else R.string.chat_notice_goal_blocked,
            ),
        )
    }
}

@Composable
private fun NoticeText(text: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun JobFinishedNotice(notice: TurnNotice.JobFinished) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.Terminal, tint = colors.inkTertiary, size = 12.dp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(
                if (notice.failed) R.string.chat_notice_job_failed else R.string.chat_notice_job,
                notice.label,
            ),
            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GoalRoundNotice(notice: TurnNotice.GoalRound) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DdIcon(icon = DdIcons.Sparkles, tint = colors.accent, size = 14.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (notice.maxRounds > 0) {
                    stringResource(R.string.chat_notice_goal_round, notice.round, notice.maxRounds)
                } else {
                    stringResource(R.string.chat_notice_goal_round_unbounded, notice.round)
                },
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            if (notice.objective.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notice.objective,
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(
    message: AssistantMessage,
    onQuote: (Long, String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    attachmentRoot: File,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val parsed = remember(message.text) { parseAttachmentBlock(message.text) }
    val invocation = remember(parsed.text) { parseSkillInvocation(parsed.text) }
    val displayText = remember(parsed.text) { stripSkillInvocation(parsed.text) }
    val content = remember(displayText) { splitQuoted(displayText) }
    val body = content.body.ifBlank { invocation?.let { "/skill:${it.name}" }.orEmpty() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val reminder = message.reminder
            if (reminder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DdIcon(icon = DdIcons.Clock, tint = colors.inkTertiary, size = 12.dp)
                    Text(
                        text = stringResource(R.string.assistant_reminder_label, reminder),
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = colors.inkTertiary,
                    )
                }
            }
            if (parsed.attachments.isNotEmpty()) {
                AttachmentCards(attachments = parsed.attachments, root = attachmentRoot, onOpen = onOpenAttachment)
            }
            if (content.quote != null || body.isNotEmpty()) {
                SelectableMessageText(
                    messageId = message.id,
                    onQuote = { text -> onQuote(message.id, text) },
                    onSpeak = { text -> ReadAloud.speak(context.applicationContext, text) },
                ) {
                    Column(
                        Modifier
                            .clip(UserBubbleShape)
                            .background(colors.userBubble)
                            .padding(horizontal = 15.dp, vertical = 11.dp),
                    ) {
                        if (content.quote != null) {
                            QuoteContext(content.quote)
                            if (body.isNotEmpty()) Spacer(Modifier.height(6.dp))
                        }
                        if (body.isNotEmpty()) {
                            Text(
                                text = body,
                                style = DdType.body,
                                color = colors.onUserBubble,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteContext(text: String) {
    val colors = deepDeepColors()
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(colors.inkTertiary.copy(alpha = 0.45f)),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun AssistantTurn(
    message: AssistantMessage,
    onToggleStep: (Long) -> Unit,
    onCopyOutput: (String) -> Unit,
    onQuote: (Long, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRetry: (Long) -> Unit,
    attachmentRoot: File,
    autoCollapseProcess: Boolean,
    pendingQuestions: List<PendingQuestion>,
    onAnswerQuestion: (PendingQuestion, List<QuestionAnswer>) -> Unit,
    onOpenPresentFile: (String) -> Unit,
    onSharePresentFile: (String) -> Unit,
    onOpenWorkshop: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val trailingText = message.steps.lastOrNull() as? AssistantStep.Text
    val streamingAnswer = if (autoCollapseProcess && message.streaming) {
        trailingText?.text.orEmpty()
    } else {
        ""
    }
    val answer = message.text.trim().ifBlank { streamingAnswer.trim() }
    val excludedStepId = if (message.text.isBlank() && streamingAnswer.isNotBlank()) trailingText?.id else null
    val liveQuestions = pendingQuestions.associateBy { it.toolCallId }
    val liveAsk = message.steps.filterIsInstance<AssistantStep.Tool>()
        .map { it.trace }
        .filter { it.name == "ask_user_question" }
        .mapNotNull { trace -> liveQuestions[trace.toolCallId] }
        .firstOrNull()
    val presentedItems = message.steps.filterIsInstance<AssistantStep.Tool>()
        .map { it.trace }
        .filter { it.name == "present" }
        .flatMap { trace -> parsePresentedItems(trace).orEmpty() }
        .distinctBy { "${it.type}:${it.path ?: it.id}" }
    val workshopApps = message.steps.filterIsInstance<AssistantStep.Tool>()
        .map { it.trace }
        .mapNotNull { trace -> parseRegisteredWorkshop(trace) }
        .distinctBy { it.id }
    Column(Modifier.fillMaxWidth()) {
        val hasVisibleProcess = message.steps.any { it.id != excludedStepId }
        if (hasVisibleProcess) {
            ProcessSection(
                message = message,
                onToggleStep = onToggleStep,
                onCopyOutput = onCopyOutput,
                onFileClick = onOpenFile,
                autoCollapseProcess = autoCollapseProcess,
                excludedStepId = excludedStepId,
            )
        }
        if (liveAsk != null) {
            if (hasVisibleProcess) Spacer(Modifier.height(12.dp))
            key(liveAsk.requestId) {
                AskQuestionCard(
                    questions = liveAsk.questions,
                    answers = null,
                    submitted = false,
                    onAnswer = { answers -> onAnswerQuestion(liveAsk, answers) },
                )
            }
        }
        if (answer.isNotEmpty()) {
            when {
                liveAsk != null -> Spacer(Modifier.height(14.dp))
                hasVisibleProcess -> Spacer(Modifier.height(6.dp))
            }
            SelectableMessageText(
                messageId = message.id,
                onQuote = { text -> onQuote(message.id, text) },
                onSpeak = { text -> ReadAloud.speak(context.applicationContext, text) },
            ) {
                ChatMarkdown(answer, onFileClick = onOpenFile)
            }
        }
        if (presentedItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PresentCard(
                items = presentedItems,
                attachmentRoot = attachmentRoot,
                onOpenFile = onOpenPresentFile,
                onShareFile = onSharePresentFile,
                onOpenWorkshop = onOpenWorkshop,
            )
        }
        if (workshopApps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workshopApps.forEach { app ->
                    WorkshopChatCard(app = app, onOpen = onOpenWorkshop)
                }
            }
        }
        when (message.status) {
            MessageStatus.STOPPED -> StatusMark(
                text = stringResource(R.string.assistant_stopped),
                color = colors.inkTertiary,
            )

            MessageStatus.FAILED -> FailedMark(message = message, onRetry = onRetry)

            else -> Unit
        }
    }
}

@Composable
private fun FailedMark(message: AssistantMessage, onRetry: (Long) -> Unit) {
    val colors = deepDeepColors()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = message.error ?: stringResource(R.string.assistant_failed),
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.inkSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (message.retrying) {
                DdSpinner(
                    modifier = Modifier.testTag("assistant_retrying_spinner"),
                    color = colors.accent,
                    size = 16.dp,
                    strokeWidth = 1.8.dp,
                )
            } else {
                DdTextAction(
                    text = stringResource(R.string.assistant_retry),
                    onClick = { onRetry(message.id) },
                    modifier = Modifier.testTag("assistant_retry"),
                )
            }
        }
    }
}

@Composable
private fun StatusMark(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = text,
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = color,
        )
    }
}

private fun splitQuoted(text: String): BubbleContent {
    val lines = text.split('\n')
    val quoted = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line == ">" -> quoted += ""
            line.startsWith("> ") -> quoted += line.substring(2)
            else -> break
        }
        index++
    }
    if (quoted.isEmpty()) return BubbleContent(null, text)
    if (index >= lines.size || lines[index].isNotBlank()) return BubbleContent(null, text)
    val quote = quoted.joinToString("\n").trim()
    if (quote.isEmpty()) return BubbleContent(null, text)
    return BubbleContent(quote, lines.drop(index + 1).joinToString("\n").trimStart('\n'))
}
