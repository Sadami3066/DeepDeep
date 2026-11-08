package io.github.whywood.deepdeep.ui.common

import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthSelectOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthLoginSessionTest {
    @Test
    fun promptResumesWithSubmittedText() = runTest {
        val session = OAuthLoginSession("Example", onOpenUrl = {})
        val answer = async { session.prompt("Paste the authorization code", "code", secret = true) }
        advanceUntilIdle()

        assertEquals("Paste the authorization code", session.state.value.prompt?.message)
        assertEquals("code", session.state.value.prompt?.placeholder)
        assertTrue(session.state.value.prompt?.secret == true)

        assertTrue(session.submitPrompt("abc123"))
        assertEquals("abc123", answer.await())
        assertNull(session.state.value.prompt)
        assertFalse(session.submitPrompt("again"))
    }

    @Test
    fun selectResumesWithChosenOption() = runTest {
        val options = listOf(
            OAuthSelectOption("personal", "Personal", "Use your own account"),
            OAuthSelectOption("team", "Team"),
        )
        val session = OAuthLoginSession("Example", onOpenUrl = {})
        val answer = async { session.select("Choose an account", options) }
        advanceUntilIdle()

        assertEquals("Choose an account", session.state.value.message)
        assertEquals(options, session.state.value.options)
        assertFalse(session.choose("missing"))
        assertTrue(session.choose("team"))
        assertFalse(session.choose("personal"))
        assertEquals("team", answer.await())
        assertNull(session.state.value.message)
        assertTrue(session.state.value.options.isEmpty())
    }

    @Test
    fun showMessageUpdatesStateAndClearsPrompt() = runTest {
        val session = OAuthLoginSession("Example", onOpenUrl = {})
        val waiter = launch {
            runCatching { session.prompt("Old prompt", null, secret = false) }
        }
        advanceUntilIdle()
        assertEquals("Old prompt", session.state.value.prompt?.message)

        session.showMessage("Waiting for the browser")
        advanceUntilIdle()

        assertEquals("Waiting for the browser", session.state.value.message)
        assertNull(session.state.value.prompt)
        assertTrue(session.state.value.options.isEmpty())
        assertTrue(waiter.isCompleted)
    }

    @Test
    fun cancelAbortsSignalAndCancelsPendingPrompt() = runTest {
        val session = OAuthLoginSession("Example", onOpenUrl = {})
        var cancelled = false
        val waiter = launch {
            try {
                session.prompt("Authentication code", null, secret = false)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        advanceUntilIdle()
        assertEquals("Authentication code", session.state.value.prompt?.message)

        session.cancel()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertTrue(waiter.isCompleted)
        assertTrue(session.signal.isAborted)
        assertTrue(session.state.value.finished)
        assertFalse(session.state.value.busy)
        assertNull(session.state.value.prompt)
        assertFalse(session.submitPrompt("code"))
        assertFalse(session.choose("any"))
    }
}
