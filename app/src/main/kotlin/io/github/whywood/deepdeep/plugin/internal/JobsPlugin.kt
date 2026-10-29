package io.github.whywood.deepdeep.plugin.internal

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.jobTools

class JobsPlugin(
    private val jobRegistry: JobRegistry,
    strings: PluginStrings,
) : InternalPlugin(strings) {
    override val id: String = "jobs"
    override val icon: DdIcon = DdIcons.Clock
    override val nameRes: Int = R.string.plugins_internal_jobs_name
    override val summaryRes: Int = R.string.plugins_internal_jobs_summary
    override val version: String = "1.0"

    override fun settings(): List<PluginSettingSpec> = listOf(
        PluginSettingSpec.Number(
            key = MAX_RUNNING_KEY,
            titleRes = R.string.plugins_setting_max_running,
            min = 1,
            max = 20,
            defaultValue = 10,
        ),
    )

    override fun tools(binding: SessionBinding): List<CodingTool> =
        jobTools(binding, jobRegistry)

    private companion object {
        const val MAX_RUNNING_KEY = "max_running"
    }
}
