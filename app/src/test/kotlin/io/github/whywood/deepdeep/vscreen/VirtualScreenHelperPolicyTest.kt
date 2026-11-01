package io.github.whywood.deepdeep.vscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualScreenHelperPolicyTest {
    @Test
    fun acceptsCoordinatesInsideDisplay() {
        assertNull(outOfBoundsReason("tap", 0, 0, 1080, 2400))
        assertNull(outOfBoundsReason("tap", 1079, 2399, 1080, 2400))
    }

    @Test
    fun rejectsCoordinatesOutsideDisplay() {
        assertEquals(
            "tap coordinates out of virtual screen bounds: (1080, 1200), virtual screen 1080x2400",
            outOfBoundsReason("tap", 1080, 1200, 1080, 2400),
        )
        assertEquals(
            "tap coordinates out of virtual screen bounds: (540, 2400), virtual screen 1080x2400",
            outOfBoundsReason("tap", 540, 2400, 1080, 2400),
        )
    }

    @Test
    fun rejectsNegativeCoordinates() {
        assertEquals(
            "tap coordinates out of virtual screen bounds: (-1, 100), virtual screen 1080x2400",
            outOfBoundsReason("tap", -1, 100, 1080, 2400),
        )
        assertEquals(
            "tap coordinates out of virtual screen bounds: (100, -1), virtual screen 1080x2400",
            outOfBoundsReason("tap", 100, -1, 1080, 2400),
        )
    }

    @Test
    fun labelsSwipeEndpoints() {
        assertEquals(
            "swipe start coordinates out of virtual screen bounds: (-5, 10), virtual screen 720x1280",
            outOfBoundsReason("swipe start", -5, 10, 720, 1280),
        )
        assertEquals(
            "swipe end coordinates out of virtual screen bounds: (720, 10), virtual screen 720x1280",
            outOfBoundsReason("swipe end", 720, 10, 720, 1280),
        )
        assertNull(outOfBoundsReason("swipe end", 719, 1279, 720, 1280))
    }
    @Test
    fun screenshotFrameLimitMatchesProtocolHeader() {
        val limit = VirtualScreenProtocol.MAX_FRAME_BYTES
        assertEquals(false, screenshotFrameExceedsLimit(limit - 5))
        assertEquals(true, screenshotFrameExceedsLimit(limit - 4))
        assertEquals(true, screenshotFrameExceedsLimit(limit))
    }

    @Test
    fun launchMessageHidesInternalDisplayIds() {
        val message = virtualLaunchMessage("com.android.settings", 10, 10)
        assertEquals("Launched com.android.settings on the virtual screen", message)
        assertEquals(false, message.contains("10"))
    }

    @Test
    fun offScreenLaunchMessageHidesInternalDisplayIds() {
        val expected = "Launched com.android.settings but the task did not land on the virtual screen; run screen_observe again to confirm the actual page"
        assertEquals(expected, virtualLaunchMessage("com.android.settings", 10, 0))
        assertEquals(expected, virtualLaunchMessage("com.android.settings", 10, -1))
        assertEquals(false, expected.contains("10"))
        assertEquals(false, expected.contains("0"))
        assertEquals(false, expected.contains("display"))
    }
}
