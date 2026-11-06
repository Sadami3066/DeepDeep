package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import kotlinx.serialization.json.JsonObject

internal class TestPlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available
}

internal fun testDescriptorManifest(
    id: String,
    version: String = "1.0.0",
): PluginManifest = PluginManifest(
    id = id,
    version = version,
    name = LocalizedText.Literal(mapOf("default" to id, "en" to "name of $id")),
    summary = LocalizedText.Literal("summary of $id"),
    capabilities = PluginCapability.entries.map { it.wire }.toSet(),
)

internal fun testTool(
    name: String,
    execute: suspend (PluginToolCall) -> PluginToolResult = { PluginToolResult.Text("ok") },
): PluginTool = PluginTool(
    name = name,
    label = name,
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
