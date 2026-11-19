package io.github.whywood.superpi.market

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCompatibilityTest {
    private fun meta(apiLevel: Int = 7, minHostVersion: String? = null): MarketPluginMeta = MarketPluginMeta(
        apiLevel = apiLevel,
        minHostVersion = minHostVersion,
        publisherKeyId = "ed25519:test",
    )

    @Test
    fun currentApiLevelIsCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(), hostApiLevel = 7, hostVersion = "2.0.0"),
        )
    }

    @Test
    fun missingMetaIsCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(null, hostApiLevel = 7, hostVersion = "2.0.0"),
        )
    }

    @Test
    fun retiredApiLevelIsRejected() {
        assertEquals(
            MarketPluginCompatibility.ApiLevelTooOld,
            resolvePluginCompatibility(meta(apiLevel = 6), hostApiLevel = 7, hostVersion = "2.0.0"),
        )
    }

    @Test
    fun newerApiLevelRequiresAppUpdate() {
        assertEquals(
            MarketPluginCompatibility.ApiLevelTooNew,
            resolvePluginCompatibility(meta(apiLevel = 8), hostApiLevel = 7, hostVersion = "2.0.0"),
        )
    }

    @Test
    fun newerHostVersionRequiresAppUpdate() {
        assertEquals(
            MarketPluginCompatibility.HostTooOld,
            resolvePluginCompatibility(meta(minHostVersion = "2.0.1"), hostApiLevel = 7, hostVersion = "2.0.0"),
        )
    }

    @Test
    fun matchingHostVersionIgnoresSuffix() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(minHostVersion = "2.0.0"), hostApiLevel = 7, hostVersion = "2.0.0-dev"),
        )
    }

    @Test
    fun invalidHostVersionFallsBackToCompatible() {
        assertEquals(
            MarketPluginCompatibility.Compatible,
            resolvePluginCompatibility(meta(minHostVersion = "2.0.1"), hostApiLevel = 7, hostVersion = "dev"),
        )
    }
}
