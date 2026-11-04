package io.github.whywood.deepdeep.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

internal const val VOICE_STOP_TIMEOUT_MS = 1200L
internal const val VOICE_RESTART_DELAY_MS = 200L
internal const val MAX_VOICE_RETRIES = 2

internal enum class VoiceErrorDisposition { RESTART, RETRY, FAIL }

internal fun voiceErrorDisposition(error: SpeechError): VoiceErrorDisposition = when (error) {
    SpeechError.NO_MATCH, SpeechError.SPEECH_TIMEOUT -> VoiceErrorDisposition.RESTART
    SpeechError.NETWORK,
    SpeechError.NETWORK_TIMEOUT,
    SpeechError.SERVER,
    SpeechError.SERVER_DISCONNECTED,
    SpeechError.TOO_MANY_REQUESTS,
    -> VoiceErrorDisposition.RETRY

    else -> VoiceErrorDisposition.FAIL
}

internal fun voiceShouldRetry(error: SpeechError, attempt: Int): Boolean =
    voiceErrorDisposition(error) == VoiceErrorDisposition.RETRY && attempt < MAX_VOICE_RETRIES

internal fun speechErrorForCode(code: Int): SpeechError =
    (speechEventForError(code) as? SpeechEvent.Failed)?.error ?: SpeechError.SPEECH_TIMEOUT

internal class ContinuousVoiceSession(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var active: ActiveSession? = null

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun start(languageTag: String): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Failed(SpeechError.UNAVAILABLE, 0))
            close()
            return@callbackFlow
        }
        val cancelled = AtomicBoolean(false)
        val session = ActiveSession(this, languageTag)
        main.post {
            if (cancelled.get()) {
                session.release()
                return@post
            }
            active?.cancelNow()
            active = session
            session.begin()
        }
        awaitClose {
            cancelled.set(true)
            main.post {
                if (active === session) active = null
                session.release()
            }
        }
    }.buffer(Channel.UNLIMITED)

    fun stop() {
        main.post { active?.requestStop() }
    }

    fun cancel() {
        main.post { active?.cancelNow() }
    }

    private fun recognitionIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private inner class ActiveSession(
        private val events: SendChannel<SpeechEvent>,
        private val languageTag: String,
    ) : RecognitionListener {
        private var recognizer: SpeechRecognizer? = null
        private var segmentOpen = false
        private var stopped = false
        private var finished = false
        private var retries = 0
        private var lastVolume = -1f

        private val volumeStep = 0.04f

        private val restart = Runnable { startSegment() }
        private val stopTimeout = Runnable { finish() }

        fun begin() {
            if (finished) return
            val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
            if (speech == null) {
                fail(SpeechError.UNAVAILABLE)
                return
            }
            recognizer = speech
            speech.setRecognitionListener(this)
            startSegment()
        }

        fun requestStop() {
            if (finished || stopped) return
            stopped = true
            main.removeCallbacks(restart)
            if (!segmentOpen) {
                finish()
                return
            }
            runCatching { recognizer?.stopListening() }
            main.postDelayed(stopTimeout, VOICE_STOP_TIMEOUT_MS)
        }

        fun cancelNow() {
            finish()
        }

        fun release() {
            finish()
        }

        private fun finish() {
            if (finished) return
            finished = true
            segmentOpen = false
            main.removeCallbacks(restart)
            main.removeCallbacks(stopTimeout)
            val speech = recognizer
            recognizer = null
            events.close()
            if (speech != null) {
                runCatching { speech.cancel() }
                runCatching { speech.destroy() }
            }
        }

        private fun fail(error: SpeechError) {
            if (finished) return
            events.trySend(SpeechEvent.Failed(error, 0))
            finish()
        }

        private fun startSegment() {
            if (finished || stopped) return
            val speech = recognizer ?: return
            segmentOpen = true
            val started = runCatching {
                speech.startListening(recognitionIntent(languageTag))
            }.isSuccess
            if (started) return
            segmentOpen = false
            fail(SpeechError.CLIENT)
        }

        private fun scheduleRestart() {
            if (finished || stopped) return
            main.removeCallbacks(restart)
            main.postDelayed(restart, VOICE_RESTART_DELAY_MS)
        }

        private fun deliverFinal(text: String) {
            events.trySend(SpeechEvent.Final(text))
            retries = 0
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (finished) return
            val level = rmsLevel(rmsdB)
            if (abs(level - lastVolume) < volumeStep) return
            lastVolume = level
            events.trySend(SpeechEvent.Volume(level))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (finished) return
            val text = recognitionText(partialResults)
            if (text.isNotEmpty()) events.trySend(SpeechEvent.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            if (finished) return
            segmentOpen = false
            val text = recognitionText(results)
            if (text.isNotEmpty()) deliverFinal(text)
            if (stopped) {
                finish()
                return
            }
            scheduleRestart()
        }

        override fun onError(error: Int) {
            if (finished) return
            segmentOpen = false
            if (stopped) {
                finish()
                return
            }
            val speechError = speechErrorForCode(error)
            when (voiceErrorDisposition(speechError)) {
                VoiceErrorDisposition.RESTART -> scheduleRestart()
                VoiceErrorDisposition.RETRY -> {
                    if (voiceShouldRetry(speechError, retries)) {
                        retries += 1
                        scheduleRestart()
                    } else {
                        fail(speechError)
                    }
                }

                VoiceErrorDisposition.FAIL -> fail(speechError)
            }
        }
    }
}

private fun recognitionText(results: Bundle?): String = runCatching {
    results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
}.getOrDefault("")
