package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
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
    fun parsesTaskList() {
        val blocks = parseMarkdown("- [x] done\n- [ ] todo")
        val tasks = blocks.single() as MarkdownBlock.Tasks
        assertEquals(2, tasks.items.size)
        assertTrue(tasks.items[0].checked)
        assertEquals("todo", tasks.items[1].text)
    }

    @Test
    fun preservesOrderedStart() {
        val blocks = parseMarkdown("3. third\n4. fourth")
        val list = blocks.single() as MarkdownBlock.Bullets
        assertEquals(3, list.start)
        assertEquals(listOf("third", "fourth"), list.items)
    }

    @Test
    fun plainTextKeepsCodeAndTasks() {
        val plain = markdownToPlainText("```sh\necho hi\n```\n\n- [x] done")
        assertTrue(plain.contains("echo hi"))
        assertTrue(plain.contains("☑ done"))
    }
}
