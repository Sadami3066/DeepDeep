package io.github.whywood.deepdeep.data

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val SKILLS_DIRECTORY = "skills"
private const val PROJECT_SKILLS_DIRECTORY = ".pi/skills"
private const val MAX_SKILL_ID_LENGTH = 64

data class InstalledSkill(
    val id: String,
    val name: String,
    val description: String,
    val disableModelInvocation: Boolean,
    val globalEnabled: Boolean,
    val installedAt: Long,
)

class SkillRepository(
    private val repoDir: File,
    private val agentDir: File,
    private val homeDir: File,
    private val workspacesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val globalSkillsDir = File(agentDir, SKILLS_DIRECTORY)

    suspend fun list(): List<InstalledSkill> = locked {
        try {
            installedSkills()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emptyList()
        }
    }

    suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult = locked {
        when (val result = SkillArchive.parse(bytes, fallbackName)) {
            is SkillArchiveResult.Failure -> result
            is SkillArchiveResult.Success -> try {
                writeSkillDirectory(result.skill, File(repoDir, result.skill.id))
                refreshActiveCopies(result.skill.id)
                SkillArchiveResult.Success(result.skill)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SkillArchiveResult.Failure(SkillArchiveError.Io)
            }
        }
    }

    suspend fun setGlobalEnabled(id: String, enabled: Boolean): Boolean = locked {
        if (!isSafeSkillId(id)) return@locked false
        if (!File(File(repoDir, id), SKILL_MD_FILE).isFile) return@locked false
        try {
            val global = File(globalSkillsDir, id)
            if (enabled) {
                copyDirectory(File(repoDir, id), global)
                removeProjectCopies(id)
            } else {
                global.deleteRecursively()
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            false
        }
    }

    suspend fun delete(id: String): Boolean = locked {
        if (!isSafeSkillId(id)) return@locked false
        try {
            File(repoDir, id).deleteRecursively()
            File(globalSkillsDir, id).deleteRecursively()
            removeProjectCopies(id)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            false
        }
    }

    suspend fun readContent(id: String): String? = locked {
        if (!isSafeSkillId(id)) return@locked null
        try {
            val file = File(File(repoDir, id), SKILL_MD_FILE)
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
    }

    suspend fun projectEnabledIds(cwd: String): Set<String> = locked {
        if (cwd.isBlank()) return@locked emptySet()
        try {
            projectSkillDirectories(cwd)
                .map { it.name }
                .filter { isSafeSkillId(it) }
                .toSet()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emptySet()
        }
    }

    suspend fun applyProjectSkills(cwd: String, enabledIds: Set<String>): Boolean = locked {
        if (cwd.isBlank()) return@locked false
        try {
            val root = File(cwd, PROJECT_SKILLS_DIRECTORY)
            root.mkdirs()
            val repoIds = repoSkillDirectories().map { it.name }.toSet()
            root.listFiles()
                ?.filter { it.isDirectory && it.name in repoIds && it.name !in enabledIds }
                ?.forEach { it.deleteRecursively() }
            enabledIds.forEach { id ->
                if (!isSafeSkillId(id)) return@forEach
                val source = File(repoDir, id)
                if (!File(source, SKILL_MD_FILE).isFile) return@forEach
                copyDirectory(source, File(root, id))
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            false
        }
    }

    private fun installedSkills(): List<InstalledSkill> = repoSkillDirectories()
        .mapNotNull { directory ->
            val content = runCatching {
                File(directory, SKILL_MD_FILE).readText(Charsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            val metadata = parseSkillMetadata(
                content = content,
                fallbackName = directory.name,
                wrapperName = directory.name,
            ) ?: return@mapNotNull null
            InstalledSkill(
                id = directory.name,
                name = metadata.name,
                description = metadata.description,
                disableModelInvocation = metadata.disableModelInvocation,
                globalEnabled = File(globalSkillsDir, directory.name).isDirectory,
                installedAt = directory.lastModified(),
            )
        }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

    private fun repoSkillDirectories(): List<File> = repoDir.listFiles()
        ?.filter { directory ->
            directory.isDirectory &&
                isSafeSkillId(directory.name) &&
                File(directory, SKILL_MD_FILE).isFile
        }
        .orEmpty()

    private fun projectSkillDirectories(cwd: String): List<File> = File(cwd, PROJECT_SKILLS_DIRECTORY)
        .listFiles()
        ?.filter { directory ->
            directory.isDirectory && File(directory, SKILL_MD_FILE).isFile
        }
        .orEmpty()

    private fun workspaceDirectories(): List<File> = workspacesDir.listFiles()
        ?.filter { it.isDirectory }
        .orEmpty()

    private fun projectSkillsRoot(cwd: String): File = File(cwd, PROJECT_SKILLS_DIRECTORY)

    private fun projectCopyDirectories(id: String): List<File> = buildList {
        add(File(projectSkillsRoot(homeDir.absolutePath), id))
        workspaceDirectories().forEach { workspace ->
            add(File(projectSkillsRoot(workspace.absolutePath), id))
        }
    }.filter { it.isDirectory }

    private fun refreshActiveCopies(id: String) {
        val source = File(repoDir, id)
        if (!File(source, SKILL_MD_FILE).isFile) return
        val global = File(globalSkillsDir, id)
        if (global.isDirectory) copyDirectory(source, global)
        projectCopyDirectories(id).forEach { copyDirectory(source, it) }
    }

    private fun removeProjectCopies(id: String) {
        File(projectSkillsRoot(homeDir.absolutePath), id).deleteRecursively()
        workspaceDirectories().forEach { workspace ->
            File(projectSkillsRoot(workspace.absolutePath), id).deleteRecursively()
        }
    }

    private fun writeSkillDirectory(skill: ParsedSkill, target: File) {
        val parent = target.parentFile ?: repoDir
        parent.mkdirs()
        val temp = File(parent, ".tmp-${target.name}-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            File(temp, SKILL_MD_FILE).writeText(skill.content, Charsets.UTF_8)
            skill.files.forEach { file ->
                if (!isSafeRelativePath(file.path)) return@forEach
                val destination = File(temp, file.path)
                destination.parentFile?.mkdirs()
                destination.writeBytes(file.bytes)
            }
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun copyDirectory(source: File, target: File) {
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val temp = File(parent, ".tmp-${target.name}-${UUID.randomUUID()}")
        try {
            source.copyRecursively(temp, overwrite = true)
            replaceDirectory(temp, target)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun replaceDirectory(source: File, target: File) {
        val parent = target.parentFile ?: throw IOException("Missing parent directory")
        parent.mkdirs()
        val backup = File(parent, ".old-${target.name}-${UUID.randomUUID()}")
        if (target.exists() && !target.renameTo(backup)) {
            target.deleteRecursively()
        }
        if (!source.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            throw IOException("Unable to replace ${target.path}")
        }
        backup.deleteRecursively()
    }

    private fun isSafeSkillId(id: String): Boolean {
        if (id.isEmpty() || id == "." || id == ".." || id.length > MAX_SKILL_ID_LENGTH) return false
        return id.all { character ->
            character.isLetterOrDigit() || character == '-' || character == '_' || character == '.'
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith('/') || path.contains('\\')) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        withContext(ioDispatcher) { block() }
    }
}
