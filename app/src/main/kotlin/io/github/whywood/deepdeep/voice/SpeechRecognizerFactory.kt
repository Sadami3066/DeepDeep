package io.github.whywood.deepdeep.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

internal fun createSpeechRecognizer(context: Context): SpeechRecognizer? {
    val appContext = context.applicationContext
    for (component in recognitionCandidates(appContext)) {
        val speech = runCatching {
            SpeechRecognizer.createSpeechRecognizer(appContext, component)
        }.getOrNull()
        if (speech != null) return speech
    }
    return runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
}

private fun recognitionCandidates(context: Context): List<ComponentName> {
    val packageManager = context.packageManager
    return runCatching {
        packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
    }.getOrDefault(emptyList())
        .mapNotNull { resolve -> resolve.serviceInfo?.let { Info(it.packageName, it.name, it.permission) } }
        .distinctBy { Info(it.packageName, it.name, it.permission) }
        .sortedWith(
            compareBy<Info> { if (it.permission == null) 0 else 1 }
                .thenBy { it.packageName }
                .thenBy { it.name },
        )
        .map { ComponentName(it.packageName, it.name) }
}

private data class Info(val packageName: String, val name: String, val permission: String?)
