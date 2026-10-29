package io.github.whywood.deepdeep.ui.chat

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class Image(val alt: String, val source: String, val title: String?) : MarkdownBlock
    data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data class Math(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal sealed interface MarkdownMarker {
    data object Bullet : MarkdownMarker
    data class Ordered(val number: Int) : MarkdownMarker
    data class Task(val checked: Boolean) : MarkdownMarker
}

internal data class MarkdownListItem(
    val marker: MarkdownMarker,
    val text: String,
    val children: List<MarkdownListItem> = emptyList(),
)

internal data class MarkdownFootnote(val number: Int, val text: String)

internal data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
    val links: Map<String, String>,
    val footnoteNumbers: Map<String, Int>,
    val footnotes: List<MarkdownFootnote>,
)

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

private data class RawListItem(
    val indent: Int,
    val marker: MarkdownMarker,
    val text: StringBuilder,
)

private class InlineContext {
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

internal fun parseMarkdown(source: String): List<MarkdownBlock> = parseMarkdownDocument(source).blocks

internal fun parseMarkdownDocument(source: String): MarkdownDocument {
    val context = InlineContext()
    val blocks = parseBlocks(source, context)
    return MarkdownDocument(
        blocks = blocks,
        links = context.links,
        footnoteNumbers = context.footnoteNumbers,
        footnotes = context.footnotes,
    )
}

internal data class MarkdownChunk(val text: String)

internal class MarkdownStreamPlan(
    val chunks: List<MarkdownChunk>,
    val links: Map<String, String>,
    val footnoteNumbers: Map<String, Int>,
    val footnotes: List<MarkdownFootnote>,
)

private sealed interface MarkdownDefinition {
    data class Footnote(val label: String, val text: String) : MarkdownDefinition

    data class Link(val label: String, val destination: String) : MarkdownDefinition
}

private class MarkdownQuoteGroup(
    val startLine: Int,
    val entries: List<MarkdownDefinition>,
)

internal class MarkdownStreamPlanner {
    private var source: String? = null
    private var plan: MarkdownStreamPlan? = null
    private val lines = mutableListOf<String>()
    private val cleanLines = mutableListOf<String>()
    private val definitions = mutableListOf<MarkdownDefinition>()
    private val quoteGroups = mutableListOf<MarkdownQuoteGroup>()
    private val chunks = mutableListOf<MarkdownChunk>()
    private val chunkStarts = mutableListOf<Int>()
    private var stableLineCount = 0
    private var stableFence = false
    private var stableEntryCount = 0

    fun plan(newSource: String): MarkdownStreamPlan {
        val previous = source
        val cached = plan
        if (previous == newSource && cached != null) return cached
        if (previous != null && newSource.length > previous.length && newSource.startsWith(previous)) {
            append(previous, newSource)
        } else {
            rebuild(newSource)
        }
        source = newSource
        return buildPlan().also { built -> plan = built }
    }

    private fun rebuild(newSource: String) {
        lines.clear()
        lines += newSource.replace("\r\n", "\n").split('\n')
        cleanLines.clear()
        definitions.clear()
        quoteGroups.clear()
        chunks.clear()
        chunkStarts.clear()
        stableLineCount = 0
        stableFence = false
        stableEntryCount = 0
        updateExtraction()
        scanTail(0)
    }

    private fun append(previous: String, newSource: String) {
        val appendedRaw = newSource.substring(previous.length)
        val appended = appendedRaw.replace("\r\n", "\n")
        val carryCarriageReturn = lines.isNotEmpty() && lines.last().endsWith("\r") && appendedRaw.startsWith("\n")
        val last = if (lines.isEmpty()) "" else lines.removeAt(lines.lastIndex)
        if (carryCarriageReturn) {
            lines += last.dropLast(1)
            lines += appended.drop(1).split('\n')
        } else {
            val pieces = appended.split('\n')
            lines += last + pieces[0]
            lines += pieces.drop(1)
        }
        updateExtraction()
        scanTail(chunkScanStart())
    }

    private fun updateExtraction() {
        cleanLines.subList(stableLineCount, cleanLines.size).clear()
        definitions.subList(stableEntryCount, definitions.size).clear()
        var fence = stableFence
        for (index in stableLineCount until lines.size) {
            if (index == lines.size - 1) {
                stableEntryCount = definitions.size
                stableFence = fence
            }
            fence = extractDefinitionLines(lines, index, index + 1, fence, cleanLines, definitions)
        }
        stableLineCount = lines.size - 1
    }

    private fun chunkScanStart(): Int = if (chunks.size < 3) 0 else chunkStarts[chunks.size - 2]

    private fun scanTail(startIndex: Int) {
        if (startIndex == 0) {
            chunks.clear()
            chunkStarts.clear()
            quoteGroups.clear()
        } else {
            chunks.subList(chunks.size - 2, chunks.size).clear()
            chunkStarts.subList(chunkStarts.size - 2, chunkStarts.size).clear()
            quoteGroups.removeAll { group -> group.startLine >= startIndex }
        }
        var chunkStart = startIndex
        scanStructure(
            lines = cleanLines,
            startIndex = startIndex,
            onBlank = { index ->
                if (isChunkBoundary(cleanLines, index)) {
                    addChunk(chunkStart, index)
                    chunkStart = index + 1
                }
            },
            onQuote = { quoteStart, content ->
                quoteGroups += MarkdownQuoteGroup(quoteStart, collectQuoteEntries(content))
            },
        )
        addChunk(chunkStart, cleanLines.size)
    }

    private fun addChunk(start: Int, end: Int) {
        if (start >= end) return
        val text = cleanLines.subList(start, end).joinToString("\n")
        if (text.isNotBlank()) {
            chunks += MarkdownChunk(text)
            chunkStarts += start
        }
    }

    private fun buildPlan(): MarkdownStreamPlan {
        val context = InlineContext()
        definitions.forEach { definition -> applyDefinition(definition, context) }
        quoteGroups.forEach { group -> group.entries.forEach { entry -> applyDefinition(entry, context) } }
        return MarkdownStreamPlan(
            chunks = chunks.toList(),
            links = context.links,
            footnoteNumbers = context.footnoteNumbers,
            footnotes = context.footnotes,
        )
    }
}

internal fun planMarkdownStream(source: String): MarkdownStreamPlan = MarkdownStreamPlanner().plan(source)

internal fun parseMarkdownChunk(text: String): List<MarkdownBlock> =
    parseBlockLines(text.split('\n'), InlineContext())

internal fun parseMarkdownChunked(source: String): MarkdownDocument {
    val plan = planMarkdownStream(source)
    return MarkdownDocument(
        blocks = plan.chunks.flatMap { parseMarkdownChunk(it.text) },
        links = plan.links,
        footnoteNumbers = plan.footnoteNumbers,
        footnotes = plan.footnotes,
    )
}

private fun collectQuoteEntries(lines: List<String>): List<MarkdownDefinition> {
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

private fun scanStructure(
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

private fun isChunkBoundary(lines: List<String>, index: Int): Boolean {
    var next = index + 1
    while (next < lines.size && lines[next].isBlank()) next++
    if (next >= lines.size) return true
    if (listItemPattern.matches(lines[next])) return false
    return isBlockStart(lines, next) || indentWidth(leadingWhitespace(lines[next])) == 0
}

private fun parseBlocks(source: String, context: InlineContext): List<MarkdownBlock> =
    parseBlockLines(extractDefinitions(source.replace("\r\n", "\n").split('\n'), context), context)

private fun parseBlockLines(lines: List<String>, context: InlineContext): List<MarkdownBlock> {
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

private fun extractDefinitionLines(
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

private fun applyDefinition(definition: MarkdownDefinition, context: InlineContext) {
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

internal fun markdownToPlainText(text: String): String = markdownToPlainText(parseMarkdownDocument(text))

internal fun markdownToPlainText(document: MarkdownDocument): String {
    val parts = document.blocks.map { blockPlainText(it, document) }.filter { it.isNotBlank() }.toMutableList()
    if (document.footnotes.isNotEmpty()) {
        parts += document.footnotes.joinToString("\n") {
            "${it.number}. ${inlinePlainText(it.text, document.links, document.footnoteNumbers)}"
        }
    }
    return parts.joinToString("\n\n")
}

private fun blockPlainText(block: MarkdownBlock, document: MarkdownDocument): String = when (block) {
    is MarkdownBlock.Heading -> plainInline(block.text, document)
    is MarkdownBlock.Paragraph -> plainInline(block.text, document)
    is MarkdownBlock.Code -> block.code
    is MarkdownBlock.Quote -> flattenBlocks(block.blocks, document)
    is MarkdownBlock.Image -> block.alt.ifBlank { block.source }
    is MarkdownBlock.ListBlock -> flattenItems(block.items, 0, document)
    is MarkdownBlock.Table -> (listOf(block.header) + block.rows)
        .joinToString("\n") { row -> row.joinToString("，") { plainInline(it, document) } }
    is MarkdownBlock.Math -> block.text
    MarkdownBlock.Rule -> ""
}

private fun flattenBlocks(blocks: List<MarkdownBlock>, document: MarkdownDocument): String =
    blocks.map { blockPlainText(it, document) }.filter { it.isNotBlank() }.joinToString("\n\n")

private fun flattenItems(items: List<MarkdownListItem>, depth: Int, document: MarkdownDocument): String =
    items.joinToString("\n") { item ->
        val marker = when (val current = item.marker) {
            MarkdownMarker.Bullet -> if (depth == 0) "•" else "◦"
            is MarkdownMarker.Ordered -> "${current.number}."
            is MarkdownMarker.Task -> if (current.checked) "☑" else "☐"
        }
        val line = "${"  ".repeat(depth)}$marker ${plainInline(item.text, document)}"
        if (item.children.isEmpty()) {
            line
        } else {
            line + "\n" + flattenItems(item.children, depth + 1, document)
        }
    }

private fun plainInline(text: String, document: MarkdownDocument): String =
    inlinePlainText(text, document.links, document.footnoteNumbers)

internal fun inlineNodes(
    text: String,
    links: Map<String, String> = emptyMap(),
    footnotes: Map<String, Int> = emptyMap(),
): List<InlineNode> = InlineScanner(links, footnotes).parse(text)

internal fun inlinePlainText(
    text: String,
    links: Map<String, String> = emptyMap(),
    footnotes: Map<String, Int> = emptyMap(),
): String = inlineNodes(text, links, footnotes).joinToString("") { inlinePlainNode(it) }

private fun inlinePlainNode(node: InlineNode): String = when (node) {
    is InlineNode.Text -> node.text
    is InlineNode.Code -> node.text
    is InlineNode.Kbd -> node.text
    is InlineNode.FileRef -> node.text
    is InlineNode.Math -> node.text
    is InlineNode.FootnoteRef -> "[${node.number}]"
    is InlineNode.Bold -> inlinePlainChildren(node.children)
    is InlineNode.Italic -> inlinePlainChildren(node.children)
    is InlineNode.Strike -> inlinePlainChildren(node.children)
    is InlineNode.Link -> inlinePlainChildren(node.children)
    is InlineNode.Colored -> inlinePlainChildren(node.children)
    is InlineNode.Superscript -> inlinePlainChildren(node.children)
    is InlineNode.Subscript -> inlinePlainChildren(node.children)
}

private fun inlinePlainChildren(children: List<InlineNode>): String =
    children.joinToString("") { inlinePlainNode(it) }

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
