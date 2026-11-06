package io.github.whywood.deepdeep.ui.common.chat

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
