package io.github.whywood.deepdeep.ui.settings

import io.github.whywood.deepdeep.data.ApkMirrors
import io.github.whywood.deepdeep.data.DevBundleCustomization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevBundleCustomizationStoreTest {
    @Test
    fun roundTripsExtrasOverlaysAndMirrors() {
        val customization = DevBundleCustomization(
            extras = mapOf("base" to listOf("tesseract-ocr", "qpdf")),
            overlays = mapOf("base" to listOf("dev")),
            mirrors = listOf("tuna", "tencent"),
        )
        assertEquals(customization, decodeCustomization(encodeCustomization(customization)))
    }

    @Test
    fun dropsUnknownPackagesBundlesAndMirrors() {
        val decoded = decodeCustomization(
            """
            {
              "extras": {"base": ["tesseract-ocr", "not-a-package", "tesseract-ocr"]},
              "overlays": {"base": ["dev", "not-a-bundle"]},
              "mirrors": ["tuna", "not-a-mirror", "tuna"]
            }
            """.trimIndent(),
        )
        assertEquals(mapOf("base" to listOf("tesseract-ocr")), decoded.extras)
        assertEquals(mapOf("base" to listOf("dev")), decoded.overlays)
        assertEquals(listOf("tuna"), decoded.mirrors)
    }

    @Test
    fun fallsBackToEmptyCustomization() {
        assertEquals(DevBundleCustomization(), decodeCustomization(null))
        assertEquals(DevBundleCustomization(), decodeCustomization(""))
        assertEquals(DevBundleCustomization(), decodeCustomization("not json"))
        assertEquals(DevBundleCustomization(), decodeCustomization("{}"))
    }

    @Test
    fun mirrorIdsDefaultToAliyunWhenUnset() {
        assertEquals(ApkMirrors.defaultIds(), DevBundleCustomization().mirrorIds())
        assertTrue(DevBundleCustomization(mirrors = listOf("tuna")).mirrorIds() == listOf("tuna"))
    }
}
