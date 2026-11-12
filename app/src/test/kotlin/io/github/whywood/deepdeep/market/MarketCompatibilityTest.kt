package io.github.whywood.deepdeep.market

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCompatibilityTest {
    private fun meta(apiLevel: Int = 6, minHostVersion: String? = null): MarketPluginMeta = MarketPluginMeta(
        apiLevel = apiLevel,
        minHostVersion = minHostVersion,
        publisherKeyId = "ed25519:test",
    )

    @Test
    fun currentApiLevelIsCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(), hostApiLevel = 6, hostVersion = "1.0.4"),
        )
    }

    @Test
    fun missingMetaIsCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(null, hostApiLevel = 6, hostVersion = "1.0.4"),
        )
    }

    @Test
    fun retiredApiLevelIsRejected() {
        assertEquals(
            MarketPluginCompatibility.ApiLevelTooOld,
            resolvePluginCompatibility(meta(apiLevel = 5), hostApiLevel = 6, hostVersion = "1.0.4"),
        )
    }

    @Test
    fun newerApiLevelRequiresAppUpdate() {
        assertEquals(
            MarketPluginCompatibility.ApiLevelTooNew,
            resolvePluginCompatibility(meta(apiLevel = 7), hostApiLevel = 6, hostVersion = "1.0.4"),
        )
    }

    @Test
    fun newerHostVersionRequiresAppUpdate() {
        assertEquals(
            MarketPluginCompatibility.HostTooOld,
            resolvePluginCompatibility(meta(minHostVersion = "1.0.5"), hostApiLevel = 6, hostVersion = "1.0.4"),
        )
    }

    @Test
    fun matchingHostVersionIgnoresSuffix() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(minHostVersion = "1.0.4"), hostApiLevel = 6, hostVersion = "1.0.4-dev"),
        )
    }

    @Test
    fun invalidHostVersionFallsBackToCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(minHostVersion = "1.0.5"), hostApiLevel = 6, hostVersion = "dev"),
        )
    }
}
