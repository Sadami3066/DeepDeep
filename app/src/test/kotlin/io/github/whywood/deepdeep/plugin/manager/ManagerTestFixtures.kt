package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

internal fun managerManifest(
    id: String,
    version: String = "1.0.0",
): PluginManifest = PluginManifest(
    id = id,
    version = version,
    name = LocalizedText.Literal(id),
    summary = LocalizedText.Literal("summary"),
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

internal fun managerTool(name: String): PluginTool = PluginTool(
    name = name,
    label = name,
    description = name,
    parameters = JsonObject(emptyMap()),
    execute = { PluginToolResult.Text("ok") },
)

internal fun managerRuntime(
    manifest: PluginManifest,
    plugin: DeepDeepPlugin?,
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
): ManagedPlugin = ManagedPlugin(
    id = manifest.id,
    manifest = manifest,
    plugin = plugin,
    host = testPluginHost(manifest, scope),
    scope = scope,
)

internal open class CountingPlugin(
    var availabilityResult: PluginAvailability = PluginAvailability.Available,
    var contributionsError: Throwable? = null,
    var skillsError: Throwable? = null,
    var enableError: Throwable? = null,
    var closeError: Throwable? = null,
    var closeDelayMillis: Long = 0,
) : DeepDeepPlugin {
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
        return Contributions(tools = listOf(managerTool("counted_tool")))
    }

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        hit("contributionsFor")
        return contributions(host)
    }

    override fun promptSections(host: PluginHost): List<PromptSection> {
        hit("promptSections")
        return listOf(PromptSection("section", "body"))
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
