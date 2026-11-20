package io.github.whywood.superpi.ui.remoteagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.data.relay.RelayLiveState
import io.github.whywood.superpi.design.DdEmptyState
import io.github.whywood.superpi.design.DdIcon
import io.github.whywood.superpi.design.DdIconButton
import io.github.whywood.superpi.design.DdIcons
import io.github.whywood.superpi.design.DdNoticeBar
import io.github.whywood.superpi.design.DdNoticeTone
import io.github.whywood.superpi.design.DdPageScaffold
import io.github.whywood.superpi.design.DdPressable
import io.github.whywood.superpi.design.DdSpacing
import io.github.whywood.superpi.design.DdSpinner
import io.github.whywood.superpi.design.DdTextField
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import io.github.whywood.superpi.ui.common.chat.ChatMarkdown

@Composable
internal fun RemoteChatScreen(
    deviceName: String,
    botName: String,
    deviceOnline: Boolean,
    relayState: RelayLiveState,
    blocks: List<RemoteChatBlock>,
    running: Boolean,
    settled: Boolean,
    canAbort: Boolean,
    sendFailure: RemoteChatFailure?,
    onBack: () -> Unit,
    onSend: (String) -> Boolean,
    onRetry: () -> Unit,
    onAbort: () -> Unit,
    onOpenRelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = superPiColors()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val relayOnline = relayState == RelayLiveState.Online
    val canSend = relayOnline && deviceOnline && settled && !running && input.isNotBlank()
    val submit = {
        if (onSend(input)) input = ""
    }
    LaunchedEffect(blocks, running) {
        if (blocks.isNotEmpty()) listState.scrollToItem(blocks.lastIndex)
    }
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.remoteagent_back),
                iconSize = 21.dp,
                tint = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = botName,
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (deviceOnline) colors.success else colors.inkTertiary),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.remoteagent_device_status,
                            deviceName,
                            stringResource(
                                if (deviceOnline) {
                                    R.string.remoteagent_device_online
                                } else {
                                    R.string.remoteagent_device_offline
                                },
                            ),
                        ),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier.testTag("remoteagent_screen"),
    ) {
        val waiting = blocks.isNotEmpty() && !deviceOnline
        val reconnecting = relayState == RelayLiveState.Connecting || relayState == RelayLiveState.Offline
        if (!relayOnline || waiting) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
            ) {
                if (!relayOnline) {
                    DdNoticeBar(
                        text = stringResource(
                            if (reconnecting) R.string.remoteagent_reconnecting else R.string.remoteagent_not_connected,
                        ),
                        tone = DdNoticeTone.WARNING,
                        actionLabel = if (reconnecting) null else stringResource(R.string.remoteagent_open_relay),
                        onAction = if (reconnecting) null else onOpenRelay,
                    )
                } else if (waiting) {
                    DdNoticeBar(
                        text = stringResource(R.string.remoteagent_waiting),
                        tone = DdNoticeTone.WARNING,
                    )
                }
            }
        }
        if (blocks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                DdEmptyState(
                    text = stringResource(R.string.remoteagent_empty),
                    icon = DdIcons.Desktop,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = DdSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(DdSpacing.Md),
            ) {
                itemsIndexed(blocks, key = { _, block -> block.key }) { index, block ->
                    when (block) {
                        is RemoteChatBlock.User -> RemoteChatUserRow(block)
                        is RemoteChatBlock.Assistant -> RemoteChatAssistantRow(
                            block = block,
                            live = running && index == blocks.lastIndex,
                        )

                        is RemoteChatBlock.Tool -> RemoteChatToolRow(block = block, running = running)
                        is RemoteChatBlock.Failure -> RemoteChatFailureRow(block.failure)
                    }
                }
            }
        }
        sendFailure?.let { failure ->
            val openRelay = failure == RemoteChatFailure.NotConnected
            val botGone = failure == RemoteChatFailure.BotUnavailable
            DdNoticeBar(
                text = failure.text(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = DdSpacing.Xs),
                tone = DdNoticeTone.DANGER,
                actionLabel = when {
                    openRelay -> stringResource(R.string.remoteagent_open_relay)
                    botGone -> null
                    else -> stringResource(R.string.remoteagent_retry)
                },
                onAction = when {
                    openRelay -> onOpenRelay
                    botGone -> null
                    else -> onRetry
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                DdTextField(
                    value = input,
                    onValueChange = { value -> if (!running) input = value },
                    placeholder = stringResource(R.string.remoteagent_input_placeholder),
                    modifier = Modifier.heightIn(max = 132.dp),
                    singleLine = false,
                    testTag = "remoteagent_input",
                )
            }
            Spacer(Modifier.width(DdSpacing.Sm))
            RemoteChatSendButton(
                running = running,
                enabled = if (running) canAbort else canSend,
                onClick = { if (running) onAbort() else submit() },
                sendDescription = stringResource(R.string.remoteagent_send),
                stopDescription = stringResource(R.string.remoteagent_stop),
            )
        }
    }
}

@Composable
private fun RemoteChatSendButton(
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    sendDescription: String,
    stopDescription: String,
) {
    val colors = superPiColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.size(44.dp).testTag("remoteagent_send"),
        shape = CircleShape,
        background = if (enabled) colors.accent else colors.wash,
        enabled = enabled,
        contentDescription = if (running) stopDescription else sendDescription,
    ) {
        if (running) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (enabled) colors.onAccent else colors.inkTertiary),
            )
        } else {
            DdIcon(
                icon = DdIcons.ArrowUp,
                tint = if (enabled) colors.onAccent else colors.inkTertiary,
                size = 20.dp,
            )
        }
    }
}

@Composable
private fun RemoteChatUserRow(block: RemoteChatBlock.User) {
    val colors = superPiColors()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleWidth = maxWidth * 0.82f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = bubbleWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.userBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = block.text,
                    style = DdType.body,
                    color = colors.onUserBubble,
                )
            }
        }
    }
}

@Composable
private fun RemoteChatAssistantRow(block: RemoteChatBlock.Assistant, live: Boolean) {
    val colors = superPiColors()
    Column(Modifier.fillMaxWidth()) {
        if (block.reasoning.isNotBlank()) {
            Text(
                text = block.reasoning,
                modifier = Modifier.padding(bottom = 6.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        }
        if (block.text.isNotBlank()) {
            ChatMarkdown(
                text = block.text,
                modifier = Modifier.fillMaxWidth(),
                live = live,
            )
        }
    }
}

@Composable
private fun RemoteChatToolRow(block: RemoteChatBlock.Tool, running: Boolean) {
    val colors = superPiColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(icon = DdIcons.Terminal, tint = colors.inkTertiary, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = block.name.ifBlank { block.callId },
                modifier = Modifier.weight(1f),
                style = DdType.label,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                block.result == null && running -> DdSpinner(
                    size = 12.dp,
                    strokeWidth = 1.5.dp,
                    color = colors.inkTertiary,
                )

                block.isError -> Text(
                    text = stringResource(R.string.remoteagent_tool_failed),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.danger,
                )
            }
        }
        if (block.arguments.isNotBlank()) {
            Text(
                text = block.arguments,
                modifier = Modifier.padding(top = 4.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val result = block.result
        if (!result.isNullOrBlank()) {
            Text(
                text = result,
                modifier = Modifier.padding(top = 4.dp),
                style = DdType.bodySmall,
                color = if (block.isError) colors.danger else colors.inkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteChatFailureRow(failure: RemoteChatFailure) {
    val colors = superPiColors()
    Text(
        text = failure.text(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.danger.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = DdType.bodySmall,
        color = colors.danger,
    )
}

@Composable
private fun RemoteChatFailure.text(): String = when (this) {
    RemoteChatFailure.NotConnected -> stringResource(R.string.remoteagent_error_not_connected)
    RemoteChatFailure.Disconnected -> stringResource(R.string.remoteagent_error_disconnected)
    RemoteChatFailure.Timeout -> stringResource(R.string.remoteagent_error_timeout)
    RemoteChatFailure.DeviceOffline -> stringResource(R.string.remoteagent_error_device_offline)
    RemoteChatFailure.BotUnavailable -> stringResource(R.string.remoteagent_error_bot_missing)
    RemoteChatFailure.Rejected -> stringResource(R.string.remoteagent_error_rejected)
    is RemoteChatFailure.Remote -> message.ifBlank { stringResource(R.string.remoteagent_error_run) }
}
