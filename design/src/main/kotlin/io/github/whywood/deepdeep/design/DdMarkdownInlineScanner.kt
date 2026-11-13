package io.github.whywood.deepdeep.design

internal sealed interface InlineNode {
    data class Text(val text: String) : InlineNode
    data class Code(val text: String) : InlineNode
    data class Kbd(val text: String) : InlineNode
    data class FileRef(val text: String, val path: String, val code: Boolean = false) : InlineNode
    data class Bold(val children: List<InlineNode>) : InlineNode
    data class Italic(val children: List<InlineNode>) : InlineNode
    data class Strike(val children: List<InlineNode>) : InlineNode
    data class Link(val children: List<InlineNode>, val destination: String) : InlineNode
    data class Colored(val color: Int, val children: List<InlineNode>) : InlineNode
    data class Superscript(val children: List<InlineNode>) : InlineNode
    data class Subscript(val children: List<InlineNode>) : InlineNode
    data class Math(val text: String) : InlineNode
    data class FootnoteRef(val number: Int) : InlineNode
}

private val htmlTagPattern = Regex("<(/?)([A-Za-z][A-Za-z0-9-]*)((?:\\s[^<>]*?)?)(/?)>")
private val angleUrlPattern = Regex("<(https?://[^>\\s]+)>", RegexOption.IGNORE_CASE)
private val angleEmailPattern = Regex("<([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})>")
private val hrefPattern = Regex("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
private val colorPattern = Regex("color\\s*:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})", RegexOption.IGNORE_CASE)
private val escapableChars = setOf(
    '\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '(', ')', '#', '+', '-', '.', '!', '|', '~', '$', '^',
)
private val urlTrailingChars = setOf('.', ',', ';', ':', '!', '?')
private val workspaceFileExtensions = setOf(
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "md", "markdown", "txt", "json", "xml", "yaml", "yml",
    "toml", "csv", "log", "pdf", "zip", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "h",
    "cpp", "hpp", "css", "html", "sh", "bash", "sql", "rs", "go", "swift", "rb",
)
private val fileReferenceStopChars = setOf(
    ' ', '\t', '\n', '\r', '<', '>', '(', ')', '[', ']', '{', '}', '"', '\'', '`', '|', '*',
    '，', '。', '、', '；', '：', '！', '？', '（', '）', '“', '”', '‘', '’',
)
private val fileReferenceTrailingChars = setOf('.', ',', ';', ':', '!', '?')

internal fun inlineNodes(
    text: String,
    links: Map<String, String> = emptyMap(),
    footnotes: Map<String, Int> = emptyMap(),
): List<InlineNode> = InlineScanner(links, footnotes).parse(text)

private fun linkDestination(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val value = if (trimmed.startsWith("<")) trimmed.substringAfter('<').substringBefore('>') else trimmed
    return value.substringBefore(' ').substringBefore('\t').trim()
}

private fun indexOfIgnoreCase(text: String, needle: String, start: Int): Int {
    var index = start
    while (index + needle.length <= text.length) {
        if (text.regionMatches(index, needle, 0, needle.length, ignoreCase = true)) return index
        index++
    }
    return -1
}

private fun normalizeCodeSpan(content: String): String {
    val normalized = content.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    if (normalized.length >= 2 &&
        normalized.first() == ' ' &&
        normalized.last() == ' ' &&
        normalized.any { it != ' ' }
    ) {
        return normalized.substring(1, normalized.length - 1)
    }
    return normalized
}

internal fun isPathLikeInlineCode(text: String): Boolean {
    if (text.isEmpty() || text.any { it.isWhitespace() }) return false
    val lower = text.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) return false
    if (text.startsWith("./") || text.startsWith("../") || text.startsWith("~/") || text.startsWith("/")) return true
    if (text.contains('/') || text.contains('\\')) return true
    if (hasKnownFileExtension(text)) return true
    return text.length >= 3 && text[0].isLetter() && text[1] == ':' && (text[2] == '\\' || text[2] == '/')
}

internal fun isWorkspaceFileReference(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    if (!hasKnownFileExtension(trimmed)) return false
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("attachments/") || lower.startsWith("workspace/") -> true
        lower.startsWith("files/home/") || lower.startsWith("files/workspaces/") -> true
        lower.startsWith("/") &&
            (lower.contains("/files/home/") ||
                lower.contains("/files/workspaces/") ||
                lower.contains("/files/workspace/")) -> true
        lower.startsWith("file://") -> true
        else -> false
    }
}

private fun hasKnownFileExtension(value: String): Boolean {
    val dot = value.lastIndexOf('.')
    if (dot <= 0 || dot == value.lastIndex) return false
    return value.substring(dot + 1).lowercase() in workspaceFileExtensions
}

private fun fileReferenceEnd(text: String, index: Int): Int {
    val plausible = when (text[index]) {
        'a' -> text.startsWith("attachments/", index)
        'w' -> text.startsWith("workspace/", index)
        'f' -> text.startsWith("file://", index, ignoreCase = true)
        '/' -> true
        else -> false
    }
    if (!plausible) return -1
    if (index > 0) {
        val previous = text[index - 1]
        if (previous.isLetterOrDigit() || previous == '_' || previous == '/' || previous == '.' || previous == '-') {
            return -1
        }
    }
    var end = index
    while (end < text.length && !text[end].isWhitespace() && text[end] !in fileReferenceStopChars) end++
    while (end > index && text[end - 1] in fileReferenceTrailingChars) end--
    if (end <= index) return -1
    return if (isWorkspaceFileReference(text.substring(index, end))) end else -1
}

private fun parseHexColor(value: String): Int? {
    if (value.length != 3 && value.length != 6) return null
    if (value.any { it.digitToIntOrNull(16) == null }) return null
    val expanded = if (value.length == 3) {
        buildString(value.length * 2) {
            value.forEach { char ->
                append(char)
                append(char)
            }
        }
    } else {
        value
    }
    val rgb = expanded.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or rgb
}

private class InlineScanner(
    private val links: Map<String, String>,
    private val footnotes: Map<String, Int>,
) {
    fun parse(text: String): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                nodes += InlineNode.Text(buffer.toString())
                buffer.clear()
            }
        }
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                if (index + 1 >= text.length) {
                    index++
                    continue
                }
                val next = text[index + 1]
                if (next == '\n') {
                    flush()
                    nodes += InlineNode.Text("\n")
                    index += 2
                    continue
                }
                if (next in escapableChars) {
                    buffer.append(next)
                    index += 2
                    continue
                }
                buffer.append('\\')
                index++
                continue
            }
            val fileReference = fileReferenceEnd(text, index)
            if (fileReference > 0) {
                val value = text.substring(index, fileReference)
                flush()
                nodes += InlineNode.FileRef(value, value)
                index = fileReference
                continue
            }
            if (isUrlAt(text, index)) {
                var end = index
                while (end < text.length && !text[end].isWhitespace() && text[end] != '<') end++
                while (end > index && text[end - 1] in urlTrailingChars) end--
                if (end > index) {
                    val url = text.substring(index, end)
                    flush()
                    nodes += InlineNode.Link(listOf(InlineNode.Text(url)), url)
                    index = end
                    continue
                }
            }
            if (char == '`') {
                var openEnd = index
                while (openEnd < text.length && text[openEnd] == '`') openEnd++
                val runLength = openEnd - index
                var scan = openEnd
                var closeStart = -1
                var closeEnd = -1
                while (scan < text.length) {
                    if (text[scan] == '`') {
                        var runEnd = scan
                        while (runEnd < text.length && text[runEnd] == '`') runEnd++
                        if (runEnd - scan == runLength) {
                            closeStart = scan
                            closeEnd = runEnd
                            break
                        }
                        scan = runEnd
                    } else {
                        scan++
                    }
                }
                if (closeStart >= 0) {
                    val content = normalizeCodeSpan(text.substring(openEnd, closeStart))
                    flush()
                    val fileReference = content.trim()
                    nodes += if (isWorkspaceFileReference(fileReference)) {
                        InlineNode.FileRef(content, fileReference, code = true)
                    } else {
                        InlineNode.Code(content)
                    }
                    index = closeEnd
                } else {
                    buffer.append(text, index, openEnd)
                    index = openEnd
                }
                continue
            }
            if (char == '!' && index + 1 < text.length && text[index + 1] == '[') {
                val close = text.indexOf(']', index + 2)
                val open = if (close >= 0) text.indexOf('(', close + 1) else -1
                val end = if (open >= 0) text.indexOf(')', open + 1) else -1
                if (close >= 0 && open == close + 1 && end > open) {
                    buffer.append(text, index + 2, close)
                    index = end + 1
                } else {
                    buffer.append('!')
                    index++
                }
                continue
            }
            if (char == '$') {
                if (text.startsWith("$$", index)) {
                    val end = text.indexOf("$$", index + 2)
                    if (end > index + 1) {
                        val content = text.substring(index + 2, end)
                        if (content.isNotEmpty() && !content.contains('\n')) {
                            flush()
                            nodes += InlineNode.Math(content)
                            index = end + 2
                            continue
                        }
                    }
                    buffer.append("$$")
                    index += 2
                    continue
                }
                if (index + 1 < text.length && !text[index + 1].isWhitespace()) {
                    val end = text.indexOf('$', index + 1)
                    if (end > index + 1) {
                        val content = text.substring(index + 1, end)
                        if (!content.contains('\n') && !content.last().isWhitespace()) {
                            flush()
                            nodes += InlineNode.Math(content)
                            index = end + 1
                            continue
                        }
                    }
                }
                buffer.append('$')
                index++
                continue
            }
            if (char == '[') {
                if (text.startsWith("[^", index)) {
                    val close = text.indexOf(']', index + 2)
                    if (close >= 0) {
                        val number = footnotes[text.substring(index + 2, close).trim().lowercase()]
                        if (number != null) {
                            flush()
                            nodes += InlineNode.FootnoteRef(number)
                        } else {
                            buffer.append(text, index, close + 1)
                        }
                        index = close + 1
                    } else {
                        buffer.append('[')
                        index++
                    }
                    continue
                }
                val close = text.indexOf(']', index + 1)
                if (close < 0) {
                    buffer.append('[')
                    index++
                    continue
                }
                val labelText = text.substring(index + 1, close)
                val after = close + 1
                if (after < text.length && text[after] == '(') {
                    val end = text.indexOf(')', after + 1)
                    if (end > after) {
                        flush()
                        nodes += InlineNode.Link(parse(labelText), linkDestination(text.substring(after + 1, end)))
                        index = end + 1
                        continue
                    }
                } else if (after < text.length && text[after] == '[') {
                    val labelClose = text.indexOf(']', after + 1)
                    if (labelClose >= 0) {
                        val reference = if (labelClose == after + 1) labelText else text.substring(after + 1, labelClose)
                        val destination = links[reference.trim().lowercase()]
                        if (destination != null) {
                            flush()
                            nodes += InlineNode.Link(parse(labelText), destination)
                        } else {
                            buffer.append(text, index, labelClose + 1)
                        }
                        index = labelClose + 1
                        continue
                    }
                } else {
                    val destination = links[labelText.trim().lowercase()]
                    if (destination != null) {
                        flush()
                        nodes += InlineNode.Link(parse(labelText), destination)
                        index = close + 1
                        continue
                    }
                }
                buffer.append('[')
                index++
                continue
            }
            if (char == '<') {
                val angle = angleAutolink(text, index)
                if (angle != null) {
                    flush()
                    nodes += angle.first
                    index = angle.second
                    continue
                }
                val html = inlineHtml(text, index)
                if (html != null) {
                    flush()
                    nodes += html.first
                    index = html.second
                    continue
                }
                buffer.append('<')
                index++
                continue
            }
            if (text.startsWith("**", index)) {
                val end = text.indexOf("**", index + 2)
                if (end >= 0) {
                    flush()
                    nodes += InlineNode.Bold(parse(text.substring(index + 2, end)))
                    index = end + 2
                } else {
                    buffer.append("**")
                    index += 2
                }
                continue
            }
            if (char == '*') {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    flush()
                    nodes += InlineNode.Italic(parse(text.substring(index + 1, end)))
                    index = end + 1
                } else {
                    buffer.append('*')
                    index++
                }
                continue
            }
            if (text.startsWith("~~", index)) {
                val end = text.indexOf("~~", index + 2)
                if (end >= 0) {
                    flush()
                    nodes += InlineNode.Strike(parse(text.substring(index + 2, end)))
                    index = end + 2
                } else {
                    buffer.append("~~")
                    index += 2
                }
                continue
            }
            buffer.append(char)
            index++
        }
        flush()
        return nodes
    }

    private fun isUrlAt(text: String, index: Int): Boolean {
        if (!text.startsWith("http://", index, ignoreCase = true) &&
            !text.startsWith("https://", index, ignoreCase = true)
        ) {
            return false
        }
        return index == 0 || !text[index - 1].isLetterOrDigit()
    }

    private fun angleAutolink(text: String, index: Int): Pair<InlineNode, Int>? {
        val url = angleUrlPattern.matchAt(text, index)
        if (url != null) {
            val value = url.groupValues[1]
            return InlineNode.Link(listOf(InlineNode.Text(value)), value) to (url.range.last + 1)
        }
        val email = angleEmailPattern.matchAt(text, index)
        if (email != null) {
            val value = email.groupValues[1]
            return InlineNode.Link(listOf(InlineNode.Text(value)), "mailto:$value") to (email.range.last + 1)
        }
        return null
    }

    private fun inlineHtml(text: String, index: Int): Pair<List<InlineNode>, Int>? {
        val tag = htmlTagPattern.matchAt(text, index) ?: return null
        val tagEnd = tag.range.last + 1
        val name = tag.groupValues[2].lowercase()
        if (tag.groupValues[1].isNotEmpty()) {
            return emptyList<InlineNode>() to tagEnd
        }
        if (tag.groupValues[4].isNotEmpty() || name == "br") {
            return if (name == "br") listOf(InlineNode.Text("\n")) to tagEnd else emptyList<InlineNode>() to tagEnd
        }
        val closeTag = "</$name>"
        val closeStart = indexOfIgnoreCase(text, closeTag, tagEnd)
        if (closeStart < 0) {
            return emptyList<InlineNode>() to tagEnd
        }
        val inner = text.substring(tagEnd, closeStart)
        val after = closeStart + closeTag.length
        val attributes = tag.groupValues[3]
        val nodes = when (name) {
            "b", "strong" -> listOf(InlineNode.Bold(parse(inner)))
            "i", "em" -> listOf(InlineNode.Italic(parse(inner)))
            "sup" -> listOf(InlineNode.Superscript(parse(inner)))
            "sub" -> listOf(InlineNode.Subscript(parse(inner)))
            "code" -> {
                val path = inner.trim()
                listOf(
                    if (isWorkspaceFileReference(path)) {
                        InlineNode.FileRef(inner, path, code = true)
                    } else {
                        InlineNode.Code(inner)
                    },
                )
            }
            "kbd" -> listOf(InlineNode.Kbd(inner))
            "a" -> {
                val href = hrefPattern.find(attributes)?.let { match ->
                    match.groupValues[1].ifEmpty { match.groupValues[2] }
                }
                val children = parse(inner)
                if (href.isNullOrBlank()) children else listOf(InlineNode.Link(children, href))
            }

            "span" -> {
                val color = colorPattern.find(attributes)?.groupValues?.get(1)?.let { parseHexColor(it) }
                val children = parse(inner)
                if (color == null) children else listOf(InlineNode.Colored(color, children))
            }

            else -> parse(inner)
        }
        return nodes to after
    }
}
