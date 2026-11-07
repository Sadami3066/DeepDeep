package io.github.whywood.deepdeep.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

internal class SpeechRecognizerController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var activeRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun stopListening() {
        main.post {
            activeRecognizer?.let { recognizer ->
                runCatching { recognizer.stopListening() }
            }
        }
    }

    fun listen(languageTag: String): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Failed(SpeechError.UNAVAILABLE, 0))
            close()
            return@callbackFlow
        }
        var recognizer: SpeechRecognizer? = null
        var lastVolume = -1f
        val volumeStep = 0.04f
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onRmsChanged(rmsdB: Float) {
                val level = rmsLevel(rmsdB)
                if (abs(level - lastVolume) < volumeStep) return
                lastVolume = level
                trySend(SpeechEvent.Volume(level))
            }

            override fun onError(error: Int) {
                trySend(speechEventForError(error))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) {
                    trySend(SpeechEvent.Failed(SpeechError.NO_MATCH, SpeechRecognizer.ERROR_NO_MATCH))
                } else {
                    trySend(SpeechEvent.Final(text))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) trySend(SpeechEvent.Partial(text))
            }
        }
        main.post {
            if (isClosedForSend) return@post
            val speech = createRecognizer()
            if (speech == null) {
                trySend(SpeechEvent.Failed(SpeechError.UNAVAILABLE, 0))
                close()
                return@post
            }
            recognizer = speech
            activeRecognizer = speech
            speech.setRecognitionListener(listener)
            val started = runCatching {
                speech.startListening(recognitionIntent(languageTag))
            }.isSuccess
            if (!started) {
                trySend(SpeechEvent.Failed(SpeechError.CLIENT, 0))
                close()
            }
        }
        awaitClose {
            main.post {
                val speech = recognizer
                recognizer = null
                if (activeRecognizer === speech) activeRecognizer = null
                if (speech != null) {
                    runCatching { speech.cancel() }
                    runCatching { speech.destroy() }
                }
            }
        }
    }.buffer(Channel.UNLIMITED)

    private fun createRecognizer(): SpeechRecognizer? = createSpeechRecognizer(appContext)

    private fun recognitionIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
}
