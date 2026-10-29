package io.github.whywood.deepdeep.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
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
}
