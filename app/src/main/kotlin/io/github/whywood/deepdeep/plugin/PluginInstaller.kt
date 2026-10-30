package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class PluginInstaller(
    private val recordStore: PluginRecordStore,
    private val skillStore: PluginSkillStore,
    private val strings: PluginStrings,
    private val state: MutableStateFlow<PluginsUiState>,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit,
    private val notifyInstallFinished: (label: String, success: Boolean, error: String?) -> Unit,
    private val onContributionsPending: () -> Unit,
    private val onRefreshStatuses: suspend () -> Unit,
) {
    suspend fun install(plugin: OfficialPlugin) {
        val availability = runCatching { plugin.availability() }.getOrElse { error ->
            PluginAvailability.Unavailable(error.message ?: error.javaClass.simpleName)
        }
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
        val previous = recordStore.load().records[plugin.id]
        val previousSkills = previous?.skillIds.orEmpty()
        val enabled = previous?.enabled ?: true
        val name = strings.get(plugin.nameRes)
        val title = strings.format(R.string.plugins_operation_install, name)
        setOperation(plugin.id, title, strings.get(R.string.plugins_stage_preparing), null)
        notifyInstall(name, strings.get(R.string.plugins_stage_preparing), null)
        try {
            plugin.install(progressAdapter(plugin, title))
            val skills = plugin.skills()
            recordStore.upsert(
                PluginRecord(
                    id = plugin.id,
                    version = plugin.version,
                    installedAt = System.currentTimeMillis(),
                    skillIds = skills.map { it.id },
                    enabled = enabled,
                ),
            )
            val skillError = runCatching {
                skillStore.sync(plugin.id, skills, previousSkills, enabled)
            }.exceptionOrNull()
            if (skillError != null) {
                state.update { it.copy(error = errorMessage(skillError)) }
            }
            onContributionsPending()
            notifyInstallFinished(name, true, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { plugin.recover() }
            if (previous == null) {
                runCatching { plugin.uninstall() }
                runCatching { skillStore.remove(plugin.id, previousSkills) }
            }
            notifyInstallFinished(name, false, errorMessage(error))
            throw error
        } finally {
            state.update { it.copy(operation = null) }
        }
    }

    suspend fun uninstall(plugin: OfficialPlugin) {
        val record = recordStore.load().records[plugin.id]
        plugin.uninstall()
        val skillIds = record?.skillIds ?: plugin.skills().map { it.id }
        runCatching { skillStore.remove(plugin.id, skillIds) }
        recordStore.remove(plugin.id)
        onRefreshStatuses()
        onContributionsPending()
    }

    private fun progressAdapter(plugin: OfficialPlugin, title: String): PluginProgress = object : PluginProgress {
        override fun report(percent: Int?, labelRes: Int) {
            val stage = strings.get(labelRes)
            state.update { current ->
                current.copy(
                    operation = PluginOperation(plugin.id, title, stage, percent),
                    interaction = current.interaction
                        ?.takeIf { it.pluginId == plugin.id && !it.confirmInstall }
                        ?.copy(progress = percent),
                )
            }
            notifyInstall(strings.get(plugin.nameRes), stage, percent)
        }
    }

    private fun setOperation(pluginId: String, title: String, stage: String?, progress: Int?) {
        state.update { it.copy(operation = PluginOperation(pluginId, title, stage, progress)) }
    }

    private fun errorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
