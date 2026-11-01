package io.github.whywood.deepdeep.vscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualScreenHelperPolicyTest {
    @Test
    fun acceptsCoordinatesInsideDisplay() {
        assertNull(outOfBoundsReason("点击", 0, 0, 1080, 2400))
        assertNull(outOfBoundsReason("点击", 1079, 2399, 1080, 2400))
    }

    @Test
    fun rejectsCoordinatesOutsideDisplay() {
        assertEquals(
            "点击坐标超出虚拟屏范围：(1080, 1200)，虚拟屏 1080×2400",
            outOfBoundsReason("点击", 1080, 1200, 1080, 2400),
        )
        assertEquals(
            "点击坐标超出虚拟屏范围：(540, 2400)，虚拟屏 1080×2400",
            outOfBoundsReason("点击", 540, 2400, 1080, 2400),
        )
    }

    @Test
    fun rejectsNegativeCoordinates() {
        assertEquals(
            "点击坐标超出虚拟屏范围：(-1, 100)，虚拟屏 1080×2400",
            outOfBoundsReason("点击", -1, 100, 1080, 2400),
        )
        assertEquals(
            "点击坐标超出虚拟屏范围：(100, -1)，虚拟屏 1080×2400",
            outOfBoundsReason("点击", 100, -1, 1080, 2400),
        )
    }

    @Test
    fun labelsSwipeEndpoints() {
        assertEquals(
            "滑动起点坐标超出虚拟屏范围：(-5, 10)，虚拟屏 720×1280",
            outOfBoundsReason("滑动起点", -5, 10, 720, 1280),
        )
        assertEquals(
            "滑动终点坐标超出虚拟屏范围：(720, 10)，虚拟屏 720×1280",
            outOfBoundsReason("滑动终点", 720, 10, 720, 1280),
        )
        assertNull(outOfBoundsReason("滑动终点", 719, 1279, 720, 1280))
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
        assertEquals("已在虚拟屏启动 com.android.settings", message)
        assertEquals(false, message.contains("10"))
    }

    @Test
    fun offScreenLaunchMessageHidesInternalDisplayIds() {
        val expected = "已启动 com.android.settings，但任务没有落在虚拟屏，请重新 screen_observe 确认真实页面"
        assertEquals(expected, virtualLaunchMessage("com.android.settings", 10, 0))
        assertEquals(expected, virtualLaunchMessage("com.android.settings", 10, -1))
        assertEquals(false, expected.contains("10"))
        assertEquals(false, expected.contains("0"))
        assertEquals(false, expected.contains("display"))
    }
}
