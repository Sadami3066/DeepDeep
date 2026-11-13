package io.github.whywood.deepdeep.plugin.host.extensions.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPluginServiceTest {
    private fun row(
        threadId: String,
        body: String,
        dateMs: Long,
        address: String = "10086",
        incoming: Boolean = true,
        read: Boolean = true,
    ): SmsRow = SmsRow(
        threadId = threadId,
        address = address,
        body = body,
        dateMs = dateMs,
        incoming = incoming,
        read = read,
    )

    @Test
    fun shortMessageIsSinglePart() {
        assertEquals(listOf("hello"), splitSmsText("hello"))
        assertEquals(listOf("x".repeat(SMS_SINGLE_LIMIT)), splitSmsText("x".repeat(SMS_SINGLE_LIMIT)))
    }

    @Test
    fun longMessageIsChunkedForMultipart() {
        val text = "a".repeat(SMS_SINGLE_LIMIT + 1)
        val parts = splitSmsText(text)
        assertEquals(2, parts.size)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.length <= SMS_PART_LIMIT })
    }

    @Test
    fun exactPartBoundaryDoesNotProduceEmptyTail() {
        val text = "b".repeat(SMS_PART_LIMIT * 3)
        val parts = splitSmsText(text)
        assertEquals(3, parts.size)
        assertTrue(parts.none { it.isEmpty() })
    }

    @Test
    fun emptyMessageHasNoParts() {
        assertEquals(emptyList<String>(), splitSmsText(""))
    }

    @Test
    fun threadsGroupByThreadWithLatestSnippetAndUnreadCount() {
        val rows = listOf(
            row("1", "older", 100, read = false),
            row("1", "latest", 200, read = false),
            row("2", "other", 50),
            row("1", "outgoing", 150, incoming = false, read = true),
        )
        val threads = smsThreads(rows, limit = 10)

        assertEquals(listOf("1", "2"), threads.map { it.id })
        val first = threads.first()
        assertEquals("latest", first.snippet)
        assertEquals(200, first.dateMs)
        assertEquals(2, first.unreadCount)
        assertEquals("10086", first.address)
    }

    @Test
    fun threadsRespectLimit() {
        val rows = (1..5).map { index -> row(index.toString(), "body-$index", index.toLong()) }
        assertEquals(2, smsThreads(rows, limit = 2).size)
        assertEquals(5, smsThreads(rows, limit = 0).size)
    }

    @Test
    fun messageMappingKeepsDirectionAndReadState() {
        val message = smsMessage(row("9", "hi", 7, address = "123", incoming = false, read = false))
        assertEquals("9", message.threadId)
        assertEquals("123", message.address)
        assertEquals("hi", message.body)
        assertFalse(message.incoming)
        assertFalse(message.read)
    }
}
