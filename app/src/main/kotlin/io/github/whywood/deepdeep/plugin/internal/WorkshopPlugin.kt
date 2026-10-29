package io.github.whywood.deepdeep.plugin.internal

import android.content.res.AssetManager
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginSkill
import io.github.whywood.deepdeep.plugin.PluginSkillFile
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.runtime.workshopEditTool
import io.github.whywood.deepdeep.runtime.workshopTools

class WorkshopPlugin(
    private val store: WorkshopStore,
    private val assets: AssetManager,
    strings: PluginStrings,
) : InternalPlugin(strings) {
    override val id: String = "workshop"
    override val icon: DdIcon = DdIcons.Apps
    override val nameRes: Int = R.string.plugins_internal_workshop_name
    override val summaryRes: Int = R.string.plugins_internal_workshop_summary
    override val version: String = "1.0"

    override fun settings(): List<PluginSettingSpec> = listOf(
        PluginSettingSpec.Toggle(
            key = SKILL_ENABLED_KEY,
            titleRes = R.string.plugins_setting_skill_enabled,
            defaultValue = true,
        ),
        PluginSettingSpec.Number(
            key = MAX_TURNS_KEY,
            titleRes = R.string.plugins_setting_max_turns,
            min = 1,
            max = 32,
            defaultValue = 16,
        ),
    )

    override fun tools(binding: SessionBinding): List<CodingTool> =
        workshopTools(store) + workshopEditTool(store)

    override fun skills(): List<PluginSkill> {
        if (!settingBool(SKILL_ENABLED_KEY, true)) return emptyList()
        val files = readWorkshopSkillFiles(assets)
        val skill = files.firstOrNull { it.path == SKILL_FILE_NAME } ?: return emptyList()
        if (skill.content.isBlank()) return emptyList()
        return listOf(
            PluginSkill(
                id = SKILL_ID,
                markdown = skill.content,
                files = files.filter { it.path != SKILL_FILE_NAME },
                legacyOwned = true,
            ),
        )
    }

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
