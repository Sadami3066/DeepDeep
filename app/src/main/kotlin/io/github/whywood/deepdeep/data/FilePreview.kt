package io.github.whywood.deepdeep.data

import java.io.File
import java.util.Locale

internal const val TEXT_PREVIEW_LIMIT_BYTES = 256 * 1024

internal enum class FilePreviewKind {
    IMAGE,
    MARKDOWN,
    HTML,
    CODE,
    VIDEO,
    UNSUPPORTED,
}

private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif")
private val videoExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")
private val htmlExtensions = setOf("html", "htm", "xhtml")
private val markdownExtensions = setOf("md", "markdown", "mdown", "mkd")
private val codeExtensions = setOf(
    "txt", "log", "json", "xml", "yaml", "yml", "toml", "kt", "kts", "java", "py", "js", "ts", "tsx",
    "jsx", "c", "h", "cpp", "hpp", "css", "sh", "bash", "sql", "rs", "go", "swift", "rb", "csv", "ini", "conf",
)
private val codeMimeTypes = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-yaml",
    "application/yaml",
    "application/toml",
)
private val codeLanguages = mapOf(
    "kt" to "kotlin",
    "kts" to "kotlin",
    "java" to "java",
    "js" to "js",
    "jsx" to "js",
    "ts" to "ts",
    "tsx" to "ts",
    "py" to "python",
    "sh" to "bash",
    "bash" to "bash",
    "json" to "json",
    "xml" to "xml",
    "html" to "html",
    "htm" to "html",
    "css" to "css",
    "sql" to "sql",
    "yaml" to "yaml",
    "yml" to "yaml",
    "toml" to "toml",
    "go" to "go",
    "rs" to "rust",
    "c" to "c",
    "h" to "c",
    "cpp" to "cpp",
    "hpp" to "cpp",
    "swift" to "swift",
    "rb" to "ruby",
)

internal fun filePreviewKind(name: String, mime: String): FilePreviewKind {
    when (fileExtension(name)) {
        in imageExtensions -> return FilePreviewKind.IMAGE
        in videoExtensions -> return FilePreviewKind.VIDEO
        in htmlExtensions -> return FilePreviewKind.HTML
        in markdownExtensions -> return FilePreviewKind.MARKDOWN
        in codeExtensions -> return FilePreviewKind.CODE
    }
    val normalized = mime.substringBefore(';').trim().lowercase(Locale.US)
    return when {
        normalized.startsWith("image/") -> FilePreviewKind.IMAGE
        normalized.startsWith("video/") -> FilePreviewKind.VIDEO
        normalized == "text/html" -> FilePreviewKind.HTML
        normalized == "text/markdown" -> FilePreviewKind.MARKDOWN
        normalized.startsWith("text/") -> FilePreviewKind.CODE
        normalized in codeMimeTypes -> FilePreviewKind.CODE
        else -> FilePreviewKind.UNSUPPORTED
    }
}

internal fun codeLanguageFor(name: String): String? = codeLanguages[fileExtension(name)]

internal data class TextFileContent(val text: String, val truncated: Boolean)

internal fun readTextLimited(file: File, limitBytes: Int = TEXT_PREVIEW_LIMIT_BYTES): TextFileContent? {
    if (!file.isFile) return null
    return runCatching {
        val limit = limitBytes.coerceAtLeast(1)
        val buffer = ByteArray(limit + 1)
        var total = 0
        file.inputStream().use { input ->
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
        }
        val truncated = total > limit
        val size = if (truncated) limit else total
        for (index in 0 until size) {
            if (buffer[index] == 0.toByte()) return@runCatching null
        }
        TextFileContent(String(buffer, 0, size, Charsets.UTF_8), truncated)
    }.getOrNull()
}

private fun fileExtension(name: String): String =
    name.substringAfterLast('.', "").trim().lowercase(Locale.US)
