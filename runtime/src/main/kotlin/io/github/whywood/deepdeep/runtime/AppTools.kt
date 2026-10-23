package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import java.io.File
import okio.Path

data class AppToolContext(
    val scheduleStore: ScheduleStore,
    val questionCenter: QuestionCenter,
    val workspaceStore: WorkspaceStore,
    val homeDir: File,
)

fun appTools(cwd: Path, context: AppToolContext): List<CodingTool> =
    scheduleTools(cwd, context) + askUserQuestionTool(context.questionCenter)
