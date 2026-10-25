package io.github.whywood.deepdeep.voice

private const val BREAK_CHARS = "。！？；.!?;\n，,、"

internal fun splitSpeechText(text: String, maxLength: Int): List<String> {
    val source = text.trim()
    if (source.isEmpty()) return emptyList()
    if (maxLength <= 0 || source.length <= maxLength) return listOf(source)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < source.length) {
        val hardEnd = (start + maxLength).coerceAtMost(source.length)
        var end = if (hardEnd == source.length) hardEnd else preferredBreak(source, start, hardEnd)
        if (end <= start) {
            end = start + if (
                start + 1 < source.length &&
                source[start].isHighSurrogate() &&
                source[start + 1].isLowSurrogate()
            ) {
                2
            } else {
                1
            }
        }
        chunks += source.substring(start, end)
        start = end
    }
    return chunks
}

private fun preferredBreak(text: String, start: Int, hardEnd: Int): Int {
    val floor = start + (hardEnd - start) / 2
    for (index in hardEnd - 1 downTo floor) {
        if (text[index] in BREAK_CHARS) return boundaryBefore(text, index + 1)
    }
    for (index in hardEnd - 1 downTo floor) {
        if (text[index].isWhitespace()) return boundaryBefore(text, index + 1)
    }
    return boundaryBefore(text, hardEnd)
}

private fun boundaryBefore(text: String, index: Int): Int {
    if (index <= 0 || index >= text.length) return index
    return if (text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()) index - 1 else index
}
