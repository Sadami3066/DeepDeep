package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyOutput: (String) -> Unit,
    onQuote: (Long, String) -> Unit,
) {
    if (message.role == AssistantRole.USER) {
        UserBubble(
            message = message,
            onCopy = onCopy,
            onQuote = onQuote,
        )
    } else {
        AssistantTurn(
            message = message,
            onToggleStep = onToggleStep,
            onCopy = onCopy,
            onShare = onShare,
            onCopyOutput = onCopyOutput,
            onQuote = onQuote,
        )
    }
}

@Composable
private fun UserBubble(
    message: AssistantMessage,
    onCopy: (String) -> Unit,
    onQuote: (Long, String) -> Unit,
) {
    val colors = deepDeepColors()
    val haptic = LocalHapticFeedback.current
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var menuExpanded by remember { mutableStateOf(false) }
    val content = remember(message.text) { splitQuoted(message.text) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(Modifier.widthIn(max = 560.dp)) {
            Column(
                Modifier
                    .clip(UserBubbleShape)
                    .background(colors.userBubble)
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuAnchor = offset
                                menuExpanded = true
                            },
                        )
                    }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                if (content.quote != null) {
                    QuoteContext(content.quote)
                    if (content.body.isNotEmpty()) Spacer(Modifier.height(6.dp))
                }
                if (content.body.isNotEmpty()) {
                    Text(
                        text = content.body,
                        style = DdType.body,
                        color = colors.onUserBubble,
                    )
                }
            }
            MessageActionMenu(
                messageId = message.id,
                expanded = menuExpanded,
                anchor = menuAnchor,
                showShare = false,
                onDismissRequest = { menuExpanded = false },
                onCopy = { onCopy(message.text) },
                onQuote = { onQuote(message.id, message.text) },
                onShare = {},
            )
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
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyOutput: (String) -> Unit,
    onQuote: (Long, String) -> Unit,
) {
    val colors = deepDeepColors()
    val haptic = LocalHapticFeedback.current
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var menuExpanded by remember { mutableStateOf(false) }
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuAnchor = offset
                                menuExpanded = true
                            },
                        )
                    },
            ) {
                ChatMarkdown(answer)
                MessageActionMenu(
                    messageId = message.id,
                    expanded = menuExpanded,
                    anchor = menuAnchor,
                    showShare = true,
                    onDismissRequest = { menuExpanded = false },
                    onCopy = { onCopy(readable) },
                    onQuote = { onQuote(message.id, message.text) },
                    onShare = { onShare(readable) },
                )
            }
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
                onQuote = { onQuote(message.id, message.text) },
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
