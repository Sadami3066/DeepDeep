package io.github.whywood.deepdeep.service

import android.content.Context
import java.io.File

object Diagnostics {
    private const val MAX_LINES = 400
    private const val MAX_BYTES = 128 * 1024L

    fun log(context: Context, message: String, error: Throwable) {
        log(File(context.filesDir, "diagnostics"), message, error)
    }

    fun log(directory: File, message: String, error: Throwable) {
        runCatching {
            val file = File(directory, "errors.log")
            directory.mkdirs()
            val detail = error.message?.replace('\n', ' ')?.take(300)
            file.appendText("${System.currentTimeMillis()} $message ${error::class.java.simpleName}: $detail\n")
            val lines = file.readLines()
            if (file.length() > MAX_BYTES || lines.size > MAX_LINES) {
                file.writeText(lines.takeLast(MAX_LINES).joinToString(separator = "\n", postfix = "\n"))
            }
        }
    }
}
