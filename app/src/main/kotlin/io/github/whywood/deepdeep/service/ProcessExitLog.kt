package io.github.whywood.deepdeep.service

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

object ProcessExitLog {
    fun record(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrNull().orEmpty()
        if (exits.isEmpty()) return
        val file = File(context.filesDir, "diagnostics/process-exits.log")
        val lastRecorded = lastTimestamp(file)
        val fresh = exits
            .filter { it.timestamp > lastRecorded }
            .sortedBy { it.timestamp }
        if (fresh.isEmpty()) return
        file.parentFile?.mkdirs()
        val lines = fresh.joinToString(separator = "") { exit ->
            val description = exit.description?.replace('\n', ' ').orEmpty()
            "${exit.timestamp} reason=${exit.reason} " +
                "importance=${exit.importance} pssKb=${exit.pss} rssKb=${exit.rss} " +
                "desc=$description\n"
        }
        runCatching { file.appendText(lines) }
        if (file.length() > MAX_BYTES) {
            runCatching {
                val tail = file.readLines().takeLast(MAX_LINES)
                file.writeText(tail.joinToString(separator = "\n", postfix = "\n"))
            }
        }
    }

    private fun lastTimestamp(file: File): Long {
        if (!file.exists()) return 0L
        val line = runCatching { file.readLines().lastOrNull { it.isNotBlank() } }.getOrNull() ?: return 0L
        return line.substringBefore(' ').toLongOrNull() ?: 0L
    }

    private const val MAX_BYTES = 64 * 1024L
    private const val MAX_LINES = 200
}
