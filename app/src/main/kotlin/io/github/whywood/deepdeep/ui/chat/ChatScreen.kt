package io.github.whywood.deepdeep.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentRef
import java.io.File
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.GoalView
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.goal.GoalBar
import io.github.whywood.deepdeep.ui.questions.AskQuestionCard
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ChatScreenActions(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onOpenDrawer: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onReload: () -> Unit,
    val onModelChange: (String) -> Unit,
    val onProviderChange: (String) -> Unit,
    val onThinkingLevelChange: (ThinkingLevel) -> Unit,
    val onToggleStep: (messageId: Long, stepId: Long) -> Unit,
    val onQuoteMessage: (Long, String) -> Unit,
    val onClearQuote: () -> Unit,
    val onPickAttachment: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onOpenAttachment: (AttachmentRef) -> Unit,
    val onOpenFile: (String) -> Unit,
    val onOpenSchedules: () -> Unit,
    val onRetry: (Long) -> Unit,
    val onPickCommand: (CommandItem) -> Unit,
    val onPauseGoal: () -> Unit,
    val onResumeGoal: () -> Unit,
    val onOpenJobs: () -> Unit,
    val onModelsOpenChange: (Boolean) -> Unit,
    val onOpenUsage: () -> Unit,
    val onAnswerQuestion: (PendingQuestion, List<QuestionAnswer>) -> Unit,
    val onOpenForeignQuestion: (String) -> Unit,
    val onOpenPresentFile: (String) -> Unit,
    val onSharePresentFile: (String) -> Unit,
    val onOpenWorkshop: (String) -> Unit,
    val onOpenFiles: () -> Unit,
    val onVoiceClick: (() -> Unit)?,
    val onFollowUp: () -> Unit,
    val onClearQueue: () -> Unit,
    val onScrollAnchorChange: (sessionId: String, index: Int, offset: Int, atBottom: Boolean) -> Unit,
)

data class ChatScreenOverlay(
    val reminderCount: Int,
    val reloading: Boolean,
    val compacting: Boolean = false,
    val commandItems: List<CommandItem> = emptyList(),
    val goal: GoalView? = null,
    val jobCount: Int = 0,
    val modelsOpen: Boolean = false,
    val pendingQuestions: List<PendingQuestion> = emptyList(),
    val foreignQuestion: PendingQuestion? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    nickname: String,
    providers: List<ProviderSummary>,
    attachmentRoot: File,
    chatUiStrings: ChatUiStrings,
    actions: ChatScreenActions,
    overlay: ChatScreenOverlay,
    scrollAnchorFor: (String) -> ScrollAnchor?,
    modifier: Modifier = Modifier,
    autoCollapseProcess: Boolean = true,
    chatFontScale: Float = 1f,
) {
    val (
        onInputChange, onSend, onStop, onOpenDrawer, onOpenSettings, onReload,
        onModelChange, onProviderChange, onThinkingLevelChange, onToggleStep,
        onQuoteMessage, onClearQuote, onPickAttachment, onRemoveAttachment,
        onOpenAttachment, onOpenFile, onOpenSchedules, onRetry, onPickCommand,
        onPauseGoal, onResumeGoal, onOpenJobs, onModelsOpenChange, onOpenUsage,
        onAnswerQuestion, onOpenForeignQuestion, onOpenPresentFile, onSharePresentFile,
        onOpenWorkshop, onOpenFiles, onVoiceClick, onFollowUp, onClearQueue, onScrollAnchorChange,
    ) = actions
    val (reminderCount, reloading, compacting, commandItems, goal, jobCount, modelsOpen, pendingQuestions, foreignQuestion) = overlay
    val attachments = state.attachments
    val queuedInputs = state.queuedInputs
    val importingAttachment = state.importingAttachment
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val controller = rememberChatScrollController(listState)
    val density = LocalDensity.current
    val chatDensity = remember(density, chatFontScale) {
        Density(density = density.density, fontScale = density.fontScale * chatFontScale)
    }
    val imeBottomPx = with(density) { WindowInsets.imeAnimationTarget.getBottom(density) }
    val navigationBottomPx = with(density) { WindowInsets.navigationBars.getBottom(density) }
    val statusTopPx = with(density) { WindowInsets.statusBars.getTop(density) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var stableStatusTopPx by remember { mutableIntStateOf(statusTopPx) }
    var stableNavigationBottomPx by remember { mutableIntStateOf(navigationBottomPx) }
    SideEffect {
        if (windowFocused) {
            stableStatusTopPx = statusTopPx
            stableNavigationBottomPx = navigationBottomPx
        }
    }
    val targetBottomInsetPx = maxOf(imeBottomPx, stableNavigationBottomPx).toFloat()
    val animatedBottomInset = remember { Animatable(0f) }
    LaunchedEffect(targetBottomInsetPx) {
        animatedBottomInset.animateTo(
            targetValue = targetBottomInsetPx,
            animationSpec = tween(DdMotion.BASE + 80, easing = DdMotion.EaseInOut),
        )
    }
    val bottomInset = Modifier.layout { measurable, constraints ->
        val inset = animatedBottomInset.value.roundToInt().coerceAtLeast(0)
        val available = constraints.maxHeight
        if (available == androidx.compose.ui.unit.Constraints.Infinity) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val contentHeight = (available - inset).coerceAtLeast(0)
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = contentHeight),
            )
            layout(placeable.width, placeable.height + inset) { placeable.place(0, 0) }
        }
    }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val copiedMessage = stringResource(R.string.assistant_copied)
    val configured = state.configured
    val messages = state.messages
    val stablePendingQuestions = remember(pendingQuestions) { pendingQuestions }
    val thinkingLevels = remember(state.models, state.modelId) {
        state.models.firstOrNull { it.id == state.modelId }?.thinkingLevels.orEmpty()
    }
    val latestMessages by rememberUpdatedState(state.messages)
    val latestScrollAnchorFor by rememberUpdatedState(scrollAnchorFor)
    val latestScrollAnchorChange by rememberUpdatedState(onScrollAnchorChange)
    val selectionCoordinator = remember { SelectionCoordinator() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestRootOrigin by rememberUpdatedState(rootOrigin)
    val selectionClearMargin = with(density) { 28.dp.toPx() }

    fun copyText(text: String) {
        if (copyToClipboard(context, text)) {
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    var knownCount by remember { mutableIntStateOf(state.messages.size) }
    var knownSessionId by remember { mutableStateOf(state.conversationId) }
    var pendingLocalSend by remember { mutableStateOf(false) }

    LaunchedEffect(state.conversationId, state.historyLoaded) {
        val sessionId = state.conversationId ?: return@LaunchedEffect
        if (!state.historyLoaded) return@LaunchedEffect
        knownCount = state.messages.size
        knownSessionId = sessionId
        val anchor = latestScrollAnchorFor(sessionId)
        if (anchor == null || anchor.atBottom) {
            controller.jumpToBottom(animate = false)
        } else {
            controller.restoreAnchor(anchor.index, anchor.offset)
        }
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                latestScrollAnchorChange(sessionId, index, offset, controller.atBottom)
            }
    }

    LaunchedEffect(controller) {
        snapshotFlow { latestMessages }.collect {
            controller.followContent()
        }
    }

    LaunchedEffect(state.conversationId, state.messages.size) {
        val size = state.messages.size
        val grew = state.conversationId == knownSessionId && size > knownCount
        if (grew && (controller.following || pendingLocalSend)) controller.jumpToBottom(animate = false)
        pendingLocalSend = false
        knownCount = size
        knownSessionId = state.conversationId
    }

    val latestChatState by rememberUpdatedState(state)

    LaunchedEffect(state.sending) {
        if (!state.sending) pendingLocalSend = false
    }

    LaunchedEffect(pendingLocalSend) {
        if (!pendingLocalSend) return@LaunchedEffect
        delay(2_000)
        if (!latestChatState.sending) pendingLocalSend = false
    }

    LaunchedEffect(controller) {
        snapshotFlow { animatedBottomInset.value to composerHeightPx }.collect {
            controller.anchorToBottom()
        }
    }

    LaunchedEffect(state.sending) {
        if (state.sending) controller.followContent()
    }

    LaunchedEffect(pendingQuestions.map { it.requestId }) {
        if (pendingQuestions.isNotEmpty()) {
            delay(60)
            controller.jumpToBottom(animate = true)
        }
    }


    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .then(bottomInset)
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
            state = ChatTopBarState(reminderCount = reminderCount, reloading = reloading),
            actions = ChatTopBarActions(
                onMenu = {
                    keyboard?.hide()
                    onOpenDrawer()
                },
                onReload = {
                    keyboard?.hide()
                    onReload()
                },
                onOpenSchedules = {
                    keyboard?.hide()
                    onOpenSchedules()
                },
                onOpenFiles = {
                    keyboard?.hide()
                    onOpenFiles()
                },
            ),
            modifier = Modifier.padding(top = with(density) { stableStatusTopPx.toDp() }),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.switchingConversation) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DdSpinner(
                        modifier = Modifier.testTag("switching_progress"),
                        color = colors.inkTertiary,
                        size = 18.dp,
                        strokeWidth = 2.dp,
                    )
                }
            } else if (messages.isEmpty()) {
                EmptyState(
                    loaded = state.historyLoaded,
                    nickname = nickname,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CompositionLocalProvider(LocalDensity provides chatDensity) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
                                    ChatTurn(
                                        message = message,
                                        onToggleStep = { stepId -> onToggleStep(message.id, stepId) },
                                        onCopyOutput = ::copyText,
                                        onQuote = onQuoteMessage,
                                        onOpenAttachment = onOpenAttachment,
                                        onOpenFile = onOpenFile,
                                        onRetry = onRetry,
                                        attachmentRoot = attachmentRoot,
                                        autoCollapseProcess = autoCollapseProcess,
                                        pendingQuestions = stablePendingQuestions,
                                        onAnswerQuestion = onAnswerQuestion,
                                        onOpenPresentFile = onOpenPresentFile,
                                        onSharePresentFile = onSharePresentFile,
                                        onOpenWorkshop = onOpenWorkshop,
                                    )
                                }
                            }
                        }
                        stablePendingQuestions
                            .firstOrNull { pending ->
                                messages.none { message ->
                                    message.steps.any { step ->
                                        step is AssistantStep.Tool && step.trace.toolCallId == pending.toolCallId
                                    }
                                }
                            }
                            ?.let { pending ->
                                item(key = "pending-question-${pending.requestId}") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                        Box(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
                                            AskQuestionCard(
                                                questions = pending.questions,
                                                answers = null,
                                                submitted = false,
                                                onAnswer = { answers -> onAnswerQuestion(pending, answers) },
                                            )
                                        }
                                    }
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
                    activityText = chatUiStrings.activity(state.activity),
                    startedAt = state.activityStartedAt,
                    modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth(),
                )
            }
        }
        AnimatedVisibility(
            visible = compacting || state.compacting,
            enter = fadeIn(tween(DdMotion.FAST)) + expandVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
            exit = fadeOut(tween(DdMotion.FAST)) + shrinkVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.chat_compacting),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                )
            }
        }
        foreignQuestion?.let { foreign ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.raised)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("chat_foreign_question"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_question_other),
                        modifier = Modifier.weight(1f),
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = colors.inkSecondary,
                    )
                    DdTextAction(
                        text = stringResource(R.string.chat_question_other_action),
                        onClick = { onOpenForeignQuestion(foreign.sessionId) },
                    )
                }
            }
        }
        goal?.let { view ->
            GoalBar(
                view = view,
                onPause = onPauseGoal,
                onResume = onResumeGoal,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        state.error?.let { ErrorRow(it) }
        val paletteQuery = commandQuery(state.input)
        var lastPaletteQuery by remember { mutableStateOf("") }
        LaunchedEffect(paletteQuery) {
            if (paletteQuery != null) lastPaletteQuery = paletteQuery
        }
        AnimatedVisibility(
            visible = paletteQuery != null && !state.sending,
            enter = fadeIn(tween(DdMotion.FAST)) + expandVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
            exit = fadeOut(tween(DdMotion.FAST)) + shrinkVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
        ) {
            SlashPalette(
                items = commandItems,
                query = paletteQuery ?: lastPaletteQuery,
                onPick = { item -> onPickCommand(item) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        ChatComposer(
            state = ChatComposerState(
                value = state.input,
                ready = state.ready && state.historyLoaded && !state.switchingConversation,
                sending = state.sending,
                configured = configured,
                modelLabel = if (configured) state.modelLabel else "",
                modelId = state.modelId,
                models = state.models,
                providerId = state.providerId,
                providers = providers,
                thinkingLevel = state.thinkingLevel,
                thinkingLevels = thinkingLevels,
                quote = state.quote,
                attachments = attachments,
                attachmentRoot = attachmentRoot,
                queuedInputs = queuedInputs,
                importingAttachment = importingAttachment,
                modelsOpen = modelsOpen,
            ),
            actions = ChatComposerActions(
                onValueChange = onInputChange,
                onSend = {
                    if (!state.sending) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        pendingLocalSend = true
                    }
                    onSend()
                },
                onStop = onStop,
                onModelChange = onModelChange,
                onProviderChange = onProviderChange,
                onThinkingLevelChange = onThinkingLevelChange,
                onClearQuote = onClearQuote,
                onPickAttachment = onPickAttachment,
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachment = onOpenAttachment,
                onOpenSettings = onOpenSettings,
                onFollowUp = onFollowUp,
                onClearQueue = onClearQueue,
                onVoiceClick = onVoiceClick?.let { openVoice ->
                    {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        openVoice()
                    }
                },
                onModelsOpenChange = onModelsOpenChange,
            ),
            usage = state.usage,
            jobCount = jobCount,
            onOpenUsage = onOpenUsage,
            onOpenJobs = onOpenJobs,
            modifier = Modifier
                .onSizeChanged { composerHeightPx = it.height }
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        )
        }
    }
}

@Composable
private fun EmptyState(
    loaded: Boolean,
    nickname: String,
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
                    Image(
                        painter = painterResource(R.drawable.mascot_welcome),
                        contentDescription = null,
                        modifier = Modifier.size(148.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = greeting(nickname),
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
private fun greeting(nickname: String): String {
    val base = when (LocalTime.now().hour) {
        in 5..10 -> stringResource(R.string.assistant_greeting_morning)
        in 11..17 -> stringResource(R.string.assistant_greeting_afternoon)
        else -> stringResource(R.string.assistant_greeting_evening)
    }
    return if (nickname.isBlank()) base else "$base，$nickname"
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
