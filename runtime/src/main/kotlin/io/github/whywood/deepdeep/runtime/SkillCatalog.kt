package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.prompt.Skill
import io.github.whywood.deepdeep.sdk.coding.prompt.loadSkills
import io.github.whywood.deepdeep.sdk.coding.prompt.renderSkillPromptBlock
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

data class SkillExpansion(val name: String, val prompt: String)

data class SkillSummary(
    val name: String,
    val description: String,
)

interface SkillExpander {
    suspend fun expand(cwd: String, name: String, args: String): SkillExpansion?

    suspend fun list(cwd: String): List<SkillSummary>
}

class SkillCatalog(private val agentDir: File) : SkillExpander {
    override suspend fun expand(cwd: String, name: String, args: String): SkillExpansion? {
        val skill = discover(cwd).firstOrNull { it.name == name } ?: return null
        val content = runCatching {
            FileSystem.SYSTEM.read(skill.filePath.toPath()) { readUtf8() }
        }.getOrNull() ?: skill.content
        val fresh = skill.copy(content = content)
        val arguments = args.trim().takeIf { it.isNotEmpty() }
        return SkillExpansion(fresh.name, renderSkillPromptBlock(fresh, arguments))
    }

    override suspend fun list(cwd: String): List<SkillSummary> =
        discover(cwd)
            .map { skill -> SkillSummary(skill.name, skill.description) }
            .sortedBy { it.name.lowercase() }

    private suspend fun discover(cwd: String): List<Skill> = runCatching {
        loadSkills(
            cwd = File(cwd).absolutePath.toPath(),
            agentDir = agentDir.absolutePath.toPath(),
            fs = FileSystem.SYSTEM,
            projectTrusted = true,
        )
    }.getOrDefault(emptyList())
}
