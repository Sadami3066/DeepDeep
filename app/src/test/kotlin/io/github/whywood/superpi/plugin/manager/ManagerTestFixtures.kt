package io.github.whywood.superpi.plugin.manager

import io.github.whywood.superpi.plugin.api.Contributions
import io.github.whywood.superpi.plugin.api.SuperPIPlugin
import io.github.whywood.superpi.plugin.api.LocalizedText
import io.github.whywood.superpi.plugin.api.PluginAvailability
import io.github.whywood.superpi.plugin.api.PluginCapability
import io.github.whywood.superpi.plugin.api.PluginHost
import io.github.whywood.superpi.plugin.api.PluginManifest
import io.github.whywood.superpi.plugin.api.PluginSessionInfo
import io.github.whywood.superpi.plugin.api.PluginSkill
import io.github.whywood.superpi.plugin.api.PluginTool
import io.github.whywood.superpi.plugin.api.PluginToolResult
import io.github.whywood.superpi.plugin.api.PluginUiContributions
import io.github.whywood.superpi.plugin.api.PromptSection
import io.github.whywood.superpi.plugin.host.PluginRecord
import io.github.whywood.superpi.plugin.host.testPluginHost
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import io.github.whywood.superpi.plugin.api.available
import io.github.whywood.superpi.plugin.api.contributions
import io.github.whywood.superpi.plugin.api.pluginTool
import io.github.whywood.superpi.plugin.api.promptSection
import io.github.whywood.superpi.plugin.api.textResult
import io.github.whywood.superpi.plugin.host.external.HostPluginManifest
import io.github.whywood.superpi.plugin.host.external.hostLocalizedText

internal fun managerManifest(
    id: String,
    version: String = "1.0.0",
): HostPluginManifest = HostPluginManifest(
    id = id,
    version = version,
    name = hostLocalizedText(id),
    summary = hostLocalizedText("summary"),
    capabilities = PluginCapability.ALL.toSet(),
)

internal fun managerRecord(
    id: String,
    enabled: Boolean = true,
    installedVersion: String? = "1.0.0",
    pendingVersion: String? = null,
    lastError: String? = null,
): PluginRecord = PluginRecord(
    id = id,
    enabled = enabled,
    installedVersion = installedVersion,
    pendingVersion = pendingVersion,
    lastError = lastError,
)

internal fun managerTool(name: String): PluginTool = pluginTool(
    name = name,
    label = name,
    description = name,
    parameters = JsonObject(emptyMap()),
    execute = { textResult("ok") },
)

internal fun managerRuntime(
    manifest: PluginManifest,
    plugin: SuperPIPlugin?,
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
): ManagedPlugin = ManagedPlugin(
    id = manifest.id,
    manifest = manifest,
    plugin = plugin,
    host = testPluginHost(manifest, scope),
    scope = scope,
)

internal open class CountingPlugin(
    var availabilityResult: PluginAvailability = available(),
    var contributionsError: Throwable? = null,
    var skillsError: Throwable? = null,
    var enableError: Throwable? = null,
    var closeError: Throwable? = null,
    var closeDelayMillis: Long = 0,
) : SuperPIPlugin {
    private val hits = ConcurrentHashMap<String, Int>()

    val events = mutableListOf<String>()

    fun count(name: String): Int = hits[name] ?: 0

    private fun hit(name: String) {
        hits.merge(name, 1) { current, _ -> current + 1 }
    }

    override suspend fun availability(host: PluginHost): PluginAvailability {
        hit("availability")
        return availabilityResult
    }

    override fun skills(host: PluginHost): List<PluginSkill> {
        hit("skills")
        skillsError?.let { throw it }
        return emptyList()
    }

    override fun contributions(host: PluginHost): Contributions {
        hit("contributions")
        contributionsError?.let { throw it }
        return contributions(tools = listOf(managerTool("counted_tool")))
    }

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        hit("contributionsFor")
        return contributions(host)
    }

    override fun promptSections(host: PluginHost): List<PromptSection> {
        hit("promptSections")
        return listOf(promptSection("section", "body"))
    }

    override fun ui(host: PluginHost): PluginUiContributions {
        hit("ui")
        return PluginUiContributions.None
    }

    override suspend fun onEnable(host: PluginHost) {
        hit("onEnable")
        events += "enable"
        enableError?.let { throw it }
    }

    override suspend fun onDisable(host: PluginHost) {
        hit("onDisable")
        events += "disable"
    }

    override suspend fun close() {
        hit("close")
        events += "close"
        if (closeDelayMillis > 0) delay(closeDelayMillis)
        closeError?.let { throw it }
    }
}
