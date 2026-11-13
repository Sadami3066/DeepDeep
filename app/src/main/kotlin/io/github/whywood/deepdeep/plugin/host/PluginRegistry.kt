package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.ComposerAction
import io.github.whywood.deepdeep.plugin.api.PluginExperimentalApi
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PromptOverride
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.theme.PluginThemeSource

internal enum class PluginPhase {
    INSTALLED,
    ACTIVE,
    DEGRADED,
    FAILED,
    DISABLED;

    val contributes: Boolean
        get() = this == ACTIVE || this == DEGRADED

    companion object {
        fun derive(record: PluginRecord, facts: PluginRuntimeFacts): PluginPhase = when {
            !record.enabled -> DISABLED
            facts.failed != null -> FAILED
            !facts.loaded -> INSTALLED
            facts.degraded != null -> DEGRADED
            else -> ACTIVE
        }
    }
}

@OptIn(PluginExperimentalApi::class)
internal data class PluginContributions(
    val tools: List<PluginTool> = emptyList(),
    val skills: List<PluginSkill> = emptyList(),
    val promptSections: List<PromptSection> = emptyList(),
    val promptOverride: PromptOverride? = null,
    val ui: PluginUiContributions = PluginUiContributions.None,
    val hooks: PluginHooks = PluginHooks.None,
) {
    companion object {
        val Empty: PluginContributions = PluginContributions()
    }
}

internal data class PluginDescriptor(
    val manifest: PluginManifest,
    val phase: PluginPhase = PluginPhase.INSTALLED,
    val error: String? = null,
    val contributions: PluginContributions = PluginContributions.Empty,
    val themeSource: PluginThemeSource? = null,
) {
    val id: String get() = manifest.id
}

internal sealed interface PluginRegistration {
    data object Registered : PluginRegistration

    data class Rejected(val pluginId: String, val reason: String) : PluginRegistration
}

internal enum class PluginConflictKind {
    TOOL,
    SKILL,
    PROMPT_SECTION,
}

internal data class PluginConflict(
    val kind: PluginConflictKind,
    val id: String,
    val pluginIds: List<String>,
)

internal data class PluginToolEntry(val pluginId: String, val tool: PluginTool)

internal data class PluginSkillEntry(val pluginId: String, val skill: PluginSkill)

internal data class PluginPromptSectionEntry(val pluginId: String, val section: PromptSection)

internal data class PluginPromptOverrideEntry(
    val pluginId: String,
    val priority: Int,
    val override: PromptOverride,
)

internal data class PluginUiEntry(val pluginId: String, val contributions: PluginUiContributions)

internal data class PluginHooksEntry(val pluginId: String, val hooks: PluginHooks)

internal data class PluginBindingEntry(val pluginId: String, val binding: SlotBinding)

internal data class PluginPageEntry(val pluginId: String, val page: PluginPage)

internal data class PluginComposerActionEntry(val pluginId: String, val action: ComposerAction)

internal class PluginRegistrySnapshot(
    val version: Long,
    val plugins: List<PluginDescriptor>,
) {
    private val byId: Map<String, PluginDescriptor> = plugins.associateBy { it.id }

    val tools: List<PluginToolEntry>
    val skills: List<PluginSkillEntry>
    val promptSections: List<PluginPromptSectionEntry>
    val promptOverrides: List<PluginPromptOverrideEntry>
    val ui: List<PluginUiEntry>
    val hooks: List<PluginHooksEntry>
    val bindings: List<PluginBindingEntry>
    val pages: List<PluginPageEntry>
    val composerActions: List<PluginComposerActionEntry>
    val themeSources: List<PluginThemeSource>
    val conflicts: List<PluginConflict>

    init {
        val contributing = plugins.filter { it.phase.contributes }
        val toolOwners = linkedMapOf<String, MutableList<String>>()
        tools = buildList {
            contributing.forEach { descriptor ->
                descriptor.contributions.tools.forEach { tool ->
                    val owners = toolOwners.getOrPut(tool.name) { mutableListOf() }
                    if (owners.isEmpty()) add(PluginToolEntry(descriptor.id, tool))
                    owners += descriptor.id
                }
            }
        }
        val skillOwners = linkedMapOf<String, MutableList<String>>()
        skills = buildList {
            contributing.forEach { descriptor ->
                descriptor.contributions.skills.forEach { skill ->
                    val owners = skillOwners.getOrPut(skill.id) { mutableListOf() }
                    if (owners.isEmpty()) add(PluginSkillEntry(descriptor.id, skill))
                    owners += descriptor.id
                }
            }
        }
        val sectionOwners = linkedMapOf<String, MutableList<String>>()
        promptSections = buildList {
            contributing.forEach { descriptor ->
                descriptor.contributions.promptSections.forEach { section ->
                    val owners = sectionOwners.getOrPut(section.id) { mutableListOf() }
                    if (owners.isEmpty()) add(PluginPromptSectionEntry(descriptor.id, section))
                    owners += descriptor.id
                }
            }
        }
        promptOverrides = contributing.mapNotNull { descriptor ->
            descriptor.contributions.promptOverride?.let { override ->
                PluginPromptOverrideEntry(descriptor.id, override.priority, override)
            }
        }
        ui = contributing.map { PluginUiEntry(it.id, it.contributions.ui) }
        hooks = contributing.map { PluginHooksEntry(it.id, it.contributions.hooks) }
        bindings = ui.flatMap { entry -> entry.contributions.bindings.map { PluginBindingEntry(entry.pluginId, it) } }
        pages = ui.flatMap { entry -> entry.contributions.pages.map { PluginPageEntry(entry.pluginId, it) } }
        composerActions = ui.flatMap { entry ->
            entry.contributions.composerActions.map { PluginComposerActionEntry(entry.pluginId, it) }
        }
        themeSources = plugins.mapNotNull { it.themeSource }
        conflicts = buildList {
            collectConflicts(PluginConflictKind.TOOL, toolOwners, this)
            collectConflicts(PluginConflictKind.SKILL, skillOwners, this)
            collectConflicts(PluginConflictKind.PROMPT_SECTION, sectionOwners, this)
        }
    }

    fun plugin(pluginId: String): PluginDescriptor? = byId[pluginId]

    fun contributionsOf(pluginId: String): PluginContributions? = byId[pluginId]?.contributions

    private fun collectConflicts(
        kind: PluginConflictKind,
        owners: Map<String, List<String>>,
        target: MutableList<PluginConflict>,
    ) {
        owners.forEach { (id, pluginIds) ->
            val distinct = pluginIds.distinct()
            if (distinct.size > 1) target += PluginConflict(kind, id, distinct)
        }
    }
}

internal class PluginRegistry {
    private val lock = Any()
    private var version = 0L
    private var descriptors: List<PluginDescriptor> = emptyList()
    private var cachedSnapshot: PluginRegistrySnapshot? = null

    fun register(descriptor: PluginDescriptor): PluginRegistration = synchronized(lock) {
        if (descriptors.any { it.id == descriptor.id }) {
            PluginRegistration.Rejected(descriptor.id, "plugin ${descriptor.id} is already registered")
        } else {
            descriptors = descriptors + descriptor
            bumpVersion()
            PluginRegistration.Registered
        }
    }

    fun unregister(pluginId: String): Boolean = synchronized(lock) {
        if (descriptors.none { it.id == pluginId }) {
            false
        } else {
            descriptors = descriptors.filterNot { it.id == pluginId }
            bumpVersion()
            true
        }
    }

    fun update(pluginId: String, transform: (PluginDescriptor) -> PluginDescriptor): Boolean = synchronized(lock) {
        val index = descriptors.indexOfFirst { it.id == pluginId }
        if (index < 0) {
            false
        } else {
            val updated = transform(descriptors[index])
            require(updated.id == pluginId) { "plugin id must not change during update" }
            descriptors = descriptors.toMutableList().also { it[index] = updated }
            bumpVersion()
            true
        }
    }

    fun descriptor(pluginId: String): PluginDescriptor? = synchronized(lock) {
        descriptors.firstOrNull { it.id == pluginId }
    }

    fun snapshot(): PluginRegistrySnapshot = synchronized(lock) {
        cachedSnapshot ?: PluginRegistrySnapshot(version, descriptors).also { cachedSnapshot = it }
    }

    private fun bumpVersion() {
        version += 1
        cachedSnapshot = null
    }
}
