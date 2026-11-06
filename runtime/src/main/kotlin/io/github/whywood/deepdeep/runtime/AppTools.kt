package io.github.whywood.deepdeep.runtime

import dev.pi.coding.session.AgentSession
import dev.pi.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val questionCenter: QuestionCenter,
    val pluginTools: (SessionBinding) -> List<CodingTool> = { emptyList() },
    val pluginDeferredTools: (SessionBinding) -> List<DeferredTool> = { emptyList() },
    val extraToolFactories: List<(Path, SessionBinding) -> List<CodingTool>> = emptyList(),
)

fun appTools(
    cwd: Path,
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession? = { null },
): List<CodingTool> = buildList {
    add(askUserQuestionTool(context.questionCenter, binding))
    addAll(context.pluginTools(binding))
    addAll(deferredTools(context, binding, session))
    context.extraToolFactories.forEach { factory -> addAll(factory(cwd, binding)) }
}
