package io.github.whywood.deepdeep.plugin.internal

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginStrings
import okio.Path.Companion.toPath
import io.github.whywood.deepdeep.runtime.ScheduleService
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.scheduleTools

class SchedulePlugin(
    private val scheduleService: ScheduleService,
    strings: PluginStrings,
) : InternalPlugin(strings) {
    override val id: String = "schedule"
    override val icon: DdIcon = DdIcons.Clock
    override val nameRes: Int = R.string.plugins_internal_schedule_name
    override val summaryRes: Int = R.string.plugins_internal_schedule_summary
    override val version: String = "1.0"

    override fun tools(binding: SessionBinding): List<CodingTool> =
        scheduleTools(".".toPath(), scheduleService, binding)
}
