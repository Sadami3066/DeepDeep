package io.github.whywood.deepdeep.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.AttachmentPreviewDialog
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.TextSpeaker
import io.github.whywood.deepdeep.voice.VoiceLanguage
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class AssistantOverlayConfig(
    val autoSpeak: Boolean = false,
    val sendAfterRecognition: Boolean = false,
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
    val onOpenFile: (String) -> Unit,
    val onShareFile: (String) -> Unit,
    val onOpenWorkshop: (String) -> Unit,
    val onOpenSession: (String) -> Unit,
    val onNewSession: () -> Unit,
    val onSelectWorkspace: (String) -> Unit,
    val onRefreshLists: () -> Unit,
    val onSelectProvider: (String) -> Unit,
    val onSelectModel: (String) -> Unit,
    val onSelectThinking: (ThinkingLevel) -> Unit,
    val onClearQueue: () -> List<String>,
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
    val scope = rememberCoroutineScope()
    val speaker = remember(context) { TextSpeaker(context) }
    val recognizer = remember(context) { SpeechRecognizerController(context) }
    val activity = remember(context) { context.findActivity() }
    val inputFocus = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceFailure by remember { mutableStateOf<SpeechError?>(null) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var listenJob by remember { mutableStateOf<Job?>(null) }
    var wasSending by remember { mutableStateOf(state.conversation.sending) }
    var entered by remember { mutableStateOf(false) }
    var sentThisSession by remember { mutableStateOf(false) }
    val messages = state.conversation.messages

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        listening = false
    }

    fun dismiss() {
        stopListening()
        speaker.stop()
        actions.onDismiss()
    }

    fun submit(raw: String, followUp: Boolean) {
        val text = raw.trim()
        if (text.isEmpty()) return
        stopListening()
        speaker.stop()
        voiceFailure = null
        keyboard?.hide()
        focusManager.clearFocus()
        if (actions.onSend(text, followUp)) {
            input = ""
            sentThisSession = true
        }
    }

    fun beginListening() {
        stopListening()
        speaker.stop()
        transcript = ""
        voiceFailure = null
        listening = true
        listenJob = scope.launch {
            recognizer.listen(VoiceLanguage.recognitionTag(config.language)).collect { event ->
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
                        } else if (config.sendAfterRecognition && input.isBlank()) {
                            submit(text, followUp = false)
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

    DisposableEffect(speaker) {
        onDispose {
            listenJob?.cancel()
            listenJob = null
            speaker.stop()
            speaker.release()
        }
    }

    LaunchedEffect(state.sessionId) {
        input = ""
        sentThisSession = false
    }

    LaunchedEffect(state.conversation.loaded, state.sessionId, pendingQuestions.size) {
        if (!state.conversation.loaded) return@LaunchedEffect
        if (messages.isNotEmpty() || state.conversation.sending || pendingQuestions.isNotEmpty()) return@LaunchedEffect
        delay(250)
        inputFocus.requestFocus()
        keyboard?.show()
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

    BackHandler(onBack = ::dismiss)

    BoxWithConstraints(Modifier.fillMaxSize().testTag("assistant_overlay")) {
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
        val bottomGap = with(density) { (animatedBottomInset.value.roundToInt() + 8.dp.toPx()).toDp() }
        val panelHeight = (maxHeight * 0.52f).coerceAtLeast(240.dp)
        val hasPanelContent = messages.isNotEmpty() ||
            state.conversation.sending ||
            pendingQuestions.isNotEmpty() ||
            sentThisSession
        val panelVisible = entered && hasPanelContent && imeBottomPx == 0

        LaunchedEffect(Unit) { entered = true }

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
        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { statusTopPx.toDp() } + 8.dp, start = 12.dp, end = 12.dp)
                .height(panelHeight),
            enter = slideInVertically(
                animationSpec = tween(DdMotion.ENTER, easing = DdMotion.EaseOut),
                initialOffsetY = { -it },
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = slideOutVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseInOut),
                targetOffsetY = { -it },
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            AssistantOverlayPanel(
                state = state,
                pendingQuestions = pendingQuestions,
                config = config,
                actions = actions,
                onQuote = quote,
                onOpenInApp = { state.sessionId?.let(actions.onOpenInApp) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = entered,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(DdMotion.ENTER, easing = DdMotion.EaseOut),
                initialOffsetY = { it },
            ) + fadeIn(tween(DdMotion.FAST)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = bottomGap),
            ) {
                if (listening) {
                    AssistantOverlayListening(transcript)
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
                    OverlaySendError(error = error, onRetry = actions.onRetryStart)
                }
                AssistantOverlayComposer(
                    state = state,
                    value = input,
                    onValueChange = { next ->
                        input = next
                        if (next.isNotEmpty()) voiceFailure = null
                    },
                    listening = listening,
                    onToggleMic = {
                        if (listening) stopListening() else requestListening()
                    },
                    onSubmit = { followUp -> submit(input, followUp) },
                    config = config,
                    actions = actions,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = inputFocus,
                )
            }
        }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(
                when (error) {
                    AssistantOverlayError.StartFailed -> R.string.assistant_overlay_error_start
                    AssistantOverlayError.SendFailed -> R.string.assistant_overlay_error_send
                    AssistantOverlayError.SwitchFailed -> R.string.assistant_overlay_error_switch
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
