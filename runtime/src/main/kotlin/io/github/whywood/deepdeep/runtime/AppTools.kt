package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val scheduleService: ScheduleService,
    val questionCenter: QuestionCenter,
)

fun appTools(cwd: Path, context: AppToolContext, binding: ScheduleBinding): List<CodingTool> =
    scheduleTools(cwd, context, binding) + askUserQuestionTool(context.questionCenter)
