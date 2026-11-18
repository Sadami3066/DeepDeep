package io.github.whywood.deepdeep.vscreen

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualInputFallbackTest {
    private val online = VirtualScreenState.Online(13, 1080, 2400, "root", instanceId = "original")
    private val tap = buildJsonObject {
        put("x", 140)
        put("y", 220)
    }

    @Test
    fun preDispatchDenialFallsBackToOwnedDisplay() {
        assertEquals(
            "/system/bin/input -d 13 tap 140 220",
            virtualInputFallback(online, online, VirtualScreenCommands.TAP, tap, INPUT_DENIED_BEFORE_DISPATCH)?.line,
        )
    }

    @Test
    fun rejectsAnotherDisplayAndRebuiltDisplayEvenWhenIdMatches() {
        for (current in listOf(
            VirtualScreenState.Offline, online.copy(displayId = 0), online.copy(displayId = 14), online.copy(instanceId = "new"),
        )) {
            assertNull(virtualInputFallback(online, current, VirtualScreenCommands.TAP, tap, INPUT_DENIED_BEFORE_DISPATCH))
        }
        val main = online.copy(displayId = 0)
        assertNull(virtualInputFallback(main, main, VirtualScreenCommands.TAP, tap, INPUT_DENIED_BEFORE_DISPATCH))
    }

    @Test
    fun anUnclassifiedFailureDoesNotReplayPotentiallyDeliveredInput() {
        for (code in listOf(null, "timeout", "partial_input", "input_denied_after_dispatch")) {
            assertNull(virtualInputFallback(online, online, VirtualScreenCommands.TAP, tap, code))
        }
    }

    @Test
    fun swipeRejectsOutOfBoundsAndKeyRejectsSystemNavigation() {
        val swipe = buildJsonObject {
            put("x1", 80)
            put("y1", 60)
            put("x2", 1080)
            put("y2", 120)
        }
        val home = buildJsonObject { put("key", "home") }
        val back = buildJsonObject { put("key", "back") }
        assertNull(virtualInputFallback(online, online, VirtualScreenCommands.SWIPE, swipe, INPUT_DENIED_BEFORE_DISPATCH))
        assertNull(virtualInputFallback(online, online, VirtualScreenCommands.KEY, home, INPUT_DENIED_BEFORE_DISPATCH))
        assertEquals(
            "/system/bin/input -d 13 keyevent 4",
            virtualInputFallback(online, online, VirtualScreenCommands.KEY, back, INPUT_DENIED_BEFORE_DISPATCH)?.line,
        )
    }

    @Test
    fun explicitPressDurationSurvivesFallback() {
        val held = buildJsonObject {
            put("x", 140)
            put("y", 220)
            put("durationMs", 700)
        }
        assertEquals(
            "/system/bin/input -d 13 swipe 140 220 140 220 700",
            virtualInputFallback(online, online, VirtualScreenCommands.TAP, held, INPUT_DENIED_BEFORE_DISPATCH)?.line,
        )
    }

    @Test
    fun losingAcknowledgmentDoesNotSendInputTwice() = runTest {
        var sends = 0
        val disconnect = IOException("transport closed")
        val failure = runCatching {
            applyVirtualFallbackInput(send = { sends++ }, acknowledge = { throw disconnect })
        }.exceptionOrNull()

        assertEquals(1, sends)
        assertSame(disconnect, failure?.cause)
        assertTrue(failure?.message.orEmpty().contains("input was sent"))
    }
}
