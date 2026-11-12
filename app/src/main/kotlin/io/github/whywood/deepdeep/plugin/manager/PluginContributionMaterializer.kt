package io.github.whywood.deepdeep.plugin.manager

import android.util.Log
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginExperimentalApi
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PromptOverride
import io.github.whywood.deepdeep.plugin.api.PromptOverridePlugin
import io.github.whywood.deepdeep.plugin.api.available
import io.github.whywood.deepdeep.plugin.api.pluginSkill
import io.github.whywood.deepdeep.plugin.api.pluginSkillFile
import io.github.whywood.deepdeep.plugin.api.unavailable
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import java.io.File
import kotlinx.coroutines.CancellationException

internal data class MaterializedPlugin(
    val contributions: PluginContributions,
    val availability: PluginAvailabilityUi,
    val skills: List<PluginSkill>,
    val failure: String?,
)

internal class PluginContributionMaterializer(
    private val gate: PluginRuntimeRegistry,
) {
    suspend fun refreshAll(): List<PluginDescriptor> = gate.views().mapNotNull { view ->
        gate.applyMaterialized(view.id, materialize(view))
    }

    suspend fun materialize(view: PluginRuntimeView): MaterializedPlugin {
        val plugin = view.plugin
            ?: return MaterializedPlugin(
                contributions = PluginContributions.Empty,
                availability = view.record.lastError
                    ?.takeIf { it.isNotBlank() }
                    ?.let { PluginAvailabilityUi.Unavailable(it) }
                    ?: PluginAvailabilityUi.Available,
                skills = emptyList(),
                failure = null,
            )
        var failure: String? = null
        fun record(error: Throwable) {
            if (failure == null) failure = error.message ?: error.javaClass.simpleName
        }
        val skills = try {
            materializeSkills(view, plugin)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            record(error)
            emptyList()
        }
        val availability = try {
            plugin.availability(view.host)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            record(error)
            unavailable(error.message ?: error.javaClass.simpleName)
        }
        val contributions = if (view.enabled) {
            try {
                val base = plugin.contributions(view.host)
                PluginContributions(
                    tools = base.tools,
                    skills = skills,
                    promptSections = plugin.promptSections(view.host),
                    promptOverride = materializePromptOverride(view, plugin),
                    ui = plugin.ui(view.host),
                    hooks = base.hooks,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                record(error)
                PluginContributions(skills = skills)
            }
        } else {
            PluginContributions(skills = skills)
        }
        return MaterializedPlugin(
            contributions = contributions,
            availability = availability.toUi(),
            skills = skills,
            failure = failure,
        )
    }

    fun materializeSkills(view: PluginRuntimeView, plugin: DeepDeepPlugin): List<PluginSkill> =
        plugin.skills(view.host).map { skill ->
            pluginSkill(
                id = skill.id,
                markdown = skill.markdown,
                files = skill.files.map { file ->
                    val assetPath = file.assetPath
                    if (file.content == null && assetPath != null) {
                        pluginSkillFile(file.path, content = readAsset(view, assetPath), assetPath = null)
                    } else {
                        file
                    }
                },
            )
        }

    private fun materializePromptOverride(view: PluginRuntimeView, plugin: DeepDeepPlugin): PromptOverride? {
        if (PluginCapability.PROMPT !in view.manifest.capabilities) return null
        val provider = plugin as? PromptOverridePlugin ?: return null
        return try {
            provider.promptOverride(view.host)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            promptWarning("prompt override failed for ${view.id}: ${error.message ?: error.javaClass.simpleName}")
            null
        }
    }

    private fun readAsset(view: PluginRuntimeView, path: String): String? = runCatching {
        File(view.host.assetsDir, path).readText()
    }.getOrNull()

    private fun promptWarning(message: String) {
        runCatching { Log.w(PROMPT_TAG, message) }
    }

    private companion object {
        const val PROMPT_TAG = "PluginPrompt"
    }
}
