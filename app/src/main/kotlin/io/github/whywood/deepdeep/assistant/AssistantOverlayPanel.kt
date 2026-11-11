package io.github.whywood.deepdeep.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.ui.common.chat.ChatTurn
import io.github.whywood.deepdeep.ui.common.chat.rememberChatScrollController
import io.github.whywood.deepdeep.ui.common.chat.AskQuestionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive



@Composable
internal fun AssistantOverlayPanel(
    state: AssistantOverlayState,
    pendingQuestions: List<PendingQuestion>,
    config: AssistantOverlayConfig,
    actions: AssistantOverlayActions,
    onQuote: (Long, String) -> Unit,
    onOpenInApp: () -> Unit,
    onOpenWorkspaceSwitcher: () -> Unit,
    maxHeight: Dp,
    snapHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val chatDensity = remember(density, config.chatFontScale) {
        Density(density = density.density, fontScale = density.fontScale * config.chatFontScale)
    }
    val messages = state.conversation.messages
    val retryActivity = state.conversation.activity as? RuntimeActivity.Retrying
    val retryTargetId = messages.lastOrNull { it.streaming || it.retrying }?.id
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (messages.size - 4).coerceAtLeast(0),
    )
    val scroll = rememberChatScrollController(listState)
    val shape = RoundedCornerShape(28.dp)
    val copiedMessage = stringResource(R.string.assistant_overlay_copied)
    val workspaceName = state.workspaces.firstOrNull { it.id == state.workspaceId }?.name
        ?: stringResource(R.string.assistant_overlay_workspace)
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val itemHeights = remember(state.sessionId) { mutableStateMapOf<Any, Int>() }
    var lastContentHeightPx by remember(state.sessionId) { mutableIntStateOf(-1) }
    val pendingItem = pendingQuestions.firstOrNull { pending ->
        messages.none { message ->
            message.steps.any { step ->
                step is AssistantStep.Tool && step.trace.toolCallId == pending.toolCallId
            }
        }
    }
    val pendingKey = pendingItem?.let { "pending-question-${it.requestId}" }
    val itemKeys = messages.map { it.id as Any } + listOfNotNull(pendingKey)
    val itemSpacingPx = with(density) { 18.dp.roundToPx() }
    val contentPaddingPx = with(density) { 6.dp.roundToPx() + 18.dp.roundToPx() }
    val firstUnmeasuredIndex = itemKeys.indexOfFirst { !itemHeights.containsKey(it) }
    val suffixOnlyMissing = firstUnmeasuredIndex >= 0 &&
        itemKeys.subList(firstUnmeasuredIndex, itemKeys.size).none {
            itemHeights.containsKey(it)
        }
    val measuredContentHeightPx = when {
        itemKeys.isEmpty() -> 0
        firstUnmeasuredIndex < 0 -> contentPaddingPx +
            itemKeys.sumOf { itemHeights.getValue(it) } + itemSpacingPx * (itemKeys.size - 1)
        lastContentHeightPx >= 0 && suffixOnlyMissing -> contentPaddingPx +
            itemKeys.take(firstUnmeasuredIndex).sumOf { itemHeights.getValue(it) } +
            itemSpacingPx * (itemKeys.size - 1)
        else -> -1
    }
    SideEffect {
        if (measuredContentHeightPx >= 0) lastContentHeightPx = measuredContentHeightPx
    }
    val contentHeightPx =
        if (measuredContentHeightPx >= 0) measuredContentHeightPx else lastContentHeightPx
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val contentClamped = contentHeightPx < 0 ||
        headerHeight + with(density) { contentHeightPx.toDp() } + 2.dp >= maxHeight
    val targetHeight = when {
        itemKeys.isEmpty() -> headerHeight + 8.dp
        contentHeightPx < 0 -> maxHeight
        else -> (headerHeight + with(density) { contentHeightPx.toDp() } + 2.dp)
            .coerceIn(headerHeight + 8.dp, maxHeight)
    }
    val panelHeight = animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (snapHeight) snap() else tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "overlay-panel-height",
    )

    LaunchedEffect(state.sessionId, state.conversation.loaded) {
        if (state.conversation.loaded) scroll.jumpToBottom(animate = false)
    }
    LaunchedEffect(pendingQuestions.map { it.requestId }) {
        if (pendingQuestions.isNotEmpty()) {
            delay(60)
            scroll.jumpToBottom(animate = true)
        }
    }
    LaunchedEffect(scroll, contentClamped) {
        if (!contentClamped) return@LaunchedEffect
        snapshotFlow { panelHeight.value }.collect { scroll.anchorToBottom() }
    }
    LaunchedEffect(scroll, state.conversation.sending) {
        if (!state.conversation.sending) {
            if (scroll.following) {
                delay(80)
                scroll.jumpToBottom(animate = false)
            }
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { }
            scroll.followContent()
        }
    }

    val copyOutput: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("deepdeep-assistant", text))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier
            .layout { measurable, constraints ->
                val height = panelHeight.value.roundToPx().coerceIn(0, constraints.maxHeight)
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minWidth,
                        maxWidth = constraints.maxWidth,
                        minHeight = height,
                        maxHeight = height,
                    ),
                )
                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
            }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = if (colors.dark) Color(0xFF071426) else Color(0x332563EB),
                spotColor = if (colors.dark) Color(0x4D0B2A4A) else Color(0x6638BDF8),
            )
            .clip(shape)
            .background(colors.surface)
            .then(
                if (colors.paper.luminance() < 0.5f) {
                    Modifier.border(1.dp, colors.ink.copy(alpha = 0.08f), shape)
                } else {
                    Modifier.border(1.dp, colors.accent.copy(alpha = 0.18f), shape)
                },
            )
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .heightIn(min = 54.dp)
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = workspaceName,
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.sessionTitle.ifBlank { stringResource(R.string.assistant_overlay_title) },
                        modifier = Modifier.testTag("assistant_overlay_panel_title"),
                        style = DdType.label.copy(fontSize = 16.sp, lineHeight = 21.sp),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DdIconButton(
                    icon = DdIcons.Folder,
                    onClick = onOpenWorkspaceSwitcher,
                    contentDescription = stringResource(R.string.assistant_overlay_switch_workspace),
                    modifier = Modifier.testTag("assistant_overlay_workspace_switch"),
                    size = 36.dp,
                    iconSize = 17.dp,
                    tint = colors.inkTertiary,
                )
                DdIconButton(
                    icon = DdIcons.Expand,
                    onClick = onOpenInApp,
                    contentDescription = stringResource(R.string.assistant_overlay_fullscreen),
                    modifier = Modifier.testTag("assistant_overlay_panel_fullscreen"),
                    size = 36.dp,
                    iconSize = 17.dp,
                    tint = colors.inkTertiary,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalDensity provides chatDensity) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("assistant_overlay_transcript"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            Box(Modifier.onSizeChanged { itemHeights[message.id] = it.height }) {
                                ChatTurn(
                                    message = message,
                                    onToggleStep = { stepId -> actions.onToggleStep(message.id, stepId) },
                                    onCopyOutput = copyOutput,
                                    onQuote = onQuote,
                                    onOpenAttachment = actions.onOpenPreview,
                                    onOpenFile = actions.onOpenFile,
                                    onRetry = actions.onRetry,
                                    attachmentRoot = config.attachmentRoot,
                                    autoCollapseProcess = config.autoCollapseProcess,
                                    processDetail = config.processDetail,
                                    pendingQuestions = pendingQuestions,
                                    onAnswerQuestion = actions.onAnswerQuestion,
                                    onOpenPresentFile = actions.onOpenFile,
                                    onSharePresentFile = actions.onShareFile,
                                    onOpenMiniapp = actions.onOpenMiniapp,
                                    retryAttempt = if (message.id == retryTargetId) {
                                        retryActivity?.attempt
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        if (pendingItem != null && pendingKey != null) {
                            item(key = pendingKey) {
                                AskQuestionCard(
                                    questions = pendingItem.questions,
                                    answers = null,
                                    submitted = false,
                                    onAnswer = { answers -> actions.onAnswerQuestion(pendingItem, answers) },
                                    modifier = Modifier.onSizeChanged {
                                        itemHeights[pendingKey] = it.height
                                    },
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.surface, colors.surface.copy(alpha = 0f)),
                            ),
                        ),
                )
            }
        }
    }
}
