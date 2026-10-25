package io.github.whywood.deepdeep.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpeechTextTest {
    @Test
    fun blankTextProducesNoChunks() {
        assertTrue(splitSpeechText("", 100).isEmpty())
        assertTrue(splitSpeechText("   ", 100).isEmpty())
    }

    @Test
    fun shortTextStaysWhole() {
        assertEquals(listOf("你好。"), splitSpeechText("你好。", 100))
        assertEquals(listOf("你好。"), splitSpeechText("  你好。 ", 100))
    }

    @Test
    fun splitsAtSentenceBoundary() {
        assertEquals(listOf("你好。", "世界。"), splitSpeechText("你好。世界。", 4))
    }

    @Test
    fun splitsWithoutBoundaryAtLimit() {
        assertEquals(listOf("abcd", "efgh", "ij"), splitSpeechText("abcdefghij", 4))
    }

    @Test
    fun splitsAtWhitespaceWhenNoPunctuation() {
        val chunks = splitSpeechText("hello world foo bar", 12)
        assertEquals(listOf("hello world ", "foo bar"), chunks)
        assertEquals("hello world foo bar", chunks.joinToString(""))
    }

    @Test
    fun chunksNeverExceedLimitAndRejoin() {
        val text = "深Deep 是一个助手。" + "它支持语音输入和朗读。".repeat(200)
        val chunks = splitSpeechText(text, 64)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 64 })
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun keepsSurrogatePairsIntact() {
        val chunks = splitSpeechText("😀😀😀", 3)
        assertEquals(listOf("😀", "😀", "😀"), chunks)
        assertEquals("😀😀😀", chunks.joinToString(""))
    }
}
