package io.github.whywood.deepdeep.plugin.internal

import android.content.res.AssetManager
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginSkillFile
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.runtime.workshopEditTool
import io.github.whywood.deepdeep.runtime.workshopTools

class WorkshopPlugin(
    private val store: WorkshopStore,
    private val assets: AssetManager,
    strings: PluginStrings,
) : DeepDeepPlugin {
    @Volatile
    private var values: Map<String, String> = emptyMap()

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus =
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugins_status_builtin)))

    override fun settings(): List<SettingSpec> = listOf(
        SettingSpec.Toggle(
            key = SKILL_ENABLED_KEY,
            title = LocalizedText.Resource(R.string.plugins_setting_skill_enabled),
            defaultValue = true,
        ),
        SettingSpec.Number(
            key = MAX_TURNS_KEY,
            title = LocalizedText.Resource(R.string.plugins_setting_max_turns),
            min = 1,
            max = 32,
            defaultValue = 16,
        ),
    )

    override suspend fun onSettingsChanged(host: PluginHost, values: Map<String, String>) {
        this.values = values
    }

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        val live = session ?: return Contributions()
        val binding = SessionBinding(live.supportsChatDelivery) {
            live.snapshot()?.let { BoundSession(it.sessionId, it.file, it.cwd) }
        }
        val tools = workshopTools(store, binding.supportsChatDelivery) + workshopEditTool(store)
        return Contributions(tools = CodingToolPluginAdapter.toPluginTools(tools))
    }

    override fun skills(host: PluginHost): List<PluginSkill> {
        if (!settingBool(SKILL_ENABLED_KEY, true)) return emptyList()
        val files = readWorkshopSkillFiles(assets)
        val skill = files.firstOrNull { it.path == SKILL_FILE_NAME } ?: return emptyList()
        val markdown = skill.content?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(
            PluginSkill(
                id = SKILL_ID,
                markdown = markdown,
                files = files.filter { it.path != SKILL_FILE_NAME },
                legacyOwned = true,
            ),
        )
    }

    private fun settingBool(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    companion object {
        const val SKILL_ID = "workshop"
        private const val SKILL_ENABLED_KEY = "skill_enabled"
        private const val MAX_TURNS_KEY = "max_turns"
    }
}

internal fun readWorkshopSkillFiles(assets: AssetManager): List<PluginSkillFile> =
    collectWorkshopAssetFiles(
        root = "skills/workshop",
        list = { path -> runCatching { assets.list(path)?.toList() }.getOrNull() },
        read = { path -> runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull() },
    )

internal fun collectWorkshopAssetFiles(
    root: String,
    list: (String) -> List<String>?,
    read: (String) -> String?,
): List<PluginSkillFile> {
    val files = mutableListOf<PluginSkillFile>()
    fun walk(path: String) {
        val children = list(path)
        if (children.isNullOrEmpty()) {
            val content = read(path) ?: return
            val relative = path.removePrefix(root).trimStart('/')
            if (relative.isNotEmpty()) files += PluginSkillFile(relative, content)
            return
        }
        children.sorted().forEach { child -> walk("$path/$child") }
    }
    walk(root)
    return files.sortedWith(compareBy({ it.path != SKILL_FILE_NAME }, { it.path }))
}

private const val SKILL_FILE_NAME = "SKILL.md"
