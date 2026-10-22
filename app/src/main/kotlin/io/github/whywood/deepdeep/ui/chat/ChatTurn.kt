package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onToggleStep: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyOutput: (String) -> Unit,
) {
    if (message.role == AssistantRole.USER) {
        UserBubble(text = message.text, onCopy = onCopy)
        return
    }
    AssistantTurn(
        message = message,
        onToggleStep = onToggleStep,
        onCopy = onCopy,
        onShare = onShare,
        onCopyOutput = onCopyOutput,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(text: String, onCopy: (String) -> Unit) {
    val colors = deepDeepColors()
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 6.dp,
                        bottomStart = 20.dp,
                    ),
                )
                .background(colors.userBubble)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCopy(text)
                    },
                )
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
            if (answer.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
        if (answer.isNotEmpty()) {
            ChatMarkdown(answer)
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
private fun StatusMark(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = text,
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = color,
        )
    }
}
