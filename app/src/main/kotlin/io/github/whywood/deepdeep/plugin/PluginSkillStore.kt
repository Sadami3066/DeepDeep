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
    private val repoDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun sync(
        pluginId: String,
        skills: List<PluginSkill>,
        previousIds: List<String>,
        active: Boolean,
    ) = mutex.withLock {
        withContext(ioDispatcher) {
            skills.forEach { skill -> writeSkill(repoDir, pluginId, skill) }
            val currentIds = skills.mapTo(mutableSetOf()) { it.id }
            (previousIds - currentIds).forEach { id -> removeSkill(pluginId, id) }
            skills.forEach { skill ->
                if (active) {
                    copyOwned(repoDir, skillsDir, pluginId, skill.id)
                } else {
                    removeOwned(skillsDir, pluginId, skill.id)
                }
            }
        }
    }

    suspend fun setActive(
        pluginId: String,
        skills: List<PluginSkill>,
        enabled: Boolean,
    ) = mutex.withLock {
        withContext(ioDispatcher) {
            skills.forEach { skill ->
                if (enabled) {
                    ensureRepoSkill(pluginId, skill)
                    copyOwned(repoDir, skillsDir, pluginId, skill.id)
                } else {
                    removeOwned(skillsDir, pluginId, skill.id)
                }
            }
        }
    }

    suspend fun ensureRepo(pluginId: String, skills: List<PluginSkill>) = mutex.withLock {
        withContext(ioDispatcher) {
            skills.forEach { skill -> ensureRepoSkill(pluginId, skill) }
        }
    }

    suspend fun remove(pluginId: String, skillIds: List<String>) = mutex.withLock {
        withContext(ioDispatcher) {
            skillIds.forEach { id -> removeSkill(pluginId, id) }
        }
    }

    private fun ensureRepoSkill(pluginId: String, skill: PluginSkill) {
        if (!isSafeId(skill.id)) throw IOException("invalid plugin skill id: ${skill.id}")
        val repo = File(repoDir, skill.id)
        if (marker(repo) == pluginId || File(repo, SKILL_FILE).isFile) {
            writeSkill(repoDir, pluginId, skill)
            return
        }
        val active = File(skillsDir, skill.id)
        if (marker(active) == pluginId) {
            active.copyRecursively(repo, overwrite = true)
            return
        }
        writeSkill(repoDir, pluginId, skill)
    }

    private fun writeSkill(parent: File, pluginId: String, skill: PluginSkill) {
        if (!isSafeId(skill.id)) throw IOException("invalid plugin skill id: ${skill.id}")
        if (skill.markdown.isBlank()) throw IOException("empty plugin skill: ${skill.id}")
        val target = File(parent, skill.id)
        val targetMarker = marker(target)
        if (targetMarker != null && targetMarker != pluginId) {
            throw IOException("skill directory already exists: ${skill.id}")
        }
        if (target.exists() && targetMarker == null) {
            throw IOException("skill directory already exists: ${skill.id}")
        }
        parent.mkdirs()
        val temp = File(parent, ".tmp-${skill.id}-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            File(temp, SKILL_FILE).writeText(skill.markdown, Charsets.UTF_8)
            File(temp, MARKER).writeText(pluginId, Charsets.UTF_8)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun copyOwned(sourceParent: File, targetParent: File, pluginId: String, id: String) {
        if (!isSafeId(id)) return
        val source = File(sourceParent, id)
        if (marker(source) != pluginId) {
            throw IOException("missing plugin skill: $id")
        }
        val target = File(targetParent, id)
        val targetMarker = marker(target)
        if (target.exists() && targetMarker != pluginId) {
            throw IOException("skill directory already exists: $id")
        }
        targetParent.mkdirs()
        val temp = File(targetParent, ".tmp-$id-${UUID.randomUUID()}")
        try {
            source.copyRecursively(temp, overwrite = true)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun removeSkill(pluginId: String, id: String) {
        removeOwned(skillsDir, pluginId, id)
        removeOwned(repoDir, pluginId, id)
    }

    private fun removeOwned(parent: File, pluginId: String, id: String) {
        if (!isSafeId(id)) return
        val target = File(parent, id)
        if (!target.isDirectory) return
        if (marker(target) == pluginId) target.deleteRecursively()
    }

    private fun marker(directory: File): String? {
        if (!directory.isDirectory) return null
        return runCatching { File(directory, MARKER).readText().trim() }.getOrNull()
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
