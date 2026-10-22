package io.github.whywood.deepdeep.data

import java.util.Locale

enum class AttachmentKind {
    IMAGE,
    TEXT,
    OTHER,
}

data class AttachmentRef(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val path: String,
    val kind: AttachmentKind,
)

private const val ATTACHMENTS_OPEN = "<attachments>"
private const val ATTACHMENTS_CLOSE = "</attachments>"

internal fun attachmentKind(mime: String, name: String): AttachmentKind {
    if (mime.startsWith("image/")) return AttachmentKind.IMAGE
    if (mime.startsWith("text/")) return AttachmentKind.TEXT
    if (mime in setOf("application/json", "application/xml", "application/javascript", "application/x-yaml")) {
        return AttachmentKind.TEXT
    }
    val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> AttachmentKind.IMAGE
        "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml", "kt", "kts", "java", "py", "js",
        "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "css", "html", "htm", "sh", "bash", "sql", "rs",
        "go", "swift", "rb", "log", "csv", "ini", "conf" -> AttachmentKind.TEXT

        else -> AttachmentKind.OTHER
    }
}

internal fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "txt", "log" -> "text/plain"
    "md", "markdown" -> "text/markdown"
    "json" -> "application/json"
    "xml" -> "application/xml"
    "yaml", "yml" -> "application/x-yaml"
    "toml" -> "application/toml"
    "csv" -> "text/csv"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}

internal fun sanitizeAttachmentName(raw: String): String {
    val normalized = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
    return normalized.take(96).ifBlank { "attachment" }
}

fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

fun encodeAttachmentBlock(attachments: List<AttachmentRef>): String {
    if (attachments.isEmpty()) return ""
    return buildString {
        appendLine(ATTACHMENTS_OPEN)
        attachments.forEach { attachment ->
            append("- ")
            append(attachment.path)
            append(" | ")
            append(attachment.name.replace('|', '-'))
            append(" | ")
            append(attachment.mime)
            append(" | ")
            append(attachment.size)
            appendLine()
        }
        appendLine("files are in the workspace; use the read tool to inspect them")
        append(ATTACHMENTS_CLOSE)
    }.trimEnd()
}

data class ParsedMessageText(
    val attachments: List<AttachmentRef>,
    val text: String,
)

fun parseAttachmentBlock(source: String): ParsedMessageText {
    val open = source.indexOf(ATTACHMENTS_OPEN)
    if (open < 0) return ParsedMessageText(emptyList(), source.trim())
    val close = source.indexOf(ATTACHMENTS_CLOSE, open + ATTACHMENTS_OPEN.length)
    if (close < 0) return ParsedMessageText(emptyList(), source.trim())
    val block = source.substring(open + ATTACHMENTS_OPEN.length, close)
    val attachments = block.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("- ") }
        .mapNotNull { line ->
            val parts = line.removePrefix("- ").split(" | ").map { it.trim() }
            if (parts.size < 4) return@mapNotNull null
            val path = parts[0]
            val name = parts[1].ifBlank { path.substringAfterLast('/') }
            val mime = parts[2].ifBlank { guessMime(name) }
            val size = parts[3].toLongOrNull() ?: 0L
            AttachmentRef(
                id = path,
                name = name,
                mime = mime,
                size = size,
                path = path,
                kind = attachmentKind(mime, name),
            )
        }
        .toList()
    val text = (source.substring(0, open) + source.substring(close + ATTACHMENTS_CLOSE.length))
        .trim()
    return ParsedMessageText(attachments, text)
}
