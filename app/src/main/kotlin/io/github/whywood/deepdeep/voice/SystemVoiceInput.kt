package io.github.whywood.deepdeep.voice

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent

private const val VOICE_RECOGNITION_SERVICE = "voice_recognition_service"

internal fun hasDefaultRecognitionService(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, VOICE_RECOGNITION_SERVICE)
}.getOrNull()?.isNotBlank() == true

internal fun systemVoiceIntent(context: Context, languageTag: String): Intent? {
    val packageManager = context.packageManager
    val resolved = runCatching {
        packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
    }.getOrDefault(emptyList())
    val target = resolved.firstOrNull()?.activityInfo ?: return null
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        setClassName(target.packageName, target.name)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
}

internal fun systemVoiceText(data: Intent?): String? = data
    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
    ?.firstOrNull()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
