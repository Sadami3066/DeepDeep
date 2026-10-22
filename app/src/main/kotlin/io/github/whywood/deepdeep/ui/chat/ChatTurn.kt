package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus

@Composable
internal fun ChatTurn(
    message: AssistantMessage,
    activity: String?,
    activityStartedAt: Long?,
    onToggleStep: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyOutput: (String) -> Unit,
) {
    if (message.role == AssistantRole.USER) {
        UserBubble(message.text)
        return
    }
    AssistantTurn(
        message = message,
        activity = activity,
        activityStartedAt = activityStartedAt,
        onToggleStep = onToggleStep,
        onCopy = onCopy,
        onShare = onShare,
        onCopyOutput = onCopyOutput,
    )
}

@Composable
private fun UserBubble(text: String) {
    val colors = deepDeepColors()
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp,
                        bottomStart = 6.dp,
                    ),
                )
                .background(colors.userBubble)
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(
                text = text,
                style = DdType.body,
                color = colors.onUserBubble,
            )
        }
    }
}

@Composable
private fun AssistantTurn(
    message: AssistantMessage,
    activity: String?,
    activityStartedAt: Long?,
    onToggleStep: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyOutput: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val answer = message.text.trim()
    val readable = remember(answer) { markdownToPlainText(answer) }
    Column(Modifier.fillMaxWidth()) {
        if (message.steps.isNotEmpty()) {
            ProcessSection(
                message = message,
                onToggleStep = onToggleStep,
                onCopyOutput = onCopyOutput,
            )
            if (answer.isNotEmpty() || message.streaming) Spacer(Modifier.height(6.dp))
        }
        if (answer.isNotEmpty()) {
            ChatMarkdown(answer)
        }
        if (message.streaming) {
            if (answer.isNotEmpty()) Spacer(Modifier.height(10.dp))
            WorkingIndicator(activity = activity, startedAt = activityStartedAt)
        }
        when (message.status) {
            MessageStatus.STOPPED -> StatusMark(
                text = stringResource(R.string.assistant_stopped),
                color = colors.inkTertiary,
            )

            MessageStatus.FAILED -> StatusMark(
                text = stringResource(R.string.assistant_failed),
                color = colors.inkSecondary,
            )

            else -> Unit
        }
        if (!message.streaming && answer.isNotEmpty()) {
            ChatActionBar(
                messageId = message.id,
                onCopy = { onCopy(readable) },
                onShare = { onShare(readable) },
            )
        }
    }
}

@Composable
private fun StatusMark(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = text,
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = color,
        )
    }
}
