package io.github.whywood.deepdeep.plugin.internal

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.timerTools

class TimerPlugin(
    private val timerService: TimerService,
    strings: PluginStrings,
) : InternalPlugin(strings) {
    override val id: String = "timer"
    override val icon: DdIcon = DdIcons.Clock
    override val nameRes: Int = R.string.plugins_internal_timer_name
    override val summaryRes: Int = R.string.plugins_internal_timer_summary
    override val version: String = "1.0"

    override fun tools(binding: SessionBinding): List<CodingTool> =
        timerTools(timerService, binding)
}
