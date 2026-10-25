package io.github.whywood.deepdeep.voice

import android.speech.SpeechRecognizer

internal sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Volume(val level: Float) : SpeechEvent
    data class Failed(val error: SpeechError, val code: Int) : SpeechEvent
    data object Timeout : SpeechEvent
}

internal enum class SpeechError {
    NO_MATCH,
    SPEECH_TIMEOUT,
    INSUFFICIENT_PERMISSIONS,
    NETWORK,
    NETWORK_TIMEOUT,
    RECOGNIZER_BUSY,
    LANGUAGE_UNAVAILABLE,
    LANGUAGE_NOT_SUPPORTED,
    SERVER,
    SERVER_DISCONNECTED,
    CLIENT,
    AUDIO,
    TOO_MANY_REQUESTS,
    CANNOT_CHECK_SUPPORT,
    CANNOT_LISTEN_TO_DOWNLOAD,
    UNAVAILABLE,
    UNKNOWN,
}

internal fun speechEventForError(code: Int): SpeechEvent = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH -> SpeechEvent.Failed(SpeechError.NO_MATCH, code)
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechEvent.Timeout
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        SpeechEvent.Failed(SpeechError.INSUFFICIENT_PERMISSIONS, code)

    SpeechRecognizer.ERROR_NETWORK -> SpeechEvent.Failed(SpeechError.NETWORK, code)
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechEvent.Failed(SpeechError.NETWORK_TIMEOUT, code)
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechEvent.Failed(SpeechError.RECOGNIZER_BUSY, code)
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        SpeechEvent.Failed(SpeechError.LANGUAGE_UNAVAILABLE, code)

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        SpeechEvent.Failed(SpeechError.LANGUAGE_NOT_SUPPORTED, code)

    SpeechRecognizer.ERROR_SERVER -> SpeechEvent.Failed(SpeechError.SERVER, code)
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
        SpeechEvent.Failed(SpeechError.SERVER_DISCONNECTED, code)

    SpeechRecognizer.ERROR_CLIENT -> SpeechEvent.Failed(SpeechError.CLIENT, code)
    SpeechRecognizer.ERROR_AUDIO -> SpeechEvent.Failed(SpeechError.AUDIO, code)
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SpeechEvent.Failed(SpeechError.TOO_MANY_REQUESTS, code)
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
        SpeechEvent.Failed(SpeechError.CANNOT_CHECK_SUPPORT, code)

    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
        SpeechEvent.Failed(SpeechError.CANNOT_LISTEN_TO_DOWNLOAD, code)

    else -> SpeechEvent.Failed(SpeechError.UNKNOWN, code)
}

internal fun rmsLevel(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0.08f, 1f)
