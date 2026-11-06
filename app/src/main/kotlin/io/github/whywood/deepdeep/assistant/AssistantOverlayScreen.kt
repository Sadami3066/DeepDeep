package io.github.whywood.deepdeep.assistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.common.chat.AttachmentPreviewDialog
import io.github.whywood.deepdeep.ui.common.rememberComposerVoice
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.TextSpeaker
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first



internal data class AssistantOverlayConfig(
    val autoSpeak: Boolean = false,
    val entryMode: OverlayEntryMode = OverlayEntryMode.NONE,
    val autoCollapseProcess: Boolean = true,
    val chatFontScale: Float = 1f,
    val language: AppLanguage = AppLanguage.CHINESE,
    val attachmentRoot: File = File("/"),
)

internal data class AssistantOverlayActions(
    val onDismiss: () -> Unit,
    val onOpenInApp: (String) -> Unit,
    val onSend: (String, Boolean) -> Boolean,
    val onStop: () -> Unit,
    val onRetry: (Long) -> Unit,
    val onToggleStep: (Long, Long) -> Unit,
    val onAnswerQuestion: (PendingQuestion, List<QuestionAnswer>) -> Unit,
    val onOpenPreview: (AttachmentRef) -> Unit,
    val onPickAttachment: () -> Unit,
    val onOpenCamera: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onOpenFile: (String) -> Unit,
    val onShareFile: (String) -> Unit,
    val onOpenMiniapp: (String) -> Unit,
    val onOpenSession: (String) -> Unit,
    val onNewSessionInWorkspace: (String) -> Unit,
    val onRefreshLists: () -> Unit,
    val onSelectProvider: (String) -> Unit,
    val onSelectModel: (String) -> Unit,
    val onSelectThinking: (ThinkingLevel) -> Unit,
    val onClearQueue: () -> List<String>,
    val onRemoveQueued: (QueuedInput) -> Unit,
    val onSendQueued: (QueuedInput) -> Unit,
    val onRetryStart: () -> Unit,
    val onDismissPreview: () -> Unit,
    val onSharePreview: () -> Unit,
)

internal data class AssistantOverlayPreview(
    val attachment: AttachmentRef,
    val file: File,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AssistantOverlayScreen(
    state: AssistantOverlayState,
    pendingQuestions: List<PendingQuestion>,
    preview: AssistantOverlayPreview?,
    config: AssistantOverlayConfig,
    actions: AssistantOverlayActions,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val speaker = remember(context) { TextSpeaker(context) }
    val inputFocus = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
    var wasSending by remember { mutableStateOf(state.conversation.sending) }
    var entered by remember { mutableStateOf(false) }
    var laidOut by remember { mutableStateOf(false) }
    var slideStarted by remember { mutableStateOf(false) }
    var panelSlideDone by remember { mutableStateOf(false) }
    var panelMounted by remember { mutableStateOf(false) }
    var panelLaidOut by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var switcherOpen by remember { mutableStateOf(false) }
    var switcherLevel by remember { mutableStateOf<OverlaySwitcherLevel>(OverlaySwitcherLevel.Workspaces) }
    var voiceSendRequested by remember { mutableStateOf(false) }
    var focusRequest by remember { mutableStateOf(0) }
    var entryTriggered by rememberSaveable { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val messages = state.conversation.messages
    val glowSignal = remember(state, pendingQuestions) { overlayRunSignal(state, pendingQuestions) }
    val glow = rememberOverlayGlow(glowSignal, entryPlayed = false, entryStart = entered)

    val voice = rememberComposerVoice(
        language = config.language,
        onCommit = { text ->
            input = if (input.isBlank()) text else input.trimEnd() + " " + text
        },
        onSendRequested = { voiceSendRequested = true },
    )

    fun dismiss() {
        speaker.stop()
        actions.onDismiss()
    }

    fun submit(raw: String, followUp: Boolean) {
        val text = raw.trim()
        if (text.isEmpty()) return
        speaker.stop()
        voice.clearFailure()
        keyboard?.hide()
        focusManager.clearFocus()
        if (actions.onSend(text, followUp)) {
            input = ""
        }
    }

    LaunchedEffect(voiceSendRequested) {
        if (!voiceSendRequested) return@LaunchedEffect
        voiceSendRequested = false
        submit(input, followUp = false)
    }

    DisposableEffect(speaker) {
        onDispose {
            speaker.stop()
            speaker.release()
        }
    }

    LaunchedEffect(state.sessionId) {
        input = ""
    }

    LaunchedEffect(state.conversation.sending) {
        if (state.conversation.sending) {
            wasSending = true
            return@LaunchedEffect
        }
        if (!wasSending) return@LaunchedEffect
        wasSending = false
        if (!config.autoSpeak) return@LaunchedEffect
        val answer = messages.lastOrNull()
            ?.takeIf { it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.DONE }
            ?.let(::answerText)
        if (!answer.isNullOrBlank()) speaker.speak(answer, config.language)
    }

    val quote: (Long, String) -> Unit = { _, text ->
        val quoted = text.trim().lineSequence().joinToString("\n") { line -> "> $line" }
        input = if (input.isBlank()) "$quoted\n\n" else "$quoted\n\n$input"
        focusRequest++
    }

    LaunchedEffect(focusRequest) {
        if (focusRequest == 0) return@LaunchedEffect
        inputFocus.requestFocus()
        keyboard?.show()
    }

    val openPermissionSettings: () -> Unit = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    BackHandler {
        if (switcherOpen) switcherOpen = false else dismiss()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onSizeChanged { if (it.width > 0 && it.height > 0) laidOut = true }
            .testTag("assistant_overlay"),
    ) {
        val imeBottomPx = with(density) { WindowInsets.imeAnimationTarget.getBottom(density) }
        val navigationBottomPx = with(density) { WindowInsets.navigationBars.getBottom(density) }
        val statusTopPx = with(density) { WindowInsets.statusBars.getTop(density) }
        var stableNavigationBottomPx by remember { mutableIntStateOf(navigationBottomPx) }
        SideEffect {
            if (windowFocused) {
                stableNavigationBottomPx = navigationBottomPx
            }
        }
        val extraImeInset = remember { Animatable(0f) }
        val extraImeTargetPx =
            (maxOf(imeBottomPx, stableNavigationBottomPx) - stableNavigationBottomPx).toFloat()
        LaunchedEffect(imeBottomPx, stableNavigationBottomPx) {
            extraImeInset.animateTo(
                targetValue = extraImeTargetPx,
                animationSpec = tween(DdMotion.BASE + 80, easing = DdMotion.EaseInOut),
            )
        }
        val panelTopPx = statusTopPx.toFloat() + with(density) { 8.dp.toPx() }
        val composerBottomPaddingPx =
            stableNavigationBottomPx + with(density) { 8.dp.toPx() }
        val composerTopPx = with(density) { maxHeight.toPx() } -
            composerBottomPaddingPx - composerHeightPx - extraImeTargetPx
        val panelMaxHeight = with(density) {
            (composerTopPx - panelTopPx - 24.dp.toPx()).toDp()
        }.coerceIn(120.dp, (maxHeight * 0.5f).coerceAtLeast(120.dp))
        val panelTarget = state.sessionId != null && state.conversation.loaded
        LaunchedEffect(panelTarget) {
            if (panelTarget) {
                panelMounted = true
                return@LaunchedEffect
            }
            panelLaidOut = false
        }

        LaunchedEffect(laidOut, windowFocused) {
            if (entered || !laidOut) return@LaunchedEffect
            delay(if (windowFocused) 90 else 500)
            entered = true
        }
        LaunchedEffect(entered, panelTarget, panelLaidOut) {
            if (slideStarted || !entered) return@LaunchedEffect
            if (state.sessionId == null && state.error == null) return@LaunchedEffect
            if (panelTarget && !panelLaidOut) return@LaunchedEffect
            slideStarted = true
        }
        LaunchedEffect(entered) {
            if (!entered) return@LaunchedEffect
            delay(1600)
            if (!slideStarted) slideStarted = true
        }
        val composerProgress by animateFloatAsState(
            targetValue = if (slideStarted) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.95f, stiffness = 520f),
            label = "overlay-composer",
        )
        LaunchedEffect(entered, config.entryMode) {
            if (!entered || entryTriggered) return@LaunchedEffect
            entryTriggered = true
            when (config.entryMode) {
                OverlayEntryMode.VOICE -> voice.start()
                OverlayEntryMode.CAMERA -> actions.onOpenCamera()
                OverlayEntryMode.NONE -> Unit
            }
        }
        val panelProgress by animateFloatAsState(
            targetValue = if (slideStarted && panelMounted) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.95f, stiffness = 520f),
            label = "overlay-panel",
        )
        LaunchedEffect(slideStarted) {
            if (!slideStarted) {
                panelSlideDone = false
                return@LaunchedEffect
            }
            snapshotFlow { panelProgress }.first { it >= 0.99f }
            panelSlideDone = true
        }
        LaunchedEffect(panelTarget) {
            if (panelTarget) return@LaunchedEffect
            snapshotFlow { panelProgress }.first { it <= 0.01f }
            panelMounted = false
            panelLaidOut = false
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.dark) 0.34f else 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss,
                ),
        )
        if (entered) {
            OverlayScreenGlow(glow, Modifier.fillMaxSize())
        }
        if (panelMounted) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = -(1f - panelProgress) * size.height
                    }
                    .padding(
                        top = with(density) { statusTopPx.toDp() } + 8.dp,
                        start = 12.dp,
                        end = 12.dp,
                    ),
            ) {
                AssistantOverlayPanel(
                    state = state,
                    pendingQuestions = pendingQuestions,
                    config = config,
                    actions = actions,
                    onQuote = quote,
                    onOpenInApp = { state.sessionId?.let(actions.onOpenInApp) },
                    onOpenWorkspaceSwitcher = {
                        switcherLevel = OverlaySwitcherLevel.Workspaces
                        switcherOpen = true
                    },
                    maxHeight = panelMaxHeight,
                    snapHeight = !panelSlideDone,
                    modifier = Modifier.fillMaxWidth().onSizeChanged { panelLaidOut = true },
                )
            }
        }
        val entryPending = !entryTriggered && config.entryMode != OverlayEntryMode.NONE
        if (entered && !entryPending) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = (1f - composerProgress) * size.height - extraImeInset.value
                    }
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = with(density) { (stableNavigationBottomPx + 8.dp.toPx()).toDp() })
                    .onSizeChanged { composerHeightPx = it.height },
            ) {
                voice.failure?.let { failure ->
                    OverlayVoiceFailure(
                        failure = failure,
                        showSettings = failure == SpeechError.INSUFFICIENT_PERMISSIONS,
                        onRetry = voice.start,
                        onOpenSettings = openPermissionSettings,
                    )
                }
                state.error?.let { error ->
                    OverlaySendError(error = error, onRetry = actions.onRetryStart)
                }
                AssistantOverlayComposer(
                    state = state,
                    value = input,
                    onValueChange = { next ->
                        input = next
                        voice.clearFailure()
                    },
                    voice = voice,
                    onSubmit = { followUp -> submit(input, followUp) },
                    actions = actions,
                    glow = glow,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = inputFocus,
                )
            }
        }
        AssistantOverlaySwitcher(
            state = state,
            actions = actions,
            visible = switcherOpen,
            level = switcherLevel,
            width = minOf(272.dp, maxWidth * 0.76f),
            onClose = { switcherOpen = false },
            onLevelChange = { switcherLevel = it },
        )
        if (preview != null) {
            AttachmentPreviewDialog(
                attachment = preview.attachment,
                file = preview.file,
                onDismiss = actions.onDismissPreview,
                onShare = actions.onSharePreview,
            )
        }
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
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .shadow(10.dp, shape, clip = false, ambientColor = colors.ink, spotColor = colors.ink)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.96f))
            .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = voiceErrorMessage(failure),
            modifier = Modifier.testTag("assistant_overlay_voice_error"),
            style = DdType.bodySmall,
            color = colors.danger,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RowActions(
            retryText = stringResource(R.string.assistant_overlay_retry),
            onRetry = onRetry,
            showSettings = showSettings,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun OverlaySendError(
    error: AssistantOverlayError,
    onRetry: () -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .shadow(10.dp, shape, clip = false, ambientColor = colors.ink, spotColor = colors.ink)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.96f))
            .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(
                when (error) {
                    AssistantOverlayError.StartFailed -> R.string.assistant_overlay_error_start
                    AssistantOverlayError.SendFailed -> R.string.assistant_overlay_error_send
                    AssistantOverlayError.SwitchFailed -> R.string.assistant_overlay_error_switch
                    AssistantOverlayError.AttachmentFailed -> R.string.chat_error_attachment_failed
                    AssistantOverlayError.AttachmentLimit -> R.string.chat_error_max_attachments
                    AssistantOverlayError.AttachmentTooLarge -> R.string.chat_error_attachment_too_large
                },
            ),
            modifier = Modifier.testTag("assistant_overlay_error"),
            style = DdType.bodySmall,
            color = colors.danger,
        )
        if (error == AssistantOverlayError.SendFailed || error == AssistantOverlayError.StartFailed) {
            RowActions(
                retryText = stringResource(R.string.assistant_overlay_retry),
                onRetry = onRetry,
                showSettings = false,
                onOpenSettings = {},
            )
        }
    }
}

@Composable
private fun RowActions(
    retryText: String,
    onRetry: () -> Unit,
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    androidx.compose.foundation.layout.Row {
        DdTextAction(
            text = retryText,
            onClick = onRetry,
            modifier = Modifier.testTag("assistant_overlay_retry"),
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

private fun answerText(message: io.github.whywood.deepdeep.runtime.AssistantMessage): String {
    val trailing = message.steps.lastOrNull() as? io.github.whywood.deepdeep.runtime.AssistantStep.Text
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
