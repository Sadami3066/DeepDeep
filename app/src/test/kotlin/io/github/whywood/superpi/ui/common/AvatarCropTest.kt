package io.github.whywood.superpi.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCropTest {
    @Test
    fun squareImageUsesWholeImageAtDefaultZoom() {
        val rect = avatarCropRect(1000, 1000, viewport = 300f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(AvatarCropRect(0, 0, 1000), rect)
    }

    @Test
    fun landscapeImageCropsCenteredSquare() {
        val rect = avatarCropRect(1000, 500, viewport = 300f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(AvatarCropRect(250, 0, 500), rect)
    }

    @Test
    fun zoomReducesSourceSide() {
        val rect = avatarCropRect(1000, 500, viewport = 300f, zoom = 2f, offsetX = 0f, offsetY = 0f)
        assertEquals(AvatarCropRect(375, 125, 250), rect)
    }

    @Test
    fun panMovesCropAndClampsAtImageEdge() {
        val panned = avatarCropRect(1000, 500, viewport = 300f, zoom = 1f, offsetX = 100f, offsetY = 0f)
        assertEquals(83, panned.left)
        assertEquals(0, panned.top)
        assertEquals(500, panned.size)

        val clamped = avatarCropRect(1000, 500, viewport = 300f, zoom = 1f, offsetX = 10000f, offsetY = 0f)
        assertEquals(AvatarCropRect(0, 0, 500), clamped)
    }

    @Test
    fun maxOffsetOnlyAllowsOverflowAxis() {
        val (landscapeX, landscapeY) = avatarCropMaxOffset(1000, 500, viewport = 300f, zoom = 1f)
        assertEquals(150f, landscapeX, 0.001f)
        assertEquals(0f, landscapeY, 0.001f)

        val (portraitX, portraitY) = avatarCropMaxOffset(500, 1000, viewport = 300f, zoom = 1f)
        assertEquals(0f, portraitX, 0.001f)
        assertEquals(150f, portraitY, 0.001f)
    }
}
