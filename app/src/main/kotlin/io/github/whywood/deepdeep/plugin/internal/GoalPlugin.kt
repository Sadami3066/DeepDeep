package io.github.whywood.deepdeep.plugin.internal

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.goalTools

class GoalPlugin(
    private val goalService: GoalService,
    strings: PluginStrings,
) : InternalPlugin(strings) {
    override val id: String = "goal"
    override val icon: DdIcon = DdIcons.Target
    override val nameRes: Int = R.string.plugins_internal_goal_name
    override val summaryRes: Int = R.string.plugins_internal_goal_summary
    override val version: String = "1.0"

    override fun settings(): List<PluginSettingSpec> = listOf(
        PluginSettingSpec.Number(
            key = MAX_ROUNDS_KEY,
            titleRes = R.string.plugins_setting_max_rounds,
            min = 0,
            max = 50,
            defaultValue = 0,
        ),
    )

    override fun tools(binding: SessionBinding): List<CodingTool> =
        goalTools(binding, goalService, defaultMaxRounds = { settingInt(MAX_ROUNDS_KEY, 0) })

    private companion object {
        const val MAX_ROUNDS_KEY = "max_rounds"
    }
}
