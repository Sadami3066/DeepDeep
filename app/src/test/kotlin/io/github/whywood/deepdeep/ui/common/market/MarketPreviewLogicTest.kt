package io.github.whywood.deepdeep.ui.common.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPreviewLogicTest {
    @Test
    fun previewUrlRequiresHttps() {
        assertNotNull(previewHttpUrl("https://example.com/a.jpg"))
        assertNull(previewHttpUrl("http://example.com/a.jpg"))
        assertNull(previewHttpUrl("ftp://example.com/a.jpg"))
        assertNull(previewHttpUrl("example.com/a.jpg"))
        assertNull(previewHttpUrl("  "))
        assertNull(previewHttpUrl(""))
    }

    @Test
    fun sampleSizeCapsLongEdgeBelowTwiceTarget() {
        assertEquals(2, previewSampleSize(1080, 2400))
        assertEquals(2, previewSampleSize(2400, 1080))
        assertEquals(2, previewSampleSize(1920, 1080))
        assertEquals(1, previewSampleSize(960, 540))
        assertEquals(1, previewSampleSize(720, 1600))
        assertEquals(4, previewSampleSize(1080, 2400, maxEdge = 480))
    }

    @Test
    fun sampleSizeKeepsDegenerateInputsAtOne() {
        assertEquals(1, previewSampleSize(0, 100))
        assertEquals(1, previewSampleSize(100, -1))
        assertEquals(1, previewSampleSize(100, 100, maxEdge = 0))
    }

    @Test
    fun absentContentTypeIsAccepted() {
        assertTrue(isPreviewImageType(null))
    }

    @Test
    fun imageContentTypesAreAccepted() {
        assertTrue(isPreviewImageType("image/jpeg"))
        assertTrue(isPreviewImageType("image/jpeg; charset=binary"))
        assertTrue(isPreviewImageType("IMAGE/PNG"))
        assertTrue(isPreviewImageType("  image/webp  "))
    }

    @Test
    fun otherContentTypesAreRejected() {
        assertFalse(isPreviewImageType("text/html"))
        assertFalse(isPreviewImageType("application/octet-stream"))
        assertFalse(isPreviewImageType("image"))
        assertFalse(isPreviewImageType(""))
    }

    @Test
    fun failureWithoutTimestampCanRetry() {
        assertTrue(previewFailureRetryable(null, 0L))
        assertTrue(previewFailureRetryable(null, 1234L))
    }

    @Test
    fun failureCoolsDownBeforeRetry() {
        val now = 1_000_000L
        assertFalse(previewFailureRetryable(now, now, cooldownMs = 60_000L))
        assertFalse(previewFailureRetryable(now - 59_999L, now, cooldownMs = 60_000L))
        assertTrue(previewFailureRetryable(now - 60_000L, now, cooldownMs = 60_000L))
        assertTrue(previewFailureRetryable(now - 120_000L, now, cooldownMs = 60_000L))
    }

    @Test
    fun defaultCooldownIsFiveMinutes() {
        assertEquals(5 * 60 * 1000L, PREVIEW_FAIL_COOLDOWN_MS)
    }
}
