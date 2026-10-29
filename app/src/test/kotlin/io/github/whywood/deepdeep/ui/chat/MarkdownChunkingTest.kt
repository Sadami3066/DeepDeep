package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChunkingTest {
    @Test
    fun chunkedParseMatchesWholeDocumentForCorpus() {
        corpus().forEach(::assertEquivalent)
    }

    @Test
    fun chunkedParseMatchesWholeDocumentForEveryStreamingPrefix() {
        val source = streamingDocument()
        for (length in 0..source.length) {
            assertEquivalent(source.substring(0, length))
        }
    }

    @Test
    fun chunksAheadOfStreamingTailStayStable() {
        val source = streamingDocument()
        var previous: MarkdownStreamPlan? = null
        for (length in 1..source.length) {
            val plan = planMarkdownStream(source.substring(0, length))
            val before = previous
            if (before != null) {
                val stable = before.chunks.dropLast(2)
                assertEquals("prefix $length", stable, plan.chunks.take(stable.size))
            }
            previous = plan
        }
    }

    @Test
    fun keepsFenceAndListBlocksWholeAcrossBlankLines() {
        val fence = planMarkdownStream("```\na\n\nb\n```\n\ntext")
        assertEquals(2, fence.chunks.size)
        val code = parseMarkdownChunk(fence.chunks[0].text).filterIsInstance<MarkdownBlock.Code>().single()
        assertEquals("a\n\nb", code.code)
        val list = planMarkdownStream("- one\n\n- two\n\ntext")
        assertEquals(2, list.chunks.size)
        val items = parseMarkdownChunk(list.chunks[0].text).filterIsInstance<MarkdownBlock.ListBlock>().single()
        assertEquals(listOf("one", "two"), items.items.map { it.text })
    }

    @Test
    fun shortensAndReplacesFencesWithoutStaleChunks() {
        val longer = planMarkdownStream("alpha\n\nbeta\n\n```kt\nval x = 1\n```")
        val shorter = planMarkdownStream("alpha")
        assertEquals(listOf("alpha"), shorter.chunks.map { it.text })
        assertTrue(longer.chunks.size > shorter.chunks.size)
        assertEquivalent("alpha")
        val replaced = planMarkdownStream("```kt\nval y = 2\n```")
        assertEquals(1, replaced.chunks.size)
    }

    private fun assertEquivalent(source: String) {
        val whole = parseMarkdownDocument(source)
        val chunked = parseMarkdownChunked(source)
        assertEquals("blocks for $source", whole.blocks, chunked.blocks)
        assertEquals("links for $source", whole.links, chunked.links)
        assertEquals("footnote numbers for $source", whole.footnoteNumbers, chunked.footnoteNumbers)
        assertEquals("footnotes for $source", whole.footnotes, chunked.footnotes)
        assertEquals("plain text for $source", markdownToPlainText(whole), markdownToPlainText(chunked))
    }

    private fun corpus(): List<String> = listOf(
        "",
        "\n",
        "   \n\n\t\n",
        "plain paragraph",
        "one line\nsecond line",
        "# Heading\n\n## Second\n\n### Third",
        "Title One\n===\n\nSubtitle\n---",
        "text\n\n---",
        "---",
        "**bold** and *italic* and ~~strike~~ and `code`",
        "[notes](attachments/75d6_notes.md) and [site](https://example.com)",
        "see attachments/75d6_notes.md and workspace/x.png now",
        "<b>bold</b> <kbd>Ctrl</kbd> <span style=\"color:#c0392b\">red</span>",
        "energy \$E=mc^2\$ done\n\n\$\$\nE=mc^2\n\$\$",
        "```kotlin\nval answer = 42\n```",
        "```\nlabel: https://example.com\n\nblank line above\n```\n\nafter",
        "```js\nconst x = 1\n```\n\n- item\n\n1. one\n2. two",
        "before\n```\ncode\n\n\nmore code\n```\nafter",
        "- one\n  - one a\n  - one b\n- two",
        "- first\n  continued\n- next",
        "- one\n\n- two\n\n- three",
        "- first\n\n  continued after blank\n\n- next",
        "1. setup\n   - [x] install\n   - [ ] configure\n2. run\n   3. nested",
        "3. third\n4. fourth",
        "- \n  deeper\n- \n\t- tabbed",
        "| Name | Value |\n| --- | --- |\n| a | 1 |\n| b | 2 |\n\nafter table",
        "| Name | Value |\n| --- | --- |\n> [^x]: swallowed | row\n",
        "> intro\n> - item a\n> - item b\n> > deeper\n> ```js\n> const x = 1\n> ```",
        "> outer\n> > deepest",
        "> see [foo] and [^n]\n> [foo]: https://example.com/quote\n> [^n]: quoted note",
        "> [^q]: quote note\n\n[^top]: top note\n\nAlpha[^top] beta[^q]",
        "[^top]: top note\n\n> [^q]: quote note\n\nAlpha[^top] beta[^q]",
        "Alpha[^a] beta[^B].\n\n[^a]: First note\n[^b]: Second *note*",
        "Text [full][Label] and [collapsed][] and [shortcut].\n\n[LABEL]: https://example.com/full \"Title\"\n[collapsed]: https://example.com/collapsed\n[shortcut]: https://example.com/shortcut",
        "[foo]: https://example.com\n\ntext before\n\nmore [foo]",
        "text before\n\n[foo]: https://example.com\n\nmore [foo]",
        "answer\n\n[^n]: note after\n\nrefer [^n]",
        "$$\n```x\n$$\n\n[foo]: https://example.com\n\nsee [foo]",
        "para\n\n$$\nline\n\nmore\n$$\n\nafter",
        "para\n\n" + "$" + "$" + "E=mc^2" + "$" + "$" + "\n\nafter",
        "unclosed fence\n```\ncode\n\nstill code",
        "unclosed math\n$$\nmath\n\nstill math",
        "text\n\n![" + "chart](/tmp/chart.png)\n\nafter",
        "a\n|b\n| --- |\nafter",
        "   indented paragraph\n\nsecond",
        "line\r\n\r\nsecond\r\n",
        "\n\nleading blanks\n\ntrailing blanks\n\n\n",
        longDocument(),
    )

    private fun streamingDocument(): String = buildString {
        append("Intro **bold** and [file](attachments/a.md).\n\n")
        append("## Section\n\n")
        append("- one\n\n- two\n  - nested\n\n")
        append("| k | v |\n| --- | --- |\n| a | b |\n\n")
        append("> quote intro\n> [ref]: https://example.com/q\n> [^qf]: quoted note\n\n")
        append("```kotlin\nfun run(): Int {\n\n    return 1\n}\n```\n\n")
        append("Alpha[^top] beta[^qf] and [ref].\n\n")
        append("[^top]: top note\n\n")
        append("$" + "$" + "E=mc^2" + "$" + "$" + "\n\n")
        append("Tail paragraph that keeps growing")
    }

    private fun longDocument(): String = buildString {
        repeat(40) { index ->
            append("## Section ").append(index).append("\n\n")
            append("Streaming paragraph with **bold**, `inline code` and a [link](https://example.com/")
                .append(index)
                .append(").\n\n")
            append("- first ").append(index).append("\n")
            append("  - nested\n")
            append("- [x] done\n\n")
            append("| key | value |\n| --- | --- |\n| a | ").append(index).append(" |\n\n")
            append("```kotlin\nval value = ").append(index).append(" // note\n```\n\n")
        }
    }
}
