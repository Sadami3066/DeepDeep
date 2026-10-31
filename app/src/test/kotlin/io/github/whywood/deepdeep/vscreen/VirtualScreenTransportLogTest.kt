package io.github.whywood.deepdeep.vscreen

import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenTransportLogTest {
    @Test
    fun failureLogCarriesReasonBackendPidAndDisplay() {
        val line = VirtualScreenTransportLog.failure(
            kind = TransportFailureKind.HEARTBEAT,
            backend = PrivilegedLauncher.BACKEND_ADB,
            helperPid = 4242,
            displayId = 12,
            detail = "no pong for 15000ms",
        )
        assertTrue(line.startsWith("event=transport_lost"))
        assertTrue(line.contains("reason=heartbeat"))
        assertTrue(line.contains("backend=adb"))
        assertTrue(line.contains("helperPid=4242"))
        assertTrue(line.contains("displayId=12"))
        assertTrue(line.contains("detail=no pong for 15000ms"))
    }

    @Test
    fun missingFieldsAreMarkedExplicitly() {
        val line = VirtualScreenTransportLog.failure(
            kind = TransportFailureKind.EOF,
            backend = null,
            helperPid = null,
            displayId = null,
            detail = "stream ended",
        )
        assertTrue(line.contains("reason=eof"))
        assertTrue(line.contains("backend=-"))
        assertTrue(line.contains("helperPid=-"))
        assertTrue(line.contains("displayId=-"))
    }

    @Test
    fun eventFormatKeepsStableKeys() {
        val line = VirtualScreenTransportLog.event("transport_online", "backend" to "root", "displayId" to 3)
        assertTrue(line.startsWith("event=transport_online "))
        assertTrue(line.contains("backend=root"))
        assertTrue(line.contains("displayId=3"))
    }
}
