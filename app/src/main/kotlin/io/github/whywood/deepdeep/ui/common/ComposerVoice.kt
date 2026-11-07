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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.voice.ContinuousVoiceSession
import io.github.whywood.deepdeep.voice.hasDefaultRecognitionService
import io.github.whywood.deepdeep.voice.systemVoiceIntent
import io.github.whywood.deepdeep.voice.systemVoiceText
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.VoiceLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val VOICE_MIN_VOLUME = 0.08f

internal fun appendVoiceSegment(buffer: String, segment: String): String {
    val next = segment.trim()
    if (next.isEmpty()) return buffer
    val current = buffer.trim()
    return if (current.isEmpty()) next else current + " " + next
}

internal fun takeVoiceCommitText(buffer: String): String? =
    buffer.trim().takeIf { it.isNotEmpty() }

internal class ComposerVoice(
    val listening: Boolean,
    val stopping: Boolean,
    val failure: SpeechError?,
    val volume: Float,
    val buffer: String,
    val partial: String,
    val start: () -> Unit,
    val stop: () -> Unit,
    val send: () -> Unit,
    val cancel: () -> Unit,
    val clearFailure: () -> Unit,
)

@Composable
internal fun rememberComposerVoice(
    language: AppLanguage,
    onCommit: (String) -> Unit,
    onSendRequested: () -> Unit,
): ComposerVoice {
    val context = LocalContext.current
    val activity = remember(context) { context.findVoiceActivity() }
    val scope = rememberCoroutineScope()
    val session = remember(context) { ContinuousVoiceSession(context) }
    val usesSystemVoice = remember(context) { !hasDefaultRecognitionService(context) }
    val currentLanguage by rememberUpdatedState(language)
    val commit by rememberUpdatedState(onCommit)
    val sendRequested by rememberUpdatedState(onSendRequested)

    var listening by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<SpeechError?>(null) }
    var volume by remember { mutableStateOf(VOICE_MIN_VOLUME) }
    var buffer by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendingSend by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    fun finishRecording(sendAfterCommit: Boolean) {
        job = null
        listening = false
        stopping = false
        volume = VOICE_MIN_VOLUME
        partial = ""
        val text = takeVoiceCommitText(buffer)
        buffer = ""
        if (text != null) commit(text)
        if (sendAfterCommit) sendRequested()
    }

    val systemVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            systemVoiceText(result.data)?.let { commit(it) }
        }
    }

    fun launchSystemVoice() {
        val tag = VoiceLanguage.recognitionTag(currentLanguage)
        val intent = systemVoiceIntent(context, tag)
        if (intent == null) {
            failure = SpeechError.UNAVAILABLE
            return
        }
        failure = null
        systemVoiceLauncher.launch(intent)
    }

    fun beginRecording() {
        job?.cancel()
        job = null
        failure = null
        buffer = ""
        partial = ""
        volume = VOICE_MIN_VOLUME
        stopping = false
        pendingSend = false
        listening = true
        val languageTag = VoiceLanguage.recognitionTag(currentLanguage)
        job = scope.launch {
            session.start(languageTag).collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> partial = event.text
                    is SpeechEvent.Volume -> volume = event.level
                    is SpeechEvent.Final -> {
                        buffer = appendVoiceSegment(buffer, event.text)
                        partial = ""
                    }

                    is SpeechEvent.Failed -> {
                        failure = event.error
                        if (usesSystemVoice || event.error == SpeechError.UNAVAILABLE) launchSystemVoice()
                    }
                    SpeechEvent.Timeout -> failure = SpeechError.SPEECH_TIMEOUT
                }
            }
            val sendAfterCommit = pendingSend
            pendingSend = false
            finishRecording(sendAfterCommit)
        }
    }

    fun cancelRecording() {
        job?.cancel()
        job = null
        session.cancel()
        listening = false
        stopping = false
        pendingSend = false
        buffer = ""
        partial = ""
        volume = VOICE_MIN_VOLUME
    }

    fun requestStop(sendAfterCommit: Boolean) {
        if (!listening || stopping) return
        stopping = true
        pendingSend = sendAfterCommit
        partial = ""
        session.stop()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            failure = SpeechError.INSUFFICIENT_PERMISSIONS
        }
    }

    val start: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            granted && usesSystemVoice -> launchSystemVoice()
            granted -> beginRecording()
            permanentlyDenied -> {
                failure = SpeechError.INSUFFICIENT_PERMISSIONS
                context.openVoiceAppSettings()
            }

            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(session) {
        onDispose {
            job?.cancel()
            session.cancel()
        }
    }

    return ComposerVoice(
        listening = listening,
        stopping = stopping,
        failure = failure,
        volume = volume,
        buffer = buffer,
        partial = partial,
        start = start,
        stop = { requestStop(sendAfterCommit = false) },
        send = { requestStop(sendAfterCommit = true) },
        cancel = { cancelRecording() },
        clearFailure = { failure = null },
    )
}

private fun Context.findVoiceActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openVoiceAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
