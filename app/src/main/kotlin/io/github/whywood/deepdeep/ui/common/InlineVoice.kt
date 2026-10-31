package io.github.whywood.deepdeep.ui.common

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.VoiceLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val MIC_HOLD_THRESHOLD_MS = 280L

internal enum class VoiceTranscriptMode {
    Send,
    Fill,
}

internal data class VoiceTranscriptOutcome(
    val mode: VoiceTranscriptMode,
    val text: String,
)

internal fun voiceTranscriptOutcome(
    input: String,
    transcript: String,
    sendAfterRecognition: Boolean,
): VoiceTranscriptOutcome? {
    val text = transcript.trim()
    if (text.isEmpty()) return null
    if (sendAfterRecognition && input.isBlank()) return VoiceTranscriptOutcome(VoiceTranscriptMode.Send, text)
    val merged = if (input.isBlank()) text else input.trimEnd() + " " + text
    return VoiceTranscriptOutcome(VoiceTranscriptMode.Fill, merged)
}

internal class InlineVoiceInput(
    val listening: Boolean,
    val failure: SpeechError?,
    val toggle: () -> Unit,
    val pressStart: () -> Unit,
    val pressEnd: (heldLong: Boolean) -> Unit,
    val cancel: () -> Unit,
    val clearFailure: () -> Unit,
)

@Composable
internal fun rememberInlineVoiceInput(
    language: AppLanguage,
    sendAfterRecognition: Boolean,
    input: () -> String,
    onFill: (String) -> Unit,
    onSend: () -> Unit,
): InlineVoiceInput {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val recognizer = remember(context) { SpeechRecognizerController(context) }
    val currentLanguage by rememberUpdatedState(language)
    val currentSendAfterRecognition by rememberUpdatedState(sendAfterRecognition)
    val currentInput by rememberUpdatedState(input)
    val currentFill by rememberUpdatedState(onFill)
    val currentSend by rememberUpdatedState(onSend)
    var listening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<SpeechError?>(null) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var listenJob by remember { mutableStateOf<Job?>(null) }
    var wasListening by remember { mutableStateOf(false) }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        listening = false
    }

    fun finishListening() {
        if (listening) recognizer.stopListening()
    }

    fun beginListening() {
        stopListening()
        failure = null
        listening = true
        listenJob = scope.launch {
            recognizer.listen(VoiceLanguage.recognitionTag(currentLanguage)).collect { event ->
                when (event) {
                    is SpeechEvent.Partial, is SpeechEvent.Volume -> Unit
                    is SpeechEvent.Final -> {
                        listening = false
                        listenJob = null
                        val outcome = voiceTranscriptOutcome(currentInput(), event.text, currentSendAfterRecognition)
                        if (outcome == null) {
                            failure = SpeechError.NO_MATCH
                        } else {
                            currentFill(outcome.text)
                            if (outcome.mode == VoiceTranscriptMode.Send) currentSend()
                        }
                    }

                    is SpeechEvent.Failed -> {
                        listening = false
                        listenJob = null
                        failure = event.error
                    }

                    SpeechEvent.Timeout -> {
                        listening = false
                        listenJob = null
                        failure = SpeechError.SPEECH_TIMEOUT
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
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            failure = SpeechError.INSUFFICIENT_PERMISSIONS
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

    val requestListening: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            granted -> beginListening()
            permanentlyDenied -> openPermissionSettings()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(recognizer) {
        onDispose {
            listenJob?.cancel()
            listenJob = null
        }
    }

    return InlineVoiceInput(
        listening = listening,
        failure = failure,
        toggle = { if (listening) stopListening() else requestListening() },
        pressStart = {
            wasListening = listening
            if (!listening) requestListening()
        },
        pressEnd = { heldLong ->
            when {
                heldLong -> finishListening()
                wasListening -> stopListening()
            }
        },
        cancel = { stopListening() },
        clearFailure = { failure = null },
    )
}

@Composable
internal fun Modifier.voiceMicGestures(
    onPressStart: () -> Unit,
    onPressEnd: (heldLong: Boolean) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val pressStart by rememberUpdatedState(onPressStart)
    val pressEnd by rememberUpdatedState(onPressEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressStart()
            val early = withTimeoutOrNull(MIC_HOLD_THRESHOLD_MS) {
                waitForUpOrCancellation() != null
            }
            when (early) {
                null -> {
                    if (waitForUpOrCancellation() != null) {
                        pressEnd(true)
                    } else {
                        cancel()
                    }
                }

                true -> pressEnd(false)
                false -> cancel()
            }
        }
    }
}

@Composable
internal fun voiceFailureText(error: SpeechError): String = when (error) {
    SpeechError.INSUFFICIENT_PERMISSIONS -> stringResource(R.string.voice_error_permission)
    SpeechError.UNAVAILABLE,
    SpeechError.CANNOT_CHECK_SUPPORT,
    SpeechError.CANNOT_LISTEN_TO_DOWNLOAD,
    -> stringResource(R.string.voice_error_unavailable)

    SpeechError.NO_MATCH -> stringResource(R.string.voice_error_no_match)
    SpeechError.SPEECH_TIMEOUT -> stringResource(R.string.voice_error_timeout)
    SpeechError.NETWORK, SpeechError.NETWORK_TIMEOUT -> stringResource(R.string.voice_error_network)
    SpeechError.RECOGNIZER_BUSY -> stringResource(R.string.voice_error_busy)
    SpeechError.LANGUAGE_NOT_SUPPORTED,
    SpeechError.LANGUAGE_UNAVAILABLE,
    -> stringResource(R.string.voice_error_language)

    else -> stringResource(R.string.voice_error_generic)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
