package io.github.whywood.deepdeep.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copiesStreamWithinLimit() {
        val payload = ByteArray(64) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertTrue(copyWithLimit(ByteArrayInputStream(payload), output, 128))
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test
    fun rejectsStreamOverLimit() {
        val payload = ByteArray(128) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertFalse(copyWithLimit(ByteArrayInputStream(payload), output, 64))
        assertTrue(output.size() <= 64)
    }

    @Test
    fun acceptsStreamExactlyAtLimit() {
        val payload = ByteArray(64) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertTrue(copyWithLimit(ByteArrayInputStream(payload), output, 64))
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test
    fun oversizedUnknownLengthStreamLeavesNoFile() {
        val target = File(temporaryFolder.root, "attachments/unknown.bin")
        val payload = ByteArray(4_096) { it.toByte() }
        val unknownLength = object : InputStream() {
            private var index = 0

            override fun read(): Int = if (index < payload.size) payload[index++].toInt() and 0xFF else -1
        }

        assertFalse(writeAttachment(unknownLength, target, 1_024))
        assertFalse(target.exists())
    }

    @Test
    fun attachmentLimitIsOneGiB() {
        assertEquals(1_073_741_824L, FileAttachmentStore.MAX_BYTES)
    }

    @Test
    fun requiresSpaceMarginAboveFileSize() {
        val margin = 64L * 1024 * 1024
        assertTrue(hasSpaceForImport(1_024L, 1_024L + margin))
        assertFalse(hasSpaceForImport(1_024L, 1_024L + margin - 1))
    }

    @Test
    fun formatsGigabyteSizes() {
        assertEquals("1.0 GB", formatAttachmentSize(1L shl 30))
        assertEquals("1.5 GB", formatAttachmentSize(1_610_612_736L))
        assertEquals("1024.0 MB", formatAttachmentSize((1L shl 30) - 1))
    }

    @Test
    fun limitsImagePixelsTo64Megapixels() {
        assertTrue(isSupportedImageSize(8_192, 8_192))
        assertFalse(isSupportedImageSize(8_192, 8_193))
        assertFalse(isSupportedImageSize(0, 0))
    }
}
