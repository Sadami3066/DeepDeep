package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScalingTest {
    @Test
    fun matchesSdkEdgeLimit() {
        assertEquals(2000, ImageNormalizer.MAX_EDGE)
    }

    @Test
    fun largePhotoKeepsHalfResolutionForExactDownscale() {
        assertEquals(2, ImageScaling.sampleSizeFor(4080, 3060))
        assertEquals(ScaledSize(2000, 1500), ImageScaling.scaledSizeFor(2040, 1530))
    }

    @Test
    fun tallScreenshotDecodesAtFullSizeBeforeDownscaling() {
        assertEquals(1, ImageScaling.sampleSizeFor(1080, 2400))
        assertEquals(ScaledSize(900, 2000), ImageScaling.scaledSizeFor(1080, 2400))
    }

    @Test
    fun hugeImageSamplesWithinDecodeBudget() {
        assertEquals(4, ImageScaling.sampleSizeFor(8000, 6000))
        assertEquals(ScaledSize(2000, 1500), ImageScaling.scaledSizeFor(2000, 1500))
    }

    @Test
    fun smallImageIsNotUpscaled() {
        assertEquals(1, ImageScaling.sampleSizeFor(1800, 1200))
        assertEquals(ScaledSize(1800, 1200), ImageScaling.scaledSizeFor(1800, 1200))
        assertEquals(ScaledSize(400, 300), ImageScaling.scaledSizeFor(400, 300))
    }

    @Test
    fun extremeAspectRatioKeepsAtLeastOnePixel() {
        assertEquals(1, ImageScaling.sampleSizeFor(20000, 20))
        assertEquals(ScaledSize(2000, 2), ImageScaling.scaledSizeFor(20000, 20))
        assertEquals(ScaledSize(1, 1), ImageScaling.scaledSizeFor(1, 1))
    }
}
