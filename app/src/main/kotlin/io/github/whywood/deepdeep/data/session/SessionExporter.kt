package io.github.whywood.deepdeep.data.session

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionExporter {
    fun suggestedFileName(title: String, sessionId: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        val base = cleaned.take(40)
            .trimEnd(' ', '_', '.', ',', '\uFF0C', '\u3001', ';', '\uFF1B', ':', '\uFF1A', '-')
            .ifBlank { "session" }
        return "$base-$sessionId.jsonl"
    }

    suspend fun write(resolver: ContentResolver, target: Uri, filePath: String) {
        withContext(Dispatchers.IO) {
            val source = File(filePath)
            require(source.isFile) { "Session file is missing: $filePath" }
            resolver.openOutputStream(target)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open export target")
        }
    }
}
