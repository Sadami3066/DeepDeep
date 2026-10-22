package io.github.whywood.deepdeep.ui.chat

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val code: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val bulletPattern = Regex("^\\s*[-*+]\\s+(.+)$")
private val orderedPattern = Regex("^\\s*\\d+[.)]\\s+(.+)$")
private val rulePattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val separatorCellPattern = Regex("^:?-{3,}:?$")

internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        when {
            trimmed.isEmpty() -> index++

            trimmed.startsWith("```") -> {
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(code.joinToString("\n"))
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
                val match = headingPattern.matchEntire(trimmed) ?: error("heading")
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                index++
            }

            rulePattern.matches(trimmed) -> {
                blocks += MarkdownBlock.Rule
                index++
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote += lines[index].trim().removePrefix(">").trimStart()
                    index++
                }
                blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            }

            bulletPattern.matches(lines[index]) -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = bulletPattern.matchEntire(lines[index]) ?: break
                    items += match.groupValues[1]
                    index++
                }
                blocks += MarkdownBlock.Bullets(items, ordered = false)
            }

            orderedPattern.matches(lines[index]) -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = orderedPattern.matchEntire(lines[index]) ?: break
                    items += match.groupValues[1]
                    index++
                }
                blocks += MarkdownBlock.Bullets(items, ordered = true)
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size && !isBlockStart(lines, index)) {
                    paragraph += lines[index]
                    index++
                }
                if (paragraph.isNotEmpty()) {
                    blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
                }
            }
        }
    }
    return blocks
}

private fun isBlockStart(lines: List<String>, index: Int): Boolean {
    val trimmed = lines[index].trim()
    return trimmed.isEmpty() ||
        trimmed.startsWith("```") ||
        headingPattern.matches(trimmed) ||
        rulePattern.matches(trimmed) ||
        trimmed.startsWith(">") ||
        bulletPattern.matches(lines[index]) ||
        orderedPattern.matches(lines[index]) ||
        isTableStart(lines, index)
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    if (!lines[index].contains('|')) return false
    val cells = splitRow(lines[index + 1])
    return cells.isNotEmpty() && cells.all { separatorCellPattern.matches(it) }
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

internal fun markdownToPlainText(text: String): String =
    parseMarkdown(text)
        .map { block ->
            when (block) {
                is MarkdownBlock.Heading -> stripInline(block.text)
                is MarkdownBlock.Paragraph -> stripInline(block.text)
                is MarkdownBlock.Code -> block.code
                is MarkdownBlock.Quote -> stripInline(block.text)
                is MarkdownBlock.Bullets -> block.items.joinToString("\n") { "• ${stripInline(it)}" }
                is MarkdownBlock.Table -> (listOf(block.header) + block.rows)
                    .joinToString("\n") { row -> row.joinToString("，") { stripInline(it) } }
                MarkdownBlock.Rule -> ""
            }
        }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

private fun stripInline(text: String): String = text
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("`([^`]*)`"), "$1")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("~~(.+?)~~"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
