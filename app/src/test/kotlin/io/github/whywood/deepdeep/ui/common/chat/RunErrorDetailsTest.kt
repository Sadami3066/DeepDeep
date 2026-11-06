package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.ui.common.chat.localizedRunError

import io.github.whywood.deepdeep.ui.common.chat.rawErrorDetails

import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RunErrorDetailsTest {
    private val strings = ConversationStrings.fallback()

    @Test
    fun localizedRunErrorReplacesSummaryAndKeepsProviderError() {
        val message = AssistantMessage(
            id = 1,
            role = AssistantRole.ASSISTANT,
            text = "",
            rawError = "413 Request Entity Too Large",
        )

        val localized = message.localizedRunError(strings::runError)

        assertEquals(strings.requestTooLarge, localized.error)
        assertEquals("413 Request Entity Too Large", localized.rawError)
    }

    @Test
    fun localizedRunErrorLeavesMessageWithoutProviderErrorUntouched() {
        val message = AssistantMessage(id = 1, role = AssistantRole.ASSISTANT, text = "")

        assertSame(message, message.localizedRunError(strings::runError))
    }

    @Test
    fun rawErrorDetailsRejectsBlankProviderErrors() {
        assertNull(rawErrorDetails(null))
        assertNull(rawErrorDetails("  \n "))
    }

    @Test
    fun rawErrorDetailsTrimsProviderError() {
        assertEquals("boom", rawErrorDetails("  boom \n"))
    }

    @Test
    fun rawErrorDetailsTruncatesLongProviderError() {
        val long = "x".repeat(500)

        val details = rawErrorDetails(long)

        assertEquals(300, details?.length)
        assertEquals(long.take(300), details)
    }
}
