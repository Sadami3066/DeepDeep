package io.github.whywood.deepdeep.vscreen

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VirtualScreenProtocolTest {
    @Test
    fun textFrameRoundTrip() {
        val json = buildJsonObject {
            put("id", 7)
            put("cmd", "ping")
        }
        val encoded = encode(VirtualScreenProtocol.text(json))
        val frame = VirtualScreenProtocol.read(ByteArrayInputStream(encoded))
            ?: error("expected a frame")
        assertEquals(VirtualScreenFrameKind.TEXT, frame.kind)
        val parsed = VirtualScreenProtocol.json(frame)
        assertEquals(7, parsed.intField("id"))
        assertEquals("ping", parsed.stringField("cmd"))
    }

    @Test
    fun binaryFrameRoundTrip() {
        val payload = ByteArray(1024) { (it % 251).toByte() }
        val encoded = encode(VirtualScreenProtocol.binary(payload))
        val frame = VirtualScreenProtocol.read(ByteArrayInputStream(encoded))
            ?: error("expected a frame")
        assertEquals(VirtualScreenFrameKind.BINARY, frame.kind)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun screenshotFrameRoundTrip() {
        val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val encoded = encode(VirtualScreenProtocol.screenshot(payload))
        val frame = VirtualScreenProtocol.read(ByteArrayInputStream(encoded))
            ?: error("expected a frame")
        assertEquals(VirtualScreenFrameKind.SCREENSHOT, frame.kind)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun framesSplitAcrossChunksAreReassembled() {
        val json = buildJsonObject { put("id", 11) }
        val encoded = encode(VirtualScreenProtocol.text(json), VirtualScreenProtocol.binary(ByteArray(37) { 1 }))
        val input = ChunkedInputStream(encoded, chunkSize = 3)
        val first = VirtualScreenProtocol.read(input)
        val second = VirtualScreenProtocol.read(input)
        assertEquals(VirtualScreenFrameKind.TEXT, first?.kind)
        assertEquals(VirtualScreenFrameKind.BINARY, second?.kind)
        assertEquals(37, second?.payload?.size)
        assertNull(VirtualScreenProtocol.read(input))
    }

    @Test
    fun multipleFramesAreReadInOrder() {
        val encoded = encode(
            VirtualScreenProtocol.text(buildJsonObject { put("id", 1) }),
            VirtualScreenProtocol.binary(byteArrayOf(9, 8, 7)),
            VirtualScreenProtocol.text(buildJsonObject { put("id", 2) }),
        )
        val input = ByteArrayInputStream(encoded)
        assertEquals(1, VirtualScreenProtocol.json(VirtualScreenProtocol.read(input)!!).intField("id"))
        assertArrayEquals(byteArrayOf(9, 8, 7), VirtualScreenProtocol.read(input)!!.payload)
        assertEquals(2, VirtualScreenProtocol.json(VirtualScreenProtocol.read(input)!!).intField("id"))
        assertNull(VirtualScreenProtocol.read(input))
    }

    @Test
    fun cleanEndOfStreamReturnsNull() {
        assertNull(VirtualScreenProtocol.read(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun truncatedHeaderFails() {
        val encoded = encode(VirtualScreenProtocol.binary(ByteArray(16)))
        val truncated = encoded.copyOfRange(0, 3)
        expectIOException { VirtualScreenProtocol.read(ByteArrayInputStream(truncated)) }
    }

    @Test
    fun truncatedPayloadFails() {
        val encoded = encode(VirtualScreenProtocol.binary(ByteArray(16)))
        val truncated = encoded.copyOfRange(0, encoded.size - 5)
        expectIOException { VirtualScreenProtocol.read(ByteArrayInputStream(truncated)) }
    }

    @Test
    fun oversizedFrameIsRejected() {
        val header = header(VirtualScreenProtocol.MAX_FRAME_BYTES + 1, VirtualScreenFrameKind.TEXT.code)
        expectIOException { VirtualScreenProtocol.read(ByteArrayInputStream(header)) }
    }

    @Test
    fun unknownFrameKindIsRejected() {
        val header = header(4, 9)
        expectIOException { VirtualScreenProtocol.read(ByteArrayInputStream(header + ByteArray(4))) }
    }

    @Test
    fun zeroLengthFrameIsRejected() {
        expectIOException { VirtualScreenProtocol.read(ByteArrayInputStream(header(0, VirtualScreenFrameKind.TEXT.code))) }
    }

    @Test
    fun jsonRejectsBinaryFrames() {
        val frame = VirtualScreenProtocol.binary(byteArrayOf(1))
        expectIllegalArgument { VirtualScreenProtocol.json(frame) }
    }

    private fun encode(vararg frames: VirtualScreenFrame): ByteArray {
        val output = ByteArrayOutputStream()
        frames.forEach { VirtualScreenProtocol.write(output, it) }
        return output.toByteArray()
    }

    private fun header(payloadLength: Int, kind: Byte): ByteArray = byteArrayOf(
        (payloadLength ushr 24).toByte(),
        (payloadLength ushr 16).toByte(),
        (payloadLength ushr 8).toByte(),
        payloadLength.toByte(),
        kind,
    )

    private fun expectIOException(block: () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.isNotEmpty())
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.isNotEmpty())
        }
    }

    private class ChunkedInputStream(
        private val data: ByteArray,
        private val chunkSize: Int,
    ) : InputStream() {
        private var offset = 0

        override fun read(): Int = if (offset < data.size) data[offset++].toInt() and 0xFF else -1

        override fun read(target: ByteArray, targetOffset: Int, length: Int): Int {
            if (offset >= data.size) return -1
            val count = minOf(length, chunkSize, data.size - offset)
            data.copyInto(target, targetOffset, offset, offset + count)
            offset += count
            return count
        }
    }
}
