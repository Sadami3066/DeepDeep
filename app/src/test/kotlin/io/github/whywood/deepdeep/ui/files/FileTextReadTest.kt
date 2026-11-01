package io.github.whywood.deepdeep.ui.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTextReadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun write(name: String, bytes: ByteArray): File =
        temporaryFolder.newFile(name).also { it.writeBytes(bytes) }

    @Test
    fun readsSmallFileWithoutTruncation() {
        val file = write("small.txt", "hello world".toByteArray(Charsets.UTF_8))
        val content = readTextLimited(file, limitBytes = 64)
        assertNotNull(content)
        assertEquals("hello world", content?.text)
        assertFalse(content?.truncated ?: true)
    }

    @Test
    fun marksTruncatedWhenFileExceedsLimit() {
        val file = write("large.txt", "abcdefghij".toByteArray(Charsets.UTF_8))
        val content = readTextLimited(file, limitBytes = 4)
        assertNotNull(content)
        assertEquals("abcd", content?.text)
        assertTrue(content?.truncated ?: false)
    }

    @Test
    fun exactLimitIsNotTruncated() {
        val file = write("exact.txt", "abcd".toByteArray(Charsets.UTF_8))
        val content = readTextLimited(file, limitBytes = 4)
        assertNotNull(content)
        assertEquals("abcd", content?.text)
        assertFalse(content?.truncated ?: true)
    }

    @Test
    fun emptyFileIsEmptyContent() {
        val file = write("empty.txt", ByteArray(0))
        val content = readTextLimited(file)
        assertNotNull(content)
        assertEquals("", content?.text)
        assertFalse(content?.truncated ?: true)
    }

    @Test
    fun invalidUtf8IsReplacedInsteadOfFailing() {
        val file = write("broken.txt", byteArrayOf(0x41, 0xC3.toByte(), 0x28, 0x42))
        val content = readTextLimited(file)
        assertNotNull(content)
        val text = content?.text.orEmpty()
        assertTrue(text.startsWith("A"))
        assertTrue(text.endsWith("B"))
        assertTrue(text.contains('\uFFFD'))
    }

    @Test
    fun utf8TextDecodes() {
        val file = write("utf8.txt", "中文 ok".toByteArray(Charsets.UTF_8))
        val content = readTextLimited(file)
        assertEquals("中文 ok", content?.text)
    }

    @Test
    fun binaryContentWithNullByteIsRejected() {
        val file = write("binary.bin", byteArrayOf(0x50, 0x4B, 0x00, 0x04, 0x00))
        assertNull(readTextLimited(file))
    }

    @Test
    fun nullByteBeyondLimitIsIgnored() {
        val file = write("late-null.bin", byteArrayOf(0x41, 0x42, 0x00, 0x43))
        val content = readTextLimited(file, limitBytes = 2)
        assertNotNull(content)
        assertEquals("AB", content?.text)
        assertTrue(content?.truncated ?: false)
    }

    @Test
    fun missingFileIsNull() {
        val file = File(temporaryFolder.root, "missing.txt")
        assertNull(readTextLimited(file))
    }

    @Test
    fun directoryIsNull() {
        assertNull(readTextLimited(temporaryFolder.newFolder("dir")))
    }
}
