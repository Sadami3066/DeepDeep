package io.github.whywood.deepdeep.plugin

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PluginSkillStore(
    private val skillsDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun sync(
        pluginId: String,
        skills: List<PluginSkill>,
        previousIds: List<String>,
    ) = mutex.withLock {
        withContext(ioDispatcher) {
            skills.forEach { skill -> writeSkill(pluginId, skill) }
            val currentIds = skills.mapTo(mutableSetOf()) { it.id }
            (previousIds - currentIds).forEach { id -> removeSkill(pluginId, id) }
        }
    }

    suspend fun remove(pluginId: String, skillIds: List<String>) = mutex.withLock {
        withContext(ioDispatcher) {
            skillIds.forEach { id -> removeSkill(pluginId, id) }
        }
    }

    private fun writeSkill(pluginId: String, skill: PluginSkill) {
        if (!isSafeId(skill.id)) throw IOException("invalid plugin skill id: ${skill.id}")
        if (skill.markdown.isBlank()) throw IOException("empty plugin skill: ${skill.id}")
        val target = File(skillsDir, skill.id)
        if (target.exists()) {
            val marker = runCatching { File(target, MARKER).readText().trim() }.getOrNull()
            if (marker != pluginId) {
                throw IOException("skill directory already exists: ${skill.id}")
            }
        }
        skillsDir.mkdirs()
        val temp = File(skillsDir, ".tmp-${skill.id}-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            File(temp, SKILL_FILE).writeText(skill.markdown, Charsets.UTF_8)
            File(temp, MARKER).writeText(pluginId, Charsets.UTF_8)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun removeSkill(pluginId: String, id: String) {
        if (!isSafeId(id)) return
        val target = File(skillsDir, id)
        if (!target.isDirectory) return
        val marker = runCatching { File(target, MARKER).readText().trim() }.getOrNull()
        if (marker == pluginId) target.deleteRecursively()
    }

    private fun replaceDirectory(source: File, target: File) {
        val parent = target.parentFile ?: throw IOException("missing skill parent")
        parent.mkdirs()
        val backup = File(parent, ".old-${target.name}-${UUID.randomUUID()}")
        if (target.exists() && !target.renameTo(backup)) {
            target.deleteRecursively()
        }
        if (!source.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            throw IOException("unable to replace ${target.path}")
        }
        backup.deleteRecursively()
    }

    private fun isSafeId(id: String): Boolean {
        if (id.isEmpty() || id.length > 64 || id == "." || id == "..") return false
        return id.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private companion object {
        const val SKILL_FILE = "SKILL.md"
        const val MARKER = ".deepdeep-plugin"
    }
}
