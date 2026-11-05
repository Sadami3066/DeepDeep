package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.plugin.api.PluginSkill

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
    ): List<String> = mutex.withLock {
        withContext(ioDispatcher) {
            val skipped = mutableListOf<String>()
            skills.forEach { skill ->
                if (!writeSkill(repoDir, pluginId, skill)) skipped += skill.id
            }
            val currentIds = skills.mapTo(mutableSetOf()) { it.id }
            (previousIds - currentIds).forEach { id -> removeSkill(pluginId, id) }
            skills.forEach { skill ->
                if (active) {
                    if (!copyOwned(repoDir, skillsDir, pluginId, skill)) {
                        if (skill.id !in skipped) skipped += skill.id
                    }
                } else {
                    removeOwned(skillsDir, pluginId, skill.id)
                }
            }
            skipped
        }
    }

    suspend fun setActive(
        pluginId: String,
        skills: List<PluginSkill>,
        enabled: Boolean,
    ): List<String> = mutex.withLock {
        withContext(ioDispatcher) {
            val skipped = mutableListOf<String>()
            skills.forEach { skill ->
                if (enabled) {
                    if (!ensureRepoSkill(pluginId, skill) || !copyOwned(repoDir, skillsDir, pluginId, skill)) {
                        skipped += skill.id
                    }
                } else {
                    removeOwned(skillsDir, pluginId, skill.id)
                }
            }
            skipped
        }
    }

    suspend fun ensureRepo(pluginId: String, skills: List<PluginSkill>): List<String> = mutex.withLock {
        withContext(ioDispatcher) {
            skills.mapNotNull { skill -> skill.id.takeIf { !ensureRepoSkill(pluginId, skill) } }
        }
    }

    suspend fun remove(pluginId: String, skillIds: List<String>) = mutex.withLock {
        withContext(ioDispatcher) {
            skillIds.forEach { id -> removeSkill(pluginId, id) }
        }
    }

    private fun ensureRepoSkill(pluginId: String, skill: PluginSkill): Boolean {
        if (!isSafeId(skill.id)) throw IOException("invalid plugin skill id: ${skill.id}")
        val repo = File(repoDir, skill.id)
        if (marker(repo) == pluginId || File(repo, SKILL_FILE).isFile) {
            return writeSkill(repoDir, pluginId, skill)
        }
        val active = File(skillsDir, skill.id)
        if (marker(active) == pluginId) {
            active.copyRecursively(repo, overwrite = true)
            return true
        }
        return writeSkill(repoDir, pluginId, skill)
    }

    private fun writeSkill(parent: File, pluginId: String, skill: PluginSkill): Boolean {
        validateSkill(skill)
        val target = File(parent, skill.id)
        val targetMarker = marker(target)
        if (targetMarker != null && targetMarker != pluginId) {
            throw IOException("skill directory already exists: ${skill.id}")
        }
        if (target.exists() && targetMarker == null) {
            val existing = runCatching { File(target, SKILL_FILE).readText() }.getOrNull() ?: return false
            if (existing != skill.markdown) return false
        }
        publishSkillDirectory(parent, pluginId, skill)
        return true
    }

    private fun publishSkillDirectory(parent: File, pluginId: String, skill: PluginSkill) {
        parent.mkdirs()
        val target = File(parent, skill.id)
        val temp = File(parent, ".tmp-${skill.id}-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            File(temp, SKILL_FILE).writeText(skill.markdown, Charsets.UTF_8)
            skill.files.forEach { file ->
                val destination = File(temp, file.path)
                destination.parentFile?.mkdirs()
                destination.writeText(file.content.orEmpty(), Charsets.UTF_8)
            }
            File(temp, MARKER).writeText(pluginId, Charsets.UTF_8)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun copyOwned(sourceParent: File, targetParent: File, pluginId: String, skill: PluginSkill): Boolean {
        if (!isSafeId(skill.id)) return false
        val source = File(sourceParent, skill.id)
        if (marker(source) != pluginId) return false
        val target = File(targetParent, skill.id)
        val targetMarker = marker(target)
        if (target.exists() && targetMarker != pluginId) {
            if (targetMarker != null) throw IOException("skill directory already exists: ${skill.id}")
            val sourceMarkdown = runCatching { File(source, SKILL_FILE).readText() }.getOrNull() ?: return false
            val targetMarkdown = runCatching { File(target, SKILL_FILE).readText() }.getOrNull() ?: return false
            if (sourceMarkdown != targetMarkdown) return false
        }
        targetParent.mkdirs()
        val temp = File(targetParent, ".tmp-${skill.id}-${UUID.randomUUID()}")
        try {
            source.copyRecursively(temp, overwrite = true)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
        return true
    }

    private fun validateSkill(skill: PluginSkill) {
        if (!isSafeId(skill.id)) throw IOException("invalid plugin skill id: ${skill.id}")
        if (skill.markdown.isBlank()) throw IOException("empty plugin skill: ${skill.id}")
        val seen = mutableSetOf<String>()
        skill.files.forEach { file ->
            val path = safeRelativePath(file.path)
                ?: throw IOException("invalid plugin skill file path: ${file.path}")
            if (path == SKILL_FILE || path == MARKER) {
                throw IOException("reserved plugin skill file path: $path")
            }
            if (!seen.add(path)) throw IOException("duplicate plugin skill file: $path")
        }
    }

    private fun safeRelativePath(raw: String): String? {
        val replaced = raw.replace('\\', '/')
        if (replaced.isEmpty() || replaced.startsWith("/")) return null
        if (replaced.any { it.code < 0x20 || it.code == 0x7f } || replaced.contains(':')) return null
        val segments = replaced.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
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
