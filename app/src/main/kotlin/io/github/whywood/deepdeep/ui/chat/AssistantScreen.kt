package io.github.whywood.deepdeep.ui.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.DeepSeekModels
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import java.time.LocalTime
import kotlinx.coroutines.delay

private sealed interface AssistantSheet {
    data object Model : AssistantSheet
    data object Thinking : AssistantSheet
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    configured: Boolean,
    configuredModel: String?,
    userName: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onModelChange: (String?) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onToggleStep: (messageId: Long, stepId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val modelOptions = remember { DeepSeekModels.options() }
    var sheet by remember { mutableStateOf<AssistantSheet?>(null) }
    var previousCount by remember { mutableIntStateOf(0) }
    val copiedMessage = stringResource(R.string.assistant_copied)
    val shareChooserTitle = stringResource(R.string.assistant_share_chooser)
    val shareUnavailable = stringResource(R.string.assistant_share_unavailable)
    val activeModelId = state.modelOverride ?: configuredModel
    val activeOption = modelOptions.firstOrNull { it.id == activeModelId }
    val thinkingLevels = activeOption?.thinkingLevels.orEmpty()
    val thinkingLevel = state.thinkingLevel.takeIf { it in thinkingLevels }
        ?: ThinkingLevel.OFF.takeIf { it in thinkingLevels }
        ?: thinkingLevels.firstOrNull()
        ?: ThinkingLevel.OFF
    val modelLabel = modelDisplayName(
        options = modelOptions,
        model = activeModelId,
        fallback = stringResource(R.string.assistant_model_unconfigured),
    )

    fun copyAnswer(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("deepdeep", text))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    fun shareAnswer(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, shareChooserTitle))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, shareUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun scrollToBottom() {
        if (state.messages.isEmpty()) return
        listState.scrollToItem(state.messages.lastIndex, 100_000)
    }

    LaunchedEffect(state.messages.size) {
        val grew = state.messages.size > previousCount
        previousCount = state.messages.size
        if (grew) scrollToBottom()
    }

    LaunchedEffect(state.conversationId) {
        scrollToBottom()
    }

    val streamed = state.messages.lastOrNull()
    LaunchedEffect(state.sending, streamed?.text?.length, streamed?.steps?.size) {
        if (state.sending) scrollToBottom()
    }

    LaunchedEffect(imeVisible) {
        if (!imeVisible || state.messages.isEmpty()) return@LaunchedEffect
        delay(350)
        listState.animateScrollToItem(state.messages.lastIndex, 100_000)
    }

    Box(modifier.fillMaxSize().background(colors.paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            AssistantTopBar(
                showNewChat = state.messages.isNotEmpty(),
                onMenu = {
                    keyboard?.hide()
                    onOpenDrawer()
                },
                onNewChat = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onNewChat()
                },
            )

            AnimatedContent(
                targetState = state.messages.isEmpty(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    fadeIn(tween(DdMotion.FAST)) togetherWith fadeOut(tween(DdMotion.FAST))
                },
                label = "assistant-body",
            ) { home ->
                if (home) {
                    HomeIntro(userName = userName)
                } else {
                    MessageList(
                        messages = state.messages,
                        listState = listState,
                        onToggleStep = onToggleStep,
                        onCopy = ::copyAnswer,
                        onShare = ::shareAnswer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            state.error?.let { ErrorRow(it) }

            AssistantComposer(
                value = state.input,
                configured = configured,
                modelLabel = modelLabel,
                thinkingLevel = thinkingLevel,
                thinkingLevels = thinkingLevels,
                sending = state.sending,
                onValueChange = onInputChange,
                onSend = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSend()
                },
                onStop = onStop,
                onModel = {
                    keyboard?.hide()
                    if (configured) sheet = AssistantSheet.Model else onOpenSettings()
                },
                onThinking = {
                    keyboard?.hide()
                    if (configured) {
                        sheet = AssistantSheet.Thinking
                    } else {
                        onOpenSettings()
                    }
                },
                onOpenSettings = onOpenSettings,
            )
        }
    }

    when (sheet) {
        AssistantSheet.Model -> AssistantModelSheet(
            options = modelOptions,
            modelOverride = state.modelOverride,
            configuredModel = configuredModel,
            onSelect = {
                onModelChange(it)
                sheet = null
            },
            onClose = { sheet = null },
        )
        AssistantSheet.Thinking -> AssistantThinkingSheet(
            levels = thinkingLevels,
            selected = thinkingLevel,
            onSelect = {
                onThinkingLevelChange(it)
                sheet = null
            },
            onClose = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun AssistantTopBar(
    showNewChat: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = deepDeepColors()
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
        DdIconButton(
            icon = DdIcons.Menu,
            onClick = onMenu,
            contentDescription = stringResource(R.string.assistant_menu),
            modifier = Modifier.align(Alignment.CenterStart),
            tint = colors.ink,
            iconSize = 21.dp,
        )
        DdWordmark(Modifier.align(Alignment.Center))
        if (showNewChat) {
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = onNewChat,
                contentDescription = stringResource(R.string.assistant_new_chat),
                modifier = Modifier.align(Alignment.CenterEnd),
                tint = colors.inkSecondary,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun HomeIntro(userName: String) {
    val colors = deepDeepColors()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> stringResource(R.string.assistant_greeting_morning, userName)
        in 11..17 -> stringResource(R.string.assistant_greeting_afternoon, userName)
        else -> stringResource(R.string.assistant_greeting_evening, userName)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
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
                    text = greeting,
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

@Composable
private fun MessageList(
    messages: List<AssistantMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleStep: (messageId: Long, stepId: Long) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(
                message = message,
                onToggleStep = { stepId -> onToggleStep(message.id, stepId) },
                onCopy = onCopy,
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun MessageItem(
    message: AssistantMessage,
    onToggleStep: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val colors = deepDeepColors()
    if (message.role == AssistantRole.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomEnd = 6.dp,
                            bottomStart = 20.dp,
                        ),
                    )
                    .background(colors.userBubble)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.text,
                    style = DdType.body,
                    color = colors.onUserBubble,
                )
            }
        }
        return
    }

    val answer = message.answerText()
    val readable = remember(answer) { markdownToPlainText(answer) }
    val stopped = message.text.trimEnd().endsWith(STOPPED_MARKER)
    val lastStepId = message.steps.lastOrNull()?.id
    Column(Modifier.fillMaxWidth()) {
        message.steps.forEachIndexed { index, step ->
            when (step) {
                is AssistantStep.Reasoning -> ReasoningBlock(
                    step = step,
                    running = message.streaming && answer.isBlank() && step.id == lastStepId,
                    onToggle = { onToggleStep(step.id) },
                )
                is AssistantStep.Tool -> ToolTraceBlock(
                    tool = step.trace,
                    onToggle = { onToggleStep(step.trace.id) },
                )
            }
            if (index != message.steps.lastIndex) Spacer(Modifier.height(2.dp))
        }
        if (message.steps.isNotEmpty() && (answer.isNotBlank() || stopped)) {
            Spacer(Modifier.height(10.dp))
        }
        if (answer.isNotBlank()) {
            AssistantMarkdown(answer)
        }
        if (stopped) {
            if (answer.isNotBlank()) Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.assistant_stopped),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.inkTertiary,
            )
        }
        if (!message.streaming) {
            AssistantActionBar(
                messageId = message.id,
                answer = readable,
                onCopy = { onCopy(readable) },
                onShare = { onShare(readable) },
            )
        }
    }
}

@Composable
private fun ErrorRow(error: String) {
    val colors = deepDeepColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        DdHairline()
        Text(
            text = error,
            modifier = Modifier.padding(top = 10.dp),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
    }
}
