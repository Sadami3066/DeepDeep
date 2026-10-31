package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPreviewTest {
    @Test
    fun firstFrameIsAccepted() {
        assertTrue(floatingPreviewAccepted(lastAcceptedAt = 0L, now = 1_000L))
    }

    @Test
    fun frameInsideIntervalIsRejected() {
        assertFalse(floatingPreviewAccepted(lastAcceptedAt = 1_000L, now = 1_199L))
    }

    @Test
    fun frameOnIntervalBoundaryIsAccepted() {
        assertTrue(floatingPreviewAccepted(lastAcceptedAt = 1_000L, now = 1_200L))
    }

    @Test
    fun laterFrameIsAccepted() {
        assertTrue(floatingPreviewAccepted(lastAcceptedAt = 1_000L, now = 1_800L))
    }

    @Test
    fun backwardsClockIsRejected() {
        assertFalse(floatingPreviewAccepted(lastAcceptedAt = 1_000L, now = 900L))
    }

    @Test
    fun customIntervalIsRespected() {
        assertFalse(floatingPreviewAccepted(lastAcceptedAt = 5_000L, now = 5_050L, intervalMs = 100L))
        assertTrue(floatingPreviewAccepted(lastAcceptedAt = 5_000L, now = 5_100L, intervalMs = 100L))
    }

    @Test
    fun stableStateKeepsNonOnlineStates() {
        assertEquals(VirtualScreenState.Offline, floatingStableVirtualScreenState(VirtualScreenState.Offline))
        assertEquals(VirtualScreenState.Starting, floatingStableVirtualScreenState(VirtualScreenState.Starting))
        assertEquals(
            VirtualScreenState.Failed("boom"),
            floatingStableVirtualScreenState(VirtualScreenState.Failed("boom")),
        )
    }

    @Test
    fun stableOnlineStateMatchesStateWithoutFrame() {
        val online = VirtualScreenState.Online(
            displayId = 3,
            width = 720,
            height = 1280,
            backend = "root",
        )
        assertEquals(online, floatingStableVirtualScreenState(online))
    }

    @Test
    fun ratioUsesOnlineDimensions() {
        val online = VirtualScreenState.Online(
            displayId = 3,
            width = 720,
            height = 1280,
            backend = "adb",
        )
        assertEquals(0.5625f, floatingPreviewRatio(online), 0.0001f)
    }

    @Test
    fun ratioFallsBackForNonOnlineStates() {
        assertEquals(9f / 16f, floatingPreviewRatio(VirtualScreenState.Offline), 0.0001f)
        assertEquals(9f / 16f, floatingPreviewRatio(VirtualScreenState.Starting), 0.0001f)
        assertEquals(9f / 16f, floatingPreviewRatio(VirtualScreenState.Failed("boom")), 0.0001f)
    }

    @Test
    fun ratioFallsBackForInvalidOnlineDimensions() {
        val online = VirtualScreenState.Online(
            displayId = 3,
            width = 0,
            height = 1280,
            backend = "adb",
        )
        assertEquals(9f / 16f, floatingPreviewRatio(online), 0.0001f)
    }

    @Test
    fun previewEnabledOnlyWhileWindowShownAndPreviewOpen() {
        assertTrue(floatingPreviewEnabled(shown = true, previewOpen = true))
        assertFalse(floatingPreviewEnabled(shown = false, previewOpen = true))
        assertFalse(floatingPreviewEnabled(shown = true, previewOpen = false))
    }
}
