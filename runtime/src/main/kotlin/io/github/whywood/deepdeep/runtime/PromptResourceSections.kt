package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.prompt.SystemPromptInput
import io.github.whywood.deepdeep.sdk.coding.prompt.buildSystemPrompt
import io.github.whywood.deepdeep.sdk.coding.prompt.loadPromptResources
import okio.FileSystem
import okio.Path

internal class PromptResourceSections(
    private val cwd: Path,
    private val agentDir: Path,
    private val fs: FileSystem,
    private val excludedSkills: Set<String>,
) {
    private val trackedKeys = setOf("skills", "project_context", "addendum")
    @Volatile
    private var baseKeys: Set<String>? = null

    suspend fun current(): Map<String, String> {
        val sections = rendered() ?: return emptyMap()
        val knownBaseKeys = baseKeys ?: sections.keys.intersect(trackedKeys).also { baseKeys = it }
        val result = LinkedHashMap<String, String>()
        for (key in trackedKeys) {
            val value = sections[key]
            when {
                value != null -> result[key] = value
                key in knownBaseKeys -> result[key] = ""
            }
        }
        return result
    }

    private suspend fun rendered(): Map<String, String>? = runCatching {
        val resources = loadPromptResources(
            cwd = cwd,
            agentDir = agentDir,
            fs = fs,
            projectTrusted = true,
            excludedSkills = excludedSkills,
        )
        buildSystemPrompt(
            SystemPromptInput(
                preamble = resources.systemPromptOverride,
                addendum = resources.appendSystemPrompt,
                projectInstructions = resources.contextFiles,
                skills = resources.skills,
                cwd = cwd,
            ),
        ).sections
    }.getOrNull()
}
