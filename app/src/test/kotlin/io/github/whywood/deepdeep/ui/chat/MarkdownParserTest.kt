package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parsesFenceLanguageAndCode() {
        val blocks = parseMarkdown("```kotlin\nval answer = 42\n```")
        val code = blocks.single() as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.code)
    }

    @Test
    fun parsesNestedBulletList() {
        val items = listBlock("- one\n  - one a\n  - one b\n- two").items
        assertEquals(2, items.size)
        assertEquals("one", items[0].text)
        assertEquals(listOf("one a", "one b"), items[0].children.map { it.text })
        assertEquals("two", items[1].text)
        assertTrue(items[0].marker is MarkdownMarker.Bullet)
    }

    @Test
    fun parsesTabIndentedChild() {
        val items = listBlock("- parent\n\t- child\n\t\t- grandchild").items
        assertEquals("child", items[0].children.single().text)
        assertEquals("grandchild", items[0].children.single().children.single().text)
    }

    @Test
    fun parsesMixedOrderedAndTaskChildren() {
        val items = listBlock("1. setup\n   - [x] install\n   - [ ] configure\n2. run\n   3. nested").items
        assertEquals(2, items.size)
        assertEquals(MarkdownMarker.Ordered(1), items[0].marker)
        assertEquals(MarkdownMarker.Task(true), items[0].children[0].marker)
        assertEquals(MarkdownMarker.Task(false), items[0].children[1].marker)
        assertEquals(MarkdownMarker.Ordered(2), items[1].marker)
        assertEquals(MarkdownMarker.Ordered(3), items[1].children.single().marker)
    }

    @Test
    fun keepsOrderedStartNumber() {
        val items = listBlock("3. third\n4. fourth").items
        assertEquals(MarkdownMarker.Ordered(3), items[0].marker)
        assertEquals(MarkdownMarker.Ordered(4), items[1].marker)
    }

    @Test
    fun continuesItemTextAcrossLines() {
        val items = listBlock("- first\n  continued\n- next").items
        assertEquals("first\ncontinued", items[0].text)
        assertEquals("next", items[1].text)
    }

    @Test
    fun parsesImageBlock() {
        val image = parseMarkdown("![A cat](https://example.com/cat.png \"Cat\")").single() as MarkdownBlock.Image
        assertEquals("A cat", image.alt)
        assertEquals("https://example.com/cat.png", image.source)
        assertEquals("Cat", image.title)
    }

    @Test
    fun parsesLocalImageWithoutTitle() {
        val path = parseMarkdown("![chart](/tmp/chart.png)").single() as MarkdownBlock.Image
        assertEquals("/tmp/chart.png", path.source)
        assertNull(path.title)
        val uri = parseMarkdown("![photo](file:///sdcard/photo.jpg)").single() as MarkdownBlock.Image
        assertEquals("file:///sdcard/photo.jpg", uri.source)
    }

    @Test
    fun parsesTable() {
        val table = parseMarkdown("| Name | Value |\n| --- | --- |\n| a | 1 |\n| b | 2 |")
            .single() as MarkdownBlock.Table
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("b", "2"), table.rows[1])
    }

    @Test
    fun plainTextFlattensNestedLists() {
        val plain = markdownToPlainText("- a\n  - b\n- [x] done")
        assertTrue(plain.contains("• a"))
        assertTrue(plain.contains("  ◦ b"))
        assertTrue(plain.contains("☑ done"))
    }

    @Test
    fun plainTextKeepsCodeTasksAndImageAlt() {
        val plain = markdownToPlainText("```sh\necho hi\n```\n\n- [x] done\n\n![chart](/tmp/chart.png)")
        assertTrue(plain.contains("echo hi"))
        assertTrue(plain.contains("☑ done"))
        assertTrue(plain.contains("chart"))
    }

    @Test
    fun parsesNestedBlockquoteContent() {
        val source = listOf(
            "> intro",
            "> - item a",
            "> - item b",
            "> > deeper",
            "> ```js",
            "> const x = 1",
            "> ```",
        ).joinToString("\n")
        val quote = parseMarkdown(source).single() as MarkdownBlock.Quote
        assertEquals(4, quote.blocks.size)
        assertEquals(MarkdownBlock.Paragraph("intro"), quote.blocks[0])
        val list = quote.blocks[1] as MarkdownBlock.ListBlock
        assertEquals(listOf("item a", "item b"), list.items.map { it.text })
        val nested = quote.blocks[2] as MarkdownBlock.Quote
        assertEquals(MarkdownBlock.Paragraph("deeper"), nested.blocks.single())
        val code = quote.blocks[3] as MarkdownBlock.Code
        assertEquals("js", code.language)
        assertEquals("const x = 1", code.code)
    }

    @Test
    fun parsesNestedQuoteWithSpaceMarker() {
        val quote = parseMarkdown("> outer\n> > deepest").single() as MarkdownBlock.Quote
        assertEquals(MarkdownBlock.Paragraph("outer"), quote.blocks[0])
        val nested = quote.blocks[1] as MarkdownBlock.Quote
        assertEquals(MarkdownBlock.Paragraph("deepest"), nested.blocks.single())
    }

    @Test
    fun parsesSetextHeadings() {
        val blocks = parseMarkdown("Title One\n===\n\nSubtitle\n---")
        assertEquals(MarkdownBlock.Heading(1, "Title One"), blocks[0])
        assertEquals(MarkdownBlock.Heading(2, "Subtitle"), blocks[1])
    }

    @Test
    fun ruleIsNotSetextWithoutPrecedingParagraph() {
        assertEquals(MarkdownBlock.Rule, parseMarkdown("---").single())
        val blocks = parseMarkdown("text\n\n---")
        assertEquals(MarkdownBlock.Paragraph("text"), blocks[0])
        assertEquals(MarkdownBlock.Rule, blocks[1])
    }

    @Test
    fun resolvesBackslashEscapes() {
        assertEquals("*literal* \\ \\q", inlinePlainText("\\*literal\\* \\\\ \\q"))
        assertTrue(inlineNodes("\\*literal\\*").none { it is InlineNode.Italic })
        assertEquals("line\nnext", inlinePlainText("line\\\nnext"))
    }

    @Test
    fun parsesAutolinks() {
        val bare = inlineNodes("see https://example.com/a?b=1 now").filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com/a?b=1", bare.destination)
        val angle = inlineNodes("<https://example.com>").filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com", angle.destination)
        val email = inlineNodes("<user@example.com>").filterIsInstance<InlineNode.Link>().single()
        assertEquals("mailto:user@example.com", email.destination)
        assertEquals("user@example.com", inlinePlainText("<user@example.com>"))
        assertEquals("see https://example.com now", inlinePlainText("see https://example.com now"))
    }

    @Test
    fun parsesReferenceLinks() {
        val source = """
            |Text [full][Label] and [collapsed][] and [shortcut].
            |
            |[LABEL]: https://example.com/full "Title"
            |[collapsed]: https://example.com/collapsed
            |[shortcut]: https://example.com/shortcut
        """.trimMargin()
        val document = parseMarkdownDocument(source)
        assertEquals(1, document.blocks.size)
        val paragraph = document.blocks.single() as MarkdownBlock.Paragraph
        val links = inlineNodes(paragraph.text, document.links).filterIsInstance<InlineNode.Link>()
        assertEquals(
            listOf(
                "https://example.com/full",
                "https://example.com/collapsed",
                "https://example.com/shortcut",
            ),
            links.map { it.destination },
        )
        assertEquals("[full][missing] [missing]", inlinePlainText("[full][missing] [missing]"))
    }

    @Test
    fun keepsDefinitionLinesInsideCodeFence() {
        val code = parseMarkdown("```\n[label]: https://example.com\n```").single() as MarkdownBlock.Code
        assertEquals("[label]: https://example.com", code.code)
    }

    @Test
    fun parsesInlineHtmlSubset() {
        assertEquals(2, inlineNodes("<b>bold</b> and <strong>strong</strong>").filterIsInstance<InlineNode.Bold>().size)
        assertEquals(2, inlineNodes("<i>i</i><em>em</em>").filterIsInstance<InlineNode.Italic>().size)
        assertEquals(InlineNode.Code("a + b"), inlineNodes("<code>a + b</code>").single())
        assertEquals(InlineNode.Kbd("Ctrl"), inlineNodes("<kbd>Ctrl</kbd>").single())
        assertTrue(inlineNodes("<sup>2</sup>").single() is InlineNode.Superscript)
        assertTrue(inlineNodes("<sub>x</sub>").single() is InlineNode.Subscript)
        assertEquals("a\nb", inlinePlainText("a<br>b"))
        assertEquals(
            InlineNode.Colored(0xFFC0392B.toInt(), listOf(InlineNode.Text("red"))),
            inlineNodes("<span style=\"color:#c0392b\">red</span>").single(),
        )
        assertEquals(
            InlineNode.Link(listOf(InlineNode.Text("site")), "https://example.com"),
            inlineNodes("<a href=\"https://example.com\">site</a>").single(),
        )
        assertEquals("keep bold", inlinePlainText("<div class=\"x\">keep <b>bold</b></div>"))
        assertEquals("bold and rest", inlinePlainText("<b>bold and rest"))
        assertEquals("plain", inlinePlainText("<span style=\"font-weight:bold\">plain</span>"))
    }

    @Test
    fun parsesInlineAndDisplayMath() {
        val nodes = inlineNodes("energy \$E=mc^2\$ done")
        assertEquals("E=mc^2", nodes.filterIsInstance<InlineNode.Math>().single().text)
        assertEquals("energy E=mc^2 done", inlinePlainText("energy \$E=mc^2\$ done"))
        assertEquals("\$ x \$", inlinePlainText("\$ x \$"))
        val display = parseMarkdown("\$\$\nE=mc^2\n\$\$").single() as MarkdownBlock.Math
        assertEquals("E=mc^2", display.text)
        val oneLine = parseMarkdown("\$\$E=mc^2\$\$").single() as MarkdownBlock.Math
        assertEquals("E=mc^2", oneLine.text)
    }

    @Test
    fun parsesFootnotes() {
        val source = "Alpha[^a] beta[^B].\n\n[^a]: First note\n[^b]: Second *note*"
        val document = parseMarkdownDocument(source)
        assertEquals(1, document.blocks.size)
        assertEquals(listOf(1, 2), document.footnotes.map { it.number })
        assertEquals("First note", document.footnotes[0].text)
        assertEquals(mapOf("a" to 1, "b" to 2), document.footnoteNumbers)
        val paragraph = document.blocks.single() as MarkdownBlock.Paragraph
        val refs = inlineNodes(paragraph.text, footnotes = document.footnoteNumbers)
            .filterIsInstance<InlineNode.FootnoteRef>()
        assertEquals(listOf(1, 2), refs.map { it.number })
        val plain = markdownToPlainText(source)
        assertTrue(plain.contains("Alpha[1] beta[2]."))
        assertTrue(plain.contains("1. First note"))
        assertTrue(plain.contains("2. Second note"))
        assertFalse(plain.contains("[^"))
        assertFalse(plain.contains("https://"))
    }

    @Test
    fun plainTextFlattensNestedQuotes() {
        val plain = markdownToPlainText("> outer\n> - one\n> > inner\n> ```sh\n> echo hi\n> ```")
        assertTrue(plain.contains("outer"))
        assertTrue(plain.contains("• one"))
        assertTrue(plain.contains("inner"))
        assertTrue(plain.contains("echo hi"))
    }

    @Test
    fun malformedListsDoNotCrash() {
        val blocks = parseMarkdown("- \n  deeper\n- \n\t- tabbed")
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun parsesTenKilobytesQuickly() {
        val source = largeMarkdown()
        assertTrue("source was ${source.length} chars", source.length >= 10_000)
        parseMarkdown(source)
        var best = Long.MAX_VALUE
        repeat(3) {
            val start = System.nanoTime()
            parseMarkdown(source)
            best = minOf(best, System.nanoTime() - start)
        }
        val millis = best / 1_000_000
        assertTrue("parse took ${millis}ms for ${source.length} chars", millis < 50)
    }

    @Test
    fun highlightsKotlinTokens() {
        val code = "val answer = \"forty two\" // note\nfun run(argument: Int) = 42"
        val tokens = highlightCode(code, "kt")
        assertTrue(tokens.any { it.type == CodeTokenType.KEYWORD && textOf(code, it) == "val" })
        assertTrue(tokens.any { it.type == CodeTokenType.STRING && textOf(code, it) == "\"forty two\"" })
        assertTrue(tokens.any { it.type == CodeTokenType.COMMENT && textOf(code, it) == "// note" })
        assertTrue(tokens.any { it.type == CodeTokenType.NUMBER && textOf(code, it) == "42" })
        assertTrue(tokens.any { it.type == CodeTokenType.FUNCTION && textOf(code, it) == "run" })
        assertTrue(tokens.any { it.type == CodeTokenType.TYPE && textOf(code, it) == "Int" })
    }

    @Test
    fun unknownLanguageStaysPlain() {
        assertTrue(highlightCode("plain prose here", "unknown").isEmpty())
        assertTrue(highlightCode("plain prose here", null).isEmpty())
    }

    @Test
    fun unterminatedStringsAndCommentsTerminate() {
        val string = highlightCode("val s = \"\"\"never closed\nstill going", "kotlin")
        assertTrue(string.any { it.type == CodeTokenType.STRING })
        val comment = highlightCode("/* never closed\nval x = 1", "kotlin")
        assertTrue(comment.any { it.type == CodeTokenType.COMMENT })
    }

    @Test
    fun highlightsDiffLines() {
        val code = "@@ -1 +1 @@\n-old\n+new\n unchanged"
        val tokens = highlightCode(code, "diff")
        assertTrue(tokens.any { it.type == CodeTokenType.KEYWORD && textOf(code, it) == "@@ -1 +1 @@" })
        assertTrue(tokens.any { it.type == CodeTokenType.COMMENT && textOf(code, it) == "-old" })
        assertTrue(tokens.any { it.type == CodeTokenType.STRING && textOf(code, it) == "+new" })
    }

    @Test
    fun detectsWorkspaceFileReferences() {
        assertTrue(isWorkspaceFileReference("attachments/75d6_notes.md"))
        assertTrue(isWorkspaceFileReference("workspace/x.png"))
        assertTrue(isWorkspaceFileReference("/data/user/0/io.github.whywood.deepdeep/files/workspace/x.png"))
        assertTrue(isWorkspaceFileReference("/data/user/0/io.github.whywood.deepdeep/files/home/notes.md"))
        assertTrue(isWorkspaceFileReference("/data/user/0/io.github.whywood.deepdeep/files/workspaces/abc123/notes.md"))
        assertTrue(isWorkspaceFileReference("files/home/notes.md"))
        assertTrue(isWorkspaceFileReference("file:///data/user/0/io.github.whywood.deepdeep/files/workspace/x.png"))
        assertTrue(isWorkspaceFileReference("workspace/report.PDF"))
        assertFalse(isWorkspaceFileReference("workspace/folder"))
        assertFalse(isWorkspaceFileReference("notes.md"))
        assertFalse(isWorkspaceFileReference("http://example.com/files/workspace/x.md"))
        assertFalse(isWorkspaceFileReference("tmp/files/workspace/x.md"))
        assertFalse(isWorkspaceFileReference("attachments/script.unknownext"))
        assertFalse(isWorkspaceFileReference("see attachments notes"))
    }

    @Test
    fun inlineNodesDetectFileReferences() {
        val source =
            "see attachments/75d6_notes.md and /data/user/0/io.github.whywood.deepdeep/files/workspace/x.png now"
        val refs = inlineNodes(source).filterIsInstance<InlineNode.FileRef>()
        assertEquals(
            listOf(
                "attachments/75d6_notes.md",
                "/data/user/0/io.github.whywood.deepdeep/files/workspace/x.png",
            ),
            refs.map { it.path },
        )
        assertTrue(refs.none { it.code })
        assertEquals(source, inlinePlainText(source))
        assertEquals(emptyList<InlineNode.FileRef>(), inlineNodes("attachments folder and workspace stuff").filterIsInstance<InlineNode.FileRef>())
        assertEquals("open attachments/x.md.", inlinePlainText("open attachments/x.md."))
        val uri = inlineNodes("file:///data/user/0/io.github.whywood.deepdeep/files/workspace/x.png").single()
        assertEquals(
            InlineNode.FileRef(
                "file:///data/user/0/io.github.whywood.deepdeep/files/workspace/x.png",
                "file:///data/user/0/io.github.whywood.deepdeep/files/workspace/x.png",
            ),
            uri,
        )
    }

    @Test
    fun inlineCodeFileReferencesKeepCodeStyleNode() {
        assertEquals(
            InlineNode.FileRef("attachments/x.md", "attachments/x.md", code = true),
            inlineNodes("`attachments/x.md`").single(),
        )
        assertEquals(InlineNode.Code("val x = 1"), inlineNodes("`val x = 1`").single())
    }

    @Test
    fun markdownLinksRouteWorkspaceFiles() {
        val source = "[notes](attachments/75d6_notes.md) and [site](https://example.com)"
        val links = inlineNodes(source).filterIsInstance<InlineNode.Link>()
        assertEquals(
            listOf("attachments/75d6_notes.md", "https://example.com"),
            links.map { it.destination },
        )
        assertTrue(isWorkspaceFileReference(links[0].destination))
        assertFalse(isWorkspaceFileReference(links[1].destination))
        assertEquals("notes and site", inlinePlainText(source))
    }

    @Test
    fun parsesCommonMarkCodeSpans() {
        assertEquals(InlineNode.Code("a ` b"), inlineNodes("``a ` b``").single())
        assertEquals(InlineNode.Code("code"), inlineNodes("` code `").single())
        assertEquals(InlineNode.Code("a b"), inlineNodes("`a\nb`").single())
        assertEquals(InlineNode.Code("a b"), inlineNodes("`a\tb`").single())
        assertEquals(InlineNode.Code("  "), inlineNodes("`  `").single())
        assertEquals("``a`", inlinePlainText("``a`"))
    }

    @Test
    fun classifiesPathLikeInlineCode() {
        assertTrue(isPathLikeInlineCode("src/main/Main.kt"))
        assertTrue(isPathLikeInlineCode("../notes.md"))
        assertTrue(isPathLikeInlineCode("/data/user/0/io.github.whywood.deepdeep/files/home"))
        assertTrue(isPathLikeInlineCode("./build.gradle.kts"))
        assertTrue(isPathLikeInlineCode("basics.md"))
        assertTrue(isPathLikeInlineCode("README.md"))
        assertFalse(isPathLikeInlineCode("https://example.com/a"))
        assertFalse(isPathLikeInlineCode("http://example.com"))
        assertFalse(isPathLikeInlineCode("mailto:user@example.com"))
        assertFalse(isPathLikeInlineCode("sk-1234"))
        assertFalse(isPathLikeInlineCode("val x = 1"))
        assertFalse(isPathLikeInlineCode("414514"))
    }

    @Test
    fun pathLikeCodeSpansStayCodeNodes() {
        assertEquals(InlineNode.Code("src/main/Main.kt"), inlineNodes("`src/main/Main.kt`").single())
        assertEquals(
            InlineNode.FileRef("attachments/x.md", "attachments/x.md", code = true),
            inlineNodes("`attachments/x.md`").single(),
        )
    }

    private fun listBlock(source: String): MarkdownBlock.ListBlock =
        parseMarkdown(source).single() as MarkdownBlock.ListBlock

    private fun textOf(code: String, token: CodeToken): String = code.substring(token.start, token.end)

    private fun largeMarkdown(): String = buildString {
        repeat(60) { index ->
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
