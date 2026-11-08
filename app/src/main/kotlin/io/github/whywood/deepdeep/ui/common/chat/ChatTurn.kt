package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.parseAttachmentBlock
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.TurnSource
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.MessageSummary
import io.github.whywood.deepdeep.runtime.MessageSummaryKind
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.ui.common.chat.AttachmentCards
import io.github.whywood.deepdeep.ui.common.chat.PluginCustomMessageSlot
import io.github.whywood.deepdeep.ui.common.chat.AskQuestionCard
import io.github.whywood.deepdeep.ui.common.chat.MiniappChatCard
import io.github.whywood.deepdeep.ui.common.chat.parseRegisteredMiniapp
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject




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
    onOpenMiniapp: (String) -> Unit = {},
    retryAttempt: Int? = null,
) {
    val notice = message.notice
    if (notice != null) {
        NoticeRow(notice)
        return
    }
    val summary = message.summary
    if (summary != null) {
        MessageSummaryCard(message.id, summary)
        return
    }
    val customType = message.customType
    if (customType != null) {
        val payload = remember(message.customPayload) {
            message.customPayload
                ?.let { raw -> runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() }
                ?: JsonObject(emptyMap())
        }
        PluginCustomMessageSlot(
            messageId = message.id.toString(),
            customType = customType,
            payload = payload,
            fallback = {
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = DdType.body,
                        color = deepDeepColors().ink,
                    )
                }
            },
        )
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
            onOpenMiniapp = onOpenMiniapp,
            retryAttempt = retryAttempt,
        )
    }
}

@Composable
private fun MessageSummaryCard(messageId: Long, summary: MessageSummary) {
    val colors = deepDeepColors()
    var expanded by remember(messageId) { mutableStateOf(false) }
    val title = when (summary.kind) {
        MessageSummaryKind.COMPACTION -> stringResource(R.string.assistant_summary_compacted)
        MessageSummaryKind.BRANCH -> stringResource(R.string.assistant_summary_branch)
    }
    val tokensBefore = summary.tokensBefore
    val tokensAfter = summary.tokensAfter
    val tokens = if (summary.kind == MessageSummaryKind.COMPACTION && tokensBefore != null) {
        if (tokensAfter != null) {
            stringResource(
                R.string.assistant_summary_tokens,
                formatTokenCount(tokensBefore),
                formatTokenCount(tokensAfter),
            )
        } else {
            stringResource(R.string.assistant_summary_tokens_before, formatTokenCount(tokensBefore))
        }
    } else {
        null
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DdShapes.Medium)
                .background(colors.wash)
                .clickable { expanded = !expanded }
                .padding(start = DdSpacing.Md, end = 10.dp, top = 9.dp, bottom = 9.dp)
                .testTag("assistant_summary"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = when (summary.kind) {
                    MessageSummaryKind.COMPACTION -> DdIcons.Refresh
                    MessageSummaryKind.BRANCH -> DdIcons.GitFork
                },
                tint = colors.inkTertiary,
                size = 13.dp,
            )
            Spacer(Modifier.width(DdSpacing.Sm))
            Text(
                text = title,
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkSecondary,
            )
            if (tokens != null) {
                Spacer(Modifier.width(DdSpacing.Sm))
                Text(
                    text = tokens,
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            DdIcon(
                icon = DdIcons.ChevronRight,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
                tint = colors.inkTertiary,
                size = 14.dp,
            )
        }
        if (summary.text.isNotBlank()) {
            Text(
                text = summary.text.trim(),
                modifier = Modifier.padding(start = DdSpacing.Md, end = DdSpacing.Md, top = 6.dp),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
                color = colors.inkTertiary,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

        is TurnNotice.InterruptedQuestions -> NoticeText(
            stringResource(R.string.chat_notice_interrupted_questions, notice.count),
        )

        is TurnNotice.InterruptedRun -> NoticeText(
            stringResource(R.string.chat_notice_interrupted_run, notice.count),
        )
    }
}

@Composable
private fun NoticeText(text: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DdSpacing.Xs),
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = DdSpacing.Xxs),
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
        DdIcon(icon = DdIcons.Target, tint = colors.accent, size = 14.dp)
        Spacer(Modifier.width(DdSpacing.Sm))
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
                Spacer(Modifier.height(DdSpacing.Xxs))
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
            if (message.injectedSource == TurnSource.PLUGIN) {
                Text(
                    text = stringResource(R.string.assistant_plugin_message),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.inkTertiary,
                )
            }
            val reminder = message.reminder
            if (reminder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DdSpacing.Xs),
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
                .background(colors.inkTertiary.copy(alpha = DdAlpha.Hairline)),
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
    onOpenMiniapp: (String) -> Unit,
    retryAttempt: Int?,
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
    val toolSteps = message.steps.filterIsInstance<AssistantStep.Tool>()
    val liveAsk = remember(toolSteps, pendingQuestions) {
        val liveQuestions = pendingQuestions.associateBy { it.toolCallId }
        toolSteps.asSequence()
            .map { it.trace }
            .filter { it.name == "ask_user_question" }
            .mapNotNull { trace -> liveQuestions[trace.toolCallId] }
            .firstOrNull()
    }
    val presentedItems = remember(toolSteps) {
        toolSteps.asSequence()
            .map { it.trace }
            .filter { it.name == "present" }
            .flatMap { trace -> parsePresentedItems(trace).orEmpty().asSequence() }
            .distinctBy { "${it.type}:${it.path ?: it.id}" }
            .toList()
    }
    val miniappApps = remember(toolSteps) {
        toolSteps.asSequence()
            .map { it.trace }
            .mapNotNull { trace -> parseRegisteredMiniapp(trace) }
            .distinctBy { it.id }
            .toList()
    }
    Column(Modifier.fillMaxWidth()) {
        val hasVisibleProcess = message.steps.any { it.id != excludedStepId } || message.streaming
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
            if (hasVisibleProcess) Spacer(Modifier.height(DdSpacing.Md))
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
                ChatMarkdown(answer, onFileClick = onOpenFile, live = message.streaming)
            }
        }
        if (presentedItems.isNotEmpty() && !message.streaming) {
            Spacer(Modifier.height(DdSpacing.Md))
            PresentCard(
                items = presentedItems,
                attachmentRoot = attachmentRoot,
                onOpenFile = onOpenPresentFile,
                onShareFile = onSharePresentFile,
                onOpenMiniapp = onOpenMiniapp,
            )
        }
        if (miniappApps.isNotEmpty()) {
            Spacer(Modifier.height(DdSpacing.Md))
            Column(verticalArrangement = Arrangement.spacedBy(DdSpacing.Sm)) {
                miniappApps.forEach { app ->
                    MiniappChatCard(app = app, onOpen = onOpenMiniapp)
                }
            }
        }
        when (message.status) {
            MessageStatus.STOPPED -> if (!hasVisibleProcess) {
                StatusMark(
                    text = stringResource(R.string.assistant_stopped),
                    color = colors.inkTertiary,
                )
            }

            MessageStatus.FAILED -> FailedMark(message = message, retryAttempt = retryAttempt)

            else -> Unit
        }
    }
}

@Composable
private fun FailedMark(message: AssistantMessage, retryAttempt: Int?) {
    val colors = deepDeepColors()
    val rawDetails = rawErrorDetails(message.rawError)
    var detailsExpanded by rememberSaveable(message.id, message.rawError) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = DdSpacing.Sm)) {
        if (message.retrying) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdSpinner(
                    modifier = Modifier.testTag("assistant_retrying_spinner"),
                    color = colors.accent,
                    size = 16.dp,
                    strokeWidth = 1.8.dp,
                )
                if (retryAttempt != null) {
                    Spacer(Modifier.width(DdSpacing.Sm))
                    Text(
                        text = stringResource(R.string.chat_status_retrying_format, retryAttempt),
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = colors.inkSecondary,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DdShapes.Medium)
                    .background(colors.danger.copy(alpha = if (colors.dark) 0.12f else 0.06f))
                    .padding(horizontal = DdSpacing.Md, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdIcon(icon = DdIcons.Info, tint = colors.danger, size = 15.dp)
                    Spacer(Modifier.width(DdSpacing.Sm))
                    Text(
                        text = message.error ?: stringResource(R.string.assistant_failed),
                        modifier = Modifier.weight(1f),
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = colors.inkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (rawDetails != null) {
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DdTextAction(
                            text = stringResource(
                                if (detailsExpanded) {
                                    R.string.assistant_error_details_hide
                                } else {
                                    R.string.assistant_error_details_show
                                },
                            ),
                            onClick = { detailsExpanded = !detailsExpanded },
                            color = colors.inkTertiary,
                            modifier = Modifier.testTag("assistant_error_details_toggle"),
                        )
                    }
                    if (detailsExpanded) {
                        Text(
                            text = rawDetails,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(DdShapes.Small)
                                .background(colors.codeBackground)
                                .padding(horizontal = 10.dp, vertical = DdSpacing.Sm)
                                .testTag("assistant_error_details"),
                            style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
                            color = colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMark(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = DdSpacing.Sm)) {
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
