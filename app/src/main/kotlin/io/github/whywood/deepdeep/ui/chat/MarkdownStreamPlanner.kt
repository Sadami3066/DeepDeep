package io.github.whywood.deepdeep.ui.chat

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
