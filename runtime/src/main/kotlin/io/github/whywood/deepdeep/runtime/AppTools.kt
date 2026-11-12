package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.session.AgentSession
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import okio.Path

data class AppToolContext(
    val questionCenter: QuestionCenter,
    val pluginTools: (SessionBinding) -> List<CodingTool> = { emptyList() },
    val pluginDeferredTools: (SessionBinding) -> List<DeferredTool> = { emptyList() },
    val extraToolFactories: List<(Path, SessionBinding) -> List<CodingTool>> = emptyList(),
    val questionWaitMs: () -> Long = { 10 * 60_000L },
)

fun appTools(
    cwd: Path,
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession? = { null },
): List<CodingTool> = buildList {
    add(askUserQuestionTool(context.questionCenter, binding, runCatching { context.questionWaitMs() }.getOrDefault(10 * 60_000L)))
    addAll(context.pluginTools(binding))
    addAll(deferredTools(context, binding, session))
    context.extraToolFactories.forEach { factory -> addAll(factory(cwd, binding)) }
}
