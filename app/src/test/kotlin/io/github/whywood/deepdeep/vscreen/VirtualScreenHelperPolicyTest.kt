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
}
