package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val scheduleService: ScheduleService,
    val questionCenter: QuestionCenter,
    val pluginController: PluginController? = null,
    val extraToolFactories: List<(SessionBinding) -> List<CodingTool>> = emptyList(),
)

fun appTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = buildList {
    addAll(scheduleTools(cwd, context, binding))
    add(askUserQuestionTool(context.questionCenter, binding))
    pluginSetupTool(context.pluginController)?.let(::add)
    context.extraToolFactories.forEach { factory -> addAll(factory(binding)) }
}
