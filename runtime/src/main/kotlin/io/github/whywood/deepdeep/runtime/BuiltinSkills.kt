package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BuiltinSkillFile(
    val path: String,
    val content: String,
)

object BuiltinSkills {
    private val ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    fun ensure(repoDir: File, agentDir: File, id: String, content: String): Boolean =
        ensure(repoDir, agentDir, id, listOf(BuiltinSkillFile(SKILL_FILE, content)))

    fun ensure(repoDir: File, agentDir: File, id: String, files: List<BuiltinSkillFile>): Boolean {
        if (!ID_REGEX.matches(id)) return false
        if (files.isEmpty()) return false
        val paths = files.map { file -> safeRelativePath(file.path) ?: return false }
        if (paths.toSet().size != paths.size) return false
        val repoRoot = File(repoDir, id)
        val activeRoot = File(File(agentDir, SKILLS_DIR), id)
        val repoExisted = File(repoRoot, SKILL_FILE).isFile
        val activeExisted = File(activeRoot, SKILL_FILE).isFile
        var changed = false
        files.forEach { file ->
            changed = writeIfChanged(File(repoRoot, safeRelativePath(file.path)!!), file.content) || changed
        }
        if (activeExisted || !repoExisted) {
            files.forEach { file ->
                changed = writeIfChanged(File(activeRoot, safeRelativePath(file.path)!!), file.content) || changed
            }
        }
        return changed
    }

    private fun safeRelativePath(path: String): String? {
        val raw = path.replace('\\', '/')
        if (raw.startsWith("/")) return null
        val normalized = raw.trim('/')
        if (normalized.isEmpty()) return null
        val parts = normalized.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return normalized
    }

    private fun writeIfChanged(target: File, content: String): Boolean {
        if (target.isFile && runCatching { target.readText() }.getOrNull() == content) return false
        val directory = target.parentFile ?: return false
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val temp = File.createTempFile(target.name + ".", ".tmp", directory)
        try {
            temp.writeText(content)
            runCatching {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private const val SKILL_FILE = "SKILL.md"
    private const val SKILLS_DIR = "skills"
}
