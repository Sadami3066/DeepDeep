package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePayloadTest {
    private val send = "android.intent.action.SEND"
    private val sendMultiple = "android.intent.action.SEND_MULTIPLE"

    @Test
    fun parsesSingleStreamShare() {
        val payload = parseSharePayload(
            action = send,
            type = "image/png",
            streamUris = listOf("content://media/1"),
            text = "  看看这个  ",
            title = "  截屏  ",
        )
        assertEquals(listOf("content://media/1"), payload.streams)
        assertEquals("看看这个", payload.text)
        assertEquals("截屏", payload.title)
        assertFalse(payload.isEmpty)
    }

    @Test
    fun sendKeepsOnlyFirstStream() {
        val payload = parseSharePayload(
            action = send,
            type = "*/*",
            streamUris = listOf("content://media/1", "content://media/2"),
        )
        assertEquals(listOf("content://media/1"), payload.streams)
    }

    @Test
    fun sendMultipleKeepsAllUniqueStreams() {
        val payload = parseSharePayload(
            action = sendMultiple,
            type = "image/*",
            streamUris = listOf("content://media/1", "content://media/2", "content://media/3"),
        )
        assertEquals(
            listOf("content://media/1", "content://media/2", "content://media/3"),
            payload.streams,
        )
    }

    @Test
    fun clipUrisAreUsedWhenStreamsAreMissing() {
        val single = parseSharePayload(
            action = send,
            type = "image/png",
            clipUris = listOf("content://clip/1", "content://clip/2"),
        )
        assertEquals(listOf("content://clip/1"), single.streams)

        val multiple = parseSharePayload(
            action = sendMultiple,
            type = "image/*",
            clipUris = listOf("content://clip/1", "content://clip/2"),
        )
        assertEquals(listOf("content://clip/1", "content://clip/2"), multiple.streams)
    }

    @Test
    fun streamsTakePrecedenceOverClipsAndDuplicateUrisAreDropped() {
        val payload = parseSharePayload(
            action = sendMultiple,
            type = "*/*",
            streamUris = listOf("content://media/1", "content://media/2"),
            clipUris = listOf("content://media/2", "content://clip/3"),
        )
        assertEquals(
            listOf("content://media/1", "content://media/2", "content://clip/3"),
            payload.streams,
        )
    }

    @Test
    fun trimsAndDropsBlankUris() {
        val payload = parseSharePayload(
            action = sendMultiple,
            type = "*/*",
            streamUris = listOf("  ", " content://media/1 ", ""),
            clipUris = listOf(" content://media/2\n"),
        )
        assertEquals(listOf("content://media/1", "content://media/2"), payload.streams)
    }

    @Test
    fun capsStreamsAtMaximum() {
        val uris = (0 until MAX_SHARE_STREAMS + 5).map { index -> "content://media/$index" }
        val payload = parseSharePayload(sendMultiple, "*/*", streamUris = uris)
        assertEquals(MAX_SHARE_STREAMS, payload.streams.size)
        assertEquals("content://media/0", payload.streams.first())
        assertEquals("content://media/${MAX_SHARE_STREAMS - 1}", payload.streams.last())
    }

    @Test
    fun textOnlyShareIsNotEmpty() {
        val payload = parseSharePayload(
            action = send,
            type = "text/plain",
            text = "  你好，分享一段文字  ",
        )
        assertTrue(payload.streams.isEmpty())
        assertEquals("你好，分享一段文字", payload.text)
        assertFalse(payload.isEmpty)
    }

    @Test
    fun emptyShareIsEmpty() {
        val payload = parseSharePayload(
            action = send,
            type = "text/plain",
            streamUris = emptyList(),
            clipUris = listOf("  "),
            text = "   ",
            title = "   ",
        )
        assertTrue(payload.streams.isEmpty())
        assertEquals("", payload.text)
        assertEquals("", payload.title)
        assertTrue(payload.isEmpty)
    }

    @Test
    fun unknownActionKeepsAllStreams() {
        val payload = parseSharePayload(
            action = null,
            type = null,
            streamUris = listOf("content://media/1", "content://media/2"),
        )
        assertEquals(listOf("content://media/1", "content://media/2"), payload.streams)
    }
}
