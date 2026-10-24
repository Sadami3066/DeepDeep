package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object BuiltinSkills {
    private val ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    fun ensure(repoDir: File, agentDir: File, id: String, content: String): Boolean {
        if (!ID_REGEX.matches(id)) return false
        val repoFile = File(File(repoDir, id), SKILL_FILE)
        val activeFile = File(File(File(agentDir, SKILLS_DIR), id), SKILL_FILE)
        val repoExisted = repoFile.isFile
        var changed = writeIfChanged(repoFile, content)
        if (activeFile.isFile || !repoExisted) {
            changed = writeIfChanged(activeFile, content) || changed
        }
        return changed
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
