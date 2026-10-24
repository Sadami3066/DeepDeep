package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillInvocationTest {
    private val block = """
        <skill name="pdf-tools" location="/abs/pdf-tools/SKILL.md">
        References are relative to /abs/pdf-tools.

        Use pdftotext with JSON {"mode":"full"} and raw <div> tags.
        </skill>
    """.trimIndent()

    @Test
    fun parsesBlockWithArguments() {
        val invocation = requireNotNull(parseSkillInvocation("$block\n\nSummarize the quarterly report"))
        assertEquals("pdf-tools", invocation.name)
        assertEquals("Summarize the quarterly report", invocation.userText)
    }

    @Test
    fun parsesBlockWithoutArguments() {
        val invocation = requireNotNull(parseSkillInvocation(block))
        assertEquals("pdf-tools", invocation.name)
        assertEquals("", invocation.userText)
    }

    @Test
    fun parsesBlockAfterQuotedText() {
        val text = "> earlier quote\n\n$block\n\nmake a board"
        val invocation = requireNotNull(parseSkillInvocation(text))
        assertEquals("pdf-tools", invocation.name)
        assertEquals("make a board", invocation.userText)
        val stripped = stripSkillInvocation(text)
        assertTrue(stripped.startsWith("> earlier quote"))
        assertTrue(stripped.endsWith("make a board"))
    }

    @Test
    fun stripsMultipleSkillBlocks() {
        val other = block.replace("pdf-tools", "workshop")
        val text = "$block\n\n$other\n\nuse both"
        val invocation = requireNotNull(parseSkillInvocation(text))
        assertEquals("pdf-tools", invocation.name)
        assertEquals("use both", invocation.userText)
        assertEquals("use both", stripSkillInvocation(text))
    }

    @Test
    fun returnsNullWithoutBlock() {
        assertNull(parseSkillInvocation("plain message"))
        assertNull(parseSkillInvocation(""))
        assertNull(parseSkillInvocation("$block\n\nargs".replace("</skill>", "")))
        assertNull(parseSkillInvocation("""<skill name="pdf-tools">body</skill>"""))
        assertNull(parseSkillInvocation("""<skill location="/abs/SKILL.md">body</skill>"""))
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("plain message", stripSkillInvocation("plain message"))
    }
}
