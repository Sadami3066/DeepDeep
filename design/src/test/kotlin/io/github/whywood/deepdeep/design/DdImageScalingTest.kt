package io.github.whywood.deepdeep.design

import org.junit.Assert.assertEquals
import org.junit.Test

class DdImageScalingTest {
    @Test
    fun matchesSdkEdgeLimit() {
        assertEquals(2000, DdImageScaling.MAX_EDGE)
    }

    @Test
    fun largePhotoKeepsHalfResolutionForExactDownscale() {
        assertEquals(2, DdImageScaling.sampleSizeFor(4080, 3060))
        assertEquals(DdScaledSize(2000, 1500), DdImageScaling.scaledSizeFor(2040, 1530))
    }

    @Test
    fun tallScreenshotDecodesAtFullSizeBeforeDownscaling() {
        assertEquals(1, DdImageScaling.sampleSizeFor(1080, 2400))
        assertEquals(DdScaledSize(900, 2000), DdImageScaling.scaledSizeFor(1080, 2400))
    }

    @Test
    fun hugeImageSamplesWithinDecodeBudget() {
        assertEquals(4, DdImageScaling.sampleSizeFor(8000, 6000))
        assertEquals(DdScaledSize(2000, 1500), DdImageScaling.scaledSizeFor(2000, 1500))
    }

    @Test
    fun smallImageIsNotUpscaled() {
        assertEquals(1, DdImageScaling.sampleSizeFor(1800, 1200))
        assertEquals(DdScaledSize(1800, 1200), DdImageScaling.scaledSizeFor(1800, 1200))
        assertEquals(DdScaledSize(400, 300), DdImageScaling.scaledSizeFor(400, 300))
    }

    @Test
    fun extremeAspectRatioKeepsAtLeastOnePixel() {
        assertEquals(1, DdImageScaling.sampleSizeFor(20000, 20))
        assertEquals(DdScaledSize(2000, 2), DdImageScaling.scaledSizeFor(20000, 20))
        assertEquals(DdScaledSize(1, 1), DdImageScaling.scaledSizeFor(1, 1))
    }
}
