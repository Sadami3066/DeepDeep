package io.github.whywood.deepdeep.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentRef
import java.io.File
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onModelChange: (String) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onToggleStep: (messageId: Long, stepId: Long) -> Unit,
    onQuoteMessage: (Long, String) -> Unit,
    onClearQuote: () -> Unit,
    attachments: List<AttachmentRef>,
    onPickAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    onOpenFile: (String) -> Unit,
    attachmentRoot: File,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val controller = rememberChatScrollController(listState)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val copiedMessage = stringResource(R.string.assistant_copied)
    val shareChooserTitle = stringResource(R.string.assistant_share_chooser)
    val shareUnavailable = stringResource(R.string.assistant_share_unavailable)
    val configured = state.configured
    val thinkingLevels = remember(state.models, state.modelId) {
        state.models.firstOrNull { it.id == state.modelId }?.thinkingLevels.orEmpty()
    }
    val latestMessages by rememberUpdatedState(state.messages)
    val selectionCoordinator = remember { SelectionCoordinator() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestRootOrigin by rememberUpdatedState(rootOrigin)
    val selectionClearMargin = with(density) { 28.dp.toPx() }

    fun copyText(text: String) {
        if (copyToClipboard(context, text)) {
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAnswer(text: String) {
        if (!shareText(context, text, shareChooserTitle)) {
            Toast.makeText(context, shareUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(controller) {
        snapshotFlow { latestMessages }.collect { controller.followContent() }
    }

    var knownCount by remember { mutableIntStateOf(state.messages.size) }
    LaunchedEffect(state.messages.size) {
        val size = state.messages.size
        if (size > knownCount) controller.jumpToBottom(animate = false)
        knownCount = size
    }

    LaunchedEffect(state.conversationId, state.historyLoaded) {
        if (state.historyLoaded) controller.jumpToBottom(animate = false)
    }

    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            delay(260)
            controller.followContent()
        }
    }

    LaunchedEffect(state.sending) {
        if (state.sending) {
            delay(320)
            controller.followContent()
        }
    }


    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
            .pointerInput(selectionCoordinator, selectionClearMargin) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val windowPosition = down.position + latestRootOrigin
                    if (!selectionCoordinator.isInsideSelectionArea(windowPosition, selectionClearMargin)) {
                        selectionCoordinator.clear()
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalSelectionCoordinator provides selectionCoordinator) {
        ChatTopBar(
            onMenu = {
                keyboard?.hide()
                onOpenDrawer()
            },
            onNewChat = {
                keyboard?.hide()
                focusManager.clearFocus()
                onNewChat()
            },
            onOpenSettings = {
                keyboard?.hide()
                onOpenSettings()
            },
            modifier = Modifier.statusBarsPadding(),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.switchingConversation) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).testTag("switching_progress"),
                        color = colors.inkTertiary,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent,
                    )
                }
            } else if (state.messages.isEmpty()) {
                EmptyState(loaded = state.historyLoaded, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
                                ChatTurn(
                                    message = message,
                                    onToggleStep = { stepId -> onToggleStep(message.id, stepId) },
                                    onCopy = ::copyText,
                                    onShare = ::shareAnswer,
                                    onCopyOutput = ::copyText,
                                    onQuote = onQuoteMessage,
                                    onOpenAttachment = onOpenAttachment,
                                    onOpenFile = onOpenFile,
                                    attachmentRoot = attachmentRoot,
                                )
                            }
                        }
                    }
                }
            }
            if (controller.showJumpToBottom) {
                JumpToBottomChip(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    onClick = { scope.launch { controller.jumpToBottom(animate = true) } },
                )
            }
        }
        AnimatedVisibility(
            visible = state.sending,
            enter = fadeIn(tween(DdMotion.FAST)) + expandVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
            exit = fadeOut(tween(DdMotion.FAST)) + shrinkVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 18.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                WorkingIndicator(
                    activity = state.activity,
                    startedAt = state.activityStartedAt,
                    modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth(),
                )
            }
        }
        state.error?.let { ErrorRow(it) }
        ChatComposer(
            value = state.input,
            ready = state.ready && state.historyLoaded && !state.switchingConversation,
            sending = state.sending,
            configured = configured,
            modelLabel = if (configured) state.modelLabel else "",
            modelId = state.modelId,
            models = state.models,
            thinkingLevel = state.thinkingLevel,
            thinkingLevels = thinkingLevels,
            quote = state.quote,
            attachments = attachments,
            attachmentRoot = attachmentRoot,
            onValueChange = onInputChange,
            onSend = {
                keyboard?.hide()
                focusManager.clearFocus()
                onSend()
            },
            onStop = onStop,
            onModelChange = onModelChange,
            onThinkingLevelChange = onThinkingLevelChange,
            onClearQuote = onClearQuote,
            onPickAttachment = onPickAttachment,
            onRemoveAttachment = onRemoveAttachment,
            onOpenAttachment = onOpenAttachment,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        )
        }
    }
}

@Composable
private fun EmptyState(
    loaded: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Box(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = stringResource(R.string.assistant_loading),
                style = DdType.body,
                color = colors.inkTertiary,
            )
        } else {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(DdMotion.PAGE, easing = DdMotion.EaseOut)) +
                    slideInVertically(
                        animationSpec = tween(DdMotion.PAGE, easing = DdMotion.EaseOut),
                        initialOffsetY = { it / 18 },
                    ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = greeting(),
                        modifier = Modifier.testTag("home_greeting"),
                        style = DdType.display,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.assistant_home_prompt),
                        style = DdType.body,
                        color = colors.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> stringResource(R.string.assistant_greeting_morning)
    in 11..17 -> stringResource(R.string.assistant_greeting_afternoon)
    else -> stringResource(R.string.assistant_greeting_evening)
}

@Composable
private fun ErrorRow(error: String) {
    val colors = deepDeepColors()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = 780.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.wash)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = error,
                style = DdType.bodySmall.copy(lineHeight = 20.sp),
                color = colors.inkSecondary,
            )
        }
    }
}

@Composable
private fun JumpToBottomChip(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .border(1.dp, colors.hairline.copy(alpha = 0.65f), CircleShape)
            .testTag("jump_to_bottom"),
        shape = CircleShape,
        background = colors.surface,
        contentDescription = stringResource(R.string.assistant_jump_bottom),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkSecondary, size = 13.dp)
            Text(
                text = stringResource(R.string.assistant_jump_bottom),
                style = DdType.chip,
                color = colors.inkSecondary,
            )
        }
    }
}
