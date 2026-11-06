package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import java.io.File
import kotlinx.coroutines.CancellationException

internal data class MaterializedPlugin(
    val contributions: PluginContributions,
    val availability: PluginAvailabilityUi,
    val status: PluginStatusUi?,
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
                contributions = PluginContributions(),
                availability = view.record.lastError
                    ?.takeIf { it.isNotBlank() }
                    ?.let { PluginAvailabilityUi.Unavailable(it) }
                    ?: PluginAvailabilityUi.Available,
                status = null,
                skills = emptyList(),
                failure = null,
            )
        var failure: String? = null
        fun record(error: Throwable) {
            if (failure == null) failure = error.message ?: error.javaClass.simpleName
        }
        val settings = try {
            plugin.settings()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            record(error)
            emptyList()
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
            PluginAvailability.Unavailable(error.message ?: error.javaClass.simpleName)
        }
        val installed = view.installed || !view.manifest.installable
        val available = availability is PluginAvailability.Available
        val status = if (view.enabled && installed && available) {
            try {
                plugin.status(view.host).toUi()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                record(error)
                null
            }
        } else {
            null
        }
        val contributions = if (view.enabled && installed) {
            try {
                val statusApi = status?.toApi() ?: PluginStatus.NotReady("")
                val base = plugin.contributions(view.host)
                PluginContributions(
                    tools = base.tools,
                    skills = skills,
                    promptSections = plugin.promptSections(view.host),
                    settings = settings,
                    actions = if (status != null) plugin.actions(view.host, statusApi) else emptyList(),
                    detailRows = plugin.detailRows(view.host, statusApi),
                    ui = plugin.ui(view.host),
                    hooks = base.hooks,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                record(error)
                PluginContributions(skills = skills, settings = settings)
            }
        } else {
            PluginContributions(skills = skills, settings = settings)
        }
        return MaterializedPlugin(
            contributions = contributions,
            availability = availability.toUi(),
            status = status,
            skills = skills,
            failure = failure,
        )
    }

    fun materializeSkills(view: PluginRuntimeView, plugin: io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin): List<PluginSkill> =
        plugin.skills(view.host).map { skill ->
            skill.copy(
                files = skill.files.map { file ->
                    val assetPath = file.assetPath
                    if (file.content == null && assetPath != null) {
                        file.copy(content = readAsset(view, assetPath))
                    } else {
                        file
                    }
                },
            )
        }

    private fun readAsset(view: PluginRuntimeView, path: String): String? = runCatching {
        File(view.host.assetsDir, path).readText()
    }.getOrNull()
}
