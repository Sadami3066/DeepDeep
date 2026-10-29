package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val questionCenter: QuestionCenter,
    val pluginController: PluginController? = null,
    val pluginTools: (SessionBinding) -> List<CodingTool> = { emptyList() },
    val extraToolFactories: List<(Path, SessionBinding) -> List<CodingTool>> = emptyList(),
)

fun appTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = buildList {
    add(askUserQuestionTool(context.questionCenter, binding))
    pluginSetupTool(context.pluginController)?.let(::add)
    addAll(context.pluginTools(binding))
    context.extraToolFactories.forEach { factory -> addAll(factory(cwd, binding)) }
}
