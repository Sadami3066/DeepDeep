package io.github.whywood.deepdeep.runtime

data class PluginPromptSection(
    val pluginId: String,
    val id: String,
    val body: String,
    val priority: Int,
)

data class PluginPromptContribution(
    val pluginId: String,
    val priority: Int,
    val preamble: String? = null,
    val rules: String? = null,
    val sections: List<PluginPromptSection> = emptyList(),
)

class PluginPromptOverrides(
    val contributions: List<PluginPromptContribution> = emptyList(),
) {
    companion object {
        val None: PluginPromptOverrides = PluginPromptOverrides()
    }
}

internal data class PluginPromptMerge(
    val pluginsBody: String?,
    val preamble: String?,
    val rules: String?,
) {
    companion object {
        val None: PluginPromptMerge = PluginPromptMerge(null, null, null)
    }
}

internal const val PLUGIN_PROMPT_PER_PLUGIN_BUDGET: Int = 32 * 1024

internal const val PLUGIN_PROMPT_TOTAL_BUDGET: Int = 64 * 1024

internal fun mergePluginPromptOverrides(
    overrides: PluginPromptOverrides,
    legacyBody: String?,
    warn: (String) -> Unit = {},
): PluginPromptMerge {
    val candidates = overrides.contributions.withIndex()
        .sortedWith(compareByDescending<IndexedValue<PluginPromptContribution>> { it.value.priority }.thenBy { it.index })
    val accepted = mutableListOf<PluginPromptContribution>()
    var total = 0
    for (candidate in candidates) {
        val contribution = candidate.value
        val size = contributionSize(contribution)
        if (size > PLUGIN_PROMPT_PER_PLUGIN_BUDGET) {
            warn("plugin ${contribution.pluginId} prompt override exceeds $PLUGIN_PROMPT_PER_PLUGIN_BUDGET chars, skipped")
            continue
        }
        if (total + size > PLUGIN_PROMPT_TOTAL_BUDGET) {
            warn("plugin ${contribution.pluginId} prompt override exceeds total budget, skipped")
            continue
        }
        accepted += contribution
        total += size
    }
    val preamble = singleValue(accepted, "preamble", warn) { it.preamble?.trim()?.takeIf(String::isNotEmpty) }
    val rules = singleValue(accepted, "rules", warn) { it.rules?.trim()?.takeIf(String::isNotEmpty) }
    val sections = mergeSections(accepted, warn)
    val body = buildList {
        legacyBody?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        sections.forEach { section -> section.body.trim().takeIf { it.isNotEmpty() }?.let(::add) }
    }.joinToString("\n\n").takeIf { it.isNotEmpty() }
    return PluginPromptMerge(
        pluginsBody = body,
        preamble = preamble,
        rules = rules,
    )
}

private fun contributionSize(contribution: PluginPromptContribution): Int =
    (contribution.preamble?.length ?: 0) +
        (contribution.rules?.length ?: 0) +
        contribution.sections.sumOf { it.body.length }

private fun singleValue(
    accepted: List<PluginPromptContribution>,
    field: String,
    warn: (String) -> Unit,
    value: (PluginPromptContribution) -> String?,
): String? {
    val candidates = accepted.mapNotNull { contribution -> value(contribution)?.let { contribution to it } }
    val winner = candidates.firstOrNull()?.second
    if (candidates.size > 1) {
        val losers = candidates.drop(1).joinToString(", ") { it.first.pluginId }
        warn("multiple plugin prompt $field overrides, kept ${candidates.first().first.pluginId}, ignored: $losers")
    }
    return winner
}

private fun mergeSections(
    accepted: List<PluginPromptContribution>,
    warn: (String) -> Unit,
): List<PluginPromptSection> {
    val order = accepted.withIndex().associate { (index, contribution) -> contribution.pluginId to index }
    val all = accepted.flatMap { contribution ->
        contribution.sections.mapNotNull { section ->
            section.body.trim().takeIf { it.isNotEmpty() }?.let { body ->
                section.copy(pluginId = contribution.pluginId, body = body)
            }
        }
    }.sortedWith(
        compareByDescending<PluginPromptSection> { it.priority }
            .thenBy { order[it.pluginId] ?: Int.MAX_VALUE },
    )
    val seen = mutableSetOf<String>()
    val kept = all.filter { section ->
        if (seen.add(section.id)) {
            true
        } else {
            warn("duplicate plugin prompt section ${section.id}, kept ${seen.size} winner and dropped ${section.pluginId}")
            false
        }
    }
    return kept.sortedWith(
        compareBy<PluginPromptSection> { it.priority }
            .thenBy { order[it.pluginId] ?: Int.MAX_VALUE },
    )
}
