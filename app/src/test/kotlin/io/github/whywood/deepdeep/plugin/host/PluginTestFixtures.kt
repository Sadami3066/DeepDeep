package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import kotlinx.serialization.json.JsonObject

internal class TestPlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus = PluginStatus.Ready("ready")
}

internal fun testManifest(
    id: String,
    version: String = "1.0.0",
    source: PluginSource = PluginSource.BUILTIN,
    order: Int = 0,
): PluginManifest = PluginManifest(
    id = id,
    version = version,
    source = source,
    order = order,
    name = LocalizedText.Literal(mapOf("default" to id, "en" to "name of $id")),
    summary = LocalizedText.Literal("summary of $id"),
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
    source: PluginSource = PluginSource.BUILTIN,
    contributions: PluginContributions = PluginContributions.Empty,
): PluginDescriptor = PluginDescriptor(
    manifest = testManifest(id, source = source),
    plugin = TestPlugin(),
    source = source,
    phase = phase,
    contributions = contributions,
)
