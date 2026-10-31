package io.github.whywood.deepdeep.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbOutputTruncationTest {
    @Test
    fun asciiOutputWithinLimitIsUnchanged() {
        assertEquals("abcdef", takeUtf8("abcdef", 6))
        assertEquals("abcdef", takeUtf8("abcdef", 32))
        assertFalse(utf8ByteLength("abcdef") > 6)
    }

    @Test
    fun asciiOutputIsCutByBytes() {
        assertEquals("abc", takeUtf8("abcdef", 3))
        assertTrue(utf8ByteLength("abcdef") > 3)
    }

    @Test
    fun multibyteOutputKeepsWholeCharacters() {
        assertEquals("你", takeUtf8("你好", 4))
        assertEquals("你", takeUtf8("你好", 3))
        assertEquals("", takeUtf8("你好", 2))
        assertFalse(takeUtf8("你好", 4).contains('\uFFFD'))
    }

    @Test
    fun surrogatePairIsNeverSplit() {
        assertEquals("a", takeUtf8("a😀b", 3))
        assertEquals("", takeUtf8("😀", 2))
        assertEquals("😀", takeUtf8("😀", 4))
    }

    @Test
    fun truncationIsMeasuredInBytesNotChars() {
        assertEquals(6, utf8ByteLength("你好"))
        assertEquals(4, utf8ByteLength("😀"))
        assertTrue(utf8ByteLength("你好") > 5)
        assertFalse(utf8ByteLength("你好") > 6)
    }

    @Test
    fun zeroLimitProducesEmptyOutput() {
        assertEquals("", takeUtf8("anything", 0))
        assertEquals("", takeUtf8("anything", -1))
    }
}
