package io.github.whywood.deepdeep.data.crash

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashInfo(
    val versionName: String,
    val versionCode: Long,
    val device: String,
    val androidSdk: Int,
)

class CrashLogStore(
    private val directory: File,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    data class Entry(val file: File, val recordedAt: Long)

    fun record(thread: Thread, error: Throwable, info: CrashInfo): Entry? {
        val text = format(thread, error, info, System.currentTimeMillis())
        return runCatching {
            directory.mkdirs()
            val timestamp = System.currentTimeMillis()
            val target = File(directory, "crash-$timestamp.txt")
            val temporary = File(directory, "crash-$timestamp.part")
            temporary.writeText(text)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            prune()
            Entry(target, timestamp)
        }.getOrNull()
    }

    fun latest(): Entry? = entries().firstOrNull()

    fun entries(): List<Entry> =
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(NAME_PREFIX) && it.name.endsWith(NAME_SUFFIX) }
            .sortedByDescending { it.name }
            .mapNotNull { file ->
                val time = file.name.removePrefix(NAME_PREFIX).removeSuffix(NAME_SUFFIX).toLongOrNull()
                    ?: file.lastModified()
                Entry(file, time)
            }

    fun read(entry: Entry): String = runCatching { entry.file.readText() }.getOrDefault("")

    fun clear() {
        entries().forEach { entry -> runCatching { entry.file.delete() } }
    }

    internal fun format(thread: Thread, error: Throwable, info: CrashInfo, timestamp: Long): String {
        val time = TIME_FORMAT.format(Date(timestamp))
        return buildString {
            appendLine("DeepDeep crash report")
            appendLine("time: $time")
            appendLine("version: ${info.versionName} (${info.versionCode})")
            appendLine("android: ${info.androidSdk}")
            appendLine("device: ${info.device}")
            appendLine("thread: ${thread.name}")
            appendLine()
            appendLine("${error.javaClass.name}: ${error.message.orEmpty()}")
            val stack = runCatching { error.stackTraceToString() }.getOrDefault("")
            append(stack.take(MAX_STACK_CHARS))
        }
    }

    private fun prune() {
        entries().drop(maxEntries).forEach { entry -> runCatching { entry.file.delete() } }
    }

    private companion object {
        const val MAX_ENTRIES = 5
        const val MAX_STACK_CHARS = 64 * 1024
        const val NAME_PREFIX = "crash-"
        const val NAME_SUFFIX = ".txt"
        val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
    }
}
