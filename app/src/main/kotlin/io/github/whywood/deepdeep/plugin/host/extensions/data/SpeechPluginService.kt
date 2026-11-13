package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.plugin.api.PluginSpeechResult
import io.github.whywood.deepdeep.plugin.api.SpeechService
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.SpeechEvent
import io.github.whywood.deepdeep.voice.SpeechRecognizerController
import io.github.whywood.deepdeep.voice.TextSpeaker
import io.github.whywood.deepdeep.voice.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun speechErrorMessage(error: SpeechError): String = when (error) {
    SpeechError.NO_MATCH -> "No speech was recognized; try again"
    SpeechError.SPEECH_TIMEOUT -> "Speech recognition timed out"
    SpeechError.INSUFFICIENT_PERMISSIONS -> "Microphone permission was not granted"
    SpeechError.NETWORK -> "Speech recognition network error"
    SpeechError.NETWORK_TIMEOUT -> "Speech recognition network timed out"
    SpeechError.RECOGNIZER_BUSY -> "Speech recognizer is busy; retry in a moment"
    SpeechError.LANGUAGE_UNAVAILABLE, SpeechError.LANGUAGE_NOT_SUPPORTED ->
        "Speech recognition does not support the current language"

    SpeechError.SERVER -> "Speech recognition server error"
    SpeechError.SERVER_DISCONNECTED -> "Speech recognition server disconnected"
    SpeechError.CLIENT -> "Speech recognition client error"
    SpeechError.AUDIO -> "Speech recognition audio error"
    SpeechError.TOO_MANY_REQUESTS -> "Speech recognition is rate limited; retry later"
    SpeechError.CANNOT_CHECK_SUPPORT, SpeechError.CANNOT_LISTEN_TO_DOWNLOAD,
    SpeechError.UNAVAILABLE,
    -> "No speech recognition service is available on this device"

    SpeechError.UNKNOWN -> "Speech recognition failed"
}

internal class SpeechPluginServiceImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val language: () -> AppLanguage,
    private val interaction: PluginInteraction,
) : SpeechService {
    private val speaker = TextSpeaker(context)
    private val recognizer = SpeechRecognizerController(context)

    init {
        scope.launch {
            try {
                awaitCancellation()
            } finally {
                runCatching { speaker.release() }
            }
        }
    }

    override val speakAvailable: Boolean
        get() = speaker.available != false

    override val recognizeAvailable: Boolean
        get() = recognizer.isAvailable()

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext false
        speaker.speak(body, language())
        true
    }

    override fun stopSpeaking() {
        speaker.stop()
    }

    override fun speaking(): Flow<Boolean> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                trySend(speaker.speaking)
                handler.postDelayed(this, SPEAKING_POLL_MS)
            }
        }
        handler.post(tick)
        awaitClose { handler.removeCallbacks(tick) }
    }

    override suspend fun recognize(timeoutMs: Long): PluginSpeechResult {
        if (!recognizeAvailable) {
            return PluginSpeechResult(text = null, error = "No speech recognition service is available on this device")
        }
        if (!DataPermissions.granted(context, Manifest.permission.RECORD_AUDIO)) {
            val granted = interaction.requestPermissions(DataPermissions.microphone)
            if (granted[Manifest.permission.RECORD_AUDIO] != true) {
                return PluginSpeechResult(text = null, error = "Microphone permission was not granted")
            }
        }
        val timeout = timeoutMs.coerceIn(1_000L, MAX_TIMEOUT_MS)
        val event = withTimeoutOrNull(timeout) {
            recognizer.listen(VoiceLanguage.recognitionTag(language())).first { event ->
                event is SpeechEvent.Final || event is SpeechEvent.Failed || event is SpeechEvent.Timeout
            }
        }
        return when (event) {
            is SpeechEvent.Final -> PluginSpeechResult(text = event.text.takeIf { it.isNotBlank() })
            is SpeechEvent.Failed -> PluginSpeechResult(text = null, error = speechErrorMessage(event.error))
            is SpeechEvent.Timeout -> PluginSpeechResult(text = null, error = "Speech recognition timed out")
            null -> PluginSpeechResult(text = null, error = "Speech recognition timed out after ${timeout}ms")
            else -> PluginSpeechResult(text = null, error = "Speech recognition failed")
        }
    }

    override fun openVoiceSettings(): Boolean {
        val intent = Intent(ACTION_TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

        const val SPEAKING_POLL_MS = 100L

        const val MAX_TIMEOUT_MS = 120_000L
    }
}
