package io.github.whywood.deepdeep.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.whywood.deepdeep.data.AppLanguage

internal class TextSpeaker(context: Context) {
    private companion object {
        const val UTTERANCE_PREFIX = "deepdeep-voice"
    }

    private data class PendingSpeech(val text: String, val language: AppLanguage)

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var released = false
    private var language = AppLanguage.SYSTEM
    private var pending: PendingSpeech? = null
    private var lastUtteranceId: String? = null
    private var utteranceSeq = 0

    var available by mutableStateOf<Boolean?>(null)
        private set

    var speaking by mutableStateOf(false)
        private set

    fun speak(text: String, language: AppLanguage) {
        val body = text.trim()
        if (body.isEmpty() || released) return
        this.language = language
        val active = engine
        if (active != null && initialized) {
            if (applyLanguage(active, language)) {
                available = true
                startSpeech(active, body)
            } else {
                available = false
            }
            return
        }
        pending = PendingSpeech(body, language)
        speaking = true
        if (engine == null) initialize()
    }

    fun stop() {
        pending = null
        lastUtteranceId = null
        speaking = false
        runCatching { engine?.stop() }
    }

    fun release() {
        released = true
        pending = null
        lastUtteranceId = null
        speaking = false
        val speech = engine
        engine = null
        initialized = false
        runCatching {
            speech?.stop()
            speech?.shutdown()
        }
    }

    private fun initialize() {
        available = null
        val speech = runCatching {
            TextToSpeech(appContext) { status -> main.post { handleInit(status) } }
        }.getOrNull()
        if (speech == null) {
            available = false
            pending = null
            speaking = false
            return
        }
        engine = speech
    }

    private fun handleInit(status: Int) {
        val speech = engine
        if (speech == null) return
        if (status != TextToSpeech.SUCCESS) {
            engine = null
            initialized = false
            pending = null
            speaking = false
            available = false
            runCatching { speech.shutdown() }
            return
        }
        initialized = true
        runCatching { speech.setSpeechRate(1.0f) }
        speech.setOnUtteranceProgressListener(progressListener)
        val next = pending
        pending = null
        if (next == null) {
            available = applyLanguage(speech, language)
            return
        }
        language = next.language
        if (applyLanguage(speech, next.language)) {
            available = true
            startSpeech(speech, next.text)
        } else {
            speaking = false
            available = false
        }
    }

    private fun startSpeech(speech: TextToSpeech, text: String) {
        val chunks = splitSpeechText(text, TextToSpeech.getMaxSpeechInputLength())
        if (chunks.isEmpty()) {
            speaking = false
            return
        }
        utteranceSeq += 1
        val ids = chunks.indices.map { "$UTTERANCE_PREFIX-$utteranceSeq-$it" }
        lastUtteranceId = ids.last()
        speaking = true
        chunks.forEachIndexed { index, chunk ->
            val result = speech.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                ids[index],
            )
            if (result == TextToSpeech.ERROR) {
                lastUtteranceId = null
                speaking = false
                return
            }
        }
    }

    private fun applyLanguage(speech: TextToSpeech, language: AppLanguage): Boolean {
        for (locale in VoiceLanguage.ttsCandidates(language)) {
            val status = speech.setLanguage(locale)
            if (status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
    }

    private fun finishIfCurrent(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == lastUtteranceId) {
                lastUtteranceId = null
                speaking = false
            }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finishIfCurrent(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finishIfCurrent(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finishIfCurrent(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finishIfCurrent(utteranceId)
    }
}
