package io.github.whywood.deepdeep.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConversationStringsTest {
    private val strings = ConversationStrings.fallback()

    @Test
    fun oversizedRequestErrorsMapToActionableKind() {
        listOf(
            "fail to allocate at 62110616",
            "413 Request Entity Too Large",
            "payload too large",
            "request body too large",
        ).forEach { raw ->
            assertEquals(raw, RunErrorKind.REQUEST_TOO_LARGE, strings.runErrorKind(raw))
        }
    }

    @Test
    fun oversizedRequestSummaryDropsHttpCode() {
        assertEquals(strings.requestTooLarge, strings.runError("413 Request Entity Too Large"))
        assertEquals(strings.requestTooLarge, strings.runError("fail to allocate at 62110616"))
    }

    @Test
    fun oversizedRequestIsNotAutoRetried() {
        assertFalse(strings.canAutoRetry(RunErrorKind.REQUEST_TOO_LARGE))
    }

    @Test
    fun otherErrorsKeepTheirOwnKind() {
        assertEquals(RunErrorKind.SERVER, strings.runErrorKind("500 Internal Server Error"))
        assertEquals(strings.withCode(strings.serviceUnavailable, "500"), strings.runError("500 Internal Server Error"))
        assertEquals(RunErrorKind.TIMEOUT, strings.runErrorKind("request timed out"))
    }
}
