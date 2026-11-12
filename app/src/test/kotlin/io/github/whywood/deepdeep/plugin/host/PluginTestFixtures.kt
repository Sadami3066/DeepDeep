package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.available
import io.github.whywood.deepdeep.plugin.api.pluginTool
import io.github.whywood.deepdeep.plugin.api.textResult
import io.github.whywood.deepdeep.plugin.host.external.HostPluginManifest
import io.github.whywood.deepdeep.plugin.host.external.hostLocalizedText
import kotlinx.serialization.json.JsonObject
import io.github.whywood.deepdeep.plugin.api.contributions

internal class TestPlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = available()
}

internal fun testDescriptorManifest(
    id: String,
    version: String = "1.0.0",
): io.github.whywood.deepdeep.plugin.host.external.HostPluginManifest = io.github.whywood.deepdeep.plugin.host.external.HostPluginManifest(
    id = id,
    version = version,
    name = hostLocalizedText(mapOf("default" to id, "en" to "name of $id")),
    summary = hostLocalizedText("summary of $id"),
    capabilities = PluginCapability.ALL.toSet(),
)

internal fun testTool(
    name: String,
    execute: suspend (PluginToolCall) -> PluginToolResult = { textResult("ok") },
): PluginTool = pluginTool(
    name = name,
    description = "description of $name",
    parameters = JsonObject(emptyMap()),
    execute = execute,
)

internal fun testDescriptor(
    id: String,
    phase: PluginPhase = PluginPhase.ACTIVE,
    contributions: PluginContributions = PluginContributions.Empty,
): PluginDescriptor = PluginDescriptor(
    manifest = testDescriptorManifest(id),
    phase = phase,
    contributions = contributions,
)
