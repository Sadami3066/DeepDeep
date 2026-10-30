package io.github.whywood.deepdeep.assistant

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.ui.chat.ChatInputField
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ChatTurn
import io.github.whywood.deepdeep.ui.chat.WorkingIndicator
import io.github.whywood.deepdeep.ui.chat.rememberChatScrollController
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.TextSpeaker
import io.github.whywood.deepdeep.voice.VoiceLanguage
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(28.dp)
private val SuggestionShape = RoundedCornerShape(12.dp)
private val ActionShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantOverlayScreen(
    state: AssistantOverlayState,
    chatUiStrings: ChatUiStrings,
    autoSpeak: Boolean,
    sendAfterRecognition: Boolean,
    autoCollapseProcess: Boolean,
    chatFontScale: Float,
    language: AppLanguage,
    attachmentRoot: File,
    pendingQuestion: Boolean,
    modelLabel: String,
    onOpenQuestion: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInApp: (String) -> Unit,
    onAsk: (String) -> Boolean,
    onStop: () -> Unit,
    onRetry: (Long) -> Unit,
    onToggleStep: (Long, Long) -> Unit,
    onRetryStart: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.assistant_overlay_copied)
    val openInAppMessage = stringResource(R.string.assistant_overlay_open_in_app_file)
    val closeDescription = stringResource(R.string.assistant_overlay_close)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val speaker = remember(context) { TextSpeaker(context) }
    val recognizer = remember(context) { SpeechRecognizerController(context) }
    val activity = remember(context) { context.findActivity() }
    val density = LocalDensity.current
    val chatDensity = remember(density, chatFontScale) {
        Density(density = density.density, fontScale = density.fontScale * chatFontScale)
    }
    val listState = rememberLazyListState()
    val scroll = rememberChatScrollController(listState)
    var input by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceFailure by remember { mutableStateOf<SpeechError?>(null) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var listenJob by remember { mutableStateOf<Job?>(null) }
    var wasSending by remember { mutableStateOf(state.conversation.sending) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val messages = state.conversation.messages
    val busy = state.conversation.sending
    val latestMessages by rememberUpdatedState(messages)

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        listening = false
    }

    fun dismiss() {
        stopListening()
        speaker.stop()
        onDismiss()
    }

    val send: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            stopListening()
            speaker.stop()
            voiceFailure = null
            keyboard?.hide()
            focusManager.clearFocus()
            if (onAsk(text)) input = ""
        }
    }

    fun beginListening() {
        stopListening()
        speaker.stop()
        transcript = ""
        voiceFailure = null
        listening = true
        listenJob = scope.launch {
            recognizer.listen(VoiceLanguage.recognitionTag(language)).collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> transcript = event.text
                    is SpeechEvent.Volume -> Unit
                    is SpeechEvent.Final -> {
                        listening = false
                        listenJob = null
                        transcript = ""
                        val text = event.text.trim()
                        if (text.isEmpty()) {
                            voiceFailure = SpeechError.NO_MATCH
                        } else if (sendAfterRecognition && input.isBlank() && onAsk(text)) {
                            input = ""
                        } else {
                            input = if (input.isBlank()) text else input.trimEnd() + " " + text
                        }
                    }

                    is SpeechEvent.Failed -> {
                        listening = false
                        listenJob = null
                        voiceFailure = event.error
                    }

                    SpeechEvent.Timeout -> {
                        listening = false
                        listenJob = null
                        voiceFailure = SpeechError.SPEECH_TIMEOUT
                    }
                }
            }
            listening = false
            listenJob = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            permissionPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO,
                )
            voiceFailure = SpeechError.INSUFFICIENT_PERMISSIONS
        }
    }

    val requestListening: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val toggleListening: () -> Unit = {
        if (listening) {
            stopListening()
        } else {
            requestListening()
        }
    }

    DisposableEffect(speaker) {
        onDispose {
            listenJob?.cancel()
            listenJob = null
            speaker.stop()
            speaker.release()
        }
    }

    LaunchedEffect(state.sessionId, state.conversation.loaded) {
        if (state.conversation.loaded) {
            scroll.jumpToBottom(animate = false)
        }
    }

    LaunchedEffect(scroll) {
        snapshotFlow { latestMessages }.collect {
            scroll.followContent()
        }
    }

    LaunchedEffect(state.conversation.sending) {
        if (state.conversation.sending) {
            wasSending = true
            return@LaunchedEffect
        }
        if (!wasSending) return@LaunchedEffect
        wasSending = false
        if (!autoSpeak) return@LaunchedEffect
        val answer = messages.lastOrNull()
            ?.takeIf { it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.DONE }
            ?.text
        if (!answer.isNullOrBlank()) {
            speaker.speak(answer, language)
        }
    }

    val openPermissionSettings: () -> Unit = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    val copyOutput: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("deepdeep-assistant", text))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    val openInAppNotice: (String) -> Unit = {
        Toast.makeText(context, openInAppMessage, Toast.LENGTH_SHORT).show()
    }

    val quote: (Long, String) -> Unit = { _, text ->
        val quoted = text.trim().lineSequence().joinToString("\n") { line -> "> $line" }
        input = if (input.isBlank()) "$quoted\n\n" else "$quoted\n\n$input"
    }

    val imeBottomPx = with(density) { WindowInsets.imeAnimationTarget.getBottom(density) }
    val navigationBottomPx = with(density) { WindowInsets.navigationBars.getBottom(density) }
    val statusTopPx = with(density) { WindowInsets.statusBars.getTop(density) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var stableNavigationBottomPx by remember { mutableIntStateOf(navigationBottomPx) }
    SideEffect {
        if (windowFocused) {
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
    val bottomGapPx = with(density) { 8.dp.toPx() }.roundToInt()
    val minimumCardPx = with(density) { 160.dp.toPx() }.roundToInt()
    val dragThresholdPx = with(density) { 96.dp.toPx() }
    val cardContainer = Modifier.layout { measurable, constraints ->
        val inset = animatedBottomInset.value.roundToInt().coerceAtLeast(0)
        val available = constraints.maxHeight
        if (available == Constraints.Infinity) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val usable = (available - statusTopPx - inset - bottomGapPx).coerceAtLeast(0)
            val maxCardHeight = (usable * 0.78f).roundToInt().coerceAtLeast(minimumCardPx)
            val placeable = measurable.measure(
                constraints.copy(
                    minHeight = 0,
                    maxHeight = maxCardHeight.coerceAtMost((available - inset - bottomGapPx).coerceAtLeast(0)),
                ),
            )
            layout(placeable.width, placeable.height + inset + bottomGapPx) {
                placeable.place(0, 0)
            }
        }
    }

    fun settleCard(animatedOffset: Float) {
        scope.launch {
            animate(
                initialValue = animatedOffset,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> dragOffset = value }
        }
    }

    val headerDrag = Modifier.pointerInput(dragThresholdPx) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dragOffset > dragThresholdPx) {
                    scope.launch {
                        animate(
                            initialValue = dragOffset,
                            targetValue = dragThresholdPx * 2f,
                            animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                        ) { value, _ -> dragOffset = value }
                        dismiss()
                    }
                } else {
                    settleCard(dragOffset)
                }
            },
            onDragCancel = { settleCard(dragOffset) },
        ) { _, dragAmount ->
            dragOffset = (dragOffset + dragAmount).coerceIn(0f, dragThresholdPx * 2f)
        }
    }

    LaunchedEffect(scroll) {
        snapshotFlow { animatedBottomInset.value }.collect {
            scroll.anchorToBottom()
        }
    }

    val lastAnswer = messages.lastOrNull {
        it.role == AssistantRole.ASSISTANT && it.notice == null && it.summary == null
    }?.takeIf { it.status == MessageStatus.DONE && !busy }?.let(::answerText).orEmpty()
    val showActions = lastAnswer.isNotEmpty()
    val speaking = speaker.speaking
    val copyDescription = stringResource(R.string.assistant_overlay_copy)
    val speakDescription = stringResource(
        if (speaking) R.string.assistant_overlay_stop_speaking else R.string.assistant_overlay_speak,
    )
    val shareDescription = stringResource(R.string.assistant_overlay_share)
    val openAppDescription = stringResource(R.string.assistant_overlay_open_app)

    val copyAnswer: () -> Unit = { copyOutput(lastAnswer) }
    val toggleSpeak: () -> Unit = {
        if (speaker.speaking) speaker.stop() else speaker.speak(lastAnswer, language)
    }
    val shareAnswer: () -> Unit = {
        val payload = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, lastAnswer)
        runCatching { context.startActivity(Intent.createChooser(payload, null)) }
    }

    LaunchedEffect(listening, voiceFailure, state.error, showActions) {
        scroll.anchorToBottom()
    }

    Box(Modifier.fillMaxSize().testTag("assistant_overlay")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.ink.copy(alpha = 0.10f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(cardContainer),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = dragOffset
                        alpha = (
                            1f - (dragOffset - dragThresholdPx).coerceAtLeast(0f) / dragThresholdPx
                            ).coerceIn(0f, 1f)
                    }
                    .shadow(
                        elevation = 12.dp,
                        shape = CardShape,
                        clip = false,
                        ambientColor = Color(0xFF0B1220),
                        spotColor = Color(0x3D0B1220),
                    )
                    .clip(CardShape)
                    .background(colors.surface)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.fillMaxWidth().then(headerDrag)) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 44.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(colors.inkTertiary.copy(alpha = 0.4f)),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_overlay_title),
                            modifier = Modifier.weight(1f),
                            style = DdType.label.copy(fontSize = 18.sp, lineHeight = 24.sp),
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (modelLabel.isNotBlank()) {
                            Row(
                                Modifier
                                    .widthIn(max = 110.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.ink.copy(alpha = 0.05f))
                                    .padding(horizontal = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = modelLabel,
                                    modifier = Modifier.testTag("assistant_overlay_model"),
                                    style = DdType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                    color = colors.inkTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        DdIconButton(
                            icon = DdIcons.Close,
                            onClick = ::dismiss,
                            contentDescription = closeDescription,
                            modifier = Modifier.testTag("assistant_overlay_close"),
                            size = 36.dp,
                            iconSize = 18.dp,
                            tint = colors.inkTertiary,
                        )
                    }
                }
                when {
                    !state.conversation.loaded -> {
                        if (state.error == null) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.assistant_loading),
                                    style = DdType.body,
                                    color = colors.inkTertiary,
                                )
                            }
                        }
                    }

                    messages.isEmpty() -> OverlaySuggestions(
                        modifier = Modifier.fillMaxWidth(),
                        onSuggestion = send,
                    )

                    else -> Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        CompositionLocalProvider(LocalDensity provides chatDensity) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("assistant_overlay_transcript"),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 8.dp,
                                    bottom = 4.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    ChatTurn(
                                        message = message,
                                        onToggleStep = { stepId ->
                                            onToggleStep(message.id, stepId)
                                        },
                                        onCopyOutput = copyOutput,
                                        onQuote = quote,
                                        onOpenAttachment = { attachment -> openInAppNotice(attachment.path) },
                                        onOpenFile = openInAppNotice,
                                        onRetry = onRetry,
                                        attachmentRoot = attachmentRoot,
                                        autoCollapseProcess = autoCollapseProcess,
                                        onOpenPresentFile = openInAppNotice,
                                        onSharePresentFile = openInAppNotice,
                                    )
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
                if (pendingQuestion) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 2.dp)
                            .testTag("assistant_overlay_pending_question"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_overlay_question),
                            modifier = Modifier.weight(1f),
                            style = DdType.bodySmall,
                            color = colors.inkSecondary,
                        )
                        DdTextAction(
                            text = stringResource(R.string.assistant_overlay_question_action),
                            onClick = onOpenQuestion,
                            modifier = Modifier.testTag("assistant_overlay_open_question"),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = busy,
                    enter = fadeIn(tween(DdMotion.FAST)) +
                        expandVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
                    exit = fadeOut(tween(DdMotion.FAST)) +
                        shrinkVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
                ) {
                    WorkingIndicator(
                        activityText = chatUiStrings.activity(state.conversation.activity),
                        startedAt = state.conversation.activityStartedAt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                if (listening) {
                    OverlayListening(transcript)
                }
                voiceFailure?.let { failure ->
                    OverlayVoiceFailure(
                        failure = failure,
                        showSettings = needsPermissionSettings(failure, permissionPermanentlyDenied),
                        onRetry = requestListening,
                        onOpenSettings = openPermissionSettings,
                    )
                }
                state.error?.let { error ->
                    OverlaySendError(
                        error = error,
                        onRetry = onRetryStart,
                    )
                }
                if (showActions && imeBottomPx == 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                            .testTag("assistant_overlay_actions"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OverlayActionButton(
                            icon = DdIcons.Copy,
                            description = copyDescription,
                            testTag = "assistant_overlay_copy",
                            onClick = copyAnswer,
                        )
                        OverlayActionButton(
                            icon = if (speaking) DdIcons.VolumeX else DdIcons.Volume,
                            description = speakDescription,
                            testTag = "assistant_overlay_speak",
                            onClick = toggleSpeak,
                        )
                        OverlayActionButton(
                            icon = DdIcons.Share,
                            description = shareDescription,
                            testTag = "assistant_overlay_share",
                            onClick = shareAnswer,
                        )
                        Spacer(Modifier.weight(1f))
                        val sessionId = state.sessionId
                        if (sessionId != null) {
                            OverlayActionButton(
                                icon = DdIcons.External,
                                description = openAppDescription,
                                testTag = "assistant_overlay_open_app",
                                onClick = { onOpenInApp(sessionId) },
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(colors.ink.copy(alpha = 0.08f)),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdPressable(
                        onClick = toggleListening,
                        modifier = Modifier.size(36.dp).testTag("assistant_overlay_mic"),
                        shape = CircleShape,
                        background = if (listening) colors.accentSoft else colors.wash,
                        contentDescription = stringResource(R.string.assistant_overlay_mic),
                    ) {
                        DdIcon(
                            icon = if (listening) DdIcons.AudioLines else DdIcons.Mic,
                            tint = if (listening) colors.accent else colors.inkSecondary,
                            size = 18.dp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        ChatInputField(
                            value = input,
                            onValueChange = { next ->
                                input = next
                                if (next.isNotEmpty()) voiceFailure = null
                            },
                            placeholder = stringResource(R.string.assistant_overlay_input_hint),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            onSend = { send(input) },
                            testTag = "assistant_overlay_input",
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    if (busy) {
                        DdPressable(
                            onClick = {
                                stopListening()
                                speaker.stop()
                                onStop()
                            },
                            modifier = Modifier.size(36.dp).testTag("assistant_overlay_stop"),
                            shape = CircleShape,
                            background = colors.wash,
                            contentDescription = stringResource(R.string.assistant_overlay_stop),
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.danger),
                            )
                        }
                    } else {
                        val canSend = input.isNotBlank()
                        DdPressable(
                            onClick = { send(input) },
                            modifier = Modifier.size(36.dp).testTag("assistant_overlay_send"),
                            shape = CircleShape,
                            background = if (canSend) colors.accent else colors.wash,
                            enabled = canSend,
                            contentDescription = stringResource(R.string.assistant_overlay_send),
                        ) {
                            DdIcon(
                                icon = DdIcons.ArrowUp,
                                tint = if (canSend) colors.onAccent else colors.inkTertiary,
                                size = 18.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySuggestions(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val suggestions = listOf(
        stringResource(R.string.assistant_overlay_suggestion_weather),
        stringResource(R.string.assistant_overlay_suggestion_music),
        stringResource(R.string.assistant_overlay_suggestion_places),
    )
    Column(
        modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            DdPressable(
                onClick = { onSuggestion(suggestion) },
                modifier = Modifier
                    .height(42.dp)
                    .border(1.dp, colors.ink.copy(alpha = 0.10f), SuggestionShape)
                    .testTag("assistant_overlay_suggestion"),
                shape = SuggestionShape,
                background = colors.surface,
                contentDescription = suggestion,
            ) {
                Text(
                    text = suggestion,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = DdType.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = colors.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OverlayActionButton(
    icon: DdIcon,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.size(44.dp).testTag(testTag),
        shape = CircleShape,
        background = Color.Transparent,
        contentDescription = description,
    ) {
        Box(
            Modifier.size(36.dp).clip(ActionShape).background(colors.wash),
            contentAlignment = Alignment.Center,
        ) {
            DdIcon(icon = icon, tint = colors.inkSecondary, size = 17.dp)
        }
    }
}

@Composable
private fun OverlayListening(transcript: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.AudioLines, tint = colors.accent, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = transcript.ifEmpty { stringResource(R.string.assistant_overlay_listening) },
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = if (transcript.isEmpty()) colors.inkTertiary else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayVoiceFailure(
    failure: SpeechError,
    showSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = voiceErrorMessage(failure),
            modifier = Modifier.weight(1f).testTag("assistant_overlay_voice_error"),
            style = DdType.bodySmall,
            color = colors.danger,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        DdTextAction(
            text = stringResource(R.string.assistant_overlay_retry),
            onClick = onRetry,
            modifier = Modifier.testTag("assistant_overlay_voice_retry"),
        )
        if (showSettings) {
            Spacer(Modifier.width(2.dp))
            DdTextAction(
                text = stringResource(R.string.assistant_overlay_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("assistant_overlay_open_settings"),
            )
        }
    }
}

@Composable
private fun OverlaySendError(
    error: AssistantOverlayError,
    onRetry: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                when (error) {
                    AssistantOverlayError.Busy -> R.string.assistant_overlay_error_busy
                    AssistantOverlayError.SendFailed -> R.string.assistant_overlay_error_send
                },
            ),
            modifier = Modifier.weight(1f).testTag("assistant_overlay_error"),
            style = DdType.bodySmall,
            color = colors.danger,
        )
        if (error == AssistantOverlayError.SendFailed) {
            DdTextAction(
                text = stringResource(R.string.assistant_overlay_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("assistant_overlay_retry"),
            )
        }
    }
}

private fun answerText(message: AssistantMessage): String {
    val trailing = message.steps.lastOrNull() as? AssistantStep.Text
    return message.text.trim().ifBlank { trailing?.text?.trim().orEmpty() }
}

@Composable
private fun voiceErrorMessage(error: SpeechError): String = when (error) {
    SpeechError.INSUFFICIENT_PERMISSIONS -> stringResource(R.string.assistant_overlay_error_mic)
    SpeechError.UNAVAILABLE,
    SpeechError.CANNOT_CHECK_SUPPORT,
    SpeechError.CANNOT_LISTEN_TO_DOWNLOAD,
    -> stringResource(R.string.assistant_overlay_voice_unavailable)

    SpeechError.NO_MATCH -> stringResource(R.string.voice_error_no_match)
    SpeechError.SPEECH_TIMEOUT -> stringResource(R.string.voice_error_timeout)
    SpeechError.NETWORK, SpeechError.NETWORK_TIMEOUT -> stringResource(R.string.voice_error_network)
    SpeechError.RECOGNIZER_BUSY -> stringResource(R.string.voice_error_busy)
    SpeechError.LANGUAGE_NOT_SUPPORTED,
    SpeechError.LANGUAGE_UNAVAILABLE,
    -> stringResource(R.string.voice_error_language)

    else -> stringResource(R.string.voice_error_generic)
}

private fun needsPermissionSettings(error: SpeechError, permanentlyDenied: Boolean): Boolean =
    permanentlyDenied || error == SpeechError.INSUFFICIENT_PERMISSIONS

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
