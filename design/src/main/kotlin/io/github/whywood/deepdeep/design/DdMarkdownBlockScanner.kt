package io.github.whywood.deepdeep.design

private data class RawListItem(
    val indent: Int,
    val marker: MarkdownMarker,
    val text: StringBuilder,
)

internal class InlineContext {
    val links = LinkedHashMap<String, String>()
    val footnoteNumbers = LinkedHashMap<String, Int>()
    val footnotes = mutableListOf<MarkdownFootnote>()
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val rulePattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val imagePattern = Regex("^!\\[([^\\]]*)]\\(\\s*([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\s*\\)$")
private val listItemPattern = Regex("^([ \\t]*)([-*+]|\\d{1,9}[.)])\\s+(.*)$")
private val taskItemPattern = Regex("^([ \\t]*)([-*+])\\s+\\[([ xX])]\\s+(.*)$")
private val tableSeparatorPattern = Regex("^:?-{3,}:?$")
private val setextH1Pattern = Regex("^={3,}$")
private val setextH2Pattern = Regex("^-{3,}$")
private val footnoteDefinitionPattern = Regex("^\\[\\^([^\\]]+)]:\\s*(.+)$")
private val referenceDefinitionPattern =
    Regex("^\\[([^\\]^]+)]:\\s*(\\S+)(?:\\s+(?:\"[^\"]*\"|'[^']*'|\\([^)]*\\)))?\\s*$")

internal sealed interface MarkdownDefinition {
    data class Footnote(val label: String, val text: String) : MarkdownDefinition

    data class Link(val label: String, val destination: String) : MarkdownDefinition
}

internal fun collectQuoteEntries(lines: List<String>): List<MarkdownDefinition> {
    val entries = mutableListOf<MarkdownDefinition>()
    val clean = mutableListOf<String>()
    if (lines.none { line -> line.trimStart().startsWith("[") }) {
        clean += lines
    } else {
        extractDefinitionLines(lines, 0, lines.size, false, clean, entries)
    }
    val result = entries.toMutableList()
    val quotes = mutableListOf<List<String>>()
    scanStructure(clean, onQuote = { _, content -> quotes += content })
    quotes.forEach { content -> result += collectQuoteEntries(content) }
    return result
}

internal fun scanStructure(
    lines: List<String>,
    startIndex: Int = 0,
    onBlank: ((Int) -> Unit)? = null,
    onQuote: ((Int, List<String>) -> Unit)? = null,
) {
    var index = startIndex
    var inFence = false
    var inMath = false
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        when {
            inFence -> {
                if (trimmed.startsWith("```")) inFence = false
                index++
            }

            inMath -> {
                if (trimmed.endsWith("$$")) inMath = false
                index++
            }

            trimmed.startsWith("```") -> {
                inFence = true
                index++
            }

            trimmed.startsWith("$$") -> {
                if (!isSingleLineMath(trimmed)) inMath = true
                index++
            }

            isTableStart(lines, index) -> {
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) index++
            }

            trimmed.startsWith(">") -> {
                val quoteStart = index
                val quote = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!current.startsWith(">")) break
                    quote += current.removePrefix(">").removePrefix(" ")
                    index++
                }
                onQuote?.invoke(quoteStart, quote)
            }

            trimmed.isEmpty() -> {
                onBlank?.invoke(index)
                index++
            }

            else -> index++
        }
    }
}

private fun isSingleLineMath(trimmed: String): Boolean {
    val rest = trimmed.removePrefix("$$")
    return rest.endsWith("$$") && rest.length >= 2
}

internal fun isChunkBoundary(lines: List<String>, index: Int): Boolean {
    var next = index + 1
    while (next < lines.size && lines[next].isBlank()) next++
    if (next >= lines.size) return true
    if (listItemPattern.matches(lines[next])) return false
    return isBlockStart(lines, next) || indentWidth(leadingWhitespace(lines[next])) == 0
}

internal fun parseBlocks(source: String, context: InlineContext): List<MarkdownBlock> =
    parseBlockLines(extractDefinitions(source.replace("\r\n", "\n").split('\n'), context), context)

internal fun parseBlockLines(lines: List<String>, context: InlineContext): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        val image = if (trimmed.startsWith("![")) imagePattern.matchEntire(trimmed) else null
        when {
            trimmed.isEmpty() -> index++

            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(language, code.joinToString("\n").trimEnd())
            }

            trimmed.startsWith("$$") -> {
                val content = mutableListOf<String>()
                val rest = trimmed.removePrefix("$$")
                if (rest.endsWith("$$") && rest.length >= 2) {
                    content += rest.removeSuffix("$$").trim()
                    index++
                } else {
                    if (rest.isNotEmpty()) content += rest
                    index++
                    while (index < lines.size) {
                        val current = lines[index].trim()
                        if (current.endsWith("$$")) {
                            val before = current.removeSuffix("$$").trimEnd()
                            if (before.isNotEmpty()) content += before
                            index++
                            break
                        }
                        content += lines[index]
                        index++
                    }
                }
                val math = content.joinToString("\n").trim()
                if (math.isNotEmpty()) blocks += MarkdownBlock.Math(math)
            }

            isTableStart(lines, index) -> {
                val header = splitRow(lines[index])
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                    rows += splitRow(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Table(header, rows)
            }

            headingPattern.matches(trimmed) -> {
                val match = headingPattern.matchEntire(trimmed)
                if (match != null) {
                    blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                }
                index++
            }

            rulePattern.matches(trimmed) -> {
                blocks += MarkdownBlock.Rule
                index++
            }

            image != null -> {
                blocks += MarkdownBlock.Image(
                    alt = image.groupValues[1],
                    source = image.groupValues[2],
                    title = image.groupValues[3].ifEmpty { null },
                )
                index++
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!current.startsWith(">")) break
                    quote += current.removePrefix(">").removePrefix(" ")
                    index++
                }
                blocks += MarkdownBlock.Quote(parseBlocks(quote.joinToString("\n"), context))
            }

            listItemPattern.matches(line) -> {
                val parsed = parseList(lines, index)
                blocks += parsed.first
                index = parsed.second
            }

            else -> {
                val paragraph = mutableListOf<String>()
                var heading: MarkdownBlock.Heading? = null
                while (index < lines.size) {
                    if (paragraph.isNotEmpty()) {
                        val level = setextLevel(lines[index])
                        if (level != null) {
                            heading = MarkdownBlock.Heading(level, paragraph.joinToString("\n"))
                            index++
                            break
                        }
                    }
                    if (isBlockStart(lines, index)) break
                    paragraph += lines[index]
                    index++
                }
                if (heading != null) {
                    blocks += heading
                } else if (paragraph.isNotEmpty()) {
                    blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
                }
            }
        }
    }
    return blocks
}

private fun extractDefinitions(lines: List<String>, context: InlineContext): List<String> {
    if (lines.none { line -> line.trimStart().startsWith("[") }) return lines
    val entries = mutableListOf<MarkdownDefinition>()
    val clean = mutableListOf<String>()
    extractDefinitionLines(lines, 0, lines.size, false, clean, entries)
    entries.forEach { definition -> applyDefinition(definition, context) }
    return clean
}

internal fun extractDefinitionLines(
    lines: List<String>,
    start: Int,
    end: Int,
    inFence: Boolean,
    clean: MutableList<String>,
    entries: MutableList<MarkdownDefinition>,
): Boolean {
    var fence = inFence
    for (index in start until end) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            fence = !fence
            clean += line
            continue
        }
        if (fence || !trimmed.startsWith("[")) {
            clean += line
            continue
        }
        val footnote = footnoteDefinitionPattern.matchEntire(trimmed)
        if (footnote != null) {
            entries += MarkdownDefinition.Footnote(footnote.groupValues[1].trim(), footnote.groupValues[2].trim())
            clean += ""
            continue
        }
        val reference = referenceDefinitionPattern.matchEntire(trimmed)
        if (reference != null) {
            val destination = reference.groupValues[2].removePrefix("<").removeSuffix(">")
            if (destination.isNotEmpty()) {
                entries += MarkdownDefinition.Link(reference.groupValues[1].trim().lowercase(), destination)
                clean += ""
                continue
            }
        }
        clean += line
    }
    return fence
}

internal fun applyDefinition(definition: MarkdownDefinition, context: InlineContext) {
    when (definition) {
        is MarkdownDefinition.Footnote -> addFootnote(definition.label, definition.text, context)
        is MarkdownDefinition.Link -> context.links.putIfAbsent(definition.label, definition.destination)
    }
}

private fun addFootnote(label: String, text: String, context: InlineContext) {
    val key = label.lowercase()
    if (context.footnoteNumbers.containsKey(key)) return
    val number = context.footnotes.size + 1
    context.footnoteNumbers[key] = number
    context.footnotes += MarkdownFootnote(number, text)
}

private fun parseList(lines: List<String>, start: Int): Pair<MarkdownBlock.ListBlock, Int> {
    val entries = mutableListOf<RawListItem>()
    var index = start
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            var next = index + 1
            while (next < lines.size && lines[next].isBlank()) next++
            if (next >= lines.size) break
            val continues = listItemPattern.matches(lines[next]) ||
                (!isBlockStart(lines, next) && indentWidth(leadingWhitespace(lines[next])) > 0)
            if (!continues) break
            index = next
            continue
        }
        val item = parseListItem(line)
        if (item != null) {
            entries += item
            index++
            continue
        }
        if (entries.isEmpty() || isBlockStart(lines, index)) break
        val last = entries.last().text
        if (last.isNotEmpty()) last.append('\n')
        last.append(line.trim())
        index++
    }
    return MarkdownBlock.ListBlock(buildListItems(entries)) to index
}

private fun parseListItem(line: String): RawListItem? {
    val task = taskItemPattern.matchEntire(line)
    if (task != null) {
        return RawListItem(
            indent = indentWidth(task.groupValues[1]),
            marker = MarkdownMarker.Task(task.groupValues[3].equals("x", ignoreCase = true)),
            text = StringBuilder(task.groupValues[4].trimEnd()),
        )
    }
    val item = listItemPattern.matchEntire(line) ?: return null
    val markerText = item.groupValues[2]
    val marker = if (markerText[0].isDigit()) {
        MarkdownMarker.Ordered(markerText.dropLast(1).toIntOrNull() ?: 1)
    } else {
        MarkdownMarker.Bullet
    }
    return RawListItem(
        indent = indentWidth(item.groupValues[1]),
        marker = marker,
        text = StringBuilder(item.groupValues[3].trimEnd()),
    )
}

private fun buildListItems(entries: List<RawListItem>): List<MarkdownListItem> {
    if (entries.isEmpty()) return emptyList()
    return buildItems(entries, 0, entries.minOf { it.indent }).first
}

private fun buildItems(
    entries: List<RawListItem>,
    start: Int,
    baseIndent: Int,
): Pair<List<MarkdownListItem>, Int> {
    val items = mutableListOf<MarkdownListItem>()
    var index = start
    var previousOrdered: Int? = null
    while (index < entries.size) {
        val entry = entries[index]
        if (entry.indent < baseIndent) break
        var next = index + 1
        var children: List<MarkdownListItem> = emptyList()
        if (next < entries.size && entries[next].indent > entry.indent) {
            val nested = buildItems(entries, next, entries[next].indent)
            children = nested.first
            next = nested.second
        }
        val marker = when (val current = entry.marker) {
            is MarkdownMarker.Ordered -> {
                val number = previousOrdered?.plus(1) ?: current.number
                previousOrdered = number
                MarkdownMarker.Ordered(number)
            }

            else -> {
                previousOrdered = null
                current
            }
        }
        items += MarkdownListItem(marker, entry.text.toString(), children)
        index = next
    }
    return items to index
}

private fun setextLevel(line: String): Int? {
    val trimmed = line.trim()
    return when {
        setextH1Pattern.matches(trimmed) -> 1
        setextH2Pattern.matches(trimmed) -> 2
        else -> null
    }
}

private fun leadingWhitespace(line: String): String = line.takeWhile { it == ' ' || it == '\t' }

private fun indentWidth(whitespace: String): Int {
    var width = 0
    whitespace.forEach { width += if (it == '\t') 4 - (width % 4) else 1 }
    return width
}

private fun isBlockStart(lines: List<String>, index: Int): Boolean {
    val trimmed = lines[index].trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.startsWith("```")) return true
    if (trimmed.startsWith("$$")) return true
    if (headingPattern.matches(trimmed)) return true
    if (rulePattern.matches(trimmed)) return true
    if (trimmed.startsWith(">")) return true
    if (listItemPattern.matches(lines[index])) return true
    if (trimmed.startsWith("![") && imagePattern.matches(trimmed)) return true
    return isTableStart(lines, index)
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    if (!lines[index].contains('|')) return false
    val cells = splitRow(lines[index + 1])
    return cells.isNotEmpty() && cells.all { tableSeparatorPattern.matches(it) }
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }
