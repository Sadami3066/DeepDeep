package io.github.whywood.deepdeep.market

import io.github.whywood.deepdeep.plugin.api.MIN_SUPPORTED_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.host.external.PluginVersion

internal fun resolvePluginCompatibility(
    meta: MarketPluginMeta?,
    hostApiLevel: Int = PLUGIN_API_LEVEL,
    hostVersion: String? = null,
): MarketPluginCompatibility {
    meta ?: return MarketPluginCompatibility.Compatible
    if (meta.apiLevel < MIN_SUPPORTED_API_LEVEL) return MarketPluginCompatibility.ApiLevelTooOld
    if (meta.apiLevel > hostApiLevel) return MarketPluginCompatibility.ApiLevelTooNew
    val minimum = meta.minHostVersion?.let(PluginVersion::parse) ?: return MarketPluginCompatibility.Compatible
    val host = hostVersion?.let(PluginVersion::parse) ?: return MarketPluginCompatibility.Compatible
    return if (host.compareCore(minimum) < 0) {
        MarketPluginCompatibility.HostTooOld
    } else {
        MarketPluginCompatibility.Compatible
    }
}
