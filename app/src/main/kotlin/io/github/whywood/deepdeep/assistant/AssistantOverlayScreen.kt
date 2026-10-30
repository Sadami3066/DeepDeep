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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.design.DdChip
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdTypingDots
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.chat.ChatMarkdown
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.TextSpeaker
import io.github.whywood.deepdeep.voice.VoiceLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AssistantOverlayScreen(
    state: AssistantOverlayState,
    autoSpeak: Boolean,
    language: AppLanguage,
    restartListening: Int,
    pendingQuestion: Boolean,
    onOpenQuestion: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInApp: (String) -> Unit,
    onAsk: (String) -> Unit,
    onStop: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.assistant_overlay_copied)
    val shareTitle = stringResource(R.string.assistant_overlay_share_title)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val speaker = remember(context) { TextSpeaker(context) }
    val recognizer = remember(context) { SpeechRecognizerController(context) }
    val activity = remember(context) { context.findActivity() }
    val shape = RoundedCornerShape(28.dp)
    var input by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceFailure by remember { mutableStateOf<SpeechError?>(null) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var lastPrompt by remember { mutableStateOf<String?>(null) }
    val listenJob = remember { mutableStateOf<Job?>(null) }
    val busy = state.phase == AssistantOverlayPhase.Thinking ||
        state.phase == AssistantOverlayPhase.Streaming

    fun stopListening() {
        listenJob.value?.cancel()
        listenJob.value = null
        listening = false
    }

    fun beginListening() {
        stopListening()
        speaker.stop()
        transcript = ""
        voiceFailure = null
        listening = true
        listenJob.value = scope.launch {
            recognizer.listen(VoiceLanguage.recognitionTag(language)).collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> transcript = event.text
                    is SpeechEvent.Volume -> Unit
                    is SpeechEvent.Final -> {
                        transcript = event.text
                        input = ""
                        lastPrompt = event.text
                        onAsk(event.text)
                    }

                    is SpeechEvent.Failed -> voiceFailure = event.error
                    SpeechEvent.Timeout -> voiceFailure = SpeechError.SPEECH_TIMEOUT
                }
            }
            listening = false
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

    LaunchedEffect(Unit) { requestListening() }
    LaunchedEffect(restartListening) { if (restartListening > 0) requestListening() }

    DisposableEffect(speaker) {
        onDispose {
            listenJob.value?.cancel()
            listenJob.value = null
            speaker.stop()
            speaker.release()
        }
    }

    LaunchedEffect(state.phase, state.answer) {
        if (state.answer.isNotBlank() ||
            state.phase == AssistantOverlayPhase.Thinking ||
            state.phase == AssistantOverlayPhase.Streaming ||
            state.phase == AssistantOverlayPhase.Done
        ) {
            stopListening()
            voiceFailure = null
        }
    }

    LaunchedEffect(autoSpeak, language, state.phase, state.answer) {
        if (!autoSpeak || state.phase != AssistantOverlayPhase.Done) return@LaunchedEffect
        if (state.answer.isBlank()) return@LaunchedEffect
        speaker.speak(state.answer, language)
    }

    val send: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            stopListening()
            speaker.stop()
            voiceFailure = null
            input = ""
            transcript = ""
            lastPrompt = text
            keyboard?.hide()
            onAsk(text)
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

    val copyAnswer: () -> Unit = {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("deepdeep-assistant", state.answer))
        Toast.makeText(
            context,
            copiedMessage,
            Toast.LENGTH_SHORT,
        ).show()
    }

    val shareAnswer: () -> Unit = {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, state.answer)
        }
        context.startActivity(
            Intent.createChooser(
                share,
                shareTitle,
            ),
        )
    }

    val toggleSpeak: () -> Unit = {
        if (speaker.speaking) speaker.stop() else speaker.speak(state.answer, language)
    }

    Box(Modifier.fillMaxSize().testTag("assistant_overlay")) {
        Box(
            Modifier
                .matchParentSize()
                .background(colors.ink.copy(alpha = 0.04f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, shape, clip = false)
                        .clip(shape)
                        .background(colors.surface)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_overlay_title),
                            style = DdType.heading,
                            color = colors.ink,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.assistant_overlay_ai_label),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = DdType.micro.copy(letterSpacing = 0.2.sp),
                            color = colors.accent,
                        )
                        Spacer(Modifier.weight(1f))
                        DdIconButton(
                            icon = DdIcons.Close,
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.assistant_overlay_close),
                            modifier = Modifier.testTag("assistant_overlay_close"),
                            size = 34.dp,
                            iconSize = 18.dp,
                            tint = colors.inkSecondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        state.answer.isNotBlank() -> {
                            OverlayAnswer(
                                answer = state.answer,
                                live = state.phase == AssistantOverlayPhase.Streaming,
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        busy -> OverlayThinking()
                        listening -> OverlayListening(transcript)
                        state.phase == AssistantOverlayPhase.Error || voiceFailure != null -> Unit
                        else -> OverlayGreeting(onSuggestion = send)
                    }
                    if (!listening && state.phase == AssistantOverlayPhase.Error) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.assistant_overlay_error_send),
                            modifier = Modifier.testTag("assistant_overlay_error"),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                        if (lastPrompt != null) {
                            Spacer(Modifier.height(2.dp))
                            DdTextAction(
                                text = stringResource(R.string.assistant_overlay_retry),
                                onClick = { lastPrompt?.let(send) },
                                modifier = Modifier.testTag("assistant_overlay_retry"),
                            )
                        }
                    }
                    if (pendingQuestion) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                    val failure = voiceFailure.takeIf {
                        state.answer.isBlank() &&
                            state.phase != AssistantOverlayPhase.Thinking &&
                            state.phase != AssistantOverlayPhase.Streaming &&
                            state.phase != AssistantOverlayPhase.Done
                    }
                    if (failure != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = voiceErrorMessage(failure),
                            modifier = Modifier.testTag("assistant_overlay_voice_error"),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DdTextAction(
                                text = stringResource(R.string.assistant_overlay_retry),
                                onClick = requestListening,
                                modifier = Modifier.testTag("assistant_overlay_voice_retry"),
                            )
                            if (needsPermissionSettings(failure, permissionPermanentlyDenied)) {
                                Spacer(Modifier.width(16.dp))
                                DdTextAction(
                                    text = stringResource(R.string.assistant_overlay_open_settings),
                                    onClick = openPermissionSettings,
                                    modifier = Modifier.testTag("assistant_overlay_open_settings"),
                                )
                            }
                        }
                    }
                    if (state.answer.isNotBlank() || state.sessionId != null) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (lastPrompt != null) {
                                DdIconButton(
                                    icon = DdIcons.Refresh,
                                    onClick = { lastPrompt?.let(send) },
                                    contentDescription = stringResource(R.string.assistant_overlay_retry),
                                    modifier = Modifier.testTag("assistant_overlay_retry_action"),
                                    size = 36.dp,
                                    iconSize = 17.dp,
                                    tint = colors.inkSecondary,
                                )
                            }
                            if (state.answer.isNotBlank()) {
                                DdIconButton(
                                    icon = DdIcons.Copy,
                                    onClick = copyAnswer,
                                    contentDescription = stringResource(R.string.assistant_overlay_copy),
                                    modifier = Modifier.testTag("assistant_overlay_copy"),
                                    size = 36.dp,
                                    iconSize = 17.dp,
                                    tint = colors.inkSecondary,
                                )
                                DdIconButton(
                                    icon = DdIcons.Share,
                                    onClick = shareAnswer,
                                    contentDescription = stringResource(R.string.assistant_overlay_share),
                                    modifier = Modifier.testTag("assistant_overlay_share"),
                                    size = 36.dp,
                                    iconSize = 17.dp,
                                    tint = colors.inkSecondary,
                                )
                                DdIconButton(
                                    icon = if (speaker.speaking) DdIcons.VolumeX else DdIcons.Volume,
                                    onClick = toggleSpeak,
                                    contentDescription = stringResource(
                                        if (speaker.speaking) R.string.assistant_overlay_stop_speak
                                        else R.string.assistant_overlay_speak,
                                    ),
                                    modifier = Modifier.testTag("assistant_overlay_speak"),
                                    size = 36.dp,
                                    iconSize = 17.dp,
                                    tint = if (speaker.speaking) colors.accent else colors.inkSecondary,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            val sessionId = state.sessionId
                            if (sessionId != null) {
                                DdTextAction(
                                    text = stringResource(R.string.assistant_overlay_open_app),
                                    onClick = { onOpenInApp(sessionId) },
                                    modifier = Modifier.testTag("assistant_overlay_open_app"),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .shadow(12.dp, shape, clip = false)
                    .clip(shape)
                    .background(colors.composer)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdPressable(
                    onClick = requestListening,
                    modifier = Modifier.size(40.dp).testTag("assistant_overlay_mic"),
                    shape = CircleShape,
                    background = if (listening) colors.accentSoft else colors.wash,
                    enabled = !busy,
                    contentDescription = stringResource(R.string.assistant_overlay_mic),
                ) {
                    DdIcon(
                        icon = DdIcons.Mic,
                        tint = if (listening) colors.accent else colors.inkSecondary,
                        size = 20.dp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    if (listening) {
                        Text(
                            text = transcript.ifEmpty {
                                stringResource(R.string.assistant_overlay_listening)
                            },
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .testTag("assistant_overlay_input_transcript"),
                            style = DdType.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                            color = if (transcript.isEmpty()) colors.inkTertiary else colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        BasicTextField(
                            value = input,
                            onValueChange = { next ->
                                input = next
                                if (next.isNotEmpty() && listening) stopListening()
                                if (next.isNotEmpty()) voiceFailure = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .testTag("assistant_overlay_input"),
                            textStyle = DdType.body.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = colors.ink,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(input) }),
                            decorationBox = { inner ->
                                Box {
                                    if (input.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.assistant_overlay_input_hint),
                                            style = DdType.body.copy(
                                                fontSize = 16.sp,
                                                lineHeight = 24.sp,
                                            ),
                                            color = colors.inkTertiary,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                if (busy) {
                    DdPressable(
                        onClick = onStop,
                        modifier = Modifier.size(40.dp).testTag("assistant_overlay_stop"),
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
                        modifier = Modifier.size(40.dp).testTag("assistant_overlay_send"),
                        shape = CircleShape,
                        background = if (canSend) colors.accent else colors.wash,
                        enabled = canSend,
                        contentDescription = stringResource(R.string.assistant_overlay_send),
                    ) {
                        DdIcon(
                            icon = DdIcons.ArrowUp,
                            tint = if (canSend) colors.onAccent else colors.inkTertiary,
                            size = 19.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayAnswer(answer: String, live: Boolean) {
    val scroll = rememberScrollState()
    LaunchedEffect(answer) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(scroll)
            .testTag("assistant_overlay_answer"),
    ) {
        ChatMarkdown(answer, live = live)
    }
}

@Composable
private fun OverlayThinking() {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdTypingDots(color = colors.inkTertiary)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.assistant_overlay_thinking),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun OverlayListening(transcript: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.AudioLines, tint = colors.accent, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = transcript.ifEmpty { stringResource(R.string.assistant_overlay_listening) },
            modifier = Modifier.weight(1f).testTag("assistant_overlay_transcript"),
            style = DdType.body,
            color = if (transcript.isEmpty()) colors.inkTertiary else colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayGreeting(onSuggestion: (String) -> Unit) {
    val colors = deepDeepColors()
    val suggestions = listOf(
        stringResource(R.string.assistant_overlay_suggestion_weather),
        stringResource(R.string.assistant_overlay_suggestion_music),
        stringResource(R.string.assistant_overlay_suggestion_places),
    )
    Text(
        text = stringResource(R.string.assistant_overlay_hint),
        style = DdType.body,
        color = colors.inkSecondary,
    )
    Spacer(Modifier.height(12.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        suggestions.forEach { suggestion ->
            DdChip(
                text = suggestion,
                onClick = { onSuggestion(suggestion) },
                modifier = Modifier.testTag("assistant_overlay_suggestion"),
            )
        }
    }
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
