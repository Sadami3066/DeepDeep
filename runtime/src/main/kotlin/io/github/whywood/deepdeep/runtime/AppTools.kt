package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val scheduleService: ScheduleService,
    val questionCenter: QuestionCenter,
    val extraToolFactories: List<(SessionBinding) -> List<CodingTool>> = emptyList(),
)

fun appTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = buildList {
    addAll(scheduleTools(cwd, context, binding))
    add(askUserQuestionTool(context.questionCenter, binding))
    context.extraToolFactories.forEach { factory -> addAll(factory(binding)) }
}
