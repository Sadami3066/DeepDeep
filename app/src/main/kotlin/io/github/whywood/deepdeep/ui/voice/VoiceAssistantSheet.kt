package io.github.whywood.deepdeep.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.assistant.AssistantRole
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.TextSpeaker
import io.github.whywood.deepdeep.voice.VoiceLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun VoiceAssistantSheet(
    answer: String,
    answerStreaming: Boolean,
    autoSpeak: Boolean,
    sendAfterRecognition: Boolean,
    onFill: (String) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLanguage = remember(context) { LanguagePreferences(context).read() }
    val speaker = remember(context) { TextSpeaker(context) }
    val recognizer = remember(context) { SpeechRecognizerController(context) }
    val activity = remember(context) { context.findActivity() }
    val roleAvailable = remember(context) { AssistantRole.isAvailable(context) }
    val roleHeld = remember(context) { AssistantRole.isHeld(context) }
    val currentAnswer by rememberUpdatedState(answer)
    var state by remember { mutableStateOf(VoiceAssistantState()) }
    var answerBaseline by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var listenJob by remember { mutableStateOf<Job?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val dispatch: (VoiceEvent) -> Unit = { event ->
        state = reduceVoiceState(state, event, sendAfterRecognition, autoSpeak)
    }

    val closeSheet: () -> Unit = {
        listenJob?.cancel()
        listenJob = null
        speaker.stop()
        onDismiss()
    }

    val startListening: () -> Unit = {
        listenJob?.cancel()
        listenJob = null
        speaker.stop()
        level = 0f
        dispatch(VoiceEvent.StartListening)
        listenJob = scope.launch {
            recognizer.listen(VoiceLanguage.recognitionTag(appLanguage)).collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> dispatch(VoiceEvent.Partial(event.text))
                    is SpeechEvent.Volume -> level = event.level
                    is SpeechEvent.Final -> {
                        dispatch(VoiceEvent.Final(event.text))
                        if (sendAfterRecognition) {
                            answerBaseline = currentAnswer
                            onSend(event.text)
                        } else {
                            onFill(event.text)
                            closeSheet()
                        }
                    }

                    is SpeechEvent.Failed -> dispatch(VoiceEvent.Failed(event.error))
                    SpeechEvent.Timeout -> dispatch(VoiceEvent.Failed(SpeechError.SPEECH_TIMEOUT))
                }
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            startListening()
        } else {
            val permanent = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            dispatch(VoiceEvent.PermissionDenied(permanent))
        }
    }

    val requestListening: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        permissionGranted = granted
        if (granted) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val openPermissionSettings: () -> Unit = {
        roleLauncher.launch(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    val openRoleSettings: () -> Unit = {
        roleLauncher.launch(
            AssistantRole.requestIntent(context) ?: AssistantRole.settingsIntent(context),
        )
    }

    LaunchedEffect(Unit) {
        if (permissionGranted) {
            startListening()
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

    LaunchedEffect(answer, answerStreaming, state.phase) {
        if (state.phase != VoicePhase.SENDING || answerStreaming || answer == answerBaseline) {
            return@LaunchedEffect
        }
        dispatch(VoiceEvent.AnswerCompleted(answer.isNotBlank()))
        if (state.phase == VoicePhase.SPEAKING) {
            speaker.speak(answer, appLanguage)
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase != VoicePhase.SPEAKING) return@LaunchedEffect
        snapshotFlow { speaker.speaking }.first { !it }
        dispatch(VoiceEvent.SpeechFinished)
    }

    val failure = state.failure
    DdSheet(onDismiss = closeSheet) {
        DdSheetHeader(title = stringResource(R.string.voice_title), onClose = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_sheet")
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            when {
                state.phase == VoicePhase.ERROR && failure != null -> VoiceErrorBody(failure)
                state.phase == VoicePhase.LISTENING ->
                    VoiceListeningBody(level = level, transcript = state.transcript)

                state.phase == VoicePhase.SENDING || state.phase == VoicePhase.SPEAKING ->
                    VoiceAnswerBody(
                        answer = answer,
                        speaking = state.phase == VoicePhase.SPEAKING,
                        onStopSpeaking = {
                            speaker.stop()
                            dispatch(VoiceEvent.StopSpeaking)
                        },
                    )

                state.sent && answer.isNotBlank() ->
                    VoiceAnswerBody(answer = answer, speaking = false, onStopSpeaking = {})

                else -> VoiceIdleBody(onStart = requestListening)
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.phase == VoicePhase.ERROR) {
                    DdTextAction(
                        text = stringResource(R.string.voice_retry),
                        onClick = requestListening,
                        modifier = Modifier.testTag("voice_retry"),
                    )
                    if (failure?.error == SpeechError.INSUFFICIENT_PERMISSIONS) {
                        Spacer(Modifier.width(24.dp))
                        DdTextAction(
                            text = stringResource(R.string.voice_open_settings),
                            onClick = openPermissionSettings,
                            modifier = Modifier.testTag("voice_open_settings"),
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    DdTextAction(
                        text = stringResource(R.string.voice_cancel),
                        onClick = closeSheet,
                        modifier = Modifier.testTag("voice_cancel"),
                    )
                } else if (state.phase != VoicePhase.SENDING) {
                    DdTextAction(
                        text = stringResource(R.string.voice_cancel),
                        onClick = closeSheet,
                        modifier = Modifier.testTag("voice_cancel"),
                    )
                }
            }
            if (state.phase == VoicePhase.IDLE && !state.sent && !roleHeld) {
                Spacer(Modifier.height(10.dp))
                DdTextAction(
                    text = stringResource(
                        if (roleAvailable) R.string.voice_role_set else R.string.voice_role_settings,
                    ),
                    onClick = openRoleSettings,
                    modifier = Modifier.testTag("voice_role"),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
