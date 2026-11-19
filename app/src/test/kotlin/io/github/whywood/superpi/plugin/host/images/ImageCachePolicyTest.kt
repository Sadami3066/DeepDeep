package io.github.whywood.superpi.plugin.host.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCachePolicyTest {
    @Test
    fun rejectsNonHttpUrlsAndBlank() {
        assertNull(ImageCachePolicy.normalize(""))
        assertNull(ImageCachePolicy.normalize("file:///tmp/a.jpg"))
        assertNull(ImageCachePolicy.normalize("content://media/1"))
        assertNull(ImageCachePolicy.normalize("data:image/png;base64,AAAA"))
        assertNull(ImageCachePolicy.normalize("https://" + "a".repeat(2100)))
    }

    @Test
    fun keepsHttpsAndStripsFragment() {
        assertEquals(
            "https://store.is.autonavi.com/showpic/1?type=pic",
            ImageCachePolicy.normalize(" https://store.is.autonavi.com/showpic/1?type=pic#frag "),
        )
    }

    @Test
    fun triesHttpsBeforeHttp() {
        assertEquals(
            listOf("https://a.com/1.jpg", "http://a.com/1.jpg"),
            ImageCachePolicy.candidates("http://a.com/1.jpg"),
        )
        assertEquals(listOf("https://a.com/1.jpg"), ImageCachePolicy.candidates("https://a.com/1.jpg"))
    }

    @Test
    fun memoryKeyIncludesEdgeAndHashesUrl() {
        val first = ImageCachePolicy.memoryKey("https://a.com/1.jpg", 720)
        assertEquals(first, ImageCachePolicy.memoryKey("https://a.com/1.jpg", 720))
        assertTrue(first != ImageCachePolicy.memoryKey("https://a.com/1.jpg", 480))
        assertTrue(first.endsWith(":720"))
        assertEquals(64, first.substringBefore(':').length)
    }

    @Test
    fun sampleSizeCapsLongEdgeAndPixelCount() {
        assertEquals(1, ImageCachePolicy.sampleSize(640, 360, 720))
        assertEquals(2, ImageCachePolicy.sampleSize(2000, 2000, 720))
        assertTrue(ImageCachePolicy.sampleSize(8000, 8000, 720) <= 16)
        assertTrue(ImageCachePolicy.sampleSize(4000, 4000, 720) >= 4)
    }
}
